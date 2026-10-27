"""Validate a tagged release before building, and its exact JAR before publishing."""
import argparse
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import zipfile


def git(repo, *args):
    return subprocess.run(
        ["git", *args], cwd=repo, check=True, capture_output=True, text=True
    ).stdout.strip()


def release_metadata(repo, tag, jar_directory=None):
    if not re.fullmatch(r"v\d+\.\d+\.\d+(?:[-+][A-Za-z0-9.-]+)?", tag):
        raise ValueError("Release tags must use vMAJOR.MINOR.PATCH, optionally with a suffix.")
    ref = "refs/tags/" + tag
    if git(repo, "cat-file", "-t", ref) != "tag":
        raise ValueError("An annotated tag with explicit release notes is required.")
    if git(repo, "rev-parse", ref + "^{commit}") != git(repo, "rev-parse", "HEAD"):
        raise ValueError("The release tag must point to the checked-out commit.")
    changelog = git(repo, "tag", "--list", tag, "--format=%(contents)")
    if not changelog or changelog == tag:
        raise ValueError("An annotated tag with explicit release notes is required.")
    properties = {}
    for line in (repo / "gradle.properties").read_text().splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            properties[key.strip()] = value.strip()
    version = tag[1:]
    if properties.get("mod_version") != version:
        raise ValueError(
            f"Release tag version {version} does not match gradle.properties mod_version "
            f"{properties.get('mod_version')}."
        )
    minecraft = properties.get("minecraft_version")
    if not minecraft:
        raise ValueError("minecraft_version is required in gradle.properties.")
    normalized = version.lower()
    metadata = {
        "minecraft_version": minecraft,
        "version_number": version,
        "version_type": "alpha" if "alpha" in normalized else "beta" if "beta" in normalized else "release",
        "generate_release_notes": "false",
        "changelog": "" if changelog == "[no-release-notes]" else changelog,
    }
    if jar_directory is not None:
        directory = repo / jar_directory
        jars = sorted(p for p in directory.glob("*.jar")
                      if not p.name.endswith(("-sources.jar", "-dev.jar")))
        if len(jars) != 1:
            raise ValueError(f"Expected exactly one release JAR, found {len(jars)}.")
        with zipfile.ZipFile(jars[0]) as archive:
            mod = json.loads(archive.read("fabric.mod.json"))
        if mod.get("id") != "seq" or mod.get("version") != version:
            raise ValueError("The packaged Sequoia version does not match the release tag.")
        if mod.get("depends", {}).get("minecraft") != minecraft:
            raise ValueError("The packaged Minecraft version does not match gradle.properties.")
        metadata["mod_jar"] = str(jars[0].relative_to(repo))
    return metadata


def write_outputs(metadata, output):
    with Path(output).open("a", encoding="utf-8") as stream:
        for key, value in metadata.items():
            delimiter = "RELEASE_" + secrets.token_hex(16)
            while delimiter in value.splitlines():
                delimiter = "RELEASE_" + secrets.token_hex(16)
            stream.write(f"{key}<<{delimiter}\n{value}\n{delimiter}\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", default=os.environ.get("GITHUB_REF_NAME", ""))
    parser.add_argument("--jar-directory", type=Path)
    args = parser.parse_args()
    try:
        metadata = release_metadata(Path.cwd(), args.tag, args.jar_directory)
        if os.environ.get("GITHUB_OUTPUT"):
            write_outputs(metadata, os.environ["GITHUB_OUTPUT"])
        print(f"Validated release {metadata['version_number']} for Minecraft {metadata['minecraft_version']}.")
    except (ValueError, OSError, KeyError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        print(f"Release validation failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
