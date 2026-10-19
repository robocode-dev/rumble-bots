from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPOSITORY = Path(__file__).resolve().parents[1]
VALIDATOR = REPOSITORY / "scripts" / "validate_bot.py"
sys.path.insert(0, str(REPOSITORY / "scripts"))
from validate_bot import tree_hash  # noqa: E402

LANGUAGES = ("java", "csharp", "python", "typescript")


def copy_template(root: Path, language: str, name: str) -> Path:
    """Exercise the documented copy-and-rename steps in a separate submission tree."""
    destination = root / "bots" / language / name
    shutil.copytree(REPOSITORY / "templates" / language / "StarterBot", destination)
    for path in list(destination.rglob("*")):
        if path.is_file():
            path.write_text(path.read_text(encoding="utf-8").replace("StarterBot", name), encoding="utf-8")
            if "StarterBot" in path.name:
                path.rename(path.with_name(path.name.replace("StarterBot", name)))
    return destination


class TemplateSubmissionTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory(prefix="rumble templates ")
        self.root = Path(self.temporary.name)
        (self.root / "bots").mkdir()
        (self.root / "bots" / "banned.json").write_text('{"bannedOwners":[],"disqualifiedBots":[]}', encoding="utf-8")

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def validate(self, *arguments: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run([sys.executable, str(VALIDATOR), "--root", str(self.root),
                               "--owner", "template-author", *arguments], text=True, capture_output=True, check=False)

    def test_RBC001_IntegrationPositive_renamed_templates_generate_real_catalog_entries(self) -> None:
        for language in LANGUAGES:
            with self.subTest(language=language):
                name = language.capitalize() + "Scout"
                copy_template(self.root, language, name)
        result = self.validate("--generate")
        self.assertEqual(0, result.returncode, result.stderr)
        catalog = json.loads((self.root / "bots" / "index.json").read_text())
        self.assertEqual({language.capitalize() + "Scout" for language in LANGUAGES},
                         {entry["name"] for entry in catalog["bots"]})
        self.assertTrue(all(entry["owner"] == "template-author" for entry in catalog["bots"]))

    @unittest.skipUnless(os.environ.get("RUMBLE_TEMPLATE_SMOKE") == "1", "set RUMBLE_TEMPLATE_SMOKE=1 with all template runtimes installed")
    def test_RBC001_IntegrationPositive_renamed_launchers_smoke_without_changing_source_hash(self) -> None:
        directories = [copy_template(self.root, language, language.capitalize() + "Scout") for language in LANGUAGES]
        before = {directory: tree_hash(directory) for directory in directories}
        result = self.validate("--smoke", "--generate")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(before, {directory: tree_hash(directory) for directory in directories})

    def test_RBC004_IntegrationPositive_team_template_needs_only_json_and_resolves_twin_slots(self) -> None:
        copy_template(self.root, "python", "MyBot")
        team = self.root / "bots" / "python" / "MyTwins"
        team.mkdir()
        source = REPOSITORY / "templates" / "team" / "StarterTeam" / "StarterTeam.json"
        (team / "MyTwins.json").write_text(source.read_text().replace("StarterTeam", "MyTwins"), encoding="utf-8")
        self.assertEqual(["MyTwins.json"], [path.name for path in team.iterdir()])
        result = self.validate("--generate")
        self.assertEqual(0, result.returncode, result.stderr)
        catalog = json.loads((self.root / "bots" / "index.json").read_text())
        entry = next(entry for entry in catalog["bots"] if entry["name"] == "MyTwins")
        self.assertEqual(["MyBot 1.0.0", "MyBot 1.0.0"], entry["teamMembers"])

    def test_RBC003_IntegrationPositive_template_version_update_supersedes_first_entry(self) -> None:
        directory = copy_template(self.root, "python", "MyBot")
        self.assertEqual(0, self.validate("--generate").returncode)
        config_path = directory / "MyBot.json"
        config = json.loads(config_path.read_text())
        config["version"] = "1.0.1"
        config_path.write_text(json.dumps(config), encoding="utf-8")
        result = self.validate("--generate")
        self.assertEqual(0, result.returncode, result.stderr)
        catalog = json.loads((self.root / "bots" / "index.json").read_text())
        self.assertEqual([("1.0.0", "superseded"), ("1.0.1", "active")],
                         [(entry["version"], entry["status"]) for entry in catalog["bots"]])

    def test_RBC006_IntegrationPositive_official_api_and_its_declared_runtime_peer_are_accepted(self) -> None:
        copy_template(self.root, "typescript", "MyBot")
        result = self.validate()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_RBC006_IntegrationNegative_missing_lockfile_is_rejected(self) -> None:
        directory = copy_template(self.root, "typescript", "MyBot")
        (directory / "package-lock.json").unlink()
        result = self.validate()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("package-lock.json", result.stderr)

    def test_RBC006_IntegrationNegative_an_unrelated_library_is_rejected_even_if_claimed_as_a_peer(self) -> None:
        directory = copy_template(self.root, "typescript", "MyBot")
        package = json.loads((directory / "package.json").read_text())
        package["dependencies"]["unrelated-library"] = "1.0.0"
        (directory / "package.json").write_text(json.dumps(package), encoding="utf-8")
        lock = json.loads((directory / "package-lock.json").read_text())
        lock["packages"]["node_modules/@robocode.dev/tank-royale-bot-api"]["peerDependencies"]["unrelated-library"] = "1.0.0"
        (directory / "package-lock.json").write_text(json.dumps(lock), encoding="utf-8")
        result = self.validate()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("unrelated TypeScript dependency `unrelated-library`", result.stderr)

    def test_RBC006_IntegrationNegative_an_unrelated_locked_package_is_rejected(self) -> None:
        directory = copy_template(self.root, "typescript", "MyBot")
        lock = json.loads((directory / "package-lock.json").read_text())
        lock["packages"]["node_modules/unrelated-library"] = {"version": "1.0.0"}
        (directory / "package-lock.json").write_text(json.dumps(lock), encoding="utf-8")
        result = self.validate()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("unrelated locked TypeScript dependency", result.stderr)

    def test_RBC006_IntegrationNegative_missing_required_runtime_peer_is_rejected(self) -> None:
        directory = copy_template(self.root, "typescript", "MyBot")
        package = json.loads((directory / "package.json").read_text())
        del package["dependencies"]["ws"]
        (directory / "package.json").write_text(json.dumps(package), encoding="utf-8")
        lock = json.loads((directory / "package-lock.json").read_text())
        del lock["packages"][""]["dependencies"]["ws"]
        (directory / "package-lock.json").write_text(json.dumps(lock), encoding="utf-8")
        result = self.validate()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("requires its declared `ws` runtime peer", result.stderr)

    def test_RBC006_IntegrationNegative_generated_dependencies_are_not_submitted(self) -> None:
        directory = copy_template(self.root, "typescript", "MyBot")
        (directory / "node_modules").mkdir()
        result = self.validate()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("generated dependency and build directories", result.stderr)

    def test_RBC006_IntegrationNegative_an_alias_cannot_replace_the_official_api(self) -> None:
        directory = copy_template(self.root, "typescript", "MyBot")
        package = json.loads((directory / "package.json").read_text())
        package["dependencies"]["@robocode.dev/tank-royale-bot-api"] = "npm:unrelated-library@1.0.0"
        (directory / "package.json").write_text(json.dumps(package), encoding="utf-8")
        result = self.validate()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("registry version", result.stderr)

    def test_RBC006_IntegrationNegative_unofficial_lock_resolution_is_rejected(self) -> None:
        directory = copy_template(self.root, "typescript", "MyBot")
        lock = json.loads((directory / "package-lock.json").read_text())
        lock["packages"]["node_modules/ws"]["resolved"] = "https://example.com/ws.tgz"
        (directory / "package-lock.json").write_text(json.dumps(lock), encoding="utf-8")
        result = self.validate()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("official npm registry package", result.stderr)

    def test_RBC006_IntegrationNegative_malformed_peer_metadata_has_a_diagnostic(self) -> None:
        directory = copy_template(self.root, "typescript", "MyBot")
        lock = json.loads((directory / "package-lock.json").read_text())
        lock["packages"]["node_modules/@robocode.dev/tank-royale-bot-api"]["peerDependencies"] = None
        (directory / "package-lock.json").write_text(json.dumps(lock), encoding="utf-8")
        result = self.validate()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("locked peerDependencies must be an object", result.stderr)
        self.assertNotIn("Traceback", result.stderr)
