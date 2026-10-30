package com.seqwawa.seq.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

/**
 * Checks a downloaded or staged release file before it may be installed: that it is exactly the
 * release's bytes, that it is the Sequoia version the release names, and that this game can run it.
 * A file is never trusted for its name or for what an earlier session recorded about it.
 */
final class ReleaseJar {
    private static final String MOD_ID = "seq";
    private static final long MAX_METADATA_BYTES = 64 * 1024;

    sealed interface Verdict {
        /** Exactly the release, and every declared requirement is met here. */
        record Ready() implements Verdict {}

        /** Not the release's bytes or not the version it names; the file must not be used. */
        record Mismatch(String reason) implements Verdict {}

        /**
         * The right file, which this game cannot run.
         *
         * @param environment the installed versions checked, so an unchanged game need not download it again
         */
        record Incompatible(String reason, String environment) implements Verdict {}
    }

    private ReleaseJar() {}

    /**
     * @param installed the version of each installed mod by id, including {@code minecraft},
     *     {@code fabricloader} and {@code java}; empty when that mod is not installed
     */
    static Verdict check(Path file, long size, String sha256, String version,
            Function<String, Optional<Version>> installed) throws IOException {
        if (Files.size(file) != size)
            return new Verdict.Mismatch("The downloaded file is " + UpdateState.megabytes(Files.size(file))
                    + ", not the " + UpdateState.megabytes(size) + " the release lists");
        if (!sha256(file).equalsIgnoreCase(sha256))
            return new Verdict.Mismatch("The downloaded file does not match the release's checksum");
        JsonObject metadata;
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null) return new Verdict.Mismatch("The release file is not a Fabric mod");
            try (InputStream in = zip.getInputStream(entry)) {
                byte[] bytes = in.readNBytes((int) MAX_METADATA_BYTES + 1);
                if (bytes.length > MAX_METADATA_BYTES) return new Verdict.Mismatch("The release file's metadata is too large");
                metadata = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } catch (IOException | RuntimeException unreadable) {
            return new Verdict.Mismatch("The release file is not a readable Fabric mod");
        }
        String id = string(metadata, "id");
        String declared = string(metadata, "version");
        if (!MOD_ID.equals(id) || !version.equals(declared))
            return new Verdict.Mismatch("The release file is " + id + " " + declared + ", not Sequoia " + version);
        return compatibility(metadata, version, installed);
    }

    /** Evaluates {@code depends} and {@code breaks} with Fabric Loader's own version predicates. */
    static Verdict compatibility(JsonObject metadata, String version, Function<String, Optional<Version>> installed) {
        Map<String, String> checked = new TreeMap<>();
        String problem = null;
        for (String kind : List.of("depends", "breaks")) {
            JsonElement section = metadata.get(kind);
            if (section == null) continue;
            if (!section.isJsonObject()) {
                problem = "Sequoia " + version + " has requirements this game cannot read";
                break;
            }
            for (var requirement : section.getAsJsonObject().entrySet()) {
                String modId = requirement.getKey();
                Optional<Version> present = installed.apply(modId);
                checked.put(modId, present.map(Version::getFriendlyString).orElse("absent"));
                List<String> predicates = predicates(requirement.getValue());
                boolean matches;
                try {
                    matches = present.isPresent() && predicates != null && !predicates.isEmpty()
                            && anyMatches(predicates, present.get());
                } catch (VersionParsingException unreadable) {
                    predicates = null;
                    matches = false;
                }
                if (predicates == null) {
                    problem = "Sequoia " + version + " has a requirement this game cannot check: " + modId + " "
                            + requirement.getValue();
                } else if (kind.equals("depends") && !matches) {
                    problem = "Sequoia " + version + " needs " + modId + " " + String.join(" or ", predicates)
                            + present.map(v -> "; this game has " + v.getFriendlyString()).orElse("; it is not installed");
                } else if (kind.equals("breaks") && matches) {
                    problem = "Sequoia " + version + " does not work with " + modId + " " + present.get().getFriendlyString();
                }
                if (problem != null) break;
            }
            if (problem != null) break;
        }
        if (problem == null) return new Verdict.Ready();
        return new Verdict.Incompatible(problem, environment(checked));
    }

    /** {@code id=version} for each id, sorted; what an incompatibility verdict depended on. */
    static String environment(Map<String, String> versions) {
        List<String> parts = new ArrayList<>();
        new TreeMap<>(versions).forEach((id, v) -> parts.add(id + "=" + v));
        return String.join(";", parts);
    }

    /** The environment string for the ids a recorded verdict checked, as this game has them now. */
    static String currentEnvironment(String recorded, Function<String, Optional<Version>> installed) {
        Map<String, String> versions = new TreeMap<>();
        for (String part : recorded.split(";")) {
            int split = part.indexOf('=');
            if (split <= 0) continue;
            String id = part.substring(0, split);
            versions.put(id, installed.apply(id).map(Version::getFriendlyString).orElse("absent"));
        }
        return environment(versions);
    }

    private static List<String> predicates(JsonElement value) {
        List<String> found = new ArrayList<>();
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            found.add(value.getAsString());
        } else if (value.isJsonArray()) {
            for (JsonElement element : value.getAsJsonArray()) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return null;
                found.add(element.getAsString());
            }
        } else {
            return null;
        }
        return found;
    }

    private static boolean anyMatches(List<String> predicates, Version present) throws VersionParsingException {
        for (String predicate : predicates) {
            if (VersionPredicate.parse(predicate).test(present)) return true;
        }
        return false;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    static String sha256(Path file) throws IOException {
        MessageDigest digest = ReleaseDownload.sha256();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = in.read(buffer)) >= 0; ) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
