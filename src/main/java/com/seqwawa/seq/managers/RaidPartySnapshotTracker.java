package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Maintains a recent canonical party snapshot for raid-completion name resolution. */
public final class RaidPartySnapshotTracker {
    private static final long SNAPSHOT_INTERVAL_MS = 2_000;
    private static final long SNAPSHOT_MAX_AGE_MS = 5_000;
    private static final int MAX_RAID_PARTY_MEMBERS = 4;
    private static final Pattern MC_USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private static List<String> latestPartyUsernames = List.of();
    private static long lastSnapshotAtMs;

    private RaidPartySnapshotTracker() {}

    public static void tick() {
        long now = System.currentTimeMillis();
        if (now - lastSnapshotAtMs < SNAPSHOT_INTERVAL_MS) {
            return;
        }
        latestPartyUsernames = collectCurrentPartyUsernames();
        lastSnapshotAtMs = now;
    }

    public static List<String> resolvePartyMembers(List<String> parsedPartyMembers, int displayedPartySize) {
        long now = System.currentTimeMillis();
        List<String> snapshot = now - lastSnapshotAtMs <= SNAPSHOT_MAX_AGE_MS
                ? latestPartyUsernames
                : collectCurrentPartyUsernames();
        return choosePartyMembers(parsedPartyMembers, snapshot, displayedPartySize, localUsername());
    }

    static List<String> choosePartyMembers(
            List<String> parsedPartyMembers,
            List<String> snapshotPartyMembers,
            int displayedPartySize,
            String localUsername) {
        List<String> parsed = sanitizeParty(parsedPartyMembers);
        List<String> snapshot = sanitizeParty(snapshotPartyMembers);
        String local = sanitizeUsername(localUsername);

        if (displayedPartySize < 1
                || displayedPartySize > MAX_RAID_PARTY_MEMBERS
                || snapshot.size() != displayedPartySize
                || local == null
                || snapshot.stream().noneMatch(local::equalsIgnoreCase)) {
            return parsed;
        }
        return snapshot;
    }

    public static void reset() {
        latestPartyUsernames = List.of();
        lastSnapshotAtMs = 0;
    }

    private static List<String> collectCurrentPartyUsernames() {
        List<String> usernames = new ArrayList<>();
        for (WynnPartyScoreboardReader.PartyHealth member : WynnPartyScoreboardReader.readPartyHealth()) {
            usernames.add(member.username());
        }

        WynnPartySyncManager syncManager = SeqClient.getWynnPartySyncManager();
        if (syncManager != null) {
            usernames.addAll(syncManager.getObservedMemberUsernames());
        }
        return sanitizeParty(usernames);
    }

    private static List<String> sanitizeParty(List<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return List.of();
        }
        Map<String, String> distinct = new LinkedHashMap<>();
        for (String username : usernames) {
            String safeUsername = sanitizeUsername(username);
            if (safeUsername != null) {
                distinct.putIfAbsent(safeUsername.toLowerCase(Locale.ROOT), safeUsername);
            }
        }
        return List.copyOf(distinct.values());
    }

    private static String sanitizeUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        String trimmed = username.trim();
        return MC_USERNAME_PATTERN.matcher(trimmed).matches() ? trimmed : null;
    }

    private static String localUsername() {
        if (SeqClient.mc != null && SeqClient.mc.getUser() != null) {
            return sanitizeUsername(SeqClient.mc.getUser().getName());
        }
        if (SeqClient.mc != null && SeqClient.mc.player != null) {
            return sanitizeUsername(SeqClient.mc.player.getName().getString());
        }
        return null;
    }
}
