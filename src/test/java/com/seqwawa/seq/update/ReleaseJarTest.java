package com.seqwawa.seq.update;

import static com.seqwawa.seq.update.UpdateFixtures.modJar;
import static com.seqwawa.seq.update.UpdateFixtures.sha256;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.fabricmc.loader.api.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A release file is installed only if it is exactly the release and this game can run it. */
class ReleaseJarTest {
    private static final Map<String, String> GAME = Map.of(
            "minecraft", "1.21.11", "fabricloader", "0.18.4", "fabric-api", "0.141.2+1.21.11", "java", "21");

    @TempDir
    Path temp;

    private static Function<String, Optional<Version>> installed(Map<String, String> versions) {
        return id -> Optional.ofNullable(versions.get(id)).map(UpdateFixtures::version);
    }

    private ReleaseJar.Verdict check(byte[] jar, String version) throws Exception {
        Path file = temp.resolve("candidate.jar");
        Files.write(file, jar);
        return ReleaseJar.check(file, jar.length, sha256(jar), version, installed(GAME));
    }

    private String incompatible(String depends) throws Exception {
        var verdict = check(modJar("seq", "1.12.0", depends, 1000), "1.12.0");
        return assertInstanceOf(ReleaseJar.Verdict.Incompatible.class, verdict).reason();
    }

    @Test
    void theReleasedJarForThisGameIsReady() throws Exception {
        assertInstanceOf(ReleaseJar.Verdict.Ready.class, check(modJar("1.12.0"), "1.12.0"));
    }

    @Test
    void aFileWithOtherBytesOrAnotherVersionIsNotTheRelease() throws Exception {
        byte[] jar = modJar("1.12.0");
        Path file = temp.resolve("candidate.jar");
        Files.write(file, jar);
        assertInstanceOf(ReleaseJar.Verdict.Mismatch.class,
                ReleaseJar.check(file, jar.length + 1, sha256(jar), "1.12.0", installed(GAME)));
        assertInstanceOf(ReleaseJar.Verdict.Mismatch.class,
                ReleaseJar.check(file, jar.length, "0".repeat(64), "1.12.0", installed(GAME)));
        assertEquals("The release file is seq 1.12.0, not Sequoia 1.13.0",
                assertInstanceOf(ReleaseJar.Verdict.Mismatch.class, check(jar, "1.13.0")).reason());
        assertInstanceOf(ReleaseJar.Verdict.Mismatch.class, check(modJar("other", "1.12.0", "{}", 10), "1.12.0"));
        assertInstanceOf(ReleaseJar.Verdict.Mismatch.class, check("not a zip".getBytes(), "1.12.0"));
    }

    @Test
    void requirementsAreCheckedWithFabricVersionPredicates() throws Exception {
        assertEquals("Sequoia 1.12.0 needs minecraft 1.21.12; this game has 1.21.11",
                incompatible("{\"minecraft\":\"1.21.12\"}"));
        assertEquals("Sequoia 1.12.0 needs fabricloader >=0.19.0; this game has 0.18.4",
                incompatible("{\"fabricloader\":\">=0.19.0\"}"));
        assertEquals("Sequoia 1.12.0 needs java >=25; this game has 21", incompatible("{\"java\":\">=25\"}"));
        assertEquals("Sequoia 1.12.0 needs wynntils *; it is not installed", incompatible("{\"wynntils\":\"*\"}"));
        assertInstanceOf(ReleaseJar.Verdict.Ready.class,
                check(modJar("seq", "1.12.0", "{\"minecraft\":[\"1.21.12\",\"~1.21.11\"]}", 10), "1.12.0"));
    }

    @Test
    void breaksAndUncheckableRequirementsAreIncompatible() throws Exception {
        String breaks = assertInstanceOf(ReleaseJar.Verdict.Incompatible.class,
                ReleaseJar.compatibility(com.google.gson.JsonParser.parseString(
                        "{\"breaks\":{\"fabric-api\":\"<0.200.0\"}}").getAsJsonObject(), "1.12.0", installed(GAME))).reason();
        assertEquals("Sequoia 1.12.0 does not work with fabric-api 0.141.2+1.21.11", breaks);
        assertEquals("Sequoia 1.12.0 has a requirement this game cannot check: minecraft {}",
                incompatible("{\"minecraft\":{}}"));
        // Fabric reads most strings as an exact version; a bare operator is one it rejects.
        assertEquals("Sequoia 1.12.0 has a requirement this game cannot check: minecraft \">=\"",
                incompatible("{\"minecraft\":\">=\"}"));
    }

    @Test
    void anIncompatibilityRecordsTheVersionsItWasDecidedOn() throws Exception {
        var verdict = assertInstanceOf(ReleaseJar.Verdict.Incompatible.class,
                check(modJar("seq", "1.12.0", "{\"minecraft\":\"1.21.12\",\"fabricloader\":\"*\"}", 10), "1.12.0"));
        // The verdict rests on the first unmet requirement, so only what was checked up to it counts.
        assertEquals("minecraft=1.21.11", verdict.environment());
        assertEquals(verdict.environment(), ReleaseJar.currentEnvironment(verdict.environment(), installed(GAME)));
        var updated = new java.util.HashMap<>(GAME);
        updated.put("minecraft", "1.21.12");
        assertEquals("minecraft=1.21.12", ReleaseJar.currentEnvironment(verdict.environment(), installed(updated)));
    }
}
