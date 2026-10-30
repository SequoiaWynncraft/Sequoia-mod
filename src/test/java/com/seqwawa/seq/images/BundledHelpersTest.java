package com.seqwawa.seq.images;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;

class BundledHelpersTest {
    private static final List<String> MEMBERS = List.of(
            "windows-x86_64/sandbox.exe", "windows-x86_64/seqimages-decoder.exe", "linux-x86_64/seqimages-decoder");

    @TempDir
    Path temp;

    private final Map<String, byte[]> contents = new LinkedHashMap<>();
    private final Map<String, byte[]> files = new LinkedHashMap<>();

    /** A pack shaped like native/package.py's output, with the given XZ dictionary. */
    private void pack(int dictBytes) throws Exception {
        Random random = new Random(7);
        ByteArrayOutputStream expanded = new ByteArrayOutputStream();
        StringBuilder members = new StringBuilder();
        for (String path : MEMBERS) {
            byte[] content = new byte[20_000 + contents.size() * 5_000];
            random.nextBytes(content);
            contents.put(path, content);
            if (!members.isEmpty()) members.append(',');
            members.append("{\"path\":\"%s\",\"offset\":%d,\"bytes\":%d,\"sha256\":\"%s\"}"
                    .formatted(path, expanded.size(), content.length, sha256(content)));
            expanded.writeBytes(content);
        }
        ByteArrayOutputStream packed = new ByteArrayOutputStream();
        LZMA2Options options = new LZMA2Options(LZMA2Options.PRESET_DEFAULT);
        options.setDictSize(dictBytes);
        try (XZOutputStream xz = new XZOutputStream(packed, options)) {
            xz.write(expanded.toByteArray());
        }
        files.put("helpers-x86_64.xz", packed.toByteArray());
        files.put("provenance.json", """
                {"packs":{"x86_64":{"file":"helpers-x86_64.xz","bytes":%d,"sha256":"%s",
                "uncompressed_bytes":%d,"members":[%s]}}}""".formatted(
                packed.size(), sha256(packed.toByteArray()), expanded.size(), members)
                .getBytes(StandardCharsets.UTF_8));
    }

    private BundledHelpers helpers() {
        return helpers(BundledHelpers.DECODER_MEMORY_LIMIT_KIB);
    }

    private BundledHelpers helpers(int memoryLimitKiB) {
        return new BundledHelpers(
                name -> files.containsKey(name) ? new ByteArrayInputStream(files.get(name)) : null, memoryLimitKiB);
    }

    private Map<BundledHelpers.Member, Path> targets(BundledHelpers helpers, String... paths) throws IOException {
        Map<BundledHelpers.Member, Path> targets = new LinkedHashMap<>();
        for (String path : paths) targets.put(helpers.member(path), temp.resolve(path.replace('/', '_')));
        return targets;
    }

    @Test
    void extractsTheRequestedHelpersExactlyAndNothingElse() throws Exception {
        pack(1 << 20);
        BundledHelpers helpers = helpers();
        var targets = targets(helpers, "windows-x86_64/sandbox.exe", "windows-x86_64/seqimages-decoder.exe");

        helpers.extract(targets);

        for (var entry : targets.entrySet())
            assertArrayEquals(contents.get(entry.getKey().path()), Files.readAllBytes(entry.getValue()));
        assertEquals(List.of("windows-x86_64/sandbox.exe", "windows-x86_64/seqimages-decoder.exe"),
                helpers.members("windows-x86_64").stream().map(BundledHelpers.Member::path).toList());
        assertEquals(2, listing().size());
    }

    @Test
    void aLaterMemberIsReachedByStreamingPastEarlierOnes() throws Exception {
        pack(1 << 20);
        BundledHelpers helpers = helpers();
        var targets = targets(helpers, "linux-x86_64/seqimages-decoder");

        helpers.extract(targets);

        assertArrayEquals(contents.get("linux-x86_64/seqimages-decoder"),
                Files.readAllBytes(targets.values().iterator().next()));
    }

    @Test
    void aChangedPackIsRejectedBeforeDecompressionAndLeavesNoFiles() throws Exception {
        pack(1 << 20);
        files.get("helpers-x86_64.xz")[100] ^= 1;
        BundledHelpers helpers = helpers();

        IOException failure = assertThrows(IOException.class,
                () -> helpers.extract(targets(helpers, "linux-x86_64/seqimages-decoder")));

        assertEquals("Sandbox verification failed", failure.getMessage());
        assertEquals(List.of(), listing());
    }

    @Test
    void aCorruptOrTruncatedPackThatMatchesItsIndexStillFailsVerification() throws Exception {
        for (boolean truncate : new boolean[] {false, true}) {
            contents.clear();
            pack(1 << 20);
            byte[] packed = files.get("helpers-x86_64.xz");
            byte[] changed = truncate ? Arrays.copyOf(packed, packed.length / 2) : packed.clone();
            // Random members are stored uncompressed, so flip a byte inside the last member, the
            // one extracted; corruption in a skipped member is never read and cannot matter.
            if (!truncate) changed[changed.length - 200] ^= 0x55;
            String provenance = new String(files.get("provenance.json"), StandardCharsets.UTF_8)
                    .replace(sha256(packed), sha256(changed))
                    .replace("\"bytes\":" + packed.length + ",", "\"bytes\":" + changed.length + ",");
            files.put("helpers-x86_64.xz", changed);
            files.put("provenance.json", provenance.getBytes(StandardCharsets.UTF_8));
            BundledHelpers helpers = helpers();

            IOException failure = assertThrows(IOException.class,
                    () -> helpers.extract(targets(helpers, "linux-x86_64/seqimages-decoder")));

            assertEquals("Sandbox verification failed", failure.getMessage());
            assertEquals(List.of(), listing());
        }
    }

    @Test
    void aMemberWhoseBytesDifferFromItsIndexedHashIsNotWritten() throws Exception {
        pack(1 << 20);
        String provenance = new String(files.get("provenance.json"), StandardCharsets.UTF_8)
                .replace(sha256(contents.get("linux-x86_64/seqimages-decoder")), "0".repeat(64));
        files.put("provenance.json", provenance.getBytes(StandardCharsets.UTF_8));
        BundledHelpers helpers = helpers();

        assertThrows(IOException.class, () -> helpers.extract(targets(helpers, "linux-x86_64/seqimages-decoder")));

        assertEquals(List.of(), listing());
    }

    @Test
    void aDictionaryLargerThanTheMemoryLimitIsRefused() throws Exception {
        pack(1 << 20);
        BundledHelpers helpers = helpers(512);

        IOException failure = assertThrows(IOException.class,
                () -> helpers.extract(targets(helpers, "linux-x86_64/seqimages-decoder")));

        assertEquals("Sandbox verification failed", failure.getMessage());
        assertEquals(List.of(), listing());
    }

    @Test
    void aMissingHelperOrPackIsReportedAsMissing() throws Exception {
        pack(1 << 20);
        assertEquals("Missing bundled sandbox",
                assertThrows(IOException.class, () -> helpers().member("macos-x86_64/seqimages-decoder")).getMessage());
        files.remove("provenance.json");
        assertEquals("Missing bundled sandbox",
                assertThrows(IOException.class, () -> helpers().member("linux-x86_64/seqimages-decoder")).getMessage());
    }

    @Test
    void indexValuesOutOfShapeRejectTheIndex() throws Exception {
        String hash = "a".repeat(64);
        String member = "{\"path\":\"%s\",\"offset\":%d,\"bytes\":%d,\"sha256\":\"%s\"}";
        String pack = "{\"packs\":{\"x86_64\":{\"file\":\"helpers-x86_64.xz\",\"bytes\":10,\"sha256\":\"%s\","
                + "\"uncompressed_bytes\":%d,\"members\":[%s]}}}";
        for (String provenance : List.of(
                pack.formatted(hash, 10, member.formatted("linux-x86_64/../../escape", 0, 10, hash)),
                pack.formatted(hash, 10, member.formatted("linux-x86_64/seqimages-decoder", 0, 10, "../" + hash)),
                pack.formatted(hash, 10, member.formatted("linux-aarch64/seqimages-decoder", 0, 10, hash)),
                pack.formatted(hash, 20, member.formatted("linux-x86_64/a", 0, 10, hash) + ","
                        + member.formatted("linux-x86_64/b", 5, 15, hash)),
                pack.formatted(hash, 64L * 1024 * 1024 + 1, member.formatted("linux-x86_64/a", 0, 10, hash)),
                pack.formatted(hash, 40_000_000, member.formatted("linux-x86_64/a", 0, 40_000_000, hash)),
                pack.formatted(hash, 11, member.formatted("linux-x86_64/a", 0, 10, hash)),
                "{\"packs\":[]}")) {
            assertEquals("Invalid sandbox resource",
                    assertThrows(IOException.class, () -> BundledHelpers.parse(provenance)).getMessage(), provenance);
        }
    }

    private List<Path> listing() throws IOException {
        try (Stream<Path> paths = Files.list(temp)) {
            return paths.toList();
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test
    void theShippedIndexDescribesEveryHelperOfThisHost() throws Exception {
        String platform = IsolatedDecoder.platform(System.getProperty("os.name"), System.getProperty("os.arch"));
        assertFalse(BundledHelpers.JAR.members(platform).isEmpty());
    }
}
