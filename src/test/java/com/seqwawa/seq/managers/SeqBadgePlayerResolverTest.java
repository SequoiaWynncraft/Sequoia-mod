package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import com.seqwawa.seq.model.SeqBadge;
import com.seqwawa.seq.model.SeqBadgeType;
import com.seqwawa.seq.model.SeqBadgeTier;
import com.seqwawa.seq.network.auth.StoredAuthSession;

class SeqBadgePlayerResolverTest {
    private static final UUID PRIMARY_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECONDARY_UUID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID AUTH_UUID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID LAUNCHER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Test
    void prefersPrimaryUuid() {
        SeqBadge primaryBadge = new SeqBadge(SeqBadgeType.WTP, SeqBadgeTier.GOLD);
        TestBadgeLookup lookup = new TestBadgeLookup(Map.of(
                PRIMARY_UUID, List.of(primaryBadge),
                SECONDARY_UUID, List.of(new SeqBadge(SeqBadgeType.NOL, SeqBadgeTier.SILVER))));

        List<SeqBadge> badges = SeqBadgePlayerResolver.resolve(
                lookup::badgesFor,
                PRIMARY_UUID,
                SECONDARY_UUID,
                true,
                session(AUTH_UUID),
                () -> LAUNCHER_UUID);

        assertEquals(List.of(primaryBadge), badges);
        assertEquals(List.of(PRIMARY_UUID), lookup.calls());
    }

    @Test
    void fallsBackToSecondaryBeforeLocalIdentity() {
        SeqBadge secondaryBadge = new SeqBadge(SeqBadgeType.NOL, SeqBadgeTier.SILVER);
        TestBadgeLookup lookup = new TestBadgeLookup(Map.of(
                SECONDARY_UUID, List.of(secondaryBadge),
                AUTH_UUID, List.of(new SeqBadge(SeqBadgeType.WTP, SeqBadgeTier.DIAMOND))));

        List<SeqBadge> badges = SeqBadgePlayerResolver.resolve(
                lookup::badgesFor,
                PRIMARY_UUID,
                SECONDARY_UUID,
                true,
                session(AUTH_UUID),
                () -> LAUNCHER_UUID);

        assertEquals(List.of(secondaryBadge), badges);
        assertEquals(List.of(PRIMARY_UUID, SECONDARY_UUID), lookup.calls());
    }

    @Test
    void fallsBackToAuthThenLauncherForLocalPlayer() {
        SeqBadge launcherBadge = new SeqBadge(SeqBadgeType.WTP, SeqBadgeTier.BRONZE);
        TestBadgeLookup lookup = new TestBadgeLookup(Map.of(LAUNCHER_UUID, List.of(launcherBadge)));

        List<SeqBadge> badges = SeqBadgePlayerResolver.resolve(
                lookup::badgesFor,
                PRIMARY_UUID,
                SECONDARY_UUID,
                true,
                session(AUTH_UUID),
                () -> LAUNCHER_UUID);

        assertEquals(List.of(launcherBadge), badges);
        assertEquals(List.of(PRIMARY_UUID, SECONDARY_UUID, AUTH_UUID, LAUNCHER_UUID), lookup.calls());
    }

    @Test
    void doesNotUseLocalIdentityForRemotePlayer() {
        TestBadgeLookup lookup = new TestBadgeLookup(Map.of());

        List<SeqBadge> badges = SeqBadgePlayerResolver.resolve(
                lookup::badgesFor,
                PRIMARY_UUID,
                SECONDARY_UUID,
                false,
                throwingSessionSupplier(),
                () -> {
                    throw new AssertionError("launcher UUID should not be requested");
                });

        assertEquals(List.of(), badges);
        assertEquals(List.of(PRIMARY_UUID, SECONDARY_UUID), lookup.calls());
    }

    private static Supplier<StoredAuthSession> session(UUID uuid) {
        return () -> new StoredAuthSession("token", Instant.EPOCH, uuid.toString(), "Player");
    }

    private static Supplier<StoredAuthSession> throwingSessionSupplier() {
        return () -> {
            throw new AssertionError("auth session should not be requested");
        };
    }

    private static final class TestBadgeLookup {
        private final Map<UUID, List<SeqBadge>> badgesByUuid;
        private final List<UUID> calls = new ArrayList<>();

        private TestBadgeLookup(Map<UUID, List<SeqBadge>> badgesByUuid) {
            this.badgesByUuid = new HashMap<>(badgesByUuid);
        }

        private List<SeqBadge> badgesFor(UUID uuid) {
            calls.add(uuid);
            return badgesByUuid.getOrDefault(uuid, List.of());
        }

        private List<UUID> calls() {
            return List.copyOf(calls);
        }
    }
}
