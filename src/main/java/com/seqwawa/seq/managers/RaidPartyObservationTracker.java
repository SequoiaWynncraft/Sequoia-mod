package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.network.ConnectionManager;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Keeps a recent Wynn party snapshot and reports it when a raid completion is detected.
 */
public final class RaidPartyObservationTracker {
    private static final long SNAPSHOT_INTERVAL_MS = 2_000;
    private static final Duration DUPLICATE_REPORT_WINDOW = Duration.ofMinutes(1);
    private static final int MAX_RAID_PARTY_MEMBERS = 4;
    private static final Pattern MC_USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Set<String> KNOWN_RAID_NAMES = Set.of(
            "nest of the grootslangs",
            "the nameless anomaly",
            "the canyon colossus",
            "nexus of light",
            "the orphion's nexus of light",
            "the orphions nexus of light",
            "the wartorn palace");

    private static List<String> latestPartyUsernames = List.of();
    private static long lastSnapshotAtMs;
    private static String lastReportKey;
    private static Instant lastReportAt = Instant.EPOCH;

    private RaidPartyObservationTracker() {}

    public static void tick() {
        long now = System.currentTimeMillis();
        if (now - lastSnapshotAtMs < SNAPSHOT_INTERVAL_MS) {
            return;
        }

        lastSnapshotAtMs = now;
        latestPartyUsernames = collectCurrentPartyUsernames();
    }

    public static void onRaidCompleted(String raidName) {
        String safeRaidName = sanitizeRaidName(raidName);
        if (safeRaidName == null) {
            SeqClient.LOGGER.warn("[RaidPartyObservation] Skipping report with unknown raid='{}'", raidName);
            return;
        }

        List<String> partyUsernames = latestPartyUsernames.isEmpty()
                ? collectCurrentPartyUsernames()
                : latestPartyUsernames;
        if (partyUsernames.isEmpty()) {
            SeqClient.LOGGER.warn("[RaidPartyObservation] Skipping {} report: no party usernames resolved", safeRaidName);
            return;
        }
        if (partyUsernames.size() > MAX_RAID_PARTY_MEMBERS) {
            SeqClient.LOGGER.warn(
                    "[RaidPartyObservation] Skipping {} report: too many party usernames {}",
                    safeRaidName,
                    partyUsernames);
            return;
        }

        if (isDuplicateReport(safeRaidName, partyUsernames)) {
            SeqClient.LOGGER.debug(
                    "[RaidPartyObservation] Skipping duplicate {} report usernames={}",
                    safeRaidName,
                    partyUsernames);
            return;
        }

        ConnectionManager instance = ConnectionManager.getInstance();
        boolean sent = instance.sendRaidPartyObservation(safeRaidName, partyUsernames);
        if (sent) {
            rememberReport(safeRaidName, partyUsernames);
            SeqClient.LOGGER.info(
                    "[RaidPartyObservation] Sent raid party observation raid='{}' usernames={}",
                    safeRaidName,
                    partyUsernames);
        }
    }

    public static void reset() {
        latestPartyUsernames = List.of();
        lastSnapshotAtMs = 0;
        lastReportKey = null;
        lastReportAt = Instant.EPOCH;
    }

    private static List<String> collectCurrentPartyUsernames() {
        LinkedHashSet<String> usernames = new LinkedHashSet<>();

        for (WynnPartyScoreboardReader.PartyHealth member : WynnPartyScoreboardReader.readPartyHealth()) {
            addUsername(usernames, member.username());
        }

        WynnPartySyncManager syncManager = SeqClient.getWynnPartySyncManager();
        if (syncManager != null) {
            for (String username : syncManager.getObservedMemberUsernames()) {
                addUsername(usernames, username);
            }
        }

        return List.copyOf(usernames);
    }

    private static void addUsername(Set<String> usernames, String username) {
        String safeUsername = sanitizeUsername(username);
        if (safeUsername != null) {
            usernames.add(safeUsername);
        }
    }

    private static String sanitizeUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        String trimmed = username.trim();
        return MC_USERNAME_PATTERN.matcher(trimmed).matches() ? trimmed : null;
    }

    private static String sanitizeRaidName(String raidName) {
        if (raidName == null || raidName.isBlank()) {
            return null;
        }
        String trimmed = raidName.trim().replaceAll("\\s+", " ");
        return KNOWN_RAID_NAMES.contains(trimmed.toLowerCase(Locale.ROOT)) ? trimmed : null;
    }

    private static boolean isDuplicateReport(String raidName, List<String> partyUsernames) {
        Instant now = Instant.now();
        String key = reportKey(raidName, partyUsernames);
        return key.equals(lastReportKey)
                && Duration.between(lastReportAt, now).compareTo(DUPLICATE_REPORT_WINDOW) < 0;
    }

    private static void rememberReport(String raidName, List<String> partyUsernames) {
        lastReportKey = reportKey(raidName, partyUsernames);
        lastReportAt = Instant.now();
    }

    private static String reportKey(String raidName, List<String> partyUsernames) {
        return raidName.toLowerCase(Locale.ROOT) + "|" + String.join(",", lowerCase(partyUsernames));
    }

    private static List<String> lowerCase(List<String> values) {
        return values.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
    }
}
