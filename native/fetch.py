"""Download the decoder helper packs a Build run made into native/out/sandbox.

usage: fetch.py [RUN_ID]

Without RUN_ID it takes the newest successful Build run on main, whose packs match main's native
inputs. A branch that changes them needs the packs its own run built: pass that run's ID, the number
after /actions/runs/ in its URL. Packs already there are replaced, and only once the new ones have
downloaded, so a failed download leaves the old ones in place.
"""
import json, pathlib, shutil, subprocess, sys, tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
SANDBOX = ROOT / "native/out/sandbox"


def latest_main_run(run=subprocess.run):
    """The ID of the newest successful Build run on main."""
    listed = run(["gh", "run", "list", "--workflow", "Build", "--branch", "main", "--status", "success",
                  "--limit", "1", "--json", "databaseId"], capture_output=True, text=True, check=True)
    runs = json.loads(listed.stdout)
    if not runs:
        raise SystemExit("There is no successful Build run on main to take packs from; pass a run ID.")
    return str(runs[0]["databaseId"])


def fetch(run_id, sandbox=SANDBOX, run=subprocess.run):
    """Replaces SANDBOX with the packs artifact of RUN_ID, once it has downloaded in full."""
    sandbox.parent.mkdir(parents=True, exist_ok=True)
    staging = pathlib.Path(tempfile.mkdtemp(dir=sandbox.parent, prefix=".packs-"))
    try:
        run(["gh", "run", "download", run_id, "-n", "packs", "-D", str(staging)], check=True)
        if sandbox.exists():
            shutil.rmtree(sandbox)
        staging.rename(sandbox)
    finally:
        shutil.rmtree(staging, ignore_errors=True)
    names = sorted(path.name for path in sandbox.iterdir())
    print(f"native/out/sandbox now holds the packs of Build run {run_id}: {', '.join(names)}")


def main(argv):
    if len(argv) > 1:
        raise SystemExit(__doc__)
    fetch(argv[0] if argv else latest_main_run())


if __name__ == "__main__":
    main(sys.argv[1:])
