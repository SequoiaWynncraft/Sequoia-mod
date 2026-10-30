import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile


SCRIPTS = Path(__file__).parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))
SPEC = importlib.util.spec_from_file_location("github_release", SCRIPTS / "github_release.py")
RELEASE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RELEASE)
# The modules promote imports, so the tests can stand in for their network calls.
import modrinth_release
import release_metadata

TAG = "v1.12.0"
COMMIT = "c" * 40


def sha256(data):
    return hashlib.sha256(data).hexdigest()


class GitHubReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.files = {}
        self.write("sequoia-1.12.0.jar", self.jar())
        self.write("sequoia-1.12.0.jar.sha256", self.sidecar())
        self.write("sequoia-1.12.0-sources.jar", b"sources")
        self.write("sequoia-1.12.0-sources.jar.sha256", b"y" * 93)
        self.write("sequoia-images-native-source.zip", b"source zip")

    def jar(self, version="1.12.0", commit=COMMIT):
        path = self.directory / "build.jar"
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("fabric.mod.json", json.dumps({"id": "seq", "version": version}))
            archive.writestr("sandbox/provenance.json", json.dumps({"source_commit": commit}))
        data = path.read_bytes()
        path.unlink()
        return data

    def sidecar(self, digest=None, name="sequoia-1.12.0.jar"):
        """The sha256sum line Release writes, by default for the JAR as written."""
        return f"{digest or sha256(self.files['sequoia-1.12.0.jar'])}  {name}\n".encode()

    def write(self, name, data):
        (self.directory / name).write_bytes(data)
        self.files[name] = data

    def local(self):
        return RELEASE.local_files(sorted(self.directory / name for name in self.files))

    def assets(self, **changes):
        assets = [{"name": name, "size": len(data), "digest": "sha256:" + sha256(data)}
                  for name, data in self.files.items()]
        for asset in assets:
            asset.update(changes.get(asset["name"], {}))
        return assets

    def test_the_built_files_pass(self):
        RELEASE.check_assets(TAG, self.assets(), self.local())

    def test_a_missing_extra_or_changed_asset_fails(self):
        assets = self.assets()
        with self.assertRaises(ValueError):
            RELEASE.check_assets(TAG, assets[1:], self.local())
        with self.assertRaises(ValueError):
            RELEASE.check_assets(TAG, assets + [{"name": "notes.txt", "size": 1, "digest": "sha256:" + "0" * 64}],
                                 self.local())
        with self.assertRaises(ValueError):
            RELEASE.check_assets(TAG, self.assets(**{"sequoia-1.12.0.jar": {"digest": "sha256:" + "0" * 64}}),
                                 self.local())
        with self.assertRaises(ValueError):
            RELEASE.check_assets(TAG, self.assets(**{"sequoia-1.12.0.jar": {"digest": None}}), self.local())

    def test_older_updaters_must_find_exactly_the_mod_jar(self):
        self.assertEqual(["sequoia-1.12.0.jar"], RELEASE.old_updater_choices(sorted(self.files)))
        self.write("sequoia-1.12.0-windows.jar", b"another jar")
        with self.assertRaisesRegex(ValueError, "exactly one mod JAR"):
            RELEASE.check_assets(TAG, self.assets(), self.local())

    def test_older_updaters_need_the_checksum_file(self):
        del self.files["sequoia-1.12.0.jar.sha256"]
        (self.directory / "sequoia-1.12.0.jar.sha256").unlink()
        with self.assertRaisesRegex(ValueError, "sha256 is missing"):
            RELEASE.check_assets(TAG, self.assets(), self.local())

    def test_promotion_accepts_only_the_accepted_candidate_from_the_tagged_commit(self):
        jar = self.directory / "sequoia-1.12.0.jar"
        RELEASE.check_candidate(TAG, jar, sha256(self.files[jar.name]).upper(), COMMIT)
        with self.assertRaisesRegex(ValueError, "accepted candidate"):
            RELEASE.check_candidate(TAG, jar, "0" * 64, COMMIT)
        with self.assertRaisesRegex(ValueError, "built from"):
            RELEASE.check_candidate(TAG, jar, sha256(self.files[jar.name]), "d" * 40)
        self.write("sequoia-1.12.0.jar", self.jar(version="1.12.1"))
        self.write("sequoia-1.12.0.jar.sha256", self.sidecar())
        with self.assertRaisesRegex(ValueError, "not Sequoia 1.12.0"):
            RELEASE.check_candidate(TAG, jar, sha256(self.files[jar.name]), COMMIT)

    def test_promotion_requires_the_checksum_file_to_name_and_hash_the_accepted_jar(self):
        jar = self.directory / "sequoia-1.12.0.jar"
        accepted = sha256(self.files[jar.name])
        RELEASE.check_candidate(TAG, jar, accepted, COMMIT)
        for wrong in (self.sidecar(digest="0" * 64), self.sidecar(name="sequoia-1.11.1.jar"), b"not a checksum\n"):
            self.write("sequoia-1.12.0.jar.sha256", wrong)
            with self.assertRaisesRegex(ValueError, "sha256"):
                RELEASE.check_candidate(TAG, jar, accepted, COMMIT)

    def test_staging_never_changes_a_published_release(self):
        with patch.object(RELEASE, "find_release", return_value={"draft": False, "id": 1, "assets": []}), \
                patch.object(RELEASE, "gh", side_effect=AssertionError("no GitHub changes")):
            RELEASE.stage(TAG, sorted(self.directory.iterdir()))

    def test_drafting_stages_each_file_with_a_checksum_file_sha256sum_reads(self):
        libs, source = self.directory / "libs", self.directory / "source"
        libs.mkdir(), source.mkdir()
        (libs / "sequoia-1.12.0.jar").write_bytes(self.jar())
        (libs / "sequoia-1.12.0-sources.jar").write_bytes(b"sources")
        (source / "sequoia-images-native-source.zip").write_bytes(b"source zip")
        (source / "sequoia-images-native-source.zip.sha256").write_text("stale\n")
        summary = self.directory / "summary.md"
        staged = []
        with patch.object(RELEASE, "stage", side_effect=lambda tag, paths: staged.append(paths)), \
                patch.object(RELEASE, "verify", side_effect=lambda tag, paths: staged.append(paths)), \
                patch.dict("os.environ", {"GITHUB_STEP_SUMMARY": str(summary)}):
            RELEASE.draft(TAG, [libs, source])
        names = ["sequoia-1.12.0-sources.jar", "sequoia-1.12.0-sources.jar.sha256", "sequoia-1.12.0.jar",
                 "sequoia-1.12.0.jar.sha256", "sequoia-images-native-source.zip",
                 "sequoia-images-native-source.zip.sha256"]
        self.assertEqual([names, names], [[p.name for p in paths] for paths in staged])
        for path in staged[0][::2]:
            line = f"{sha256(path.read_bytes())}  {path.name}\n".encode()
            self.assertEqual(line, path.with_name(path.name + ".sha256").read_bytes())
        self.assertIn(f"{sha256((libs / 'sequoia-1.12.0.jar').read_bytes())}  sequoia-1.12.0.jar",
                      summary.read_text())

    def test_promotion_publishes_nothing_until_modrinth_can_follow(self):
        steps = []
        record = lambda name: (lambda *args, **_: steps.append(name))
        with patch.object(modrinth_release, "check", side_effect=record("modrinth check")), \
                patch.object(RELEASE, "fetch", side_effect=record("fetch")), \
                patch.object(release_metadata, "release_metadata", side_effect=record("validate")), \
                patch.object(RELEASE, "publish", side_effect=record("publish")), \
                patch.object(modrinth_release, "publish", side_effect=record("modrinth publish")):
            self.assertEqual(0, RELEASE.main(["promote", TAG, "0" * 64]))
            self.assertEqual(["modrinth check", "fetch", "validate", "publish", "modrinth publish"], steps)
            steps.clear()
            with patch.object(modrinth_release, "check", side_effect=ValueError("no token")):
                self.assertEqual(1, RELEASE.main(["promote", TAG, "0" * 64]))
            self.assertEqual([], steps)

    def test_staging_replaces_the_files_of_a_draft(self):
        draft = {"draft": True, "id": 7, "assets": [{"name": "sequoia-1.12.0.jar", "id": 70}]}
        calls = []
        with patch.object(RELEASE, "find_release", return_value=draft), \
                patch.object(RELEASE, "repository", return_value="owner/repo"), \
                patch.object(RELEASE, "gh", side_effect=lambda *args, **_: calls.append(args) or ""):
            RELEASE.stage(TAG, [self.directory / "sequoia-1.12.0.jar"])
        self.assertEqual(("api", "--method", "DELETE", "repos/owner/repo/releases/assets/70"), calls[0])
        self.assertIn("https://uploads.github.com/repos/owner/repo/releases/7/assets?name=sequoia-1.12.0.jar", calls[1])


if __name__ == "__main__":
    unittest.main()
