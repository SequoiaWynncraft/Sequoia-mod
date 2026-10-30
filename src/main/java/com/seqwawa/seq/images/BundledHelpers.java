package com.seqwawa.seq.images;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.tukaani.xz.XZInputStream;

/**
 * The helpers packed by native/package.py: one XZ stream per CPU, indexed in provenance.json.
 *
 * <p>The index gives each helper's offset, length and SHA-256 in its pack, so a helper is written
 * by streaming its pack once up to that helper; the expanded pack is never held in memory. Every
 * value from the index is checked before use, because the SHA-256 also names the extraction
 * directory.
 */
final class BundledHelpers {
    static final int MAX_HELPER_BYTES = 32 * 1024 * 1024;
    private static final long MAX_PACK_BYTES = 64L * 1024 * 1024;
    private static final int MAX_PROVENANCE_BYTES = 1024 * 1024;
    /** Twice the 16 MiB dictionary the packs use; a larger one is refused before allocation. */
    static final int DECODER_MEMORY_LIMIT_KIB = 32 * 1024;
    private static final int COPY_BUFFER_BYTES = 64 * 1024;
    private static final Pattern PACK_FILE = Pattern.compile("helpers-(x86_64|aarch64)\\.xz");
    private static final Pattern MEMBER_PATH =
            Pattern.compile("(windows|linux|macos)-(x86_64|aarch64)/[A-Za-z0-9._-]+");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    @FunctionalInterface
    interface Resources {
        /** The named file under sandbox/, or null if there is none. */
        InputStream open(String name) throws IOException;
    }

    record Member(String path, long offset, int size, String sha256) {
        String platform() {
            return path.substring(0, path.indexOf('/'));
        }
    }

    record Pack(String file, long size, String sha256, long expandedSize, List<Member> members) {}

    static final BundledHelpers JAR = new BundledHelpers(
            name -> IsolatedDecoder.class.getResourceAsStream("/sandbox/" + name), DECODER_MEMORY_LIMIT_KIB);

    private final Resources resources;
    private final int memoryLimitKiB;
    private List<Pack> packs;

    BundledHelpers(Resources resources, int memoryLimitKiB) {
        this.resources = resources;
        this.memoryLimitKiB = memoryLimitKiB;
    }

    /** The packed helper at {@code key} ("platform/name"). */
    synchronized Member member(String key) throws IOException {
        for (Pack pack : packs()) {
            for (Member member : pack.members()) {
                if (member.path().equals(key)) return member;
            }
        }
        throw new IOException("Missing bundled sandbox");
    }

    /** Every packed helper of {@code platform}. */
    synchronized List<Member> members(String platform) throws IOException {
        List<Member> found = new ArrayList<>();
        for (Pack pack : packs()) {
            for (Member member : pack.members()) {
                if (member.platform().equals(platform)) found.add(member);
            }
        }
        return found;
    }

    /**
     * Writes each member to its target in one pass over the member's pack: into a temporary file
     * beside the target, checked against the index, then moved into place.
     */
    synchronized void extract(Map<Member, Path> targets) throws IOException {
        if (targets.isEmpty()) return;
        Pack pack = packOf(targets.keySet().iterator().next());
        if (!pack.members().containsAll(targets.keySet())) throw new IOException("Sandbox verification failed");
        byte[] packed = read(pack);
        List<Member> wanted = targets.keySet().stream()
                .sorted(Comparator.comparingLong(Member::offset))
                .toList();
        List<Path> temporaries = new ArrayList<>();
        try (InputStream expanded = decompress(packed, memoryLimitKiB)) {
            long position = 0;
            byte[] buffer = new byte[COPY_BUFFER_BYTES];
            for (Member member : wanted) {
                copy(expanded, OutputStream.nullOutputStream(), member.offset() - position, buffer);
                Path target = targets.get(member);
                Path temporary = Files.createTempFile(target.getParent(), "helper-", ".tmp");
                temporaries.add(temporary);
                MessageDigest digest = sha256();
                try (OutputStream out = Files.newOutputStream(temporary)) {
                    copy(expanded, out, member.size(), buffer, digest);
                }
                if (!HexFormat.of().formatHex(digest.digest()).equals(member.sha256()))
                    throw new IOException("Sandbox verification failed");
                position = member.offset() + member.size();
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (FileAlreadyExistsException raced) {
                    // Another load extracted the same verified bytes first.
                }
            }
        } finally {
            for (Path temporary : temporaries) Files.deleteIfExists(temporary);
        }
    }

    private static InputStream decompress(byte[] packed, int memoryLimitKiB) throws IOException {
        try {
            return new XZInputStream(new ByteArrayInputStream(packed), memoryLimitKiB);
        } catch (IOException corrupt) {
            throw new IOException("Sandbox verification failed", corrupt);
        }
    }

    /** Copies exactly {@code length} bytes; a pack that decodes short or corrupt fails verification. */
    private static void copy(InputStream in, OutputStream out, long length, byte[] buffer, MessageDigest... digest)
            throws IOException {
        long remaining = length;
        while (remaining > 0) {
            int read;
            try {
                read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            } catch (IOException corrupt) {
                throw new IOException("Sandbox verification failed", corrupt);
            }
            if (read < 0) throw new IOException("Sandbox verification failed");
            for (MessageDigest d : digest) d.update(buffer, 0, read);
            out.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private Pack packOf(Member member) throws IOException {
        for (Pack pack : packs()) {
            if (pack.members().contains(member)) return pack;
        }
        throw new IOException("Missing bundled sandbox");
    }

    /** The compressed pack, a few MiB, checked against the index before any decompression. */
    private byte[] read(Pack pack) throws IOException {
        byte[] packed;
        try (InputStream in = resources.open(pack.file())) {
            if (in == null) throw new IOException("Missing bundled sandbox");
            packed = in.readNBytes((int) pack.size() + 1);
        }
        if (packed.length != pack.size() || !HexFormat.of().formatHex(sha256().digest(packed)).equals(pack.sha256()))
            throw new IOException("Sandbox verification failed");
        return packed;
    }

    private List<Pack> packs() throws IOException {
        if (packs == null) packs = parse(provenance());
        return packs;
    }

    private String provenance() throws IOException {
        try (InputStream in = resources.open("provenance.json")) {
            if (in == null) throw new IOException("Missing bundled sandbox");
            byte[] bytes = in.readNBytes(MAX_PROVENANCE_BYTES + 1);
            if (bytes.length > MAX_PROVENANCE_BYTES) throw new IOException("Invalid sandbox resource");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /** Parses and bounds the pack index; any value out of shape rejects the whole index. */
    static List<Pack> parse(String provenance) throws IOException {
        try {
            JsonElement packsElement = JsonParser.parseString(provenance).getAsJsonObject().get("packs");
            if (packsElement == null) throw new IOException("Missing bundled sandbox");
            List<Pack> parsed = new ArrayList<>();
            for (var entry : packsElement.getAsJsonObject().entrySet()) {
                JsonObject pack = entry.getValue().getAsJsonObject();
                String file = pack.get("file").getAsString();
                long size = pack.get("bytes").getAsLong();
                String sha256 = pack.get("sha256").getAsString();
                long expanded = pack.get("uncompressed_bytes").getAsLong();
                if (!PACK_FILE.matcher(file).matches() || !file.equals("helpers-" + entry.getKey() + ".xz")
                        || size <= 0 || size > MAX_PACK_BYTES || !SHA256.matcher(sha256).matches()
                        || expanded <= 0 || expanded > MAX_PACK_BYTES)
                    throw new IOException("Invalid sandbox resource");
                List<Member> members = new ArrayList<>();
                long end = 0;
                for (JsonElement element : pack.getAsJsonArray("members")) {
                    JsonObject member = element.getAsJsonObject();
                    String path = member.get("path").getAsString();
                    long offset = member.get("offset").getAsLong();
                    long bytes = member.get("bytes").getAsLong();
                    String digest = member.get("sha256").getAsString();
                    if (!MEMBER_PATH.matcher(path).matches() || !path.contains("-" + entry.getKey() + "/")
                            || offset != end || bytes <= 0 || bytes > MAX_HELPER_BYTES
                            || !SHA256.matcher(digest).matches())
                        throw new IOException("Invalid sandbox resource");
                    members.add(new Member(path, offset, (int) bytes, digest));
                    end = offset + bytes;
                }
                if (end != expanded) throw new IOException("Invalid sandbox resource");
                parsed.add(new Pack(file, size, sha256, expanded, List.copyOf(members)));
            }
            return List.copyOf(parsed);
        } catch (RuntimeException malformed) {
            throw new IOException("Invalid sandbox resource", malformed);
        }
    }

    private static MessageDigest sha256() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
