#!/usr/bin/env python3
"""Apply a moderation verdict without ever checking out or executing PR content."""

from __future__ import annotations

import argparse
import base64
import json
import os
import re
import sys
from urllib.error import HTTPError
from urllib.request import Request, urlopen

CONTEXT = "bot-moderation"
MODERATOR = "flemming-n-larsen"


class GitHubError(Exception):
    pass


def api(method: str, path: str, payload: dict | None = None):
    token = os.environ["GH_TOKEN"]
    data = json.dumps(payload).encode() if payload is not None else None
    request = Request(
        f"https://api.github.com/repos/{os.environ['GITHUB_REPOSITORY']}/{path}",
        data=data, method=method,
        headers={"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json",
                 "X-GitHub-Api-Version": "2026-03-10", "Content-Type": "application/json"},
    )
    try:
        with urlopen(request, timeout=20) as response:
            body = response.read()
            return json.loads(body) if body else None
    except HTTPError as error:
        raise GitHubError(f"GitHub {method} {path}: HTTP {error.code}: {error.read()[:400].decode(errors='replace')}") from error


def status(sha: str, state: str, description: str, run_url: str = "") -> None:
    api("POST", f"statuses/{sha}", {
        "state": state, "context": CONTEXT, "description": description[:140],
        "target_url": run_url or None,
    })


def comment(number: int, body: str) -> None:
    api("POST", f"issues/{number}/comments", {"body": body})


def current_pr(number: int, sha: str, base_sha: str = "") -> dict | None:
    pr = api("GET", f"pulls/{number}")
    if (pr["state"] != "open" or pr["base"]["ref"] != "main"
            or pr["head"]["sha"] != sha
            or (base_sha and pr["base"]["sha"] != base_sha)):
        return None
    return pr


def automated(number: int, sha: str, kind: str, reason: str, run_url: str, base_sha: str = "") -> None:
    pr = current_pr(number, sha)
    if pr is None:
        print("PR closed, retargeted, or updated; stale verdict ignored")
        return
    if base_sha and pr["base"]["sha"] != base_sha:
        status(sha, "pending", "Base changed during moderation", run_url)
        comment(number, f"@{MODERATOR} The target branch changed while `{sha[:12]}` was being tested. Please review the current base and retry or resolve with `/moderation approve <reason>`.")
        return
    reason = reason.strip()[:1200] or "Moderation did not produce a reason"
    if pr["draft"] or kind not in {"clean", "reject", "escalate"}:
        kind = "escalate"
        reason = "Draft PR or missing verdict: " + reason
    if kind == "escalate":
        status(sha, "pending", "Flemming must review this PR", run_url)
        comment(number, f"@{MODERATOR} Moderation needs your decision for `{sha[:12]}`: {reason}\n\nThe required check stays pending. After reviewing, use `/moderation approve <reason>` or request changes.")
        return
    if kind == "reject":
        status(sha, "failure", "Bot validation or battle failed", run_url)
        api("POST", f"pulls/{number}/reviews", {
            "event": "REQUEST_CHANGES", "commit_id": sha, "body": f"The bot cannot be accepted: {reason}",
        })
        return
    # The trusted job reads the live head again immediately before every irreversible action.
    api("POST", f"pulls/{number}/reviews", {
        "event": "APPROVE", "commit_id": sha,
        "body": f"Automated moderation passed for `{sha[:12]}`: {reason}",
    })
    if current_pr(number, sha, base_sha) is None:
        print("PR changed after approval; merge skipped")
        return
    status(sha, "success", "Source and completed battle passed", run_url)
    if current_pr(number, sha, base_sha) is None:
        print("PR changed after status; merge skipped")
        return
    try:
        result = api("PUT", f"pulls/{number}/merge", {"sha": sha, "merge_method": "merge"})
        if not result or not result.get("merged"):
            raise GitHubError("GitHub did not confirm the merge")
    except GitHubError as error:
        status(sha, "error", "Automatic merge needs maintainer attention", run_url)
        comment(number, f"@{MODERATOR} The bot passed moderation, but automatic merge failed for `{sha[:12]}`: {error}")
        raise


def human_override(number: int, sha: str, comment_id: int) -> None:
    pr = current_pr(number, sha)
    if pr is None:
        raise GitHubError("PR is closed, retargeted, or has a newer commit")
    entry = api("GET", f"issues/comments/{comment_id}")
    body = entry["body"].strip()
    match = re.fullmatch(r"/moderation approve\s+(.{10,500})", body, re.DOTALL)
    if entry["user"]["login"].casefold() != MODERATOR or not match:
        raise GitHubError("Only Flemming may approve an escalated PR with a stated reason")
    combined = api("GET", f"commits/{sha}/status")
    moderation = next((item for item in combined["statuses"] if item["context"] == CONTEXT), None)
    if moderation is None or moderation["state"] != "pending":
        raise GitHubError("Only a pending human escalation may be approved manually")
    status(sha, "success", "Human moderation by Flemming")
    comment(number, f"Flemming accepted the escalated moderation for `{sha[:12]}`: {match.group(1).strip()}\n\nMerge remains a human action.")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pr", required=True, type=int)
    parser.add_argument("--sha", required=True)
    parser.add_argument("--base-sha", default="")
    parser.add_argument("--kind", default="escalate")
    parser.add_argument("--reason-b64", default="")
    parser.add_argument("--run-url", default="")
    parser.add_argument("--comment-id", type=int)
    args = parser.parse_args()
    if (args.pr < 1 or not re.fullmatch(r"[0-9a-f]{40}", args.sha)
            or (args.base_sha and not re.fullmatch(r"[0-9a-f]{40}", args.base_sha))):
        parser.error("expected a positive PR number and full head SHA")
    if args.comment_id is not None:
        human_override(args.pr, args.sha, args.comment_id)
    else:
        try:
            reason = base64.b64decode(args.reason_b64, validate=True).decode("utf-8")
        except (ValueError, UnicodeError) as error:
            reason = f"Malformed validator output: {error}"
            args.kind = "escalate"
        automated(args.pr, args.sha, args.kind, reason, args.run_url, args.base_sha)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except GitHubError as error:
        print(error, file=sys.stderr)
        raise SystemExit(1) from error
