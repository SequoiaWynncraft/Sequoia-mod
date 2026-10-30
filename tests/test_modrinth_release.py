import email.parser
import email.policy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch


SCRIPTS = Path(__file__).parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))
SPEC = importlib.util.spec_from_file_location("modrinth_release", SCRIPTS / "modrinth_release.py")
MODRINTH = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODRINTH)


def sha512(data):
    return hashlib.sha512(data).hexdigest()


class FakeModrinth:
    """Answers the API calls a publish makes; versions maps version_number -> held file hashes."""

    def __init__(self, versions=None):
        self.versions = dict(versions or {})
        self.calls = []

    def __call__(self, method, path, token=None, body=None, content_type=None):
        self.calls.append((method, path, token))
        if (method, path) == ("GET", "/project/sequoia"):
            return {"id": "PROJECT"}
        if (method, path) == ("GET", "/project/PROJECT/version"):
            return [{"id": "id-" + number, "version_number": number} for number in self.versions]
        if (method, path) == ("POST", "/version"):
            parts = parse(body, content_type)
            data = json.loads(parts["data"])
            self.created = data
            self.versions[data["version_number"]] = [sha512(parts["primary"]), sha512(parts["source"])]
            return {"id": "id-" + data["version_number"]}
        if method == "GET" and path.startswith("/version/id-"):
            return {"files": [{"hashes": {"sha512": h}} for h in self.versions[path.removeprefix("/version/id-")]]}
        raise AssertionError(f"unexpected call {method} {path}")


def parse(body, content_type):
    message = email.parser.BytesParser(policy=email.policy.HTTP).parsebytes(
        f"Content-Type: {content_type}\r\n\r\n".encode() + body)
    return {part.get_param("name", header="content-disposition"): part.get_payload(decode=True)
            for part in message.iter_parts()}


class ModrinthReleaseTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.directory = Path(temp.name)
        self.jar = self.directory / "sequoia-1.12.0.jar"
        self.jar.write_bytes(b"jar")
        (self.directory / "sequoia-images-native-source.zip").write_bytes(b"source")
        self.metadata = {"version_number": "1.12.0", "minecraft_version": "1.21.11", "version_type": "release",
                         "changelog": "Notes", "mod_jar": str(self.jar)}
        patch.object(MODRINTH.release_metadata, "release_metadata", return_value=self.metadata).start()
        patch.dict("os.environ", {"MODRINTH_TOKEN": "secret"}).start()
        self.addCleanup(patch.stopall)

    def publish(self, fake):
        with patch.object(MODRINTH, "call", fake):
            MODRINTH.publish("v1.12.0", self.directory)

    def test_check_needs_the_token_before_anything_is_published(self):
        fake = FakeModrinth()
        with patch.dict("os.environ", {"MODRINTH_TOKEN": ""}), patch.object(MODRINTH, "call", fake):
            with self.assertRaisesRegex(ValueError, "MODRINTH_TOKEN"):
                MODRINTH.check()
        self.assertEqual([], fake.calls)
        with patch.object(MODRINTH, "call", fake):
            MODRINTH.check()

    def test_publish_creates_the_version_from_the_accepted_files_and_reads_it_back(self):
        fake = FakeModrinth()
        self.publish(fake)
        self.assertEqual({"name": "Sequoia 1.12.0", "version_number": "1.12.0", "changelog": "Notes",
                          "project_id": "PROJECT", "game_versions": ["1.21.11"], "version_type": "release",
                          "dependencies": [], "loaders": ["fabric"], "featured": True, "status": "listed",
                          "file_parts": ["primary", "source"], "primary_file": "primary"}, fake.created)
        self.assertEqual([("POST", "/version", "secret")], [c for c in fake.calls if c[0] == "POST"])
        self.assertTrue(all(c[2] is None for c in fake.calls if c[0] == "GET"), "reads send no token")

    def test_a_rerun_only_checks_the_existing_version(self):
        fake = FakeModrinth({"1.12.0": [sha512(b"jar"), sha512(b"source")]})
        self.publish(fake)
        self.assertNotIn("POST", [c[0] for c in fake.calls])

    def test_a_version_holding_other_files_fails(self):
        fake = FakeModrinth({"1.12.0": [sha512(b"jar"), sha512(b"other source")]})
        with self.assertRaisesRegex(ValueError, "does not hold sequoia-images-native-source.zip"):
            self.publish(fake)


if __name__ == "__main__":
    unittest.main()
