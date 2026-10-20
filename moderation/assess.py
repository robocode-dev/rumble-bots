#!/usr/bin/env python3
"""Assess an exact PR commit using trusted code and a credential-free battle container."""

from __future__ import annotations

import argparse
import base64
import json
import os
import re
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from scripts.validate_bot import (  # noqa: E402
    PLATFORMS,
    ValidationError,
    bot_directories,
    check_governance,
    resolve_teams,
    validate_bot,
)

IMAGE = "ghcr.io/robocode-dev/rumble-client@sha256:32ab3259f0ce69f319174b54ba36f4d85949c1a30ea4e65a94010b9c931acf2d"
STATUS = "bot-moderation"
RISK_PATTERNS = (
    r"\b(?:subprocess|ctypes|socket|importlib|__import__|eval|exec|compile)\b",
    r"\b(?:ProcessBuilder|Runtime\.getRuntime|System\.exit|java\.net|java\.nio\.file|Class\.forName|Unsafe)\b",
    r"\b(?:System\.Diagnostics\.Process|DllImport|NativeLibrary|System\.Net|System\.IO\.File|Assembly\.Load)\b",
    r"\b(?:child_process|node:net|node:http|node:https|require\s*\(|fetch\s*\(|Function\s*\()",
    r"\b(?:open\s*\(|os\.popen|os\.system|pathlib\.Path\s*\()",
)
SOURCE_EXTENSIONS = {".py", ".java", ".cs", ".ts", ".js", ".mjs", ".cjs"}
AUTO_MERGE_SUFFIXES = SOURCE_EXTENSIONS | {".json", ".sh", ".cmd", ".csproj", ".md", ".txt"}


@dataclass(frozen=True)
class Verdict:
    kind: str  # clean, reject, escalate
    reason: str


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def changed_files(base: str, head: str) -> list[str]:
    data = subprocess.check_output(
        ["git", "diff", "--name-only", "--no-renames", "-z", f"{base}...{head}"], cwd=ROOT
    )
    return [name.decode("utf-8") for name in data.split(b"\0") if name]


def classify_paths(paths: list[str]) -> tuple[str | None, str]:
    if not paths:
        return None, "PR contains no changed files"
    directories: set[str] = set()
    for path in paths:
        parts = path.split("/")
        if len(parts) < 4 or parts[0] != "bots" or parts[1] not in PLATFORMS:
            return None, f"Non-bot or protected file changed: {path}"
        directories.add("/".join(parts[:3]))
    if len(directories) != 1:
        return None, "A PR must change exactly one bot directory for automatic merge"
    return directories.pop(), ""


def safe_tree(head: str) -> None:
    tree = subprocess.check_output(["git", "ls-tree", "-r", "-z", head], cwd=ROOT)
    for entry in tree.split(b"\0"):
        if not entry:
            continue
        metadata, name = entry.split(b"\t", 1)
        mode = metadata.split(b" ", 1)[0]
        if mode not in (b"100644", b"100755"):
            raise ValidationError(f"Unsafe repository object ({mode.decode()}): {name.decode('utf-8', 'replace')}")


def scan_source(directory: Path) -> list[str]:
    issues: list[str] = []
    for source in directory.rglob("*"):
        if source.suffix.lower() not in SOURCE_EXTENSIONS or not source.is_file():
            continue
        for pattern in RISK_PATTERNS:
            if re.search(pattern, source.read_text(encoding="utf-8"), flags=re.IGNORECASE):
                issues.append(f"{source.name}: restricted API pattern {pattern}")
                break
    return issues


def scan_files(directory: Path) -> list[str]:
    issues: list[str] = []
    total = 0
    for path in directory.rglob("*"):
        if not path.is_file():
            continue
        size = path.stat().st_size
        total += size
        if size > 512_000 or total > 2_000_000:
            issues.append(f"{path.name}: source size exceeds automatic review limit")
        if path.suffix.lower() not in AUTO_MERGE_SUFFIXES and path.name != "LICENSE":
            issues.append(f"{path.name}: file type needs human review")
        if path.suffix.lower() in {".sh", ".cmd"} and path.name != directory.name + path.suffix:
            issues.append(f"{path.name}: extra launcher needs human review")
    return issues


def check_launchers(platform: str, name: str, directory: Path) -> list[str]:
    template = ROOT / "templates" / platform / "StarterBot"
    issues: list[str] = []
    for suffix in (".sh", ".cmd"):
        expected = (template / f"StarterBot{suffix}").read_text(encoding="utf-8").replace("StarterBot", name)
        actual = (directory / f"{name}{suffix}").read_text(encoding="utf-8")
        if actual.replace("\r\n", "\n") != expected.replace("\r\n", "\n"):
            issues.append(f"{name}{suffix} differs from the trusted source-run launcher")
    return issues


def run_battle(directory: Path, name: str, version: str) -> Verdict:
    reference = "TrackFire" if name == "Fire" else "Fire"
    submitted = f"/submitted/{name}"
    reference_path = f"/reference/{reference}"
    mounts = [
        (directory, submitted),
        (ROOT / "bots" / "java" / reference, reference_path),
        (ROOT / "moderation" / "BattleSmoke.java", "/harness/BattleSmoke.java"),
    ]
    command = [
        "docker", "run", "--rm", "--pull=never", "--read-only", "--user", "10001:10001",
        "--cpus=2", "--memory=2g", "--pids-limit=512", "--cap-drop=ALL",
        "--security-opt=no-new-privileges", "--tmpfs", "/tmp:rw,exec,nosuid,size=1024m",
        "--env", "HOME=/tmp", "--env", "RUMBLE_PYTHON=/opt/rumble-python/bin/python",
        "--env", "DOTNET_CLI_HOME=/tmp", "--env", "DOTNET_NOLOGO=1",
        "--env", "JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2",
        "--env", "npm_config_ignore_scripts=true", "--entrypoint", "java",
    ]
    for source, target in mounts:
        command.extend(["--mount", f"type=bind,src={source.resolve()},dst={target},readonly"])
    command.extend([IMAGE, "--class-path", "/opt/rumble-client/lib/*", "/harness/BattleSmoke.java",
                    submitted, reference_path, name, version])
    try:
        result = subprocess.run(command, text=True, capture_output=True, timeout=240, check=False)
    except (OSError, subprocess.TimeoutExpired) as error:
        return Verdict("escalate", f"Battle infrastructure did not finish: {error}")
    if result.returncode != 0:
        output = (result.stderr or result.stdout).strip()[-1200:]
        if result.returncode in (125, 126, 127):
            return Verdict("escalate", f"Battle container could not start: {output}")
        if "Failed to start the native thread" in result.stderr or "pthread_create failed" in result.stderr:
            return Verdict("escalate", f"Battle runner exhausted its process limit: {output}")
        return Verdict("reject", f"One-round battle failed: {output}")
    if f"RUMBLE_BATTLE_READY {name} {version}" not in result.stdout:
        return Verdict("escalate", "Battle finished without the expected completion proof")
    return Verdict("clean", f"{name} {version} completed one round against {reference}; source and security checks passed")


def assess(base: str, head: str, owner: str) -> Verdict:
    if not re.fullmatch(r"[0-9a-f]{40}", base) or not re.fullmatch(r"[0-9a-f]{40}", head):
        return Verdict("escalate", "Invalid Git revision in PR event")
    if not re.fullmatch(r"[A-Za-z0-9-]{1,39}", owner):
        return Verdict("escalate", "Invalid PR author login")
    paths = changed_files(base, head)
    bot_path, reason = classify_paths(paths)
    if not bot_path:
        return Verdict("escalate", reason)
    try:
        safe_tree(head)
    except ValidationError as error:
        return Verdict("escalate", str(error))
    with tempfile.TemporaryDirectory(prefix="rumble-pr-") as checkout:
        subprocess.run(["git", "worktree", "add", "--detach", checkout, head], cwd=ROOT,
                       check=True, stdout=subprocess.DEVNULL)
        try:
            pr_root = Path(checkout)
            directory = pr_root / bot_path
            if not directory.is_dir():
                return Verdict("reject", f"Submitted bot directory is missing: {bot_path}")
            platform = bot_path.split("/")[1]
            name = directory.name
            try:
                bots = resolve_teams([validate_bot(key, path, smoke=False)
                                      for key, path in bot_directories(pr_root)])
                check_governance(bots, pr_root, owner)
            except (ValidationError, OSError, UnicodeError) as error:
                if "source contains restricted construct" in str(error):
                    return Verdict("escalate", str(error))
                return Verdict("reject", str(error))
            target = next((bot for bot in bots if bot.directory == directory), None)
            if target is None:
                return Verdict("reject", f"No bot found at {bot_path}")
            if target.team_member_identities:
                return Verdict("escalate", "TwinDuel team metadata needs human review")
            issues = scan_files(directory) + scan_source(directory) + check_launchers(platform, name, directory)
            if issues:
                return Verdict("escalate", "; ".join(issues)[:1200])
            return run_battle(directory, name, str(target.config["version"]))
        finally:
            subprocess.run(["git", "worktree", "remove", "--force", checkout], cwd=ROOT,
                           check=True, stdout=subprocess.DEVNULL)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True)
    parser.add_argument("--head", required=True)
    parser.add_argument("--owner", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        verdict = assess(args.base, args.head, args.owner)
    except Exception as error:  # infrastructure failures must never become an approval
        verdict = Verdict("escalate", f"Moderation infrastructure failed: {error}")
    args.output.write_text(json.dumps(verdict.__dict__) + "\n", encoding="utf-8")
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with Path(output).open("a", encoding="utf-8") as handle:
            handle.write(f"kind={verdict.kind}\n")
            handle.write("reason_b64=" + base64.b64encode(verdict.reason.encode()).decode() + "\n")
    print(f"{verdict.kind}: {verdict.reason}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
