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

    def test_download_recovers_from_transient_errors(self):
        with tempfile.TemporaryDirectory() as temp, \
                patch.object(self.guest.urllib.request, "urlopen", side_effect=[
                    self.guest.urllib.error.URLError(TimeoutError()),
                    ConnectionResetError(), io.BytesIO(b"complete archive")]), \
                patch.object(self.guest.time, "sleep"):
            target = Path(temp) / "archive"
            self.guest.download("https://example.com/archive", target)
            self.assertEqual(b"complete archive", target.read_bytes())

    def test_download_does_not_retry_permanent_http_errors(self):
        error = self.guest.urllib.error.HTTPError("https://example.com/archive", 404, "missing", {}, None)
        with patch.object(self.guest.urllib.request, "urlopen", side_effect=error) as request, \
                patch.object(self.guest.time, "sleep") as sleep:
            with self.assertRaises(self.guest.urllib.error.HTTPError):
                self.guest.download(error.url, Path("unused"))
            self.assertEqual(1, request.call_count)
            sleep.assert_not_called()

    def test_download_stops_after_three_failed_attempts(self):
        with patch.object(self.guest.urllib.request, "urlopen", side_effect=ConnectionResetError()) as request, \
                patch.object(self.guest.time, "sleep"):
            with self.assertRaises(ConnectionResetError):
                self.guest.download("https://example.com/archive", Path("unused"))
            self.assertEqual(3, request.call_count)

    def test_download_retry_replaces_partial_bytes(self):
        class Interrupted(io.BytesIO):
            def read(self, size=-1):
                if self.tell():
                    raise ConnectionResetError()
                return super().read(size)

        with tempfile.TemporaryDirectory() as temp, \
                patch.object(self.guest.urllib.request, "urlopen", side_effect=[
                    Interrupted(b"partial archive is longer"), io.BytesIO(b"complete")]), \
                patch.object(self.guest.time, "sleep"):
            target = Path(temp) / "archive"
            self.guest.download("https://example.com/archive", target)
            self.assertEqual(b"complete", target.read_bytes())

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

class SourceReuseTest(unittest.TestCase):
    """Authenticated archives are reused, never trusted: every pin, notice and signature check runs."""

    def setUp(self):
        self.guest = load("guest")
        self.directory = Path(self.enterContext(tempfile.TemporaryDirectory()))
        self.files = {}
        sources = {}
        for name, suffix, member, mode in (("ffmpeg", "tar.xz", "COPYING.LGPLv2.1", "w:xz"),
                                           ("zlib", "tar.gz", "LICENSE", "w:gz")):
            archive = self.directory / f"{name}-1.{suffix}"
            with tarfile.open(archive, mode) as tar:
                info = tarfile.TarInfo(f"{name}-1/{member}")
                info.size = len(name)
                tar.addfile(info, io.BytesIO(name.encode()))
            self.files[archive.name] = archive.read_bytes()
            sources[name] = {"version": "1", "url": f"https://example.invalid/{archive.name}",
                             "sha256": sha256(self.files[archive.name]),
                             "license_files": {member: sha256(name.encode())}}
        sources["ffmpeg"].update(signature_url="https://example.invalid/ffmpeg-1.tar.xz.asc",
                                 signing_key="key.asc", signing_key_fingerprint="F" * 40)
        self.files["ffmpeg-1.tar.xz.asc"] = b"signature"
        (self.directory / "ffmpeg-1.tar.xz.asc").write_bytes(b"signature")
        self.enterContext(patch.object(self.guest, "SOURCES", sources))
        self.verify = self.enterContext(patch.object(self.guest, "verify_signature"))
        self.download = self.enterContext(patch.object(self.guest, "download", side_effect=self.serve))
        self.enterContext(redirect_stdout(io.StringIO()))
        self.enterContext(patch.object(sys, "stderr", io.StringIO()))

    def serve(self, url, destination):
        destination.write_bytes(self.files[url.rsplit("/", 1)[1]])

    def test_authenticated_sources_are_reused_without_downloading(self):
        paths = self.guest.fetch_sources(self.directory)

        self.download.assert_not_called()
        self.assertEqual({"ffmpeg": self.directory / "ffmpeg-1.tar.xz",
                          "zlib": self.directory / "zlib-1.tar.gz"}, paths)
        self.verify.assert_called_once()
        self.assertEqual((paths["ffmpeg"], self.directory / "ffmpeg-1.tar.xz.asc"),
                         self.verify.call_args.args[:2])

    def test_only_a_source_failing_its_pin_is_downloaded_again(self):
        (self.directory / "zlib-1.tar.gz").write_bytes(b"truncated")
        (self.directory / "ffmpeg-0.tar.xz").write_bytes(b"previous pin")

        self.guest.fetch_sources(self.directory)

        self.assertEqual(["https://example.invalid/zlib-1.tar.gz"],
                         [call.args[0] for call in self.download.call_args_list])
        self.assertEqual(self.files["zlib-1.tar.gz"], (self.directory / "zlib-1.tar.gz").read_bytes())
        self.assertEqual(sorted(self.files), sorted(p.name for p in self.directory.iterdir()))

    def test_a_downloaded_source_must_still_match_its_pin(self):
        (self.directory / "zlib-1.tar.gz").unlink()
        self.files["zlib-1.tar.gz"] = b"tampered upstream"

        with self.assertRaises(ValueError):
            self.guest.fetch_sources(self.directory)

    def test_a_cached_archive_with_a_changed_notice_is_rejected_without_downloading(self):
        self.guest.SOURCES["zlib"]["license_files"]["LICENSE"] = sha256(b"bundled")

        with self.assertRaises(ValueError):
            self.guest.fetch_sources(self.directory)
        self.download.assert_not_called()

    def test_a_cached_signature_that_does_not_verify_is_fetched_and_verified_again(self):
        self.verify.side_effect = [ValueError("bad signature"), None]

        self.guest.fetch_sources(self.directory)

        self.assertEqual(["https://example.invalid/ffmpeg-1.tar.xz.asc"],
                         [call.args[0] for call in self.download.call_args_list])
        self.assertEqual(2, self.verify.call_count)


class BrokerChildrenTest(unittest.TestCase):
    def setUp(self):
        spec = importlib.util.spec_from_file_location(
            "broker_children", Path(__file__).parent / "broker_children.py")
        self.children = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.children)

    def test_a_process_whose_creator_once_had_the_brokers_pid_is_not_its_decoder(self):
        # Toolhelp keeps an exited creator's PID: csrss.exe still names the smss.exe instance that
        # started it, and here the broker reused that PID.
        processes = [(716, 588, "csrss.exe"), (4012, 588, "conhost.exe"), (4100, 3000, "python.exe"),
                     (4200, 588, "seqimages-decoder.exe")]

        self.assertEqual([4200], self.children.decoder_pids(processes, 588, "seqimages-decoder.exe"))

    def test_the_decoder_image_name_is_compared_without_case(self):
        processes = [(4200, 588, "SeqImages-Decoder.EXE"), (4300, 590, "seqimages-decoder.exe")]

        self.assertEqual([4200], self.children.decoder_pids(processes, 588, "seqimages-decoder.exe"))


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

            def jar(changed=None, missing=None, helper=None):
                with zipfile.ZipFile(libs / "sequoia-1.12.0.jar", "w") as archive:
                    for name in self.package.JAR_FILES:
                        if name != missing:
                            archive.writestr("sandbox/" + name, b"other" if name == changed else name.encode())
                    if helper:
                        archive.writestr("sandbox/" + helper, b"unverified override")

            jar()
            (libs / "sequoia-1.12.0-sources.jar").write_bytes(b"sources")
            with redirect_stdout(io.StringIO()):
                self.package.verify_jar(libs, out)
                overrides = [{"helper": f"{platform}/{name}"}
                             for platform in self.package.PLATFORMS
                             for name in self.package.SHIPPED[platform.split("-")[0]]]
                for problem in [{"changed": "helpers-aarch64.xz"}, {"missing": "provenance.json"}, *overrides]:
                    jar(**problem)
                    with self.assertRaises(ValueError):
                        self.package.verify_jar(libs, out)

    def test_source_archive_preserves_native_verification_media(self):
        root = Path(__file__).parents[2]
        fixtures = [root / "src/test/resources/klipy-report.mp4",
                    *sorted((root / "src/test/resources").glob("webp-*.webp"))]
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            sources = {"ffmpeg": {"url": "https://example.invalid/ffmpeg.tar.xz",
                                   "signature_url": "https://example.invalid/ffmpeg.tar.xz.asc"},
                       "zlib": {"url": "https://example.invalid/zlib.tar.gz"}}
            for name in ("ffmpeg.tar.xz", "ffmpeg.tar.xz.asc", "zlib.tar.gz"):
                (directory / name).write_bytes(b"archive fixture")
            with patch.object(self.package.guest, "SOURCES", sources), \
                    patch.object(self.package.guest, "ARCHIVES", directory):
                self.package.source_archive(directory / "source.zip")
            with zipfile.ZipFile(directory / "source.zip") as archive:
                for fixture in fixtures:
                    member = "sequoia-images-native-source/" + fixture.relative_to(root).as_posix()
                    self.assertEqual(archive.read(member), fixture.read_bytes())


class ToolchainPinTest(unittest.TestCase):
    def test_mise_installs_the_toolchain_cargo_reads(self):
        root = Path(__file__).parents[2]
        mise = tomllib.loads((root / "mise.toml").read_text())["tools"]["rust"]
        cargo = tomllib.loads((root / "native/decoder/rust-toolchain.toml").read_text())["toolchain"]
        self.assertEqual((cargo["channel"], cargo["profile"]), (mise["version"], mise["profile"]))


if __name__ == "__main__":
    unittest.main()
