from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPOSITORY = Path(__file__).parents[1]
VALIDATOR = REPOSITORY / "scripts" / "validate_bot.py"
TEST_OWNER = "flemming-n-larsen"
sys.path.insert(0, str(REPOSITORY / "scripts"))
from validate_bot import tree_hash  # noqa: E402
from test_templates import copy_template  # noqa: E402


class ValidatorIntegrationTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        shutil.copytree(REPOSITORY / "bots", self.root / "bots")

        # Submission tests run as the repository's default test owner. Exclude other owners'
        # live bots from the fixture so a contributor's version update is not mistaken for
        # an unrelated source change by the fixture owner.
        bots_root = self.root / "bots"
        owners_path = bots_root / "owners.json"
        owners = json.loads(owners_path.read_text(encoding="utf-8"))
        owner_record = next(owner for owner in owners["owners"] if TEST_OWNER in owner["accounts"])
        catalog_path = bots_root / "index.json"
        catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
        owned_entries = [entry for entry in catalog["bots"] if entry["owner"] == TEST_OWNER]
        owned_paths = {entry["path"] for entry in owned_entries}

        for platform_directory in bots_root.iterdir():
            if not platform_directory.is_dir():
                continue
            for bot_directory in platform_directory.iterdir():
                relative_path = bot_directory.relative_to(self.root).as_posix()
                if bot_directory.is_dir() and relative_path not in owned_paths:
                    shutil.rmtree(bot_directory)

        # The tests need a published Python bot. Build it from the official template rather than
        # depend on a live Python bot, which may be retired from the Rumble.
        shutil.rmtree(bots_root / "python", ignore_errors=True)
        owned_entries = [entry for entry in owned_entries if not entry["path"].startswith("bots/python/")]
        orbit = copy_template(self.root, "python", "Orbit")
        orbit_config = json.loads((orbit / "Orbit.json").read_text(encoding="utf-8"))
        owned_entries.append({"name": "Orbit", "version": orbit_config["version"], "platform": "Python",
                              "path": "bots/python/Orbit", "sourceHash": tree_hash(orbit), "owner": TEST_OWNER,
                              "authors": orbit_config["authors"], "addedAt": "2026-10-21", "status": "active",
                              "teamMembers": []})
        owner_record["bots"] = sorted(set(owner_record["bots"]) | {"Orbit"})

        catalog["bots"] = owned_entries
        owners["owners"] = [owner_record]
        catalog_path.write_text(json.dumps(catalog, indent=2) + "\n", encoding="utf-8")
        owners_path.write_text(json.dumps(owners, indent=2) + "\n", encoding="utf-8")

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def run_validator(self, *arguments: str, owner: str = TEST_OWNER) -> subprocess.CompletedProcess[str]:
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
        script = destination / f"{name}.sh"
        script.chmod(script.stat().st_mode | 0o111)  # the validator requires the executable bit on POSIX

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
        orbit = next(entry for entry in catalog["bots"] if entry["name"] == "Orbit" and entry["status"] == "active")
        self.assertEqual("active", orbit["status"])

    def test_catalog_can_be_reproduced_at_its_recorded_utc_timestamp(self) -> None:
        timestamp = "2026-10-21T00:00:00Z"
        self.assertEqual(0, self.run_validator("--generate", "--generated-at", timestamp).returncode)
        first = (self.root / "bots" / "index.json").read_bytes()
        self.assertEqual(0, self.run_validator("--generate", "--generated-at", timestamp).returncode)
        self.assertEqual(first, (self.root / "bots" / "index.json").read_bytes())
        self.assertEqual(timestamp, json.loads(first)["generatedAt"])

    def test_catalog_timestamp_must_be_utc(self) -> None:
        result = self.run_validator("--generate", "--generated-at", "2026-10-21T02:00:00+02:00")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("must use UTC", result.stderr)

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
        source_path.write_bytes(source_path.read_bytes() + b"\n# changed\n")  # bytes: write_text would give Windows line endings
        result = self.run_validator()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("without increasing its version", result.stderr)

    def test_version_increase_supersedes_the_previous_catalog_entry(self) -> None:
        # SpinBot has no team member depending on it, unlike Corners/Crazy (CornersCrazy),
        # so bumping its version here can't break a team identity.
        self.assertEqual(0, self.run_validator("--generate").returncode)
        config_path = self.root / "bots" / "java" / "SpinBot" / "SpinBot.json"
        config = json.loads(config_path.read_text(encoding="utf-8"))
        config["version"] = "1.0.4"
        config_path.write_text(json.dumps(config), encoding="utf-8")
        self.assertEqual(0, self.run_validator("--generate").returncode)
        catalog = json.loads((self.root / "bots" / "index.json").read_text(encoding="utf-8"))
        spinbot = [entry for entry in catalog["bots"] if entry["name"] == "SpinBot"]
        self.assertEqual(["superseded", "active"], [entry["status"] for entry in spinbot])
        # Later regenerations keep the history instead of dropping the superseded entry.
        self.assertEqual(0, self.run_validator("--generate").returncode)
        catalog = json.loads((self.root / "bots" / "index.json").read_text(encoding="utf-8"))
        spinbot = [entry for entry in catalog["bots"] if entry["name"] == "SpinBot"]
        self.assertEqual([("1.0.0", "superseded"), ("1.0.4", "active")],
                         [(entry["version"], entry["status"]) for entry in spinbot])

    def test_removed_bot_is_retired_and_its_name_stays_reserved(self) -> None:
        self.assertEqual(0, self.run_validator("--generate").returncode)
        shutil.rmtree(self.root / "bots" / "java" / "SpinBot")

        for _ in range(2):  # the retired entry also survives a later regeneration
            result = self.run_validator("--generate")
            self.assertEqual(0, result.returncode, result.stderr)
            catalog = json.loads((self.root / "bots" / "index.json").read_text(encoding="utf-8"))
            self.assertEqual([("1.0.0", "retired")], [(entry["version"], entry["status"])
                                                      for entry in catalog["bots"] if entry["name"] == "SpinBot"])

        owners = json.loads((self.root / "bots" / "owners.json").read_text(encoding="utf-8"))
        record = owners["owners"][0]
        self.assertIn("SpinBot", record["bots"])
        active = [entry for entry in catalog["bots"] if entry["status"] == "active"]
        self.assertEqual(len(active), record["activeSlots"])
        self.add_java_bot("SpinBot")
        self.assertIn("belongs to owner", self.run_validator(owner="alice").stderr)

    def statuses(self, *names: str) -> dict[str, list[str]]:
        catalog = json.loads((self.root / "bots" / "index.json").read_text(encoding="utf-8"))
        return {name: [entry["status"] for entry in catalog["bots"] if entry["name"] == name] for name in names}

    def disqualify(self, *names: str) -> None:
        banned = {"schemaVersion": 1, "bannedOwners": [], "disqualifiedBots": [{"bot": name} for name in names]}
        (self.root / "bots" / "banned.json").write_text(json.dumps(banned), encoding="utf-8")

    def test_disqualified_bot_and_its_team_leave_the_active_catalog(self) -> None:
        self.assertEqual(0, self.run_validator("--generate").returncode)
        catalog = json.loads((self.root / "bots" / "index.json").read_text(encoding="utf-8"))
        added_at = next(entry["addedAt"] for entry in catalog["bots"] if entry["name"] == "Crazy")
        self.disqualify("Crazy")

        # Moderators publish the disqualification; the moderator is not the bots' owner.
        for _ in range(2):  # it also survives a later regeneration
            result = self.run_validator("--generate", owner="moderator")
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual({"Crazy": ["disqualified"], "CornersCrazy": ["disqualified"], "Corners": ["active"]},
                             self.statuses("Crazy", "CornersCrazy", "Corners"))
        owners = json.loads((self.root / "bots" / "owners.json").read_text(encoding="utf-8"))
        record = next(owner for owner in owners["owners"] if owner["ownerId"] == TEST_OWNER)
        self.assertIn("Crazy", record["bots"])
        self.assertNotIn("moderator", [owner["ownerId"] for owner in owners["owners"]])

        # The unchanged disqualified bot does not block another owner's submission ...
        self.add_bot("Nova")
        result = self.run_validator("--generate", owner="alice")
        self.assertEqual(0, result.returncode, result.stderr)
        # ... but a changed one is rejected.
        source = next((self.root / "bots" / "java" / "Crazy").rglob("*.java"))
        source.write_bytes(source.read_bytes() + b"\n// changed\n")
        self.assertIn("bot `Crazy` is disqualified", self.run_validator(owner=TEST_OWNER).stderr)
        source.write_bytes(source.read_bytes().removesuffix(b"\n// changed\n"))
        # A team with a disqualified member cannot be submitted either.
        self.assertIn("team `CornersCrazy` has a disqualified member", self.bump_team("CornersCrazy").stderr)

        # Lifting the disqualification republishes the unchanged bots for their owner.
        self.disqualify()
        result = self.run_validator("--generate", owner="moderator")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual({"Crazy": ["active"], "CornersCrazy": ["active"]}, self.statuses("Crazy", "CornersCrazy"))
        catalog = json.loads((self.root / "bots" / "index.json").read_text(encoding="utf-8"))
        crazy = next(entry for entry in catalog["bots"] if entry["name"] == "Crazy" and entry["status"] == "active")
        self.assertEqual(TEST_OWNER, crazy["owner"])
        self.assertEqual(added_at, crazy["addedAt"])

        # Disqualifying it again records no duplicate entry.
        self.disqualify("Crazy")
        self.assertEqual(0, self.run_validator("--generate", owner="moderator").returncode)
        self.assertEqual({"Crazy": ["disqualified"]}, self.statuses("Crazy"))

    def bump_team(self, name: str) -> subprocess.CompletedProcess[str]:
        config_path = next(self.root.glob(f"bots/*/{name}/{name}.json"))
        original = config_path.read_bytes()
        config = json.loads(original)
        config["version"] = "9.9.9"
        config_path.write_text(json.dumps(config), encoding="utf-8")
        result = self.run_validator(owner=TEST_OWNER)
        config_path.write_bytes(original)
        return result

    def test_a_non_owner_cannot_restore_a_once_disqualified_version(self) -> None:
        config_path = self.root / "bots" / "java" / "SpinBot" / "SpinBot.json"
        original = config_path.read_bytes()
        self.assertEqual(0, self.run_validator("--generate").returncode)
        self.disqualify("SpinBot")
        self.assertEqual(0, self.run_validator("--generate", owner="moderator").returncode)
        self.disqualify()
        self.assertEqual(0, self.run_validator("--generate", owner="moderator").returncode)
        config = json.loads(original)
        config["version"] = "1.0.4"
        config_path.write_text(json.dumps(config), encoding="utf-8")
        self.assertEqual(0, self.run_validator("--generate").returncode)

        config_path.write_bytes(original)  # back to the bytes of the disqualified 1.0.0
        self.assertIn("belongs to owner", self.run_validator(owner="alice").stderr)

    def test_a_removed_and_disqualified_bot_is_recorded_as_disqualified(self) -> None:
        self.assertEqual(0, self.run_validator("--generate").returncode)
        shutil.rmtree(self.root / "bots" / "java" / "SpinBot")
        self.disqualify("SpinBot")
        result = self.run_validator("--generate", owner="moderator")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual({"SpinBot": ["disqualified"]}, self.statuses("SpinBot"))

        # Lifting the ban leaves the removed bot retired, not disqualified.
        self.disqualify()
        self.assertEqual(0, self.run_validator("--generate", owner="moderator").returncode)
        self.assertEqual({"SpinBot": ["retired"]}, self.statuses("SpinBot"))

    def test_rolling_back_to_a_superseded_version_keeps_one_entry_per_version(self) -> None:
        config_path = self.root / "bots" / "java" / "SpinBot" / "SpinBot.json"
        original = config_path.read_bytes()
        self.assertEqual(0, self.run_validator("--generate").returncode)
        config = json.loads(original)
        config["version"] = "1.0.4"
        config_path.write_text(json.dumps(config), encoding="utf-8")
        self.assertEqual(0, self.run_validator("--generate").returncode)
        config_path.write_bytes(original)
        self.assertEqual(0, self.run_validator("--generate").returncode)
        catalog = json.loads((self.root / "bots" / "index.json").read_text(encoding="utf-8"))
        self.assertEqual([("1.0.4", "superseded"), ("1.0.0", "active")],
                         [(entry["version"], entry["status"]) for entry in catalog["bots"] if entry["name"] == "SpinBot"])

    def test_new_bot_from_another_owner_ignores_unchanged_catalog_entries(self) -> None:
        self.assertEqual(0, self.run_validator("--generate").returncode)
        self.add_bot("Nova")
        result = self.run_validator("--generate", owner="alice")
        self.assertEqual(0, result.returncode, result.stderr)
        catalog = json.loads((self.root / "bots" / "index.json").read_text(encoding="utf-8"))
        nova = next(entry for entry in catalog["bots"] if entry["name"] == "Nova")
        self.assertEqual("alice", nova["owner"])

    def test_general_validation_does_not_claim_another_owners_unpublished_bot(self) -> None:
        config_path = self.root / "bots" / "java" / "SpinBot" / "SpinBot.json"
        config = json.loads(config_path.read_text(encoding="utf-8"))
        config["version"] = "1.0.4"
        config_path.write_text(json.dumps(config), encoding="utf-8")
        self.assertIn("belongs to owner", self.run_validator(owner="alice").stderr)
        result = self.run_validator("--skip-governance", owner="alice")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("cannot be used with --generate", self.run_validator("--skip-governance", "--generate").stderr)

    def test_registered_secondary_account_can_update_and_is_preserved(self) -> None:
        owners_path = self.root / "bots" / "owners.json"
        owners = json.loads(owners_path.read_text(encoding="utf-8"))
        owners["owners"][0]["ownerId"] = "primary"
        owners["owners"][0]["accounts"] = ["primary", "secondary"]
        owners_path.write_text(json.dumps(owners), encoding="utf-8")
        config_path = self.root / "bots" / "java" / "SpinBot" / "SpinBot.json"
        config = json.loads(config_path.read_text(encoding="utf-8"))
        config["version"] = "1.0.4"
        config_path.write_text(json.dumps(config), encoding="utf-8")
        result = self.run_validator("--generate", owner="secondary")
        self.assertEqual(0, result.returncode, result.stderr)
        regenerated_owners = json.loads(owners_path.read_text(encoding="utf-8"))
        self.assertEqual(["primary", "secondary"], regenerated_owners["owners"][0]["accounts"])

    def test_RBC004_IntegrationPositive_team_members_are_published_as_catalog_identities(self) -> None:
        self.add_bot("Nova")
        self.add_team("OrbitNova", ["Orbit 1.0.0", "Nova 1.0.0"])

        result = self.run_validator("--generate")

        self.assertEqual(0, result.returncode, result.stderr)
        catalog = json.loads((self.root / "bots" / "index.json").read_text(encoding="utf-8"))
        entries = {entry["name"]: entry for entry in catalog["bots"] if entry["status"] == "active"}
        self.assertEqual(["Orbit 1.0.0", "Nova 1.0.0"], entries["OrbitNova"]["teamMembers"])
        self.assertEqual("Python", entries["OrbitNova"]["platform"])
        self.assertEqual([], entries["Orbit"]["teamMembers"])

    def test_RBC004_IntegrationNegative_unknown_team_member_is_rejected(self) -> None:
        self.add_team("BrokenTeam", ["Orbit 1.0.0", "Missing 1.0"])

        result = self.run_validator("--generate")

        self.assertNotEqual(0, result.returncode)
        self.assertIn("unknown team member `Missing 1.0`", result.stderr)

    def test_RBC004_IntegrationNegative_member_version_bump_invalidates_the_team_identity(self) -> None:
        self.add_bot("Nova")
        self.add_team("OrbitNova", ["Orbit 1.0.0", "Nova 1.0.0"])
        self.assertEqual(0, self.run_validator("--generate").returncode)
        self.bump_version("Nova", "1.0.4")

        result = self.run_validator("--generate")

        self.assertNotEqual(0, result.returncode)
        self.assertIn("unknown team member `Nova 1.0.0`", result.stderr)

    def test_RBC004_IntegrationNegative_a_team_directory_may_not_carry_sources(self) -> None:
        self.add_team("OrbitOrbit", ["Orbit 1.0.0", "Orbit 1.0.0"])
        (self.root / "bots" / "python" / "OrbitOrbit" / "payload.py").write_text("print()", encoding="utf-8")

        result = self.run_validator("--generate")

        self.assertNotEqual(0, result.returncode)
        self.assertIn("must contain only OrbitOrbit.json", result.stderr)

    def test_RBC004_IntegrationPositive_a_team_may_span_two_platforms(self) -> None:
        self.add_java_bot("Comet")
        self.add_team("OrbitComet", ["Orbit 1.0.0", "Comet 1.0.2"])

        result = self.run_validator("--generate")

        self.assertEqual(0, result.returncode, result.stderr)
        catalog = json.loads((self.root / "bots" / "index.json").read_text(encoding="utf-8"))
        entries = {entry["name"]: entry for entry in catalog["bots"] if entry["status"] == "active"}
        self.assertEqual(["Orbit 1.0.0", "Comet 1.0.2"], entries["OrbitComet"]["teamMembers"])
        self.assertEqual("Mixed", entries["OrbitComet"]["platform"])

    def test_tree_hash_orders_files_by_posix_path_regardless_of_platform(self) -> None:
        # Path.__lt__ is case-insensitive on Windows and case-sensitive on POSIX, so a plain
        # `sorted(Path...)` would order "src/Nested.py" before "Top.cmd" on Windows but after it
        # on POSIX, producing different hashes for identical content depending on the host OS.
        directory = self.root / "bots" / "python" / "OrderCheck"
        (directory / "src").mkdir(parents=True)
        (directory / "Top.cmd").write_text("cmd", encoding="utf-8")
        (directory / "src" / "Nested.py").write_text("nested", encoding="utf-8")
        expected = hashlib.sha256()
        for relative, content in (("Top.cmd", b"cmd"), ("src/Nested.py", b"nested")):
            expected.update(relative.encode("utf-8"))
            expected.update(b"\0")
            expected.update(content)
            expected.update(b"\0")
        self.assertEqual(f"sha256:{expected.hexdigest()}", tree_hash(directory))

    @unittest.skipUnless(os.name == "posix", "the executable bit is only meaningful on POSIX")
    def test_non_executable_boot_script_is_rejected(self) -> None:
        script = self.root / "bots" / "python" / "Orbit" / "Orbit.sh"
        script.chmod(script.stat().st_mode & ~0o111)
        result = self.run_validator()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("executable bit", result.stderr)

    def test_windows_line_endings_in_a_launcher_are_rejected_with_a_plain_message(self) -> None:
        script = self.root / "bots" / "python" / "Orbit" / "Orbit.sh"
        script.write_bytes(script.read_bytes().replace(b"\n", b"\r\n"))
        result = self.run_validator()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Orbit.sh has Windows line endings (CRLF)", result.stderr)
        self.assertIn("convert it to Unix line endings (LF)", result.stderr)

    def test_windows_line_endings_in_a_source_file_or_the_configuration_are_rejected(self) -> None:
        for relative in ("src/Orbit.py", "Orbit.json"):
            with self.subTest(relative):
                path = self.root / "bots" / "python" / "Orbit" / relative
                original = path.read_bytes()
                path.write_bytes(original.replace(b"\n", b"\r\n") if b"\n" in original else original + b"\r\n")
                result = self.run_validator()
                self.assertNotEqual(0, result.returncode)
                self.assertIn(f"{relative} has Windows line endings", result.stderr)
                path.write_bytes(original)

    def test_unix_line_endings_are_accepted(self) -> None:
        for path in (self.root / "bots" / "python" / "Orbit").rglob("*"):
            if path.is_file():
                self.assertNotIn(b"\r\n", path.read_bytes(), str(path))
        self.assertEqual(0, self.run_validator().returncode)

    def set_game_types(self, name: str, game_types: object) -> None:
        config_path = self.root / "bots" / "python" / name / f"{name}.json"
        config = json.loads(config_path.read_text(encoding="utf-8"))
        config["gameTypes"] = game_types
        config_path.write_text(json.dumps(config), encoding="utf-8")

    def test_catalog_publishes_the_game_types_each_entry_plays(self) -> None:
        self.add_bot("Nova")
        self.set_game_types("Nova", ["1v1"])
        result = self.run_validator("--generate")
        self.assertEqual(0, result.returncode, result.stderr)
        catalog = json.loads((self.root / "bots" / "index.json").read_text(encoding="utf-8"))
        entries = {entry["name"]: entry for entry in catalog["bots"] if entry["status"] == "active"}
        self.assertEqual(["1v1"], entries["Nova"]["gameTypes"])
        self.assertEqual(["1v1", "melee", "twinduel"], entries["Corners"]["gameTypes"])
        self.assertEqual(["twinduel"], entries["CornersCrazy"]["gameTypes"])

    def test_invalid_game_types_are_rejected(self) -> None:
        # A wrongly cased or unranked name would otherwise leave the bot silently in no ranking.
        for game_types in ("1v1", [], ["1v1", "1v1"], [""], ["Melee"], ["1V1"], ["classic"], ["1v1", "meele"]):
            with self.subTest(game_types=game_types):
                self.set_game_types("Orbit", game_types)
                self.assertIn("`gameTypes` must be a non-empty list of distinct Rumble game types: 1v1, melee, twinduel",
                              self.run_validator().stderr)

    def test_a_team_member_must_declare_twinduel(self) -> None:
        self.add_bot("Nova")
        self.set_game_types("Nova", ["1v1", "melee"])
        self.add_team("OrbitNova", ["Orbit 1.0.0", "Nova 1.0.0"])
        self.assertIn("team member `Nova 1.0.0` does not declare the `twinduel` game type", self.run_validator().stderr)

    def test_a_team_declares_no_game_type_but_twinduel(self) -> None:
        self.add_bot("Nova")
        self.add_team("OrbitNova", ["Orbit 1.0.0", "Nova 1.0.0"])
        config_path = self.root / "bots" / "python" / "OrbitNova" / "OrbitNova.json"
        for game_types, accepted in ((["twinduel"], True), (["1v1"], False), ("twinduel", False)):
            with self.subTest(game_types=game_types):
                config = json.loads(config_path.read_text(encoding="utf-8"))
                config["gameTypes"] = game_types
                config_path.write_text(json.dumps(config), encoding="utf-8")
                result = self.run_validator()
                if accepted:
                    self.assertEqual(0, result.returncode, result.stderr)
                else:
                    self.assertIn("a team plays only `twinduel`", result.stderr)

    def test_RBC004_IntegrationNegative_duplicate_bot_names_across_platforms_are_rejected(self) -> None:
        self.add_java_bot("Orbit")

        result = self.run_validator("--generate")

        self.assertNotEqual(0, result.returncode)
        self.assertIn("is already used by", result.stderr)


if __name__ == "__main__":
    unittest.main()
