#!/usr/bin/env python3
"""Verify a generated catalog PR against trusted main without executing PR content."""

from __future__ import annotations

import argparse
import base64
import json
import os
import re
import subprocess
from datetime import UTC, datetime, timedelta
from pathlib import Path

from moderation.assess import classify_paths
from moderation.moderate import CONTEXT, GitHubError, api
from scripts.validate_bot import ValidationError, bot_directories, read_json, resolve_teams, validate_bot

CATALOG_FILES = {"bots/index.json", "bots/owners.json"}
APP_LOGIN = "tank-royale-rumble-moderator[bot]"
PUBLISHER_LOGIN = "tank-royale-rumble-publisher[bot]"


class VerificationError(Exception):
    pass


def git(*args: str) -> str:
    result = subprocess.run(["git", *args], capture_output=True, text=True, check=True)
    return result.stdout.strip()


def source_paths(number: int) -> list[str]:
    paths = []
    page = 1
    while True:
        files = api("GET", f"pulls/{number}/files?per_page=100&page={page}")
        paths.extend(item["filename"] for item in files)
        if len(files) < 100:
            return paths
        page += 1


def unpublished_bot_paths(root: Path) -> set[str]:
    catalog = read_json(root / "bots" / "index.json")
    active = {entry["name"]: entry for entry in catalog["bots"] if entry["status"] == "active"}
    bots = resolve_teams([validate_bot(platform, directory, smoke=False)
                          for platform, directory in bot_directories(root)])
    return {
        bot.directory.relative_to(root).as_posix()
        for bot in bots
        if (entry := active.get(bot.name)) is None
        or entry["version"] != bot.config["version"]
        or entry["sourceHash"] != bot.source_hash
    }


def verify(number: int, source_number: int) -> tuple[str, str]:
    pr = api("GET", f"pulls/{number}")
    head = pr["head"]["sha"]
    base = pr["base"]["sha"]
    if (pr["state"] != "open" or pr["draft"] or pr["base"]["ref"] != "main"
            or pr["head"]["repo"]["full_name"] != os.environ["GITHUB_REPOSITORY"]
            or pr["user"]["login"] != PUBLISHER_LOGIN
            or not re.fullmatch(rf"catalog/pr-{source_number}-[0-9]+-[0-9]+", pr["head"]["ref"])):
        raise VerificationError("Not a publisher-created catalog PR on main")
    if git("rev-parse", "HEAD") != base:
        raise VerificationError("Main moved after catalog generation; rerun the publisher on current main")
    source = api("GET", f"pulls/{source_number}")
    if (source["merged_at"] is None or source["base"]["ref"] != "main"
            or not re.fullmatch(r"[A-Za-z0-9-]+", source["user"]["login"])):
        raise VerificationError("Source submission is not a merged main PR")
    source_directory = classify_paths(source_paths(source_number))[0]
    if source_directory is None:
        raise VerificationError("Source PR did not change exactly one bot directory")
    pending_paths = unpublished_bot_paths(Path.cwd())
    if pending_paths != {source_directory}:
        raise VerificationError(f"Other unpublished bot changes need ownership review: {sorted(pending_paths)}")
    statuses = api("GET", f"commits/{source['head']['sha']}/statuses?per_page=100")
    moderation = next((item for item in statuses if item["context"] == CONTEXT), None)
    if (moderation is None or moderation["state"] != "success"
            or (moderation.get("creator") or {}).get("login") != APP_LOGIN):
        raise VerificationError("Source bot has no successful moderator App status")
    git("merge-base", "--is-ancestor", source["merge_commit_sha"], base)
    subprocess.run(["git", "fetch", "--no-tags", "origin", f"+refs/pull/{number}/head:refs/remotes/origin/catalog-verify"], check=True)
    if git("rev-parse", "refs/remotes/origin/catalog-verify") != head:
        raise VerificationError("Catalog PR head changed during verification")
    if git("rev-parse", f"{head}^") != base:
        raise VerificationError("Catalog commit is not based on current main")
    changed = set(git("diff", "--name-only", base, head).splitlines())
    if not changed or not changed <= CATALOG_FILES:
        raise VerificationError("Catalog PR changes unexpected files")
    proposed = {path: git("show", f"{head}:{path}") + "\n" for path in CATALOG_FILES}
    try:
        generated_at = json.loads(proposed["bots/index.json"])["generatedAt"]
        timestamp = datetime.fromisoformat(generated_at.replace("Z", "+00:00"))
        if (timestamp.tzinfo is None or timestamp.utcoffset() != timedelta(0)
                or not datetime.now(UTC) - timedelta(days=2) <= timestamp <= datetime.now(UTC) + timedelta(minutes=5)):
            raise ValueError("timestamp outside publication window")
    except (ValueError, KeyError, TypeError) as error:
        raise VerificationError(f"Invalid generatedAt timestamp: {error}") from error
    env = os.environ.copy()
    env["GITHUB_SHA"] = base
    result = subprocess.run(
        ["python", "scripts/validate_bot.py", "--root", ".", "--owner", source["user"]["login"],
         "--generate", "--generated-at", generated_at], env=env, capture_output=True, text=True,
    )
    if result.returncode:
        raise VerificationError(f"Trusted catalog generation failed: {result.stderr[:300]}")
    for path in CATALOG_FILES:
        if Path(path).read_text(encoding="utf-8") != proposed[path]:
            raise VerificationError(f"Generated content differs: {path}")
    return head, base


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pr", type=int, required=True)
    parser.add_argument("--source-pr", type=int, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        head, base = verify(args.pr, args.source_pr)
        kind, reason = "catalog", f"Exact generated catalog matches trusted main and merged bot PR #{args.source_pr}"
    except (VerificationError, ValidationError, GitHubError, subprocess.CalledProcessError, KeyError, TypeError, ValueError) as error:
        pr = api("GET", f"pulls/{args.pr}")
        head, base = pr["head"]["sha"], pr["base"]["sha"]
        kind, reason = "escalate", f"Catalog verification needs maintainer attention: {error}"
    args.output.write_text(json.dumps({"kind": kind, "reason_b64": base64.b64encode(reason.encode()).decode(),
                                       "sha": head, "base_sha": base}) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
