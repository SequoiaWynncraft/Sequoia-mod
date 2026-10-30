# Image decoder builds

Chat image previews are decoded by a sandboxed helper per platform: a Rust/Wasmtime program
(`decoder/`) that embeds a WebAssembly guest built from FFmpeg and zlib (`wasm/decoder.c`). On
Windows, `sandbox.cpp` starts it in an AppContainer. The repository holds only sources; the Build
workflow produces everything else under `native/out/`:

1. `guest.py` downloads the sources pinned in `sources.json`, stops unless their SHA-256 pins,
   FFmpeg's signature by `keys/ffmpeg-release-signing-key.asc` and the bundled license texts
   match, and builds `out/guest/decoder.wasm` with `build-wasm.sh`. The same inputs give the
   same bytes.
2. `build-native.py PLATFORM` embeds that guest in the helper, runs the Rust tests and checks the
   helper on a native runner: exact pixels, sandboxed before input, malformed input rejected, OS
   denials and, on Windows, broker cancellation (`tests/windows-cancellation.py`).
3. `notices.py check` keeps `src/main/resources/licenses` in step with `Cargo.lock`.
4. `package.py --require-all` checks each helper against the guest it was verified with, packs the
   helpers for each CPU into one XZ stream (`sandbox/helpers-x86_64.xz`, `sandbox/helpers-aarch64.xz`)
   and writes `sandbox/provenance.json`, which lists every helper's offset, length and SHA-256 in
   its pack, and `sequoia-images-native-source.zip`, the corresponding source that Release publishes
   next to the JAR on GitHub and Modrinth. The same FFmpeg guest compiled for three systems is mostly
   the same bytes, which one stream per CPU stores once; the mod unpacks only its own platform's
   helpers and checks each against its SHA-256.

## Local builds

With Python 3.11.4+, `gpgv`, `make` and Rust on Linux or macOS:

```sh
python3 native/guest.py
python3 native/build-native.py linux-x86_64 --target x86_64-unknown-linux-gnu  # without a musl C compiler
python3 native/package.py --packs-only   # packs and indexes the helpers built so far
./gradlew build -Pallow_partial_native   # without the flag, Gradle refuses a JAR missing any platform
```

## Using a modified decoder

The source zip contains this directory, the FFmpeg and zlib archives under `upstream/` and the
notices. Pack a changed FFmpeg or zlib like the original archive, then:

```sh
WASI_SDK_PATH=/path/to/wasi-sdk-34 bash native/build-wasm.sh my.wasm ffmpeg-9.0.2.tar.xz zlib-1.3.2.tar.gz
python3 native/build-native.py linux-x86_64 --guest my.wasm
```

and add the files of `native/out/sandbox/PLATFORM/` to the mod JAR at the same `sandbox/PLATFORM/`
path. A helper placed there takes precedence over the packed one; the mod runs whichever helper its
JAR contains. `python3 native/package.py --unpack PACK PROVENANCE DEST` restores the shipped helpers
from a pack, checked against the provenance, for comparison. A helper built from another guest skips the pixel and sandbox
checks, which expect the pinned guest and the repository's test media.

## Updating dependencies

Check new versions, digests and keys in `sources.json` against the FFmpeg, zlib and wasi-sdk
release pages; a changed digest alone proves nothing. After a crate or toolchain change, run
`python3 native/notices.py write` and review the result. Run the build checks with
`python3 -m unittest discover -s native/tests -p test_build_inputs.py`.
