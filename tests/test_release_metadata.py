import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile


SPEC = importlib.util.spec_from_file_location(
    "release_metadata", Path(__file__).parents[1] / "scripts/release_metadata.py")
RELEASE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RELEASE)


class ReleaseMetadataTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        self.git("init", "--quiet")
        self.git("config", "user.name", "Release test")
        self.git("config", "user.email", "release-test@example.invalid")
        (self.repo / "gradle.properties").write_text("mod_version=1.11.1\nminecraft_version=1.21.11\n")
        self.git("add", ".")
        self.git("commit", "--quiet", "-m", "Release fixture")

    def git(self, *args):
        return subprocess.run(["git", *args], cwd=self.repo, check=True, capture_output=True, text=True).stdout.strip()

    def tag(self, notes="Release notes\n\n- Shared HQ and trade style."):
        self.git("tag", "-a", "v1.11.1", "-m", notes)

    def jar(self, version="1.11.1", minecraft="1.21.11", name="sequoia-1.11.1.jar"):
        directory = self.repo / "build/libs"
        directory.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(directory / name, "w") as archive:
            archive.writestr("fabric.mod.json", json.dumps({
                "id": "seq", "version": version, "depends": {"minecraft": minecraft}}))

    def test_rejects_lightweight_tag_before_building(self):
        self.git("tag", "v1.11.1")
        with self.assertRaisesRegex(ValueError, "annotated tag"):
            RELEASE.release_metadata(self.repo, "v1.11.1")

    def test_rejects_empty_or_placeholder_release_notes(self):
        for notes in ("", "v1.11.1"):
            with self.subTest(notes=notes):
                self.git("tag", "-a", "v1.11.1", "-m", notes)
                with self.assertRaisesRegex(ValueError, "release notes"):
                    RELEASE.release_metadata(self.repo, "v1.11.1")
                self.git("tag", "-d", "v1.11.1")

    def test_rejects_tag_and_source_version_mismatch(self):
        self.tag()
        (self.repo / "gradle.properties").write_text("mod_version=1.10.17\nminecraft_version=1.21.11\n")
        with self.assertRaisesRegex(ValueError, "does not match gradle.properties"):
            RELEASE.release_metadata(self.repo, "v1.11.1")

    def test_rejects_tag_pointing_to_another_checkout(self):
        self.tag()
        self.git("commit", "--allow-empty", "--quiet", "-m", "Another commit")
        with self.assertRaisesRegex(ValueError, "checked-out commit"):
            RELEASE.release_metadata(self.repo, "v1.11.1")

    def test_accepts_annotated_release_and_preserves_multiline_notes(self):
        notes = "Release notes\n\nLiteral $(do-not-execute), % and Unicode: HQ → fastest."
        self.tag(notes)
        result = RELEASE.release_metadata(self.repo, "v1.11.1")
        self.assertEqual("1.11.1", result["version_number"])
        self.assertEqual(notes, result["changelog"])
        output = self.repo / "outputs"
        RELEASE.write_outputs(result, output)
        self.assertIn(notes, output.read_text())

    def test_explicit_no_release_notes_sentinel_is_supported(self):
        self.tag("[no-release-notes]")
        self.assertEqual("", RELEASE.release_metadata(self.repo, "v1.11.1")["changelog"])

    def test_rejects_packaged_version_or_minecraft_mismatch(self):
        self.tag()
        for version, minecraft in (("1.10.17", "1.21.11"), ("1.11.1", "1.21.10")):
            self.jar(version, minecraft)
            with self.assertRaisesRegex(ValueError, "packaged"):
                RELEASE.release_metadata(self.repo, "v1.11.1", Path("build/libs"))

    def test_requires_exactly_one_primary_jar(self):
        self.tag()
        with self.assertRaisesRegex(ValueError, "exactly one"):
            RELEASE.release_metadata(self.repo, "v1.11.1", Path("build/libs"))
        self.jar()
        self.jar(name="sequoia-1.11.1-sources.jar")
        result = RELEASE.release_metadata(self.repo, "v1.11.1", Path("build/libs"))
        self.assertEqual("build/libs/sequoia-1.11.1.jar", result["mod_jar"])
        self.jar(name="unexpected.jar")
        with self.assertRaisesRegex(ValueError, "exactly one"):
            RELEASE.release_metadata(self.repo, "v1.11.1", Path("build/libs"))


if __name__ == "__main__":
    unittest.main()
