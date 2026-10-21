"""Focused checks for bot-only eligibility and exact-commit moderation decisions."""

import tempfile
import unittest
import json
from datetime import UTC, datetime
from pathlib import Path
from subprocess import CompletedProcess
from unittest.mock import patch

from moderation import assess, moderate, verify_catalog


SHA = "a" * 40


class SubmissionEligibilityTest(unittest.TestCase):
    def test_one_bot_directory_is_eligible(self):
        self.assertEqual(
            assess.classify_paths(["bots/python/NewBot/NewBot.json", "bots/python/NewBot/src/NewBot.py"]),
            ("bots/python/NewBot", ""),
        )

    def test_mixed_or_protected_paths_go_to_human(self):
        for paths in (
            ["bots/python/NewBot/src/NewBot.py", ".github/workflows/moderate.yml"],
            ["bots/index.json"],
            ["bots/banned.json"],
            ["bots/python/One/One.py", "bots/python/Two/Two.py"],
        ):
            with self.subTest(paths=paths):
                self.assertIsNone(assess.classify_paths(paths)[0])

    def test_restricted_source_and_extra_launcher_need_human(self):
        with tempfile.TemporaryDirectory() as temp:
            bot = Path(temp) / "Example"
            bot.mkdir()
            (bot / "Example.py").write_text("import subprocess\n", encoding="utf-8")
            (bot / "extra.sh").write_text("#!/bin/sh\n", encoding="utf-8")
            self.assertTrue(assess.scan_source(bot))
            self.assertTrue(assess.scan_files(bot))

    def test_official_starters_have_reviewable_launchers(self):
        for platform in assess.PLATFORMS:
            with self.subTest(platform=platform):
                bot = assess.ROOT / "templates" / platform / "StarterBot"
                self.assertEqual(assess.check_launchers(platform, "StarterBot", bot), [])
                self.assertEqual(assess.scan_source(bot), [])
                self.assertEqual(assess.scan_files(bot), [])

    def test_container_failure_escalates_but_bot_failure_requests_changes(self):
        bot = assess.ROOT / "templates" / "java" / "StarterBot"
        for exit_code, expected in ((125, "escalate"), (1, "reject")):
            with self.subTest(exit_code=exit_code), patch.object(
                assess.subprocess, "run", return_value=CompletedProcess([], exit_code, "", "failure")
            ):
                self.assertEqual(assess.run_battle(bot, "StarterBot", "1.0.0").kind, expected)


class ExactCommitDecisionTest(unittest.TestCase):
    def setUp(self):
        self.pr = {"state": "open", "base": {"ref": "main", "sha": "c" * 40}, "head": {"sha": SHA}, "draft": False}

    def test_clean_verdict_approves_and_merges_only_the_tested_sha(self):
        calls = []

        def fake_api(method, path, payload=None):
            calls.append((method, path, payload))
            if path == "pulls/12/merge":
                return {"merged": True}
            return self.pr if path == "pulls/12" else {}

        with patch.object(moderate, "api", side_effect=fake_api):
            moderate.automated(12, SHA, "clean", "battle passed", "https://example.test/run")
        review = next(payload for method, path, payload in calls if path == "pulls/12/reviews")
        merge = next(payload for method, path, payload in calls if path == "pulls/12/merge")
        self.assertEqual(review["event"], "APPROVE")
        self.assertEqual(review["commit_id"], SHA)
        self.assertEqual(merge["sha"], SHA)

    def test_verified_catalog_uses_catalog_status_and_exact_commit_review(self):
        calls = []

        def fake_api(method, path, payload=None):
            calls.append((method, path, payload))
            if path == "pulls/12/merge":
                return {"merged": True}
            return self.pr if path == "pulls/12" else {}

        with patch.object(moderate, "api", side_effect=fake_api):
            moderate.automated(12, SHA, "catalog", "generated files match", "")
        self.assertTrue(any(path == f"statuses/{SHA}" and payload["description"] == "Generated catalog verified"
                            for _, path, payload in calls))
        self.assertTrue(any(path == "pulls/12/reviews" and payload["commit_id"] == SHA
                            for _, path, payload in calls))
        self.assertTrue(any(path == "pulls/12/merge" and payload["sha"] == SHA
                            for _, path, payload in calls))

    def test_new_commit_cannot_receive_a_stale_verdict(self):
        self.pr["head"]["sha"] = "b" * 40
        with patch.object(moderate, "api", return_value=self.pr) as api:
            moderate.automated(12, SHA, "clean", "battle passed", "")
        self.assertEqual(api.call_count, 1)

    def test_changed_base_cannot_receive_a_stale_verdict(self):
        with patch.object(moderate, "api", return_value=self.pr) as api:
            moderate.automated(12, SHA, "clean", "battle passed", "", "d" * 40)
        self.assertFalse(any(call.args[1] == "pulls/12/merge" for call in api.call_args_list))
        self.assertTrue(any(call.args[1] == f"statuses/{SHA}" and call.args[2]["state"] == "pending"
                            for call in api.call_args_list))

    def test_runtime_failure_requests_changes_and_blocks(self):
        calls = []

        def fake_api(method, path, payload=None):
            calls.append((method, path, payload))
            return self.pr if path == "pulls/12" else {}

        with patch.object(moderate, "api", side_effect=fake_api):
            moderate.automated(12, SHA, "reject", "bot timed out", "")
        self.assertTrue(any(path == "pulls/12/reviews" and payload["event"] == "REQUEST_CHANGES"
                            for _, path, payload in calls))
        self.assertFalse(any(path == "pulls/12/merge" for _, path, _ in calls))

    def test_uncertain_result_requires_flemming(self):
        calls = []

        def fake_api(method, path, payload=None):
            calls.append((method, path, payload))
            return self.pr if path == "pulls/12" else {}

        with patch.object(moderate, "api", side_effect=fake_api):
            moderate.automated(12, SHA, "escalate", "suspicious network code", "")
        self.assertTrue(any(path == f"statuses/{SHA}" and payload["state"] == "pending"
                            for _, path, payload in calls))
        self.assertTrue(any(path == "issues/12/comments" and "@flemming-n-larsen" in payload["body"]
                            for _, path, payload in calls))
        self.assertFalse(any(path == "pulls/12/merge" for _, path, _ in calls))

    def test_only_flemming_can_clear_a_pending_escalation(self):
        calls = []

        def fake_api(method, path, payload=None):
            calls.append((method, path, payload))
            if path == "pulls/12":
                return self.pr
            if path == "issues/comments/42":
                return {"body": "/moderation approve Reviewed the extra source", "user": {"login": "flemming-n-larsen"}}
            if path == f"commits/{SHA}/status":
                return {"statuses": [{"context": moderate.CONTEXT, "state": "pending"}]}
            return {}

        with patch.object(moderate, "api", side_effect=fake_api):
            moderate.human_override(12, SHA, 42)
        self.assertTrue(any(path == f"statuses/{SHA}" and payload["state"] == "success"
                            for _, path, payload in calls))
        self.assertFalse(any(path == "pulls/12/merge" for _, path, _ in calls))

    def test_other_commenter_cannot_clear_an_escalation(self):
        def fake_api(method, path, payload=None):
            if path == "pulls/12":
                return self.pr
            if path == "issues/comments/42":
                return {"body": "/moderation approve Looks good to me", "user": {"login": "someone-else"}}
            raise AssertionError("No status or merge may be written")

        with patch.object(moderate, "api", side_effect=fake_api):
            with self.assertRaises(moderate.GitHubError):
                moderate.human_override(12, SHA, 42)


class CatalogVerificationTest(unittest.TestCase):
    def setUp(self):
        self.pr = {"state": "open", "draft": False, "base": {"ref": "main", "sha": "c" * 40},
                   "head": {"sha": SHA, "ref": "catalog/pr-7-123-1", "repo": {"full_name": "robocode-dev/rumble-bots"}},
                   "user": {"login": verify_catalog.PUBLISHER_LOGIN}}

    def test_other_author_cannot_claim_generated_catalog(self):
        self.pr["user"]["login"] = "untrusted"
        with patch.dict("os.environ", {"GITHUB_REPOSITORY": "robocode-dev/rumble-bots"}), patch.object(
            verify_catalog, "api", return_value=self.pr
        ) as api:
            with self.assertRaisesRegex(verify_catalog.VerificationError, "publisher-created"):
                verify_catalog.verify(12, 7)
        self.assertEqual(api.call_count, 1)

    def test_moved_main_cannot_validate_stale_catalog(self):
        with patch.dict("os.environ", {"GITHUB_REPOSITORY": "robocode-dev/rumble-bots"}), patch.object(
            verify_catalog, "api", return_value=self.pr
        ), patch.object(verify_catalog, "git", return_value="d" * 40):
            with self.assertRaisesRegex(verify_catalog.VerificationError, "Main moved"):
                verify_catalog.verify(12, 7)

    def test_multiple_unpublished_bots_need_ownership_review(self):
        source = {"merged_at": "2026-10-21T00:00:00Z", "base": {"ref": "main"},
                  "user": {"login": "contributor"}}

        def fake_api(method, path, payload=None):
            if path == "pulls/12":
                return self.pr
            if path == "pulls/7":
                return source
            if path.startswith("pulls/7/files"):
                return [{"filename": "bots/python/Nova/Nova.json"}]
            raise AssertionError(path)

        with patch.dict("os.environ", {"GITHUB_REPOSITORY": "robocode-dev/rumble-bots"}), patch.object(
            verify_catalog, "api", side_effect=fake_api
        ), patch.object(verify_catalog, "git", return_value="c" * 40), patch.object(
            verify_catalog, "unpublished_bot_paths", return_value={"bots/python/Nova", "bots/java/Other"}
        ):
            with self.assertRaisesRegex(verify_catalog.VerificationError, "Other unpublished bot changes"):
                verify_catalog.verify(12, 7)

    def test_catalog_must_match_trusted_regeneration(self):
        base = "c" * 40
        generated_at = datetime.now(UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z")
        proposed = {"bots/index.json": json.dumps({"generatedAt": generated_at}) + "\n",
                    "bots/owners.json": "{}\n"}
        regenerated = proposed.copy()
        source = {"merged_at": generated_at, "base": {"ref": "main"},
                  "head": {"sha": "d" * 40}, "merge_commit_sha": "e" * 40,
                  "user": {"login": "contributor"}}
        statuses = [{"context": moderate.CONTEXT, "state": "success",
                     "creator": {"login": verify_catalog.APP_LOGIN}}]

        def fake_api(method, path, payload=None):
            if path == "pulls/12":
                return self.pr
            if path == "pulls/7":
                return source
            if path.startswith("pulls/7/files"):
                return [{"filename": "bots/python/Nova/Nova.json"}]
            if path == f"commits/{'d' * 40}/statuses?per_page=100":
                return statuses
            raise AssertionError(path)

        def fake_git(*args):
            if args == ("rev-parse", "HEAD"):
                return base
            if args == ("rev-parse", "refs/remotes/origin/catalog-verify"):
                return SHA
            if args == ("rev-parse", f"{SHA}^"):
                return base
            if args[:2] == ("diff", "--name-only"):
                return "bots/index.json"
            if args[0] == "show":
                return proposed[args[1].split(":", 1)[1]].strip()
            if args[:2] == ("merge-base", "--is-ancestor"):
                return ""
            raise AssertionError(args)

        with patch.dict("os.environ", {"GITHUB_REPOSITORY": "robocode-dev/rumble-bots"}), patch.object(
            verify_catalog, "api", side_effect=fake_api
        ), patch.object(verify_catalog, "unpublished_bot_paths", return_value={"bots/python/Nova"}), patch.object(
            verify_catalog, "git", side_effect=fake_git), patch.object(
            verify_catalog.subprocess, "run", return_value=CompletedProcess([], 0, "", "")
        ), patch.object(verify_catalog.Path, "read_text", autospec=True, side_effect=lambda path, encoding=None: regenerated[path.as_posix()]):
            self.assertEqual((SHA, base), verify_catalog.verify(12, 7))
            regenerated["bots/owners.json"] = '{"owner": "wrong"}\n'
            with self.assertRaisesRegex(verify_catalog.VerificationError, "Generated content differs"):
                verify_catalog.verify(12, 7)
            regenerated["bots/owners.json"] = proposed["bots/owners.json"]
            statuses[0]["creator"] = {"login": "untrusted"}
            with self.assertRaisesRegex(verify_catalog.VerificationError, "no successful moderator App status"):
                verify_catalog.verify(12, 7)
            statuses[0].pop("creator")
            with self.assertRaisesRegex(verify_catalog.VerificationError, "no successful moderator App status"):
                verify_catalog.verify(12, 7)


if __name__ == "__main__":
    unittest.main()
