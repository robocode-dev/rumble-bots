"""Focused checks for bot-only eligibility and exact-commit moderation decisions."""

import tempfile
import unittest
from pathlib import Path
from subprocess import CompletedProcess
from unittest.mock import patch

from moderation import assess, moderate


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


if __name__ == "__main__":
    unittest.main()
