#!/usr/bin/env python3
"""Validate Rumble bot submissions and generate their catalog using only the Python standard library."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import unicodedata
from dataclasses import dataclass, replace
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

ALLOWED_LICENSES = {"MIT", "Apache-2.0", "BSD-3-Clause", "GPL-3.0-or-later"}
PLATFORMS = {
    "csharp": (".cs", "C#", "Robocode.TankRoyale.BotApi"),
    "java": (".java", "JVM", "dev.robocode.tankroyale.botapi"),
    "python": (".py", "Python", "robocode_tank_royale"),
    "typescript": (".ts", "TypeScript", "@robocode/tank-royale"),
}
FORBIDDEN_SUFFIXES = {".dll", ".exe", ".jar", ".so", ".dylib", ".pyc", ".class", ".zip", ".tar", ".gz"}
FORBIDDEN_TOKENS = ("processbuilder", "runtime.exec", "subprocess", "os.system", "child_process", "system.diagnostics.process", "socket", "ctypes", "dllimport", "eval(")
LEET = str.maketrans({"0": "o", "1": "l", "3": "e", "4": "a", "5": "s", "7": "t", "8": "b", "9": "g", "@": "a", "$": "s", "!": "i", "|": "l"})


class ValidationError(Exception):
    """An actionable submission validation failure."""


@dataclass(frozen=True)
class Bot:
    directory: Path
    platform_key: str
    config: dict[str, Any]
    source_hash: str
    team_members: tuple["Bot", ...] = ()

    @property
    def name(self) -> str:
        return str(self.config["name"])

    @property
    def display_name(self) -> str:
        return f"{self.name} {self.config['version']}"

    @property
    def platform(self) -> str:
        if self.team_members:
            platforms = sorted({member.platform for member in self.team_members})
            return platforms[0] if len(platforms) == 1 else "Mixed"
        return PLATFORMS[self.platform_key][1]

    @property
    def team_member_identities(self) -> list[str]:
        value = self.config.get("teamMembers", [])
        return list(value) if isinstance(value, list) else []


def read_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValidationError(f"{path}: invalid JSON: {error}") from error
    if not isinstance(value, dict):
        raise ValidationError(f"{path}: expected a JSON object")
    return value


def skeleton(name: str) -> str:
    normalized = unicodedata.normalize("NFKD", name).casefold().translate(LEET)
    return "".join(character for character in normalized if character.isalnum())


def tree_hash(directory: Path) -> str:
    digest = hashlib.sha256()
    for path in sorted(candidate for candidate in directory.rglob("*") if candidate.is_file() and "__pycache__" not in candidate.parts):
        digest.update(path.relative_to(directory).as_posix().encode("utf-8"))
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return f"sha256:{digest.hexdigest()}"


def bot_directories(root: Path) -> list[tuple[str, Path]]:
    bots_root = root / "bots"
    result: list[tuple[str, Path]] = []
    for platform_dir in sorted(path for path in bots_root.iterdir() if path.is_dir()):
        if platform_dir.name not in PLATFORMS:
            raise ValidationError(f"{platform_dir}: unsupported platform directory")
        result.extend((platform_dir.name, path) for path in sorted(platform_dir.iterdir()) if path.is_dir())
    return result


def validate_bot(platform_key: str, directory: Path, *, smoke: bool) -> Bot:
    source_extension, expected_platform, api_token = PLATFORMS[platform_key]
    config = read_json(directory / f"{directory.name}.json")
    for field in ("name", "version", "authors", "license"):
        if not config.get(field):
            raise ValidationError(f"{directory}: missing required `{field}` in {directory.name}.json")
    if config["name"] != directory.name:
        raise ValidationError(f"{directory}: directory name must equal config name `{config['name']}`")
    if not isinstance(config["authors"], list) or not all(isinstance(author, str) and author for author in config["authors"]):
        raise ValidationError(f"{directory}: `authors` must be a non-empty list of display names")
    if config["license"] not in ALLOWED_LICENSES:
        raise ValidationError(f"{directory}: `license` must be one of {', '.join(sorted(ALLOWED_LICENSES))}")
    if "teamMembers" in config:
        members = config["teamMembers"]
        if not isinstance(members, list) or len(members) != 2 or not all(isinstance(member, str) and member for member in members):
            raise ValidationError(f"{directory}: `teamMembers` must contain exactly two `<name> <version>` member identities")
        extra = sorted(path.relative_to(directory).as_posix() for path in directory.rglob("*") if path.name != f"{directory.name}.json")
        if extra:
            raise ValidationError(f"{directory}: a team directory must contain only {directory.name}.json, found {', '.join(extra)}")
        return Bot(directory, platform_key, config, tree_hash(directory))
    if not config.get("platform"):
        raise ValidationError(f"{directory}: missing required `platform` in {directory.name}.json")
    if config["platform"] != expected_platform:
        raise ValidationError(f"{directory}: `{platform_key}` entries require platform `{expected_platform}`")
    for suffix in (".sh", ".cmd"):
        if not (directory / f"{directory.name}{suffix}").is_file():
            raise ValidationError(f"{directory}: missing required {directory.name}{suffix} boot script")
    source_files = list(directory.rglob(f"*{source_extension}"))
    if not source_files:
        raise ValidationError(f"{directory}: no {source_extension} source file found")
    source_text = "\n".join(path.read_text(encoding="utf-8") for path in source_files).lower()
    if api_token.lower() not in source_text:
        raise ValidationError(f"{directory}: source must reference the official Tank Royale {expected_platform} Bot API")
    for path in directory.rglob("*"):
        if not path.is_file():
            continue
        if "__pycache__" in path.parts:
            continue
        if path.suffix.lower() in FORBIDDEN_SUFFIXES:
            raise ValidationError(f"{path}: binary and archive artifacts are not allowed")
    for token in FORBIDDEN_TOKENS:
        if token in source_text:
            raise ValidationError(f"{directory}: source contains restricted construct `{token}` for moderator review")
    bot = Bot(directory, platform_key, config, tree_hash(directory))
    if smoke:
        smoke_bot(bot)
    return bot


def resolve_teams(bots: list[Bot]) -> list[Bot]:
    """Bind every team to its member bots, which may live under any platform directory."""
    by_name: dict[str, Bot] = {}
    for bot in bots:
        duplicate = by_name.get(bot.name)
        if duplicate is not None:
            raise ValidationError(f"{bot.directory}: bot name `{bot.name}` is already used by {duplicate.directory}")
        by_name[bot.name] = bot
    by_identity = {bot.display_name: bot for bot in bots}
    resolved: list[Bot] = []
    for bot in bots:
        if not bot.team_member_identities:
            resolved.append(bot)
            continue
        members: list[Bot] = []
        for identity in bot.team_member_identities:
            member = by_identity.get(identity)
            if member is None:
                raise ValidationError(f"{bot.directory}: unknown team member `{identity}`")
            if member.team_member_identities:
                raise ValidationError(f"{bot.directory}: team member `{identity}` cannot be another team")
            members.append(member)
        resolved.append(replace(bot, team_members=tuple(members)))
    return resolved


def smoke_bot(bot: Bot) -> None:
    script = bot.directory / f"{bot.name}.sh"
    python_executable = str(Path(sys.executable))
    if os.name == "nt":
        python_executable = "/" + python_executable[0].lower() + python_executable[2:].replace("\\", "/")
    environment = os.environ | {"RUMBLE_PYTHON": python_executable, "RUMBLE_SMOKE": "1"}
    try:
        completed = subprocess.run(["sh", str(script)], cwd=bot.directory, env=environment, text=True, capture_output=True, timeout=20, check=False)
    except OSError as error:
        raise ValidationError(f"{bot.directory}: cannot start source-run smoke check: {error}") from error
    if completed.returncode != 0 or f"RUMBLE_SMOKE_READY {bot.name}" not in completed.stdout:
        raise ValidationError(f"{bot.directory}: source-run smoke check failed: {completed.stderr.strip() or completed.stdout.strip()}")


def check_governance(bots: list[Bot], root: Path, owner: str) -> None:
    owners = read_json(root / "bots" / "owners.json") if (root / "bots" / "owners.json").exists() else {"owners": []}
    banned = read_json(root / "bots" / "banned.json")
    banned_accounts = {entry["account"] for entry in banned.get("bannedOwners", [])}
    disqualified_names = {entry["bot"] for entry in banned.get("disqualifiedBots", [])}
    if owner in banned_accounts:
        raise ValidationError(f"owner `{owner}` is banned from submissions")
    catalog_entries = read_json(root / "bots" / "index.json").get("bots", []) if (root / "bots" / "index.json").exists() else []
    catalog_by_name = {entry["name"]: entry for entry in catalog_entries if entry.get("status") == "active"}
    submitted_bots = [
        bot
        for bot in bots
        if (previous := catalog_by_name.get(bot.name)) is None
        or previous.get("version") != bot.config["version"]
        or previous.get("sourceHash") != bot.source_hash
    ]
    owner_by_bot = {name: record for record in owners.get("owners", []) for name in record.get("bots", [])}
    seen_skeletons: dict[str, str] = {}
    for bot in submitted_bots:
        if bot.name in disqualified_names:
            raise ValidationError(f"bot `{bot.name}` is disqualified")
        bot_skeleton = skeleton(bot.name)
        previous = seen_skeletons.get(bot_skeleton)
        if previous is not None and previous != bot.name:
            raise ValidationError(f"bot `{bot.name}` is confusable with `{previous}`")
        seen_skeletons[bot_skeleton] = bot.name
        existing_owner = owner_by_bot.get(bot.name)
        if existing_owner is not None and owner not in existing_owner.get("accounts", []):
            raise ValidationError(f"bot `{bot.name}` belongs to owner `{existing_owner['ownerId']}`")
        previous = catalog_by_name.get(bot.name)
        if previous is not None and previous.get("version") == bot.config["version"] and previous.get("sourceHash") != bot.source_hash:
            raise ValidationError(f"bot `{bot.name}` changed source without increasing its version")
    active_by_owner: dict[str, int] = {}
    for bot in submitted_bots:
        if bot.name not in owner_by_bot:
            active_by_owner[owner] = active_by_owner.get(owner, 0) + 1
    if active_by_owner.get(owner, 0) > 5:
        raise ValidationError(f"owner `{owner}` exceeds the five active bot slot limit")


def generated_catalog(bots: list[Bot], root: Path, owner: str) -> tuple[dict[str, Any], dict[str, Any]]:
    existing_owners = read_json(root / "bots" / "owners.json") if (root / "bots" / "owners.json").exists() else {"schemaVersion": 1, "owners": []}
    existing_catalog = read_json(root / "bots" / "index.json") if (root / "bots" / "index.json").exists() else {"bots": []}
    owner_by_bot = {bot_name: record["ownerId"] for record in existing_owners.get("owners", []) for bot_name in record.get("bots", [])}
    records: dict[str, list[str]] = {}
    for bot in bots:
        bot_owner = owner_by_bot.get(bot.name, owner)
        records.setdefault(bot_owner, []).append(bot.name)
    owners = {
        record["ownerId"]: {
            "accounts": record.get("accounts", []),
            "bots": set(record.get("bots", [])),
        }
        for record in existing_owners.get("owners", [])
    }
    for owner_id, names in records.items():
        owner_record = owners.setdefault(owner_id, {"accounts": [owner_id], "bots": set()})
        owner_record["bots"].update(names)
    owner_data = {
        "schemaVersion": 1,
        "owners": [
            {
                "ownerId": owner_id,
                "accounts": owner_record["accounts"],
                "bots": sorted(owner_record["bots"]),
                "activeSlots": len(owner_record["bots"]),
            }
            for owner_id, owner_record in sorted(owners.items())
        ],
    }
    today = datetime.now(UTC).date().isoformat()
    current_by_name = {bot.name: bot for bot in bots}
    history = []
    for entry in existing_catalog.get("bots", []):
        current = current_by_name.get(entry.get("name"))
        if current is not None and entry.get("version") != current.config["version"] and entry.get("status") == "active":
            history.append(entry | {"status": "superseded"})
        elif current is None:
            history.append(entry)
    active = []
    for bot in sorted(bots, key=lambda item: item.name.casefold()):
        previous = next((entry for entry in existing_catalog.get("bots", []) if entry.get("name") == bot.name and entry.get("version") == bot.config["version"]), None)
        team_members = list(bot.team_member_identities)
        active.append({"name": bot.name, "version": bot.config["version"], "platform": bot.platform, "path": bot.directory.relative_to(root).as_posix(), "sourceHash": bot.source_hash, "owner": owner_by_bot.get(bot.name, owner), "authors": bot.config["authors"], "addedAt": previous.get("addedAt", today) if previous else today, "status": "active", "teamMembers": team_members})
    catalog = {"schemaVersion": 1, "generatedAt": datetime.now(UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z"), "commit": os.environ.get("GITHUB_SHA", "local"), "bots": history + active}
    return catalog, owner_data


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--owner", required=True)
    parser.add_argument("--smoke", action="store_true")
    parser.add_argument("--generate", action="store_true")
    arguments = parser.parse_args()
    root = arguments.root.resolve()
    try:
        bots = resolve_teams([validate_bot(platform, directory, smoke=arguments.smoke) for platform, directory in bot_directories(root)])
        check_governance(bots, root, arguments.owner)
        if arguments.generate:
            catalog, owners = generated_catalog(bots, root, arguments.owner)
            (root / "bots" / "index.json").write_text(json.dumps(catalog, indent=2) + "\n", encoding="utf-8")
            (root / "bots" / "owners.json").write_text(json.dumps(owners, indent=2) + "\n", encoding="utf-8")
    except ValidationError as error:
        print(f"validation failed: {error}", file=sys.stderr)
        return 1
    print(f"validated {len(bots)} bot(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
