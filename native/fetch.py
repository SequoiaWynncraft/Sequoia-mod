"""Download the decoder helper packs a Build run made into native/out/sandbox.

usage: fetch.py [RUN_ID]

Without RUN_ID it takes the newest successful push build of main, whose packs match main's native
inputs. A branch that changes them needs the packs its own run built: pass that run's ID, the number
after /actions/runs/ in its URL. Only the packs and their index are replaced, and only once the new
ones have downloaded; anything else in native/out/sandbox, such as helpers built locally, stays.
"""
import json, pathlib, re, shutil, subprocess, sys, tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
SANDBOX = ROOT / "native/out/sandbox"
# What a packs artifact holds, and all fetch ever replaces.
PACK_FILE = re.compile(r"helpers-[a-z0-9_]+\.xz|provenance\.json")


def latest_main_run(run=subprocess.run):
    """The ID of the newest successful Build run that a push to main started."""
    # Only push runs: a pull request from a fork's own branch named main would match the branch alone.
    listed = run(["gh", "run", "list", "--workflow", "Build", "--branch", "main", "--event", "push",
                  "--status", "success", "--limit", "1", "--json", "databaseId"],
                 stdout=subprocess.PIPE, text=True)
    if listed.returncode != 0:
        raise SystemExit("gh could not list Build runs; its message is above.")
    runs = json.loads(listed.stdout)
    if not runs:
        raise SystemExit("There is no successful Build run on main to take packs from; pass a run ID.")
    return str(runs[0]["databaseId"])


def fetch(run_id, sandbox=SANDBOX, run=subprocess.run):
    """Replaces the packs in SANDBOX with those RUN_ID built, once they have downloaded in full."""
    if not run_id.isdigit():
        raise SystemExit(f"{run_id} is not a run ID.\n\n{__doc__}")
    sandbox.mkdir(parents=True, exist_ok=True)
    staging = pathlib.Path(tempfile.mkdtemp(dir=sandbox.parent, prefix=".packs-new-"))
    try:
        if run(["gh", "run", "download", run_id, "-n", "packs", "-D", str(staging)]).returncode != 0:
            raise SystemExit(f"gh could not download the packs of run {run_id}; the packs here are unchanged.")
        downloaded = [path.name for path in staging.iterdir()]
        if "provenance.json" not in downloaded or not all(PACK_FILE.fullmatch(name) for name in downloaded):
            raise SystemExit(f"Run {run_id}'s packs artifact is not packs and their index; the packs here are unchanged.")
        install(staging, sandbox)
    finally:
        shutil.rmtree(staging, ignore_errors=True)
    print(f"native/out/sandbox now holds the packs of Build run {run_id}: {', '.join(sorted(downloaded))}")


def install(staging, sandbox):
    """Swaps SANDBOX's pack files for STAGING's. The old ones wait aside until the new ones are all in
    place, and go back if moving them in fails; if even that fails, the error says where they are."""
    backup = pathlib.Path(tempfile.mkdtemp(dir=sandbox.parent, prefix=".packs-old-"))
    installed = []
    try:
        for path in list(sandbox.iterdir()):
            if PACK_FILE.fullmatch(path.name):
                path.rename(backup / path.name)
        for path in list(staging.iterdir()):
            path.rename(sandbox / path.name)
            installed.append(sandbox / path.name)
    except OSError as failure:
        # Only what this call put there goes; an old pack that was never moved aside stays.
        try:
            for path in installed:
                path.unlink()
            for path in list(backup.iterdir()):
                path.rename(sandbox / path.name)
        except OSError:
            raise SystemExit(f"Installing the new packs failed ({failure}), and so did putting the old ones "
                             f"back; they are in {backup}") from failure
        backup.rmdir()
        raise
    shutil.rmtree(backup)


def main(argv):
    if argv[:1] in (["-h"], ["--help"]):
        print(__doc__)
        return
    if len(argv) > 1:
        raise SystemExit(__doc__)
    fetch(argv[0] if argv else latest_main_run())


if __name__ == "__main__":
    main(sys.argv[1:])
