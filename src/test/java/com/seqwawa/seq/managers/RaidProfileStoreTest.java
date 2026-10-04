package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.seqwawa.seq.model.PremadeParty;
import com.seqwawa.seq.model.RaidProfilesResponse;
import com.seqwawa.seq.model.RaidTeamProfile;
import com.seqwawa.seq.network.ConnectionManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RaidProfileStoreTest {

    /** A sample API payload containing two members. */
    private static final String BACKEND_PAYLOAD =
            """
            {
              "schema_version": 1,
              "catalog": {
                "builds": [
                  {"key": "ASCENDANCY", "label": "Ascendancy", "position": 1},
                  {"key": "HADAL", "label": "Hadal", "position": 8}
                ],
                "raids": [
                  {"key": "TNA", "short_name": "TNA", "api_name": "The Nameless Anomaly",
                   "position": 1, "build_keys": ["ASCENDANCY"]},
                  {"key": "NOTG", "short_name": "NOTG", "api_name": "Nest of the Grootslangs",
                   "position": 3, "build_keys": ["HADAL"]}
                ]
              },
              "profiles": [
                {"minecraft": {"uuid": "10000000-0000-0000-0000-000000000002", "username": "Blousy"},
                 "builds": ["HADAL"], "can_bring_auras": true, "region": "EU",
                 "status": "notg only", "updated_at": "2026-09-04T18:12:00Z"}
              ]
            }
            """;

    private static final String BLOUSY_UUID = "10000000-0000-0000-0000-000000000002";

    private static RaidProfileStore store(Path directory) {
        return store(directory, BACKEND_PAYLOAD);
    }

    private static RaidProfileStore store(Path directory, String payload) {
        return new RaidProfileStore(
                directory.resolve("raid-profile.json"),
                directory.resolve("cache/raid-profiles.json"),
                () -> CompletableFuture.completedFuture(
                        RaidProfileStore.parseProfilesResponse(payload).orElse(null)));
    }

    // ── Backend data ──

    @Test
    void theCatalogAndProfilesComeFromTheBackend(@TempDir Path directory) {
        RaidProfileStore store = store(directory);
        store.load();

        assertTrue(store.catalog().isEmpty(), "nothing is known before the first fetch");

        store.refresh().join();

        assertEquals(2, store.catalog().builds().size());
        assertEquals(2, store.catalog().raids().size());
        assertEquals("Hadal", store.catalog().labelFor("HADAL"));
        assertEquals("The Nameless Anomaly", store.catalog().raid("TNA").apiName());

        RaidTeamProfile profile = store.profileForUuid(BLOUSY_UUID);
        assertTrue(profile.isComplete());
        assertEquals(Set.of("HADAL"), profile.buildKeys());
        assertTrue(profile.canBringAuras());
        assertEquals("notg only", profile.status());
    }

    @Test
    void aMemberWithNoProfileReadsAsUnanswered(@TempDir Path directory) {
        RaidProfileStore store = store(directory);
        store.load();
        store.refresh().join();

        assertFalse(store.profileForUuid("99999999-0000-0000-0000-000000000000").isComplete());
        assertFalse(store.profileForUuid(null).isComplete());
        assertFalse(store.profileFor(null).isComplete());
        assertFalse(store.profileForUsername("someone-else").isComplete());
    }

    @Test
    void profilesAreKeyedByUuidSoARenameCannotStrandThem(@TempDir Path directory) {
        RaidProfileStore store = store(directory);
        store.load();
        store.refresh().join();

        // Same person, new name. Matching on the uuid still finds them, and the
        // old name stops resolving rather than pointing at a ghost.
        store.applyProfileUpdate(new RaidProfilesResponse.Profile(
                new RaidProfilesResponse.MinecraftIdentity(BLOUSY_UUID, "BlousyTheSecond"),
                List.of("ASCENDANCY"),
                false,
                null,
                null,
                java.time.Instant.parse("2026-09-05T10:00:00Z")));

        assertEquals(Set.of("ASCENDANCY"), store.profileForUuid(BLOUSY_UUID).buildKeys());
        assertEquals(
                Set.of("ASCENDANCY"),
                store.profileForUsername("blousythesecond").buildKeys(),
                "the new name resolves to the same entry");
        assertEquals(1, store.sharedProfileCount(), "renaming does not create a second row");
    }

    @Test
    void uuidsMatchWhetherOrNotTheyCarryDashes(@TempDir Path directory) {
        RaidProfileStore store = store(directory);
        store.load();
        store.refresh().join();

        assertTrue(store.profileForUuid(BLOUSY_UUID.replace("-", "")).isComplete());
        assertTrue(store.profileForUuid(BLOUSY_UUID.toUpperCase(java.util.Locale.ROOT)).isComplete());
    }

    @Test
    void theResponseIsCachedSoThePanelIsNotBlankOffline(@TempDir Path directory) {
        RaidProfileStore first = store(directory);
        first.load();
        first.refresh().join();

        // A second store with a fetch that always fails still has the catalog.
        RaidProfileStore offline = new RaidProfileStore(
                directory.resolve("raid-profile.json"),
                directory.resolve("cache/raid-profiles.json"),
                () -> CompletableFuture.failedFuture(new IllegalStateException("offline")));
        offline.load();

        assertEquals(2, offline.catalog().builds().size());
        assertTrue(offline.profileForUuid(BLOUSY_UUID).isComplete());

        offline.refresh().join();

        assertEquals(2, offline.catalog().builds().size(), "a failed refresh keeps what was loaded");
        assertNotNull(offline.lastError());
    }

    @Test
    void aResponseWithNoCatalogKeepsTheLastOneThatWorked(@TempDir Path directory) {
        RaidProfileStore store = store(directory);
        store.load();
        store.refresh().join();
        assertEquals(2, store.catalog().builds().size());

        RaidProfilesResponse withoutCatalog =
                RaidProfileStore.parseProfilesResponse("{\"schema_version\":1,\"profiles\":[]}").orElseThrow();
        store.apply(withoutCatalog);

        assertEquals(2, store.catalog().builds().size(), "blanking every build chip is worse than a stale list");
    }

    @Test
    void aRefreshAlreadyInFlightIsNotStartedTwice(@TempDir Path directory) {
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<RaidProfilesResponse> pending = new CompletableFuture<>();
        RaidProfileStore store = new RaidProfileStore(
                directory.resolve("raid-profile.json"), directory.resolve("cache/raid-profiles.json"), () -> {
                    calls.incrementAndGet();
                    return pending;
                });
        store.load();

        store.refresh();
        store.refresh();

        assertEquals(1, calls.get());
        assertTrue(store.isFetching());
        pending.complete(RaidProfileStore.parseProfilesResponse(BACKEND_PAYLOAD).orElseThrow());
        assertFalse(store.isFetching());
    }

    /** The same payload with Blousy's declared builds replaced. */
    private static RaidProfilesResponse payloadWithBlousyBuild(String buildKey) {
        String payload = BACKEND_PAYLOAD.replace("\"builds\": [\"HADAL\"]", "\"builds\": [\"" + buildKey + "\"]");
        return RaidProfileStore.parseProfilesResponse(payload).orElseThrow();
    }

    /** A store whose every fetch hands back a future the test completes by hand. */
    private static RaidProfileStore storeWithManualFetches(
            Path directory, List<CompletableFuture<RaidProfilesResponse>> fetches) {
        return new RaidProfileStore(
                directory.resolve("raid-profile.json"), directory.resolve("cache/raid-profiles.json"), () -> {
                    CompletableFuture<RaidProfilesResponse> next = new CompletableFuture<>();
                    fetches.add(next);
                    return next;
                });
    }

    @Test
    void aRefreshAskedForMidFetchRunsAgainSoTheOlderResponseCannotWin(@TempDir Path directory) {
        // The save race: a fetch is out, the player saves, and the refresh that follows
        // the save arrives while that older fetch is still pending. Its pre-save data
        // must not be what the panel ends up showing.
        List<CompletableFuture<RaidProfilesResponse>> fetches = new ArrayList<>();
        RaidProfileStore store = storeWithManualFetches(directory, fetches);
        store.load();

        CompletableFuture<Void> first = store.refresh();
        CompletableFuture<Void> duringFetch = store.refresh();

        assertEquals(1, fetches.size(), "no second request while one is out");
        assertSame(first, duringFetch, "the caller waits on the fetch already running");

        fetches.get(0).complete(payloadWithBlousyBuild("HADAL"));
        assertEquals(2, fetches.size(), "the queued refresh starts once the first one lands");
        assertEquals(Set.of("HADAL"), store.profileForUuid(BLOUSY_UUID).buildKeys());

        fetches.get(1).complete(payloadWithBlousyBuild("ASCENDANCY"));
        assertEquals(Set.of("ASCENDANCY"), store.profileForUuid(BLOUSY_UUID).buildKeys());
        assertFalse(store.isFetching());
        assertEquals(2, fetches.size(), "and nothing runs after that");
    }

    @Test
    void aQueuedRefreshStillRunsWhenTheFetchAheadOfItFails(@TempDir Path directory) {
        List<CompletableFuture<RaidProfilesResponse>> fetches = new ArrayList<>();
        RaidProfileStore store = storeWithManualFetches(directory, fetches);
        store.load();

        store.refresh();
        store.refresh();
        fetches.get(0).completeExceptionally(new IllegalStateException("timed out"));

        assertEquals(2, fetches.size(), "a failure ahead of it must not swallow the queued refresh");
        fetches.get(1).complete(payloadWithBlousyBuild("ASCENDANCY"));
        assertEquals(Set.of("ASCENDANCY"), store.profileForUuid(BLOUSY_UUID).buildKeys());
        assertNull(store.lastError(), "the success clears the earlier failure");
    }

    @Test
    void aFetchThatFailsImmediatelyDoesNotLeaveTheStoreStuck(@TempDir Path directory) {
        // A separate raid-profiles backend in its sign-in cooldown answers with an
        // already failed future, so completion happens inside refresh() itself.
        AtomicInteger calls = new AtomicInteger();
        RaidProfileStore store = new RaidProfileStore(
                directory.resolve("raid-profile.json"), directory.resolve("cache/raid-profiles.json"), () -> {
                    calls.incrementAndGet();
                    return CompletableFuture.failedFuture(new IllegalStateException("not linked"));
                });
        store.load();

        store.refresh().join();
        store.refresh().join();

        assertFalse(store.isFetching());
        assertEquals(2, calls.get(), "each refresh gets its own attempt rather than a stuck flag");
        assertNotNull(store.lastError());
    }

    @Test
    void aFetchThatThrowsInsteadOfFailingDoesNotLeaveTheStoreStuck(@TempDir Path directory) {
        AtomicInteger calls = new AtomicInteger();
        RaidProfileStore store = new RaidProfileStore(
                directory.resolve("raid-profile.json"), directory.resolve("cache/raid-profiles.json"), () -> {
                    calls.incrementAndGet();
                    throw new IllegalStateException("thrown before any future existed");
                });
        store.load();

        store.refresh().join();
        store.refresh().join();

        assertFalse(store.isFetching());
        assertEquals(2, calls.get());
        assertNotNull(store.lastError());
    }

    @Test
    void savingTheSameNoteAgainDoesNotRewriteTheFile(@TempDir Path directory) throws Exception {
        Path local = directory.resolve("raid-profile.json");
        RaidProfileStore store = new RaidProfileStore(
                local, directory.resolve("cache/raid-profiles.json"), () -> CompletableFuture.completedFuture(null));
        store.load();
        store.setNote("Visroul", "solid tna aco");
        java.nio.file.attribute.FileTime written = Files.getLastModifiedTime(local);
        Files.setLastModifiedTime(local, java.nio.file.attribute.FileTime.fromMillis(written.toMillis() - 60_000L));
        java.nio.file.attribute.FileTime backdated = Files.getLastModifiedTime(local);

        store.setNote("visroul", "  solid tna aco ");

        assertEquals(backdated, Files.getLastModifiedTime(local), "an unchanged note is not written again");
        assertEquals("solid tna aco", store.noteFor("VISROUL"));
    }

    @Test
    void aCorruptCacheIsSurvivable(@TempDir Path directory) throws Exception {
        Path cache = directory.resolve("cache/raid-profiles.json");
        Files.createDirectories(cache.getParent());
        Files.writeString(cache, "{ not json at all");

        RaidProfileStore store = store(directory);
        store.load();

        assertTrue(store.catalog().isEmpty(), "a broken cache is ignored rather than fatal");
        assertTrue(store.isLoaded());
    }

    // ── Live updates over the WebSocket ──

    @Test
    void anUpdatePushAppliesTheProfile(@TempDir Path directory) {
        RaidProfileStore store = store(directory);
        store.load();
        store.refresh().join();

        store.onLiveUpdate(new ConnectionManager.RaidProfileUpdateMessage(
                "updated",
                JsonParser.parseString(
                                """
                                {"minecraft": {"uuid": "10000000-0000-0000-0000-000000000002",
                                               "username": "Blousy"},
                                 "builds": ["ASCENDANCY"], "can_bring_auras": true,
                                 "region": "NA", "status": "back on",
                                 "updated_at": "2026-09-05T10:00:00Z"}
                                """)
                        .getAsJsonObject()));

        RaidTeamProfile updated = store.profileForUuid(BLOUSY_UUID);
        assertEquals(Set.of("ASCENDANCY"), updated.buildKeys());
        assertEquals("back on", updated.status());
    }

    @Test
    void aPushIsIgnoredWhenRaidProfilesLiveOnAnotherBackend(@TempDir Path directory) {
        RaidProfileStore store = new RaidProfileStore(
                directory.resolve("raid-profile.json"),
                directory.resolve("cache/raid-profiles.json"),
                () -> CompletableFuture.completedFuture(
                        RaidProfileStore.parseProfilesResponse(BACKEND_PAYLOAD).orElse(null)),
                () -> true);
        store.load();
        store.refresh().join();
        RaidTeamProfile before = store.profileForUuid(BLOUSY_UUID);

        store.onLiveUpdate(new ConnectionManager.RaidProfileUpdateMessage(
                "updated",
                JsonParser.parseString(
                                """
                                {"minecraft": {"uuid": "10000000-0000-0000-0000-000000000002",
                                               "username": "Blousy"},
                                 "builds": ["ASCENDANCY"], "can_bring_auras": true,
                                 "region": "NA", "status": "back on",
                                 "updated_at": "2026-09-05T10:00:00Z"}
                                """)
                        .getAsJsonObject()));

        assertEquals(before, store.profileForUuid(BLOUSY_UUID),
                "the socket speaks for the main backend, not the one profiles were fetched from");
    }

    @Test
    void aRemovalPushIsNotStoredAsAnEmptyProfile(@TempDir Path directory) {
        RaidProfileStore store = store(directory);
        store.load();
        store.refresh().join();
        assertTrue(store.profileForUuid(BLOUSY_UUID).isComplete());

        // A removal carries a profile-shaped payload with null fields. Applying it
        // would read as "shared an empty profile" rather than "shared none".
        store.onLiveUpdate(new ConnectionManager.RaidProfileUpdateMessage(
                "removed",
                JsonParser.parseString(
                                """
                                {"minecraft": {"uuid": "10000000-0000-0000-0000-000000000002",
                                               "username": "BlousyTheSecond"},
                                 "builds": null, "can_bring_auras": false,
                                 "region": null, "status": null, "updated_at": null}
                                """)
                        .getAsJsonObject()));

        assertFalse(store.profileForUuid(BLOUSY_UUID).isComplete());
        assertFalse(store.profileForUsername("Blousy").isComplete(), "the name index is cleared too");
        assertEquals(0, store.sharedProfileCount());
    }

    @Test
    void aRemovalIsMatchedByUuidNotByTheNameItCarries(@TempDir Path directory) {
        RaidProfileStore store = store(directory);
        store.load();
        store.refresh().join();

        // The event names the member as they are now, which is not the name the
        // panel stored them under. The uuid is what makes them the same person.
        store.onLiveUpdate(new ConnectionManager.RaidProfileUpdateMessage(
                "removed",
                JsonParser.parseString(
                                "{\"minecraft\": {\"uuid\": \"10000000-0000-0000-0000-000000000002\","
                                        + " \"username\": \"SomeoneQuiteDifferent\"}}")
                        .getAsJsonObject()));

        assertFalse(store.profileForUuid(BLOUSY_UUID).isComplete());
    }

    @Test
    void aPushWithNoUuidIsIgnoredRatherThanGuessed(@TempDir Path directory) {
        RaidProfileStore store = store(directory);
        store.load();
        store.refresh().join();

        store.onLiveUpdate(new ConnectionManager.RaidProfileUpdateMessage(
                "removed",
                JsonParser.parseString("{\"minecraft\": {\"username\": \"Blousy\"}}").getAsJsonObject()));
        store.onLiveUpdate(null);

        assertTrue(store.profileForUuid(BLOUSY_UUID).isComplete(), "nothing was dropped on a guess");
    }

    // ── Setup gating ──

    @Test
    void setupIsNeededUntilTheBackendHoldsAProfileForYou(@TempDir Path directory) {
        RaidProfileStore store = store(directory);
        store.load();

        // No local player in a test, so nothing resolves as "you" and setup is offered.
        assertTrue(store.needsSetup());

        store.dismissSetup();

        assertFalse(store.needsSetup(), "skipping is remembered");
    }

    @Test
    void skippingSetupSurvivesARestart(@TempDir Path directory) {
        RaidProfileStore store = store(directory);
        store.load();
        store.dismissSetup();

        RaidProfileStore reloaded = store(directory);
        reloaded.load();

        assertFalse(reloaded.needsSetup());
    }

    // ── Friends ──

    @Test
    void friendsToggleOnAndOffAndSurviveARestart(@TempDir Path directory) {
        RaidProfileStore store = store(directory);
        store.load();

        store.toggleFriend("Blousy");
        store.toggleFriend("a3pki");

        assertTrue(store.isFriend("blousy"), "friendship ignores case");
        assertEquals(List.of("Blousy", "a3pki"), store.friends());

        RaidProfileStore reloaded = store(directory);
        reloaded.load();
        assertEquals(List.of("Blousy", "a3pki"), reloaded.friends());

        reloaded.toggleFriend("BLOUSY");
        assertFalse(reloaded.isFriend("Blousy"));
        assertEquals(List.of("a3pki"), reloaded.friends());
    }

    // ── Premade parties ──

    @Test
    void premadesSurviveARestart(@TempDir Path directory) {
        RaidProfileStore store = store(directory);
        store.load();

        store.savePremade(
                PremadeParty.named("TNA core").withMembersAdded(List.of("Blousy", "a3pki", "divvy")), null);

        RaidProfileStore reloaded = store(directory);
        reloaded.load();

        assertEquals(1, reloaded.premades().size());
        PremadeParty party = reloaded.premade("tna core");
        assertNotNull(party);
        assertEquals(List.of("Blousy", "a3pki", "divvy"), party.members());
        assertTrue(party.updatedAtEpochMs() > 0L, "saving stamps the time");
    }

    @Test
    void savingUnderTheSameNameReplacesRatherThanDuplicates(@TempDir Path directory) {
        RaidProfileStore store = store(directory);
        store.load();

        store.savePremade(PremadeParty.named("core").withMemberToggled("Blousy"), null);
        store.savePremade(PremadeParty.named("CORE").withMemberToggled("a3pki"), null);

        assertEquals(1, store.premades().size(), "the name matches regardless of case");
        assertEquals(List.of("a3pki"), store.premade("core").members());
    }

    @Test
    void renamingAPartyDoesNotLeaveTheOldOneBehind(@TempDir Path directory) {
        RaidProfileStore store = store(directory);
        store.load();
        store.savePremade(PremadeParty.named("core").withMemberToggled("Blousy"), null);

        store.savePremade(PremadeParty.named("TNA core").withMemberToggled("Blousy"), "core");

        assertEquals(1, store.premades().size());
        assertNull(store.premade("core"));
        assertNotNull(store.premade("TNA core"));
    }

    @Test
    void anUnusablePartyIsNotSaved(@TempDir Path directory) {
        RaidProfileStore store = store(directory);
        store.load();

        store.savePremade(PremadeParty.named("no members"), null);
        store.savePremade(PremadeParty.named("").withMemberToggled("Blousy"), null);
        store.savePremade(null, null);

        assertTrue(store.premades().isEmpty());
    }

    @Test
    void deletingRemovesTheParty(@TempDir Path directory) {
        RaidProfileStore store = store(directory);
        store.load();
        store.savePremade(PremadeParty.named("core").withMemberToggled("Blousy"), null);

        store.deletePremade("CORE");

        assertTrue(store.premades().isEmpty());
    }

    // ── Notes ──

    @Test
    void aBlankNoteRemovesTheNote(@TempDir Path directory) {
        RaidProfileStore store = store(directory);
        store.load();

        store.setNote("blousy", "good healer");
        assertEquals("good healer", store.noteFor("blousy"));

        store.setNote("blousy", "   ");
        assertNull(store.noteFor("blousy"));
    }

    @Test
    void notesAreCappedSoTheyStayOneLine(@TempDir Path directory) {
        RaidProfileStore store = store(directory);
        store.load();

        store.setNote("blousy", "y".repeat(RaidProfileStore.MAX_NOTE_LENGTH + 50));

        assertEquals(RaidProfileStore.MAX_NOTE_LENGTH, store.noteFor("blousy").length());
    }

    @Test
    void personalDataStaysOutOfTheCache(@TempDir Path directory) throws Exception {
        RaidProfileStore store = store(directory);
        store.load();
        store.refresh().join();
        store.setNote("blousy", "solid tna aco");
        store.toggleFriend("Blousy");

        String cache = Files.readString(directory.resolve("cache/raid-profiles.json"));

        assertFalse(cache.contains("solid tna aco"), "a private note must never reach the shared cache");
        assertFalse(cache.contains("friends"));
    }
}
