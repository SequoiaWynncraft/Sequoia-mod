"""Check the verified helpers against the guest and write the packs, provenance and corresponding source.

usage: package.py [--require-all] [--packs-only]
       package.py --unpack PACK PROVENANCE DEST
       package.py --verify-jar DIRECTORY

Every helper in native/out/sandbox/PLATFORM/ must match the files its native/out/PLATFORM/
validation.json recorded and have been verified with native/out/guest/decoder.wasm. The helpers for
each CPU go into one XZ stream, native/out/sandbox/helpers-ARCH.xz: the same FFmpeg guest compiled
for three systems is mostly the same bytes, which a solid stream stores once and per-file JAR
compression cannot. native/out/sandbox/provenance.json, which the JAR ships with the packs, lists
every helper's offset, length and SHA-256 in its pack. Also writes the reproducible
native/out/dist/sequoia-images-native-source.zip: the authenticated FFmpeg and zlib archives with
FFmpeg's signature, the build recipe and helper sources, and the notices. It is a zip because
Modrinth accepts zip files next to the JAR. --require-all fails unless all six platforms are present.
--packs-only skips the source archive, for test builds that have no upstream archives.
--unpack writes the helpers of a pack back to separate files, checked against the provenance.
--verify-jar checks that the mod JAR in DIRECTORY carries exactly the packs and provenance in
native/out/sandbox.
"""
import argparse, hashlib, json, lzma, os, pathlib, re, subprocess, sys, zipfile

import guest

ROOT = guest.ROOT
OUT = ROOT / "native/out"
PLATFORMS = ["windows-x86_64", "windows-aarch64", "linux-x86_64", "linux-aarch64", "macos-x86_64", "macos-aarch64"]
SHIPPED = {"windows": ["sandbox.exe", "seqimages-decoder.exe"], "linux": ["seqimages-decoder"],
           "macos": ["seqimages-decoder"]}
ARCHES = ["x86_64", "aarch64"]
# The dictionary must reach back across one whole helper to find its twin for another system.
PACK_DICT_BYTES = 16 << 20
PACK_FILTERS = [{"id": lzma.FILTER_LZMA2, "preset": 9 | lzma.PRESET_EXTREME, "dict_size": PACK_DICT_BYTES}]
# BundledHelpers refuses anything larger; keep the build within the limits the mod enforces.
MAX_MEMBER_BYTES, MAX_PACK_BYTES = 32 << 20, 64 << 20
MEMBER_PATH = re.compile(r"[a-z]+-[a-z0-9_]+/[A-Za-z0-9._-]+")
# Repository files a recipient needs to rebuild the guest and the helpers. build.yml runs the
# mise tasks and tool versions that mise.toml and mise.lock define.
SOURCE_FILES = ["LICENSE.txt", ".github/workflows/build.yml", "mise.toml", "mise.lock", "native",
                "src/main/resources/licenses"]
# The files the mod JAR ships under sandbox/.
JAR_FILES = [f"helpers-{arch}.xz" for arch in ARCHES] + ["provenance.json"]
SOURCE_EXCLUDES = ("native/out", "native/decoder/target")


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def platforms(out, guest_sha256, require_all):
    """Hashes of each present platform's shipped files; raises ValueError on any mismatch."""
    found = {}
    for name in PLATFORMS:
        if not (out / "sandbox" / name).is_dir():
            continue
        files = {f: sha256((out / "sandbox" / name / f).read_bytes()) for f in SHIPPED[name.split("-")[0]]}
        validation = json.loads((out / name / "validation.json").read_text())
        if validation != {"platform": name, "guest_sha256": guest_sha256, "executables": files}:
            raise ValueError(f"{name}: the helper differs from the one verified with this guest")
        found[name] = files
    missing = [name for name in PLATFORMS if name not in found]
    if not found or (missing and require_all):
        raise ValueError("missing platforms: " + ", ".join(missing))
    return found


def pack_members(arch, found):
    """The present helpers for one CPU, Windows first: most players then decode the least."""
    return [f"{system}-{arch}/{name}" for system in ("windows", "linux", "macos")
            if f"{system}-{arch}" in found for name in SHIPPED[system]]


def check_pack(packed, entry):
    """Decompresses a pack and checks every member against its index entry; returns the members."""
    data = lzma.decompress(packed, format=lzma.FORMAT_XZ)
    if len(data) != entry["uncompressed_bytes"] or sum(m["bytes"] for m in entry["members"]) != len(data):
        raise ValueError(f"{entry['file']}: members do not cover the pack exactly")
    members = {}
    for member in entry["members"]:
        if not MEMBER_PATH.fullmatch(member["path"]):
            raise ValueError(f"{entry['file']}: unexpected member path {member['path']!r}")
        content = data[member["offset"]:member["offset"] + member["bytes"]]
        if len(content) != member["bytes"] or sha256(content) != member["sha256"]:
            raise ValueError(f"{entry['file']}: {member['path']} does not match its index entry")
        members[member["path"]] = content
    return members


def write_packs(out, found):
    """Writes one XZ pack per CPU and returns the index the mod reads from provenance.json."""
    packs = {}
    for arch in ARCHES:
        index, data = [], bytearray()
        for path in pack_members(arch, found):
            content = (out / "sandbox" / path).read_bytes()
            if len(content) > MAX_MEMBER_BYTES:
                raise ValueError(f"{path} is larger than the mod accepts")
            index.append({"path": path, "offset": len(data), "bytes": len(content), "sha256": sha256(content)})
            data += content
        if not index:
            continue
        if len(data) > MAX_PACK_BYTES:
            raise ValueError(f"the {arch} helpers are larger together than the mod accepts")
        packed = lzma.compress(bytes(data), format=lzma.FORMAT_XZ, check=lzma.CHECK_CRC64, filters=PACK_FILTERS)
        entry = {"file": f"helpers-{arch}.xz", "bytes": len(packed), "sha256": sha256(packed),
                 "uncompressed_bytes": len(data), "dict_bytes": PACK_DICT_BYTES, "members": index}
        for path, content in check_pack(packed, entry).items():
            platform, name = path.split("/")
            if sha256(content) != found[platform][name]:
                raise ValueError(f"{path}: the pack does not reproduce the verified helper")
        (out / "sandbox" / entry["file"]).write_bytes(packed)
        packs[arch] = entry
    return packs


def unpack(pack, provenance, dest):
    """Writes each helper in a pack to DEST/PLATFORM/NAME after checking it against the provenance."""
    entries = [e for e in json.loads(provenance.read_text())["packs"].values() if e["file"] == pack.name]
    if len(entries) != 1:
        raise ValueError(f"{provenance} does not describe {pack.name}")
    packed = pack.read_bytes()
    if sha256(packed) != entries[0]["sha256"]:
        raise ValueError(f"{pack.name} differs from the pack the provenance describes")
    for path, content in check_pack(packed, entries[0]).items():
        target = dest / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(content)
        print(f"{path} {len(content)} {sha256(content)}")


def verify_jar(directory, out=OUT):
    """Raises ValueError unless the mod JAR in DIRECTORY ships exactly out/sandbox's packs."""
    jars = sorted(p for p in directory.glob("*.jar") if not p.name.endswith(("-sources.jar", "-dev.jar")))
    if len(jars) != 1:
        raise ValueError(f"expected one mod JAR in {directory}, found {len(jars)}")
    with zipfile.ZipFile(jars[0]) as jar:
        shipped = set(jar.namelist())
        for name in JAR_FILES:
            local = out / "sandbox" / name
            if f"sandbox/{name}" not in shipped or not local.is_file():
                raise ValueError(f"{jars[0].name}: sandbox/{name} is missing from the JAR or {local}")
            packed = sha256(jar.read(f"sandbox/{name}"))
            print(packed, name)
            if packed != sha256(local.read_bytes()):
                raise ValueError(f"{jars[0].name}: sandbox/{name} differs from the tested {local}")


def source_archive(path):
    """A reproducible zip, so equal sources give equal bytes."""
    upstream = [guest.SOURCES[key][field] for key, field in
                (("ffmpeg", "url"), ("zlib", "url"), ("ffmpeg", "signature_url"))]
    members = [(f"upstream/{url.rsplit('/', 1)[1]}", guest.ARCHIVES / url.rsplit("/", 1)[1]) for url in upstream]
    for entry in SOURCE_FILES:
        base = ROOT / entry
        for file in [base] if base.is_file() else sorted(p for p in base.rglob("*") if p.is_file()):
            relative = file.relative_to(ROOT).as_posix()
            if not relative.startswith(SOURCE_EXCLUDES) and "__pycache__" not in relative:
                members.append((relative, file))
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w") as archive:
        for relative, file in sorted(members):
            info = zipfile.ZipInfo("sequoia-images-native-source/" + relative, (1980, 1, 1, 0, 0, 0))
            info.create_system, info.external_attr = 3, 0o100644 << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, file.read_bytes())
    return len(members)


def package(require_all, packs_only=False):
    record = json.loads((OUT / "guest/guest.json").read_text())
    if guest.sha256(OUT / "guest/decoder.wasm") != record["decoder_wasm"]["sha256"]:
        raise ValueError("guest.json does not describe native/out/guest/decoder.wasm")
    if not packs_only:
        for source in (guest.SOURCES["ffmpeg"], guest.SOURCES["zlib"]):
            guest.require_pin(guest.ARCHIVES / source["url"].rsplit("/", 1)[1], source["sha256"])
    found = platforms(OUT, record["decoder_wasm"]["sha256"], require_all)
    packs = write_packs(OUT, found)
    commit = os.environ.get("GITHUB_SHA") or subprocess.run(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, capture_output=True, text=True, check=True).stdout.strip()
    run = (f"{os.environ['GITHUB_SERVER_URL']}/{os.environ['GITHUB_REPOSITORY']}/actions/runs/"
           f"{os.environ['GITHUB_RUN_ID']}" if "GITHUB_RUN_ID" in os.environ else None)
    provenance = {"source_commit": commit, "ci_run": run, "guest": record, "platforms": found}
    summary = ", ".join(f"{e['file']} {e['bytes']} bytes" for e in packs.values())
    if not packs_only:
        archive = OUT / "dist/sequoia-images-native-source.zip"
        count = source_archive(archive)
        provenance["corresponding_source"] = {"file": archive.name, "sha256": guest.sha256(archive)}
        summary += f"; source archive {count} files"
    provenance["packs"] = packs
    (OUT / "sandbox/provenance.json").write_text(json.dumps(provenance, indent=2) + "\n")
    print(f"{len(found)} platform(s) from guest {record['decoder_wasm']['sha256'][:12]}; {summary}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--require-all", action="store_true")
    parser.add_argument("--packs-only", action="store_true")
    parser.add_argument("--unpack", nargs=3, type=pathlib.Path, metavar=("PACK", "PROVENANCE", "DEST"))
    parser.add_argument("--verify-jar", type=pathlib.Path, metavar="DIRECTORY")
    args = parser.parse_args()
    try:
        if args.unpack:
            unpack(*args.unpack)
        elif args.verify_jar:
            verify_jar(args.verify_jar)
        else:
            package(args.require_all, args.packs_only)
    except ValueError as error:
        sys.exit(str(error))


if __name__ == "__main__":
    main()
