# Development

[mise](https://mise.jdx.dev) installs the tools this repository uses and runs its tasks. Gradle
builds the mod, Cargo builds the image decoder helper, and Python scripts check native inputs and
publish releases. Each `mise run` task calls one of them, so the commands below are the same on a
developer machine and in GitHub Actions.

## Set up

1. [Install mise](https://mise.jdx.dev/getting-started.html) 2026.10.23 or newer. CI uses 2026.10.23.
   The repository rejects older versions to prevent them from rewriting the reviewed lockfile metadata.
2. In the repository, run `mise trust` once, because `mise.toml` defines tasks.
3. Install the versions `mise.lock` records for this repository's tools, and only those:

   ```sh
   MISE_ENABLE_TOOLS=java,python,rust,gh,actionlint,shellcheck,zizmor mise install --locked
   ```

   In PowerShell, set `$env:MISE_ENABLE_TOOLS` to the same list first. A plain `mise install` also
   installs the tools of your global mise configuration. `mise install java` installs both JDKs
   below, ignoring which systems each is for, so name the tools in `MISE_ENABLE_TOOLS` instead.

   | Tool | Version | Used for |
   | --- | --- | --- |
   | Java | Zulu 21.54.201 (JDK 21.0.13.1); Corretto 21.0.13.1.2 on macOS | Gradle and the mod |
   | Python | 3.15.2 | Native build and release scripts |
   | Rust | 1.98.1, minimal profile with rustfmt | The image decoder helper |
   | gh | 2.103.3 | Release tasks |
   | actionlint, zizmor, ShellCheck | 1.7.12, 1.31.1, 0.11.0 | `mise run lint` |

   mise installs Rust through rustup. Building the decoder guest also needs `gpgv` and `make` from
   the system on Linux or macOS.

## Build and test the mod

The mod JAR ships the image decoder helpers as packs in `native/out/sandbox`, and Gradle refuses
to build without them. Take the packs CI built:

```sh
mise run native:fetch          # from main's newest successful Build run
mise run native:fetch RUN_ID   # from another run, such as a branch that changes native/
```

The run ID is the number after `/actions/runs/` in the run's URL. The new packs replace any already
there once they have downloaded. With them, the whole test suite runs, the decoder included. To build
helpers yourself, follow [image decoder builds](../native/README.md).

`-Pallow_partial_native` builds without packs, or with only some platforms. The decoder tests then
skip and say to fetch the packs, instead of failing.

`mise run build` compiles the mod, runs the unit tests and writes `build/libs/sequoia-VERSION.jar`.
The build fails if the JAR exceeds `max_release_jar_bytes` in `gradle.properties`. Arguments after
the task name go to Gradle unchanged:

```sh
mise run build -Pbackend_environment=staging
```

`backend_environment` selects the production or staging backend.
[Raid profiles](raid-profiles-protocol.md) describes `raid_profiles_environment`.

`mise run dev` starts Minecraft with the mod. Outside CI, DevAuth signs in with the Microsoft
account that `.devauth/config.toml` selects.

## Tasks

`mise tasks ls` lists every task. Arguments after a task name go to the tool it runs.

| Task | What it does |
| --- | --- |
| `build` | Compiles, runs the unit tests and builds the JAR within its size budget |
| `test` | Runs the unit tests |
| `test:platform` | Runs the updater and image preview tests, which depend on the operating system |
| `test:scripts` | Runs the Python tests for the native build inputs, the release scripts and the gathering-node script |
| `dev` | Starts Minecraft with the mod |
| `fmt` | Formats the decoder's Rust code. `mise run fmt --check` only reports changes |
| `lint` | Lints the workflows with actionlint and zizmor, and `native/build-wasm.sh` with ShellCheck |
| `check` | Runs `build`, `test:scripts` and `lint` |
| `native:fetch [RUN_ID]` | Replaces `native/out/sandbox` with the helper packs of main's newest successful Build run, or of `RUN_ID` |
| `native:guest` | Builds the decoder guest from authenticated sources. `--sources-only` only checks the sources, downloading any that are missing or invalid |
| `native:wasm OUT FFMPEG ZLIB` | Compiles a guest from local FFmpeg and zlib archives with the wasi-sdk in `WASI_SDK_PATH` |
| `native:build PLATFORM` | Builds, tests and verifies the decoder helper for this machine's platform |
| `native:pack` | Packs the verified helpers and writes their provenance and the corresponding source |
| `native:verify-jar` | Checks that the JAR in `build/libs` carries exactly the packs in `native/out/sandbox` |
| `native:notices` | Checks the bundled third-party notices. `mise run native:notices write` regenerates them |
| `map:nodes` | Regenerates the bundled gathering nodes from the Wynncraft API, keeping the Dernic and Molten kinds the API leaves unnamed |
| `release:check` | Validates the release tag, its notes and `mod_version` |
| `release:stage TAG` | Stages the built files as the draft GitHub release for `TAG` |
| `release:promote TAG SHA256` | Publishes an accepted draft to GitHub and Modrinth |

The release tasks call GitHub through `gh` for the repository in `GITHUB_REPOSITORY`, which Actions
sets. `release:promote` also needs `MODRINTH_TOKEN`.

## Continuous integration

The Build workflow runs on pull requests, on pushes to `main`, from Release, weekly and by hand:

| Job | Runs |
| --- | --- |
| `guest` | `test:scripts`, `native:guest --sources-only`, then `native:guest` unless a guest built from the same inputs is cached |
| `cached helpers` | On Linux, restores each platform's helper from the cache a run on its own platform saved after building and verifying it, and passes it on; also decides whether the change needs macOS tested |
| One job per platform | `native:build` on that platform's runner, for each helper `cached helpers` did not find |
| `pack` | `native:guest --sources-only` on the guest job's sources and `native:pack --require-all`, once for every later job |
| `build` | `native:notices`, `build` and `native:verify-jar` |
| `java PLATFORM` | `test:platform` on Windows x64; on macOS Arm too when the change touches the updater, the image previews, `native/`, the build or its JDK; on Windows and Linux Arm too for tags; on every platform but Linux x64 weekly and by hand |

The CodeQL workflow scans every language on pull requests, on pushes to `main` and weekly. Its Java
extraction runs Gradle on the JDK in `mise.toml`, so the repository uses this workflow instead of
GitHub's default setup, which cannot choose a JDK.

The guest job caches the upstream archives with the guest and hands them to `pack`, which checks
their pins, notices and FFmpeg signature again before building the source archive from them.
Each job installs only the tools it uses. Pull requests restore the tool and Gradle caches but
never save them. Guest and helper caches a pull request saves stay within that pull request. Tag
builds compile the guest again but take each helper from the cache `main` saved after building and
verifying it on its own platform, and the Release and Promote release jobs install their tools
without a cache.

GitHub's macOS runners can queue for minutes or not be placed at all, so nothing waits on them
unless the change is about macOS: a helper whose inputs changed, or the code the macOS lane tests.
A release never runs on macOS. The weekly run tests every platform, so a macOS break the path rules
miss still shows up within a week; run Build by hand to test one sooner.

## Release a version

1. Set `mod_version` in `gradle.properties` and merge the change.
2. Write the release notes and create an annotated tag on that commit. `--cleanup=verbatim`
   keeps Markdown headings, which a plain tag message would drop:

   ```sh
   git tag --cleanup=verbatim -a v1.12.0 -F notes.md
   git push origin v1.12.0
   ```

   A tag message of `[no-release-notes]` publishes the release without notes.
3. The Release workflow runs `release:check`, builds and tests the tagged commit, and runs
   `release:stage`. The draft holds the JAR, the sources JAR, `sequoia-images-native-source.zip`
   and a `.sha256` file for each. The run summary shows the JAR's SHA-256.
4. Test the draft's own JAR. Download it, check it against the SHA-256 in the run summary, and
   start Minecraft with it and Fabric API alone, as players run it:

   ```sh
   gh release download v1.12.0 -p 'sequoia-*.jar' -D /tmp/draft
   sha256sum /tmp/draft/sequoia-1.12.0.jar
   mise run release:try /tmp/draft/sequoia-1.12.0.jar
   ```

   The game runs in `run-production/`. Check that the mod loads without mixin errors.
5. Run the Promote release workflow with the tag and that SHA-256. `release:promote` checks the
   Modrinth token, then checks that the draft holds the accepted JAR built from the tag. It
   publishes the draft as the latest GitHub release, uploads the same JAR and source zip to
   Modrinth and reads both releases back. A rerun after a failure only checks what is already
   published.

## Update tools and actions

Change a version in `mise.toml`, then run `mise lock`. It refreshes the six platforms that
`mise.lock` records: Linux, macOS and Windows, each on x64 and Arm. Keep the Rust version equal to
`channel` in `native/decoder/rust-toolchain.toml`. `mise run test:scripts` fails while they differ.
The workflows install mise 2026.10.23 through `jdx/mise-action`.

Workflows pin each action to a full commit SHA, with its release in a comment. Run `mise run lint`
after changing a workflow.
