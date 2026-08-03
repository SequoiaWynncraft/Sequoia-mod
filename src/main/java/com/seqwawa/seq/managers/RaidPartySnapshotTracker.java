package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Maintains one party roster for the complete lifetime of a raid. */
public final class RaidPartySnapshotTracker {
    private static final long SNAPSHOT_INTERVAL_MS = 2_000;
    static final long HANDOFF_RETENTION_MS = 90_000;
    static final long RAID_SIDEBAR_MISSING_GRACE_MS = 15_000;
    static final long RAID_ACQUISITION_WINDOW_MS = 6_000;
    private static final int DEFAULT_MAX_RAID_PARTY_MEMBERS = 4;
    private static final int ABSOLUTE_MAX_RAID_PARTY_MEMBERS = 10;
    private static final Pattern MC_USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private static volatile TrackerState state = TrackerState.empty();
    private static long lastPollAtMs;

    private RaidPartySnapshotTracker() {}

    public static synchronized void tick() {
        long now = System.currentTimeMillis();
        if (now - lastPollAtMs < SNAPSHOT_INTERVAL_MS) {
            return;
        }
        captureSnapshot(now);
    }

    public static synchronized void refreshNow() {
        captureSnapshot(System.currentTimeMillis());
    }

    private static void captureSnapshot(long now) {
        lastPollAtMs = now;

        WynnPartyScoreboardReader.PartyObservation observation = WynnPartyScoreboardReader.readPartyObservation();
        PartySnapshot observedSnapshot = collectCurrentPartySnapshot(
                observation.members(),
                observation.canonicalRosterUsernames(),
                observation.raidSidebarActive(),
                now);
        TrackerState previous = state;
        state = state.observe(
                observedSnapshot,
                observation.partySidebarActive(),
                observation.raidSidebarActive(),
                now);
        if (previous.phase() != state.phase()) {
            SeqClient.LOGGER.debug(
                    "[RaidPartySnapshot] phase={} -> {} candidateMembers={} activeMembers={}",
                    previous.phase(),
                    state.phase(),
                    state.candidateParty().usernames().size(),
                    state.activeRaidParty().usernames().size());
        }
    }

    /** Preserve raid handoff state during short Wynncraft world/server transfers. */
    public static synchronized void onServerUnavailable() {
        state = state.serverUnavailable(System.currentTimeMillis());
    }

    static synchronized void onServerUnavailable(long now) {
        state = state.serverUnavailable(now);
    }

    /** Party chat events invalidate only the mutable pre-raid candidate. */
    public static synchronized void onPartyChanged() {
        state = state.clearCandidate();
    }

    /** Called after the completion announcement has consumed the locked roster. */
    public static synchronized void onRaidCompleted() {
        state = state.finishRaid();
    }

    /** Called from the vanilla raid-failure title packet. */
    public static synchronized void onRaidFailed() {
        state = state.finishRaid();
    }

    public static List<String> resolvePartyMembers(List<String> parsedPartyMembers, int displayedPartySize) {
        return resolvePartyMembers(parsedPartyMembers, displayedPartySize, DEFAULT_MAX_RAID_PARTY_MEMBERS);
    }

    public static List<String> resolvePartyMembers(
            List<String> parsedPartyMembers, int displayedPartySize, int maximumPartySize) {
        TrackerState currentState = state;
        PartySnapshot snapshot = currentState.activeRaidParty();
        List<String> resolved =
                choosePartyMembers(parsedPartyMembers, snapshot, displayedPartySize, maximumPartySize);
        SeqClient.LOGGER.info(
                "[RaidPartySnapshot] resolution={} phase={} displayedCount={} maxPartySize={} activeDurationMs={} snapshotMembers={} parsedMembers={} resolvedMembers={}",
                resolutionSource(parsedPartyMembers, resolved, snapshot, maximumPartySize),
                currentState.phase(),
                displayedPartySize,
                maximumPartySize,
                snapshot.capturedAtMs() == 0 ? -1 : System.currentTimeMillis() - snapshot.capturedAtMs(),
                snapshot.usernames(),
                parsedPartyMembers,
                resolved);
        return resolved;
    }

    static List<String> choosePartyMembers(
            List<String> parsedPartyMembers,
            List<SnapshotMember> snapshotPartyMembers,
            int displayedPartySize) {
        return choosePartyMembers(
                parsedPartyMembers,
                PartySnapshot.from(snapshotPartyMembers, true, 0),
                displayedPartySize,
                DEFAULT_MAX_RAID_PARTY_MEMBERS);
    }

    static List<String> choosePartyMembers(
            List<String> parsedPartyMembers, PartySnapshot snapshot, int displayedPartySize) {
        return choosePartyMembers(
                parsedPartyMembers, snapshot, displayedPartySize, DEFAULT_MAX_RAID_PARTY_MEMBERS);
    }

    static List<String> choosePartyMembers(
            List<String> parsedPartyMembers,
            PartySnapshot snapshot,
            int displayedPartySize,
            int maximumPartySize) {
        List<String> parsed = sanitizeParty(parsedPartyMembers);
        int safeMaximumPartySize =
                Math.max(1, Math.min(maximumPartySize, ABSOLUTE_MAX_RAID_PARTY_MEMBERS));

        if (displayedPartySize < 1
                || displayedPartySize > safeMaximumPartySize
                || !snapshot.raidContext()
                || snapshot.usernames().isEmpty()
                || snapshot.usernames().size() > safeMaximumPartySize) {
            return parsed;
        }

        List<String> resolved = new ArrayList<>(parsed.size());
        for (String parsedMember : parsed) {
            resolved.add(snapshot.resolveAlias(parsedMember));
        }
        boolean allParsedMembersCompatible = !resolved.isEmpty()
                && resolved.stream()
                        .allMatch(member -> snapshot.usernames().stream().anyMatch(member::equalsIgnoreCase));
        if (!allParsedMembersCompatible) {
            return parsed;
        }
        if (snapshot.usernames().size() >= displayedPartySize) {
            return snapshot.usernames();
        }
        return sanitizeParty(resolved);
    }

    public static synchronized void reset() {
        state = TrackerState.empty();
        lastPollAtMs = 0;
    }

    static synchronized void setStateForTest(TrackerState testState) {
        state = testState;
        lastPollAtMs = 0;
    }

    static TrackerState stateForTest() {
        return state;
    }

    private static PartySnapshot collectCurrentPartySnapshot(
            List<WynnPartyScoreboardReader.PartyHealth> partyHealth,
            List<String> canonicalRosterUsernames,
            boolean raidContext,
            long capturedAtMs) {
        List<SnapshotMember> members = new ArrayList<>();
        for (WynnPartyScoreboardReader.PartyHealth member : partyHealth) {
            members.add(new SnapshotMember(member.nickname(), member.username()));
        }

        List<String> supplementalRoster = sanitizeParty(canonicalRosterUsernames);
        if (supplementalRoster.isEmpty()) {
            WynnPartySyncManager syncManager = SeqClient.getWynnPartySyncManager();
            List<String> syncUsernames =
                    syncManager != null ? syncManager.getObservedMemberUsernames() : List.of();
            supplementalRoster = compatibleSupplementalRoster(members, partyHealth.size(), syncUsernames);
        }
        for (String username : supplementalRoster) {
            members.add(new SnapshotMember(username, username));
        }
        return PartySnapshot.from(members, raidContext, capturedAtMs);
    }

    static List<String> compatibleSupplementalRoster(
            List<SnapshotMember> primaryMembers, int observedRowCount, List<String> supplementalUsernames) {
        List<String> supplemental = sanitizeParty(supplementalUsernames);
        if (observedRowCount < 1 || supplemental.size() != observedRowCount) {
            return List.of();
        }

        for (SnapshotMember primaryMember : primaryMembers) {
            String username = sanitizeUsername(primaryMember.username());
            if (username != null
                    && supplemental.stream().noneMatch(username::equalsIgnoreCase)) {
                return List.of();
            }
        }
        return supplemental;
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

    private static String resolutionSource(
            List<String> parsedPartyMembers,
            List<String> resolvedPartyMembers,
            PartySnapshot snapshot,
            int maximumPartySize) {
        if (snapshot.usernames().isEmpty()) {
            return "missing_snapshot";
        }
        if (!snapshot.raidContext()) {
            return "non_raid_snapshot";
        }
        if (snapshot.usernames().size() > maximumPartySize) {
            return "oversized_snapshot";
        }
        if (resolvedPartyMembers.equals(snapshot.usernames())) {
            return "complete_snapshot";
        }
        return resolvedPartyMembers.equals(sanitizeParty(parsedPartyMembers))
                ? "completion_message"
                : "snapshot_aliases";
    }

    private static PartySnapshot updateCandidate(
            PartySnapshot current, PartySnapshot observed, boolean partySidebarActive, long now) {
        if (!partySidebarActive || observed.usernames().isEmpty()) {
            return current.usernames().isEmpty() || now - current.capturedAtMs() <= HANDOFF_RETENTION_MS
                    ? current
                    : PartySnapshot.empty();
        }
        PartySnapshot normalObservation = observed.withRaidContext(false, now);
        if (current.usernames().isEmpty() || now - current.capturedAtMs() > HANDOFF_RETENTION_MS) {
            return normalObservation;
        }
        if (current.hasSameMembers(normalObservation)) {
            return current.mergeKnownAliases(normalObservation, now);
        }
        if (current.containsAllMembers(normalObservation)) {
            return current.mergeKnownAliases(normalObservation, now);
        }
        if (normalObservation.containsAllMembers(current)) {
            return normalObservation.mergeKnownAliases(current, now);
        }
        return normalObservation;
    }

    enum Phase {
        NORMAL,
        ACQUIRING,
        ACTIVE,
        FINISHED_WAITING_FOR_EXIT
    }

    record TrackerState(
            PartySnapshot candidateParty,
            PartySnapshot activeRaidParty,
            Phase phase,
            long acquisitionStartedAtMs,
            int stableRaidObservations,
            long raidSidebarMissingSinceMs,
            long serverUnavailableSinceMs) {

        static TrackerState empty() {
            return new TrackerState(
                    PartySnapshot.empty(), PartySnapshot.empty(), Phase.NORMAL, 0, 0, 0, 0);
        }

        TrackerState observe(
                PartySnapshot observed,
                boolean partySidebarActive,
                boolean raidSidebarActive,
                long now) {
            if (phase == Phase.FINISHED_WAITING_FOR_EXIT) {
                if (raidSidebarActive) {
                    return new TrackerState(
                            candidateParty,
                            activeRaidParty,
                            phase,
                            acquisitionStartedAtMs,
                            stableRaidObservations,
                            0,
                            0);
                }
                return empty().observe(observed, partySidebarActive, false, now);
            }

            if (phase == Phase.NORMAL) {
                if (raidSidebarActive) {
                    return startRaid(observed, now);
                }
                return new TrackerState(
                        updateCandidate(candidateParty, observed, partySidebarActive, now),
                        PartySnapshot.empty(),
                        Phase.NORMAL,
                        0,
                        0,
                        0,
                        0);
            }

            if (!raidSidebarActive) {
                long missingSince = raidSidebarMissingSinceMs == 0 ? now : raidSidebarMissingSinceMs;
                if (now - missingSince >= RAID_SIDEBAR_MISSING_GRACE_MS) {
                    return empty().observe(observed, partySidebarActive, false, now);
                }
                return new TrackerState(
                        candidateParty,
                        activeRaidParty,
                        phase,
                        acquisitionStartedAtMs,
                        stableRaidObservations,
                        missingSince,
                        0);
            }

            if (phase == Phase.ACTIVE) {
                return new TrackerState(
                        candidateParty,
                        activeRaidParty.mergeKnownAliases(observed, activeRaidParty.capturedAtMs()),
                        Phase.ACTIVE,
                        acquisitionStartedAtMs,
                        stableRaidObservations,
                        0,
                        0);
            }

            PartySnapshot nextActive = activeRaidParty;
            int nextStableCount = stableRaidObservations;
            if (!observed.usernames().isEmpty()) {
                boolean sameMembers = activeRaidParty.hasSameMembers(observed);
                nextActive = activeRaidParty.usernames().isEmpty()
                        ? observed.withRaidContext(true, now)
                        : activeRaidParty.unionMembers(observed, activeRaidParty.capturedAtMs());
                nextStableCount = sameMembers ? stableRaidObservations + 1 : 1;
            }
            boolean acquisitionExpired = !nextActive.usernames().isEmpty()
                    && now - acquisitionStartedAtMs >= RAID_ACQUISITION_WINDOW_MS;
            boolean locked = acquisitionExpired;
            return new TrackerState(
                    candidateParty,
                    nextActive,
                    locked ? Phase.ACTIVE : Phase.ACQUIRING,
                    acquisitionStartedAtMs,
                    nextStableCount,
                    0,
                    0);
        }

        private TrackerState startRaid(PartySnapshot observed, long now) {
            boolean candidateIsRecent = !candidateParty.usernames().isEmpty()
                    && now - candidateParty.capturedAtMs() <= HANDOFF_RETENTION_MS;
            if (candidateIsRecent) {
                return new TrackerState(
                        candidateParty,
                        candidateParty.withRaidContext(true, now),
                        Phase.ACTIVE,
                        now,
                        0,
                        0,
                        0);
            }
            PartySnapshot initial = observed.usernames().isEmpty()
                    ? PartySnapshot.empty()
                    : observed.withRaidContext(true, now);
            return new TrackerState(
                    PartySnapshot.empty(),
                    initial,
                    Phase.ACQUIRING,
                    now,
                    initial.usernames().isEmpty() ? 0 : 1,
                    0,
                    0);
        }

        TrackerState clearCandidate() {
            return new TrackerState(
                    PartySnapshot.empty(),
                    activeRaidParty,
                    phase,
                    acquisitionStartedAtMs,
                    stableRaidObservations,
                    raidSidebarMissingSinceMs,
                    serverUnavailableSinceMs);
        }

        TrackerState finishRaid() {
            return new TrackerState(
                    PartySnapshot.empty(),
                    PartySnapshot.empty(),
                    Phase.FINISHED_WAITING_FOR_EXIT,
                    0,
                    0,
                    0,
                    serverUnavailableSinceMs);
        }

        TrackerState serverUnavailable(long now) {
            long unavailableSince = serverUnavailableSinceMs == 0 ? now : serverUnavailableSinceMs;
            if (now - unavailableSince >= HANDOFF_RETENTION_MS) {
                return empty();
            }
            return new TrackerState(
                    candidateParty,
                    activeRaidParty,
                    phase,
                    acquisitionStartedAtMs,
                    stableRaidObservations,
                    raidSidebarMissingSinceMs,
                    unavailableSince);
        }
    }

    record SnapshotMember(String displayedName, String username) {}

    record PartySnapshot(List<String> usernames, Map<String, String> aliases, boolean raidContext, long capturedAtMs) {
        PartySnapshot {
            usernames = List.copyOf(usernames);
            aliases = Map.copyOf(aliases);
        }

        static PartySnapshot from(List<SnapshotMember> members, long capturedAtMs) {
            return from(members, false, capturedAtMs);
        }

        static PartySnapshot from(List<SnapshotMember> members, boolean raidContext, long capturedAtMs) {
            Map<String, String> usernames = new LinkedHashMap<>();
            Map<String, String> aliases = new LinkedHashMap<>();
            Set<String> ambiguousAliases = new HashSet<>();

            for (SnapshotMember member : members) {
                String username = sanitizeUsername(member.username());
                if (username == null) {
                    continue;
                }

                usernames.putIfAbsent(username.toLowerCase(Locale.ROOT), username);
                addAlias(aliases, ambiguousAliases, member.displayedName(), username);
                addAlias(aliases, ambiguousAliases, username, username);
            }
            return new PartySnapshot(List.copyOf(usernames.values()), aliases, raidContext, capturedAtMs);
        }

        private static void addAlias(
                Map<String, String> aliases, Set<String> ambiguousAliases, String displayedName, String username) {
            String alias = sanitizeUsername(displayedName);
            if (alias == null) {
                return;
            }

            String key = alias.toLowerCase(Locale.ROOT);
            if (ambiguousAliases.contains(key)) {
                return;
            }
            String existing = aliases.putIfAbsent(key, username);
            if (existing != null && !existing.equalsIgnoreCase(username)) {
                aliases.remove(key);
                ambiguousAliases.add(key);
            }
        }

        private String resolveAlias(String displayedName) {
            String resolved = aliases.get(displayedName.toLowerCase(Locale.ROOT));
            return resolved != null ? resolved : displayedName;
        }

        private boolean hasSameMembers(PartySnapshot other) {
            if (usernames.size() != other.usernames.size()) {
                return false;
            }
            return usernames.stream()
                    .allMatch(username -> other.usernames.stream().anyMatch(username::equalsIgnoreCase));
        }

        private boolean containsAllMembers(PartySnapshot other) {
            return usernames.size() > other.usernames.size()
                    && other.usernames.stream()
                            .allMatch(username -> usernames.stream().anyMatch(username::equalsIgnoreCase));
        }

        private boolean containsUsername(String username) {
            return usernames.stream().anyMatch(username::equalsIgnoreCase);
        }

        private PartySnapshot withRaidContext(boolean nextRaidContext, long nextCapturedAtMs) {
            return new PartySnapshot(usernames, aliases, nextRaidContext, nextCapturedAtMs);
        }

        private PartySnapshot mergeKnownAliases(PartySnapshot other, long nextCapturedAtMs) {
            Map<String, String> mergedAliases = new LinkedHashMap<>(aliases);
            for (Map.Entry<String, String> alias : other.aliases.entrySet()) {
                if (!containsUsername(alias.getValue())) {
                    continue;
                }
                String existing = mergedAliases.get(alias.getKey());
                if (existing == null) {
                    mergedAliases.put(alias.getKey(), alias.getValue());
                } else if (!existing.equalsIgnoreCase(alias.getValue())) {
                    mergedAliases.remove(alias.getKey());
                }
            }
            return new PartySnapshot(usernames, mergedAliases, raidContext, nextCapturedAtMs);
        }

        private PartySnapshot unionMembers(PartySnapshot other, long nextCapturedAtMs) {
            Map<String, String> mergedMembers = new LinkedHashMap<>();
            for (String username : usernames) {
                mergedMembers.put(username.toLowerCase(Locale.ROOT), username);
            }
            for (String username : other.usernames) {
                if (mergedMembers.size() >= ABSOLUTE_MAX_RAID_PARTY_MEMBERS) {
                    break;
                }
                mergedMembers.putIfAbsent(username.toLowerCase(Locale.ROOT), username);
            }
            PartySnapshot withMembers = new PartySnapshot(
                    List.copyOf(mergedMembers.values()), aliases, true, nextCapturedAtMs);
            return withMembers.mergeKnownAliases(other, nextCapturedAtMs);
        }

        private static PartySnapshot empty() {
            return new PartySnapshot(List.of(), Map.of(), false, 0);
        }
    }
}
