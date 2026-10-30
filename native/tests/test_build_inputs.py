from contextlib import redirect_stdout
import hashlib
import importlib.util
import io
import json
import lzma
from pathlib import Path
import sys
import tarfile
import tempfile
import tomllib
import unittest
from unittest.mock import patch
import zipfile


sys.path.insert(0, str(Path(__file__).parents[1]))


def load(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).parents[1] / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def sha256(data):
    return hashlib.sha256(data).hexdigest()


class SourceVerificationTest(unittest.TestCase):
    def setUp(self):
        self.guest = load("guest")
        self.pinned = self.guest.SOURCES["ffmpeg"]["signing_key_fingerprint"]
        self.key = self.guest.ROOT / self.guest.SOURCES["ffmpeg"]["signing_key"]

    def gpgv(self, stdout, returncode=0):
        run = patch.object(self.guest.subprocess, "run").start()
        self.addCleanup(patch.stopall)
        run.return_value.stdout, run.return_value.stderr = stdout, ""
        run.return_value.returncode = returncode
        return run

    def test_rejects_modified_archive(self):
        with tempfile.TemporaryDirectory() as temp:
            archive = Path(temp) / "source.tar.xz"
            archive.write_bytes(b"modified source")
            with self.assertRaises(ValueError):
                self.guest.require_pin(archive, sha256(b"official source"))

    def test_rejects_signature_from_another_key(self):
        self.gpgv("[GNUPG:] GOODSIG 0 x\n[GNUPG:] VALIDSIG " + "A" * 40 + " 2026-01-01 1 0 4 0 1 8 00 "
                  + "B" * 40 + "\n")
        with self.assertRaises(ValueError):
            self.guest.verify_signature(Path("archive"), Path("signature"), self.key, self.pinned)

    def test_missing_valid_signature_fails(self):
        self.gpgv("[GNUPG:] BADSIG unknown\n", returncode=1)
        with self.assertRaises(ValueError):
            self.guest.verify_signature(Path("archive"), Path("signature"), self.key, self.pinned)

    def test_accepts_pinned_primary_signing_key(self):
        run = self.gpgv("[GNUPG:] GOODSIG 0 x\n[GNUPG:] VALIDSIG " + "A" * 40
                        + " 2026-01-01 1 0 4 0 1 8 00 " + self.pinned + "\n")
        self.guest.verify_signature(Path("archive"), Path("signature"), self.key, self.pinned)
        self.assertEqual("gpgv", run.call_args.args[0][0])

    def test_rejects_changed_license_text(self):
        with tempfile.TemporaryDirectory() as temp:
            archive = Path(temp) / "lib-1.tar.gz"
            with tarfile.open(archive, "w:gz") as tar:
                info = tarfile.TarInfo("lib-1/LICENSE")
                info.size = 7
                tar.addfile(info, io.BytesIO(b"changed"))
            with self.assertRaises(ValueError):
                self.guest.require_license(archive, "lib-1/LICENSE", sha256(b"bundled"))

class PackagingTest(unittest.TestCase):
    def setUp(self):
        self.package = load("package")

    def helpers(self, out, guest_sha256):
        for platform in self.package.PLATFORMS:
            folder = out / "sandbox" / platform
            folder.mkdir(parents=True)
            hashes = {}
            for name in self.package.SHIPPED[platform.split("-")[0]]:
                data = (platform + name).encode()
                (folder / name).write_bytes(data)
                hashes[name] = sha256(data)
            (out / platform).mkdir()
            (out / platform / "validation.json").write_text(json.dumps(
                {"platform": platform, "guest_sha256": guest_sha256, "executables": hashes}))

    def test_packages_every_platform(self):
        with tempfile.TemporaryDirectory() as temp:
            out = Path(temp)
            self.helpers(out, "guest")
            found = self.package.platforms(out, "guest", require_all=True)
            self.assertEqual(sorted(self.package.PLATFORMS), sorted(found))
            self.assertEqual(8, sum(len(files) for files in found.values()))

    def test_missing_platform_fails_for_a_release(self):
        with tempfile.TemporaryDirectory() as temp:
            out = Path(temp)
            self.helpers(out, "guest")
            for path in sorted((out / "sandbox/macos-x86_64").iterdir()):
                path.unlink()
            (out / "sandbox/macos-x86_64").rmdir()
            with self.assertRaises(ValueError):
                self.package.platforms(out, "guest", require_all=True)
            self.assertEqual(5, len(self.package.platforms(out, "guest", require_all=False)))

    def test_changed_helper_fails(self):
        with tempfile.TemporaryDirectory() as temp:
            out = Path(temp)
            self.helpers(out, "guest")
            (out / "sandbox/windows-x86_64/sandbox.exe").write_bytes(b"tampered")
            with self.assertRaises(ValueError):
                self.package.platforms(out, "guest", require_all=True)

    def test_helper_built_from_another_guest_fails(self):
        with tempfile.TemporaryDirectory() as temp:
            out = Path(temp)
            self.helpers(out, "other guest")
            with self.assertRaises(ValueError):
                self.package.platforms(out, "guest", require_all=True)

    def packed(self, out):
        self.helpers(out, "guest")
        found = self.package.platforms(out, "guest", require_all=False)
        return found, self.package.write_packs(out, found)

    def test_packs_reproduce_every_verified_helper_byte_for_byte(self):
        with tempfile.TemporaryDirectory() as temp:
            out = Path(temp)
            found, packs = self.packed(out)
            self.assertEqual(["x86_64", "aarch64"], list(packs))
            for arch, entry in packs.items():
                data = lzma.decompress((out / "sandbox" / entry["file"]).read_bytes())
                self.assertEqual(entry["uncompressed_bytes"], len(data))
                offset = 0
                for member in entry["members"]:
                    self.assertEqual(offset, member["offset"])
                    platform, name = member["path"].split("/")
                    content = data[offset:offset + member["bytes"]]
                    self.assertEqual((out / "sandbox" / member["path"]).read_bytes(), content)
                    self.assertEqual(found[platform][name], member["sha256"])
                    offset += member["bytes"]
                self.assertTrue(entry["members"][0]["path"].startswith("windows-" + arch))
            self.assertEqual(8, sum(len(entry["members"]) for entry in packs.values()))

    def test_same_helpers_give_the_same_pack(self):
        with tempfile.TemporaryDirectory() as temp:
            out = Path(temp)
            found, packs = self.packed(out)
            first = (out / "sandbox/helpers-x86_64.xz").read_bytes()
            self.assertEqual(packs, self.package.write_packs(out, found))
            self.assertEqual(first, (out / "sandbox/helpers-x86_64.xz").read_bytes())

    def test_partial_build_packs_only_present_platforms(self):
        with tempfile.TemporaryDirectory() as temp:
            out = Path(temp)
            self.helpers(out, "guest")
            for platform in ("windows-aarch64", "linux-aarch64", "macos-aarch64"):
                for path in (out / "sandbox" / platform).iterdir():
                    path.unlink()
                (out / "sandbox" / platform).rmdir()
            packs = self.package.write_packs(out, self.package.platforms(out, "guest", require_all=False))
            self.assertEqual(["x86_64"], list(packs))
            self.assertFalse((out / "sandbox/helpers-aarch64.xz").exists())

    def test_unpack_restores_helpers_and_rejects_a_changed_pack(self):
        with tempfile.TemporaryDirectory() as temp:
            out = Path(temp)
            _, packs = self.packed(out)
            provenance = out / "provenance.json"
            provenance.write_text(json.dumps({"packs": packs}))
            pack = out / "sandbox/helpers-aarch64.xz"
            with redirect_stdout(io.StringIO()):
                self.package.unpack(pack, provenance, out / "unpacked")
            for member in packs["aarch64"]["members"]:
                self.assertEqual((out / "sandbox" / member["path"]).read_bytes(),
                                 (out / "unpacked" / member["path"]).read_bytes())
            data = bytearray(pack.read_bytes())
            data[len(data) // 2] ^= 1
            pack.write_bytes(bytes(data))
            with self.assertRaises(ValueError):
                self.package.unpack(pack, provenance, out / "again")

    def test_index_entries_must_cover_the_pack_with_safe_paths(self):
        with tempfile.TemporaryDirectory() as temp:
            out = Path(temp)
            _, packs = self.packed(out)
            packed = (out / "sandbox/helpers-x86_64.xz").read_bytes()
            escaping = json.loads(json.dumps(packs["x86_64"]))
            escaping["members"][0]["path"] = "../escape"
            with self.assertRaises(ValueError):
                self.package.check_pack(packed, escaping)
            short = json.loads(json.dumps(packs["x86_64"]))
            short["members"].pop()
            with self.assertRaises(ValueError):
                self.package.check_pack(packed, short)

    def test_the_jar_must_carry_exactly_the_tested_packs(self):
        with tempfile.TemporaryDirectory() as temp:
            out, libs = Path(temp) / "out", Path(temp) / "libs"
            (out / "sandbox").mkdir(parents=True)
            libs.mkdir()
            for name in self.package.JAR_FILES:
                (out / "sandbox" / name).write_bytes(name.encode())

            def jar(changed=None, missing=None):
                with zipfile.ZipFile(libs / "sequoia-1.12.0.jar", "w") as archive:
                    for name in self.package.JAR_FILES:
                        if name != missing:
                            archive.writestr("sandbox/" + name, b"other" if name == changed else name.encode())

            jar()
            (libs / "sequoia-1.12.0-sources.jar").write_bytes(b"sources")
            with redirect_stdout(io.StringIO()):
                self.package.verify_jar(libs, out)
                for problem in ({"changed": "helpers-aarch64.xz"}, {"missing": "provenance.json"}):
                    jar(**problem)
                    with self.assertRaises(ValueError):
                        self.package.verify_jar(libs, out)


class ToolchainPinTest(unittest.TestCase):
    def test_mise_installs_the_toolchain_cargo_reads(self):
        root = Path(__file__).parents[2]
        mise = tomllib.loads((root / "mise.toml").read_text())["tools"]["rust"]
        cargo = tomllib.loads((root / "native/decoder/rust-toolchain.toml").read_text())["toolchain"]
        self.assertEqual((cargo["channel"], cargo["profile"]), (mise["version"], mise["profile"]))


if __name__ == "__main__":
    unittest.main()
