package com.seqwawa.seq.managers;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.SeqBadge;
import com.seqwawa.seq.network.auth.StoredAuthSession;

final class SeqBadgePlayerResolver {
    private SeqBadgePlayerResolver() {}

    static List<SeqBadge> resolve(
            LeaderboardBadgeService badgeService,
            UUID primaryUuid,
            UUID secondaryUuid,
            boolean localPlayer) {
        return resolve(
                badgeService::badgesFor,
                primaryUuid,
                secondaryUuid,
                localPlayer,
                SeqBadgePlayerResolver::currentStoredAuthSession,
                SeqBadgePlayerResolver::currentLauncherUuid);
    }

    static List<SeqBadge> resolve(
            Function<UUID, List<SeqBadge>> badgesFor,
            UUID primaryUuid,
            UUID secondaryUuid,
            boolean localPlayer,
            Supplier<StoredAuthSession> sessionSupplier,
            Supplier<UUID> launcherUuidSupplier) {
        List<SeqBadge> badges = badgesFor(badgesFor, primaryUuid);
        if (!badges.isEmpty()) {
            return badges;
        }

        if (secondaryUuid != null && !secondaryUuid.equals(primaryUuid)) {
            badges = badgesFor(badgesFor, secondaryUuid);
            if (!badges.isEmpty()) {
                return badges;
            }
        }

        if (!localPlayer) {
            return List.of();
        }

        StoredAuthSession session = sessionSupplier.get();
        UUID authUuid = parseUuid(session == null ? null : session.minecraftUuid());
        badges = badgesFor(badgesFor, authUuid);
        if (!badges.isEmpty()) {
            return badges;
        }

        return badgesFor(badgesFor, launcherUuidSupplier.get());
    }

    private static StoredAuthSession currentStoredAuthSession() {
        return SeqClient.getConfigManager().getStoredAuthSession();
    }

    private static UUID currentLauncherUuid() {
        return SeqClient.mc != null && SeqClient.mc.getUser() != null
                ? SeqClient.mc.getUser().getProfileId()
                : null;
    }

    private static List<SeqBadge> badgesFor(
            Function<UUID, List<SeqBadge>> badgesFor,
            UUID uuid) {
        List<SeqBadge> badges = badgesFor.apply(uuid);
        return badges == null ? List.of() : badges;
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
