package com.seqwawa.seq.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A group you run with often, saved by name so it can be pulled together in one
 * click instead of four invites typed out every time.
 * <p>
 * Members are stored as plain usernames, not as roster entries: a premade has to
 * survive the people in it being offline, which is the state they are usually in
 * when you go to invite them.
 */
public record PremadeParty(String name, List<String> members, long updatedAtEpochMs) {

    /** Wynncraft's largest raid group. Anything past this cannot all fit anyway. */
    public static final int MAX_MEMBERS = 10;

    public static final int MAX_NAME_LENGTH = 24;

    public PremadeParty {
        name = normalizeName(name);
        members = normalizeMembers(members);
    }

    public static PremadeParty named(String name) {
        return new PremadeParty(name, List.of(), 0L);
    }

    public boolean isFull() {
        return members.size() >= MAX_MEMBERS;
    }

    public boolean contains(String username) {
        if (username == null) {
            return false;
        }
        String key = username.trim().toLowerCase(Locale.ROOT);
        return members.stream().anyMatch(member -> member.toLowerCase(Locale.ROOT).equals(key));
    }

    /** Case-insensitive key, so renaming the display case does not create a duplicate. */
    public String key() {
        return name.toLowerCase(Locale.ROOT);
    }

    public PremadeParty withName(String value) {
        return new PremadeParty(value, members, updatedAtEpochMs);
    }

    /** Adds a member, or removes them when they are already in. */
    public PremadeParty withMemberToggled(String username) {
        if (username == null || username.isBlank()) {
            return this;
        }
        String trimmed = username.trim();
        if (contains(trimmed)) {
            String key = trimmed.toLowerCase(Locale.ROOT);
            List<String> remaining = members.stream()
                    .filter(member -> !member.toLowerCase(Locale.ROOT).equals(key))
                    .toList();
            return new PremadeParty(name, remaining, updatedAtEpochMs);
        }
        if (isFull()) {
            return this;
        }
        List<String> updated = new ArrayList<>(members);
        updated.add(trimmed);
        return new PremadeParty(name, updated, updatedAtEpochMs);
    }

    /** Adds several at once, skipping duplicates and stopping at the cap. */
    public PremadeParty withMembersAdded(List<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return this;
        }
        PremadeParty result = this;
        for (String username : usernames) {
            if (result.isFull()) {
                break;
            }
            if (username != null && !username.isBlank() && !result.contains(username)) {
                result = result.withMemberToggled(username);
            }
        }
        return result;
    }

    public PremadeParty savedAt(long epochMs) {
        return new PremadeParty(name, members, epochMs);
    }

    /** Whether this is worth saving: it needs a name and at least one member. */
    public boolean isUsable() {
        return !name.isBlank() && !members.isEmpty();
    }

    public String summary() {
        if (members.isEmpty()) {
            return "no members yet";
        }
        return String.join(", ", members);
    }

    private static String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= MAX_NAME_LENGTH ? trimmed : trimmed.substring(0, MAX_NAME_LENGTH);
    }

    private static List<String> normalizeMembers(List<String> value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        // A LinkedHashSet on the lowercase key keeps insertion order while making the
        // same person typed twice a single entry.
        Set<String> seen = new LinkedHashSet<>();
        List<String> normalized = new ArrayList<>();
        for (String member : value) {
            if (member == null) {
                continue;
            }
            String trimmed = member.trim();
            if (trimmed.isEmpty() || !seen.add(trimmed.toLowerCase(Locale.ROOT))) {
                continue;
            }
            normalized.add(trimmed);
            if (normalized.size() == MAX_MEMBERS) {
                break;
            }
        }
        return List.copyOf(normalized);
    }
}
