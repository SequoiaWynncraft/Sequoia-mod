"""Publish an accepted release's JAR and corresponding source to Modrinth, then read them back.

usage: modrinth_release.py check
       modrinth_release.py publish TAG DIRECTORY

check fails unless MODRINTH_TOKEN is set and the project resolves, so a promotion can stop before
it publishes anything. publish validates the release with release_metadata.py, creates the Modrinth
version from DIRECTORY's mod JAR and sequoia-images-native-source.zip unless a version with that
number already exists, as after a rerun, and then checks that Modrinth holds exactly those files.
"""
import hashlib, json, os, pathlib, secrets, subprocess, sys, urllib.error, urllib.request, zipfile

import release_metadata

API = "https://api.modrinth.com/v2"
PROJECT = "sequoia"
USER_AGENT = "SequoiaWynncraft/sequoia-mod-release-workflow"
SOURCE_ARCHIVE = "sequoia-images-native-source.zip"


def call(method, path, token=None, body=None, content_type=None):
    """One Modrinth API request; returns the decoded JSON response."""
    headers = {"User-Agent": USER_AGENT}
    if token:
        headers["Authorization"] = token
    if content_type:
        headers["Content-Type"] = content_type
    request = urllib.request.Request(API + path, data=body, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=300) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", "replace")
        raise ValueError(f"Modrinth {method} {path}: HTTP {error.code} {detail}") from None


def multipart(fields, files):
    """A multipart/form-data body: text fields, then files as name -> path."""
    boundary = "sequoia-" + secrets.token_hex(16)
    body = bytearray()
    for name, value in fields.items():
        body += f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n'.encode()
        body += value.encode() + b"\r\n"
    for name, path in files.items():
        body += (f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"; filename="{path.name}"\r\n'
                 "Content-Type: application/octet-stream\r\n\r\n").encode()
        body += path.read_bytes() + b"\r\n"
    body += f"--{boundary}--\r\n".encode()
    return bytes(body), f"multipart/form-data; boundary={boundary}"


def token():
    value = os.environ.get("MODRINTH_TOKEN", "")
    if not value:
        raise ValueError("MODRINTH_TOKEN is required to publish tagged releases to Modrinth.")
    return value


def project_id():
    identifier = call("GET", f"/project/{PROJECT}").get("id")
    if not identifier:
        raise ValueError(f"Failed to resolve the Modrinth project ID for {PROJECT}.")
    return identifier


def check():
    token()
    project_id()
    print(f"Modrinth project {PROJECT} resolves and MODRINTH_TOKEN is set.")


def sha512(path):
    digest = hashlib.sha512()
    with open(path, "rb") as stream:
        for block in iter(lambda: stream.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def publish(tag, directory):
    secret = token()
    metadata = release_metadata.release_metadata(pathlib.Path.cwd(), tag, directory)
    files = {"primary": pathlib.Path(metadata["mod_jar"]), "source": directory / SOURCE_ARCHIVE}
    project = project_id()
    version = metadata["version_number"]
    # A rerun after a later failure finds the version already there and only checks it.
    existing = [v["id"] for v in call("GET", f"/project/{project}/version") if v["version_number"] == version]
    if existing:
        version_id = existing[0]
    else:
        data = {
            "name": f"Sequoia {version}",
            "version_number": version,
            "changelog": metadata["changelog"],
            "project_id": project,
            "game_versions": [metadata["minecraft_version"]],
            "version_type": metadata["version_type"],
            "dependencies": [],
            "loaders": ["fabric"],
            "featured": True,
            "status": "listed",
            "file_parts": ["primary", "source"],
            "primary_file": "primary",
        }
        body, content_type = multipart({"data": json.dumps(data)}, files)
        version_id = call("POST", "/version", secret, body, content_type)["id"]
    # Read the version back: Modrinth must hold exactly the accepted files.
    held = {file["hashes"]["sha512"] for file in call("GET", f"/version/{version_id}")["files"]}
    for path in files.values():
        if sha512(path) not in held:
            raise ValueError(f"Modrinth version {version_id} does not hold {path.name} as accepted.")
    print(f"Modrinth version {version_id} holds the accepted files.")


def main(argv):
    try:
        if argv == ["check"]:
            check()
        elif len(argv) == 3 and argv[0] == "publish":
            publish(argv[1], pathlib.Path(argv[2]))
        else:
            print(__doc__, file=sys.stderr)
            return 2
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        print(f"Modrinth release failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
