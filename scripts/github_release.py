"""Publish a GitHub release from exactly the files that were built once and accepted.

usage: github_release.py stage TAG FILE...
       github_release.py verify TAG FILE...
       github_release.py fetch TAG JAR_SHA256 DIRECTORY
       github_release.py publish TAG JAR_SHA256 DIRECTORY

stage creates the release as a draft, or replaces the files of an existing draft; it never changes a
published release. verify checks that the release holds exactly these files, byte for byte, and that
Sequoia's updater up to 1.11.1, which installs the first sequoia-*.jar asset that is not -sources.jar,
can only pick the mod JAR. Neither makes the release visible: drafts are not "latest".

fetch downloads the release into DIRECTORY and checks it is the candidate that was accepted: the JAR's
SHA-256, every asset's GitHub digest, the JAR's version and the commit its provenance names. publish
checks the fetched files again, publishes the draft as the latest release and reads it back; a release
that is already published is only re-checked.

Calls GitHub through the gh CLI; GITHUB_REPOSITORY names the repository and GH_TOKEN authorizes.
"""
import hashlib, json, os, pathlib, subprocess, sys, time, zipfile

READBACK_SECONDS = 30


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as stream:
        for block in iter(lambda: stream.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def old_updater_choices(names):
    """The assets the updater up to 1.11.1 would accept as the mod JAR; it takes the first."""
    return [n for n in names if n.startswith("sequoia-") and n.endswith(".jar") and not n.endswith("-sources.jar")]


def check_assets(tag, assets, local):
    """Raises ValueError unless the release's assets are exactly the local files, by name, size and digest.

    assets: the release's asset objects from the GitHub API; local: file name -> (size, sha256).
    """
    published = {asset["name"]: asset for asset in assets}
    if set(published) != set(local):
        missing, extra = sorted(set(local) - set(published)), sorted(set(published) - set(local))
        raise ValueError(f"{tag}: assets differ from the build; missing {missing}, unexpected {extra}")
    for name, (size, digest) in local.items():
        asset = published[name]
        if asset["size"] != size or asset.get("digest") != "sha256:" + digest:
            raise ValueError(f"{tag}: {name} on GitHub is not the file that was built")
    jar = f"sequoia-{tag[1:]}.jar"
    if old_updater_choices(sorted(published)) != [jar]:
        raise ValueError(f"{tag}: older updaters must find exactly one mod JAR, {jar}")
    if jar + ".sha256" not in published:
        raise ValueError(f"{tag}: {jar}.sha256 is missing; older updaters require it")


def check_candidate(tag, jar, accepted_sha256, tag_commit):
    """Raises ValueError unless JAR is the accepted candidate for TAG, built from the tagged commit."""
    if sha256(jar) != accepted_sha256.lower():
        raise ValueError(f"{jar.name} is not the accepted candidate {accepted_sha256}")
    with zipfile.ZipFile(jar) as archive:
        mod = json.loads(archive.read("fabric.mod.json"))
        provenance = json.loads(archive.read("sandbox/provenance.json"))
    if mod.get("id") != "seq" or mod.get("version") != tag[1:]:
        raise ValueError(f"{jar.name} is not Sequoia {tag[1:]}")
    if provenance.get("source_commit") != tag_commit:
        raise ValueError(f"{jar.name} was built from {provenance.get('source_commit')}, not {tag} ({tag_commit})")


def gh(*args, binary=False):
    result = subprocess.run(["gh", *args], check=True, capture_output=True, text=not binary)
    return result.stdout


def repository():
    return os.environ["GITHUB_REPOSITORY"]


def find_release(tag):
    """The release for TAG, drafts included, or None."""
    pages = json.loads(gh("api", "--paginate", "--slurp", f"repos/{repository()}/releases?per_page=100"))
    matches = [release for page in pages for release in page if release["tag_name"] == tag]
    if len(matches) > 1:
        raise ValueError(f"{tag}: {len(matches)} releases share this tag")
    return matches[0] if matches else None


def local_files(paths):
    return {path.name: (path.stat().st_size, sha256(path)) for path in paths}


def upload(release, path):
    gh("api", "--method", "POST", "-H", "Content-Type: application/octet-stream",
       f"https://uploads.github.com/repos/{repository()}/releases/{release['id']}/assets?name={path.name}",
       "--input", str(path))


def stage(tag, paths):
    release = find_release(tag)
    if release is not None and not release["draft"]:
        print(f"{tag} is already published; leaving it unchanged")
        return
    if release is None:
        release = json.loads(gh("api", "--method", "POST", f"repos/{repository()}/releases",
                                "-f", f"tag_name={tag}", "-f", f"name=Sequoia {tag[1:]}", "-F", "draft=true",
                                "-f", f"body={os.environ.get('RELEASE_NOTES', '')}"))
    existing = {asset["name"]: asset for asset in release.get("assets", [])}
    for path in paths:
        if path.name in existing:
            gh("api", "--method", "DELETE", f"repos/{repository()}/releases/assets/{existing[path.name]['id']}")
        upload(release, path)
    print(f"{tag}: draft holds {len(paths)} files")


def verify(tag, paths):
    release = find_release(tag)
    if release is None:
        raise ValueError(f"{tag}: no release")
    check_assets(tag, release["assets"], local_files(paths))
    print(f"{tag} ({'draft' if release['draft'] else 'published'}) holds exactly the built files:")
    for name, (size, digest) in sorted(local_files(paths).items()):
        print(f"  {digest}  {size}  {name}")


def checked(tag, accepted_sha256, directory):
    """The release, after checking DIRECTORY holds its exact files and they are the accepted candidate."""
    release = find_release(tag)
    if release is None:
        raise ValueError(f"{tag}: no release")
    paths = sorted(directory.iterdir())
    check_assets(tag, release["assets"], local_files(paths))
    tag_commit = subprocess.run(["git", "rev-parse", f"refs/tags/{tag}^{{commit}}"], check=True,
                                capture_output=True, text=True).stdout.strip()
    check_candidate(tag, directory / f"sequoia-{tag[1:]}.jar", accepted_sha256, tag_commit)
    return release


def fetch(tag, accepted_sha256, directory):
    release = find_release(tag)
    if release is None:
        raise ValueError(f"{tag}: no release to fetch")
    directory.mkdir(parents=True, exist_ok=True)
    for asset in release["assets"]:
        (directory / asset["name"]).write_bytes(gh(
            "api", "-H", "Accept: application/octet-stream",
            f"repos/{repository()}/releases/assets/{asset['id']}", binary=True))
    checked(tag, accepted_sha256, directory)
    print(f"{tag}: fetched the accepted candidate into {directory}")


def publish(tag, accepted_sha256, directory):
    release = checked(tag, accepted_sha256, directory)
    if release["draft"]:
        gh("api", "--method", "PATCH", f"repos/{repository()}/releases/{release['id']}",
           "-F", "draft=false", "-f", "make_latest=true")
    deadline = time.monotonic() + READBACK_SECONDS
    while True:
        latest = json.loads(gh("api", f"repos/{repository()}/releases/latest"))
        if latest["tag_name"] == tag:
            check_assets(tag, latest["assets"], local_files(sorted(directory.iterdir())))
            print(f"{tag} is the latest release, holding the accepted files")
            return
        if time.monotonic() > deadline:
            raise ValueError(f"{tag} was published but the latest release is still {latest['tag_name']}")
        time.sleep(3)


def main(argv):
    if len(argv) >= 3 and argv[0] in ("stage", "verify"):
        command, tag, paths = argv[0], argv[1], [pathlib.Path(p) for p in argv[2:]]
        try:
            (stage if command == "stage" else verify)(tag, paths)
            return 0
        except (ValueError, subprocess.CalledProcessError) as error:
            print(f"Release {command} failed: {error}", file=sys.stderr)
            return 1
    if len(argv) == 4 and argv[0] in ("fetch", "publish"):
        try:
            (fetch if argv[0] == "fetch" else publish)(argv[1], argv[2], pathlib.Path(argv[3]))
            return 0
        except (ValueError, KeyError, OSError, zipfile.BadZipFile, subprocess.CalledProcessError) as error:
            print(f"Release {argv[0]} failed: {error}", file=sys.stderr)
            return 1
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
