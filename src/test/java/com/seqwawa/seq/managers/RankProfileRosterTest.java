package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.seqwawa.seq.model.RankProfilesResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RankProfileRosterTest {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Test
    void validRefreshPublishesAndCachesTheSnapshot(@TempDir Path tempDir) throws IOException {
        Path cachePath = tempDir.resolve("cache/rank-profiles.json");
        RankProfilesResponse remote = validSnapshot("RemotePlayer");
        RankProfileRoster roster =
                new RankProfileRoster(cachePath, () -> CompletableFuture.completedFuture(remote));
        List<RankProfilesResponse> delivered = new ArrayList<>();
        roster.subscribe(delivered::add);

        String result = roster.refreshAsync().join();

        assertEquals("Rank profiles refreshed: 1 profiles.", result);
        assertEquals(List.of(remote), delivered);
        assertEquals(remote, GSON.fromJson(Files.readString(cachePath), RankProfilesResponse.class));
        assertTrue(roster.status().contains("status=loaded 1 profiles"));
    }

    @Test
    void invalidRefreshPreservesTheLastGoodSnapshotAndCache(@TempDir Path tempDir) throws IOException {
        RankProfilesResponse cached = validSnapshot("CachedPlayer");

        int caseNumber = 0;
        for (RankProfilesResponse invalid : invalidSnapshots()) {
            Path cachePath = tempDir.resolve("case-" + caseNumber++).resolve("rank-profiles.json");
            Files.createDirectories(cachePath.getParent());
            String cachedJson = GSON.toJson(cached);
            Files.writeString(cachePath, cachedJson);

            RankProfileRoster roster =
                    new RankProfileRoster(cachePath, () -> CompletableFuture.completedFuture(invalid));
            List<RankProfilesResponse> delivered = new ArrayList<>();
            roster.subscribe(delivered::add);

            String result = roster.refreshAsync().join();

            assertTrue(result.startsWith("Rank profile refresh failed; using cached profiles."));
            assertEquals(List.of(cached), delivered, "invalid refresh must not publish a second snapshot");
            assertEquals(cachedJson, Files.readString(cachePath), "invalid refresh must not replace the cache");
            assertTrue(roster.status().contains("status=refresh failed"));
        }
    }

    @Test
    void invalidCacheIsNotPublishedAndCanBeReplacedByAValidRefresh(@TempDir Path tempDir) throws IOException {
        Path cachePath = tempDir.resolve("rank-profiles.json");
        Files.writeString(cachePath, "null");
        RankProfilesResponse remote = validSnapshot("RecoveredPlayer");
        RankProfileRoster roster =
                new RankProfileRoster(cachePath, () -> CompletableFuture.completedFuture(remote));
        AtomicInteger deliveries = new AtomicInteger();
        roster.subscribe(ignored -> deliveries.incrementAndGet());

        assertEquals(0, deliveries.get());
        assertTrue(roster.status().contains("status=cache load failed"));
        assertEquals("null", Files.readString(cachePath));

        roster.refreshAsync().join();

        assertEquals(1, deliveries.get());
        assertEquals(remote, GSON.fromJson(Files.readString(cachePath), RankProfilesResponse.class));
    }

    private static List<RankProfilesResponse> invalidSnapshots() {
        return Arrays.asList(
                null,
                new RankProfilesResponse(2, catalog(), List.of()),
                new RankProfilesResponse(1, null, List.of()),
                new RankProfilesResponse(1, catalog(), null));
    }

    private static RankProfilesResponse validSnapshot(String username) {
        RankProfilesResponse.Profile profile = new RankProfilesResponse.Profile(
                null,
                new RankProfilesResponse.MinecraftIdentity("00000000-0000-0000-0000-000000000001", username),
                List.of(),
                List.of(),
                null);
        return new RankProfilesResponse(1, catalog(), List.of(profile));
    }

    private static RankProfilesResponse.Catalog catalog() {
        return new RankProfilesResponse.Catalog(List.of(), List.of(), List.of());
    }
}
