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
   below, including the one for Windows on Arm, so name the tools in `MISE_ENABLE_TOOLS` instead.

   | Tool | Version | Used for |
   | --- | --- | --- |
   | Java | Temurin 21.0.13+9 (Zulu 21.0.13 on Windows on Arm) | Gradle and the mod |
   | Python | 3.15.2 | Native build and release scripts |
   | Rust | 1.98.1, minimal profile with rustfmt | The image decoder helper |
   | gh | 2.103.3 | Release tasks |
   | actionlint, zizmor, ShellCheck | 1.7.12, 1.31.1, 0.11.0 | `mise run lint` |

   mise installs Rust through rustup. Building the decoder guest also needs `gpgv` and `make` from
   the system on Linux or macOS.

## Build and test the mod

The mod JAR ships the image decoder helpers as packs in `native/out/sandbox`, and Gradle refuses
to build without them. To use the packs a CI run built, download its `packs` artifact. The run ID
is the number after `/actions/runs/` in the run's URL:

```sh
mise run native:fetch RUN_ID
```

The download fails if the run has no `packs` artifact or `native/out/sandbox` already holds packs.
Delete old packs first. To build helpers yourself, follow
[image decoder builds](../native/README.md).

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
| `test:scripts` | Runs the Python tests for the native build inputs and the release scripts |
| `dev` | Starts Minecraft with the mod |
| `fmt` | Formats the decoder's Rust code. `mise run fmt --check` only reports changes |
| `lint` | Lints the workflows with actionlint and zizmor, and `native/build-wasm.sh` with ShellCheck |
| `check` | Runs `build`, `test:scripts` and `lint` |
| `native:fetch RUN_ID` | Downloads the helper packs a Build run made into `native/out/sandbox` |
| `native:guest` | Builds the decoder guest from authenticated sources. `--sources-only` only checks the sources |
| `native:wasm OUT FFMPEG ZLIB` | Compiles a guest from local FFmpeg and zlib archives with the wasi-sdk in `WASI_SDK_PATH` |
| `native:build PLATFORM` | Builds, tests and verifies the decoder helper for this machine's platform |
| `native:pack` | Packs the verified helpers and writes their provenance and the corresponding source |
| `native:verify-jar` | Checks that the JAR in `build/libs` carries exactly the packs in `native/out/sandbox` |
| `native:notices` | Checks the bundled third-party notices. `mise run native:notices write` regenerates them |
| `release:check` | Validates the release tag, its notes and `mod_version` |
| `release:stage TAG` | Stages the built files as the draft GitHub release for `TAG` |
| `release:promote TAG SHA256` | Publishes an accepted draft to GitHub and Modrinth |

The release tasks call GitHub through `gh` for the repository in `GITHUB_REPOSITORY`, which Actions
sets. `release:promote` also needs `MODRINTH_TOKEN`.

## Continuous integration

The Build workflow runs on pull requests, on pushes to `main` and from Release:

| Job | Runs |
| --- | --- |
| `guest` | `test:scripts`, then `native:guest` unless a guest built from the same inputs is cached |
| One job per platform | `native:build` on a runner for each of the six platforms |
| `pack` | `native:guest --sources-only` and `native:pack --require-all`, once for every later job |
| `build` | `native:notices`, `build` and `native:verify-jar` |
| `java PLATFORM` | `test:platform` on Windows x64 and macOS Arm, plus the other platforms except Linux x64 for tags and manual runs |

Each job installs only the tools it uses. Pull requests restore the tool and Gradle caches but
never save them. Guest and helper caches a pull request saves stay within that pull request. Tag
builds compile the guest and helpers without their caches, and the Release and Promote release
jobs install their tools without a cache.

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
4. Test the draft's own JAR.
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
