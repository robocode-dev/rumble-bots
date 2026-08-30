from __future__ import annotations

import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPOSITORY = Path(__file__).parents[1]
VALIDATOR = REPOSITORY / "scripts" / "validate_bot.py"


class ValidatorIntegrationTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        shutil.copytree(REPOSITORY / "bots", self.root / "bots")

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def run_validator(self, *arguments: str, owner: str = "flemming-n-larsen") -> subprocess.CompletedProcess[str]:
        return subprocess.run([sys.executable, str(VALIDATOR), "--root", str(self.root), "--owner", owner, *arguments], text=True, capture_output=True, check=False)

    def add_bot(self, name: str) -> None:
        source = self.root / "bots" / "python" / "Orbit"
        destination = self.root / "bots" / "python" / name
        shutil.copytree(source, destination)
        (destination / "Orbit.sh").rename(destination / f"{name}.sh")
        (destination / "Orbit.cmd").rename(destination / f"{name}.cmd")
        config_path = destination / "Orbit.json"
        config = json.loads(config_path.read_text(encoding="utf-8"))
        config["name"] = name
        config_path.unlink()
        (destination / f"{name}.json").write_text(json.dumps(config), encoding="utf-8")

    def bump_version(self, name: str, version: str) -> None:
        config_path = self.root / "bots" / "python" / name / f"{name}.json"
        config = json.loads(config_path.read_text(encoding="utf-8"))
        config["version"] = version
        config_path.write_text(json.dumps(config), encoding="utf-8")

    def add_java_bot(self, name: str) -> None:
        destination = self.root / "bots" / "java" / name
        destination.mkdir(parents=True)
        config = {
            "name": name,
            "version": "1.0.2",
            "authors": ["Test author"],
            "license": "Apache-2.0",
            "platform": "JVM",
        }
        (destination / f"{name}.json").write_text(json.dumps(config), encoding="utf-8")
        (destination / f"{name}.java").write_text("// dev.robocode.tankroyale.botapi", encoding="utf-8")
        for suffix in (".sh", ".cmd"):
            (destination / f"{name}{suffix}").write_text("", encoding="utf-8")

    def add_team(self, name: str, members: list[str]) -> None:
        destination = self.root / "bots" / "python" / name
        destination.mkdir()
        config = {
            "name": name,
            "version": "1.0",
            "authors": ["Test author"],
            "license": "Apache-2.0",
            "teamMembers": members,
        }
        (destination / f"{name}.json").write_text(json.dumps(config), encoding="utf-8")

    def test_valid_submission_generates_an_active_catalog_entry(self) -> None:
        result = self.run_validator("--smoke", "--generate")
        self.assertEqual(0, result.returncode, result.stderr)
        catalog = json.loads((self.root / "bots" / "index.json").read_text(encoding="utf-8"))
        active_entry = next(entry for entry in catalog["bots"] if entry["status"] == "active")
        self.assertEqual("Orbit", active_entry["name"])

    def test_invalid_license_is_rejected(self) -> None:
        config_path = self.root / "bots" / "python" / "Orbit" / "Orbit.json"
        config = json.loads(config_path.read_text(encoding="utf-8"))
        config["license"] = "Proprietary"
        config_path.write_text(json.dumps(config), encoding="utf-8")
        result = self.run_validator()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("license", result.stderr)

    def test_source_change_without_version_increase_is_rejected(self) -> None:
        self.assertEqual(0, self.run_validator("--generate").returncode)
        source_path = self.root / "bots" / "python" / "Orbit" / "src" / "Orbit.py"
        source_path.write_text(source_path.read_text(encoding="utf-8") + "\n# changed\n", encoding="utf-8")
        result = self.run_validator()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("without increasing its version", result.stderr)

    def test_version_increase_supersedes_the_previous_catalog_entry(self) -> None:
        self.assertEqual(0, self.run_validator("--generate").returncode)
        config_path = self.root / "bots" / "python" / "Orbit" / "Orbit.json"
        config = json.loads(config_path.read_text(encoding="utf-8"))
        config["version"] = "1.0.3"
        config_path.write_text(json.dumps(config), encoding="utf-8")
        self.assertEqual(0, self.run_validator("--generate").returncode)
        catalog = json.loads((self.root / "bots" / "index.json").read_text(encoding="utf-8"))
        orbit = [entry for entry in catalog["bots"] if entry["name"] == "Orbit"]
        self.assertEqual(["superseded", "active"], [entry["status"] for entry in orbit])

    def test_new_bot_from_another_owner_ignores_unchanged_catalog_entries(self) -> None:
        self.assertEqual(0, self.run_validator("--generate").returncode)
        self.add_bot("Nova")
        result = self.run_validator("--generate", owner="alice")
        self.assertEqual(0, result.returncode, result.stderr)
        catalog = json.loads((self.root / "bots" / "index.json").read_text(encoding="utf-8"))
        nova = next(entry for entry in catalog["bots"] if entry["name"] == "Nova")
        self.assertEqual("alice", nova["owner"])

    def test_registered_secondary_account_can_update_and_is_preserved(self) -> None:
        owners_path = self.root / "bots" / "owners.json"
        owners = json.loads(owners_path.read_text(encoding="utf-8"))
        owners["owners"][0]["ownerId"] = "primary"
        owners["owners"][0]["accounts"] = ["primary", "secondary"]
        owners_path.write_text(json.dumps(owners), encoding="utf-8")
        config_path = self.root / "bots" / "python" / "Orbit" / "Orbit.json"
        config = json.loads(config_path.read_text(encoding="utf-8"))
        config["version"] = "1.0.3"
        config_path.write_text(json.dumps(config), encoding="utf-8")
        result = self.run_validator("--generate", owner="secondary")
        self.assertEqual(0, result.returncode, result.stderr)
        regenerated_owners = json.loads(owners_path.read_text(encoding="utf-8"))
        self.assertEqual(["primary", "secondary"], regenerated_owners["owners"][0]["accounts"])

    def test_RBC004_IntegrationPositive_team_members_are_published_as_catalog_identities(self) -> None:
        self.add_bot("Nova")
        self.add_team("OrbitNova", ["Orbit 1.0.2", "Nova 1.0.2"])

        result = self.run_validator("--generate")

        self.assertEqual(0, result.returncode, result.stderr)
        catalog = json.loads((self.root / "bots" / "index.json").read_text(encoding="utf-8"))
        entries = {entry["name"]: entry for entry in catalog["bots"] if entry["status"] == "active"}
        self.assertEqual(["Orbit 1.0.2", "Nova 1.0.2"], entries["OrbitNova"]["teamMembers"])
        self.assertEqual("Python", entries["OrbitNova"]["platform"])
        self.assertEqual([], entries["Orbit"]["teamMembers"])

    def test_RBC004_IntegrationNegative_unknown_team_member_is_rejected(self) -> None:
        self.add_team("BrokenTeam", ["Orbit 1.0.2", "Missing 1.0"])

        result = self.run_validator("--generate")

        self.assertNotEqual(0, result.returncode)
        self.assertIn("unknown team member `Missing 1.0`", result.stderr)

    def test_RBC004_IntegrationNegative_member_version_bump_invalidates_the_team_identity(self) -> None:
        self.add_bot("Nova")
        self.add_team("OrbitNova", ["Orbit 1.0.2", "Nova 1.0.2"])
        self.assertEqual(0, self.run_validator("--generate").returncode)
        self.bump_version("Nova", "1.0.3")

        result = self.run_validator("--generate")

        self.assertNotEqual(0, result.returncode)
        self.assertIn("unknown team member `Nova 1.0.2`", result.stderr)

    def test_RBC004_IntegrationNegative_a_team_directory_may_not_carry_sources(self) -> None:
        self.add_team("OrbitOrbit", ["Orbit 1.0.2", "Orbit 1.0.2"])
        (self.root / "bots" / "python" / "OrbitOrbit" / "payload.py").write_text("print()", encoding="utf-8")

        result = self.run_validator("--generate")

        self.assertNotEqual(0, result.returncode)
        self.assertIn("must contain only OrbitOrbit.json", result.stderr)

    def test_RBC004_IntegrationPositive_a_team_may_span_two_platforms(self) -> None:
        self.add_java_bot("Comet")
        self.add_team("OrbitComet", ["Orbit 1.0.2", "Comet 1.0.2"])

        result = self.run_validator("--generate")

        self.assertEqual(0, result.returncode, result.stderr)
        catalog = json.loads((self.root / "bots" / "index.json").read_text(encoding="utf-8"))
        entries = {entry["name"]: entry for entry in catalog["bots"] if entry["status"] == "active"}
        self.assertEqual(["Orbit 1.0.2", "Comet 1.0.2"], entries["OrbitComet"]["teamMembers"])
        self.assertEqual("Mixed", entries["OrbitComet"]["platform"])

    def test_RBC004_IntegrationNegative_duplicate_bot_names_across_platforms_are_rejected(self) -> None:
        self.add_java_bot("Orbit")

        result = self.run_validator("--generate")

        self.assertNotEqual(0, result.returncode)
        self.assertIn("is already used by", result.stderr)


if __name__ == "__main__":
    unittest.main()
