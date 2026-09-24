package com.seqwawa.seq.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Who in a premade can come right now: each seat is free, busy or offline, and
 * invites only ever go to the free ones.
 */
public record PremadeAvailability(List<Seat> seats) {

    public enum State {
        FREE,
        BUSY,
        OFFLINE
    }

    /** One name in the premade. {@code presence} is null when they are offline. */
    public record Seat(String username, State state, GuildMemberPresence presence, boolean self) {}

    public PremadeAvailability {
        seats = seats == null ? List.of() : List.copyOf(seats);
    }

    public static PremadeAvailability of(
            PremadeParty party, List<GuildMemberPresence> online, Predicate<String> busy, String localUsername) {
        if (party == null || party.members().isEmpty()) {
            return new PremadeAvailability(List.of());
        }
        Map<String, GuildMemberPresence> byName = online == null
                ? Map.of()
                : online.stream()
                        .collect(Collectors.toMap(GuildMemberPresence::key, member -> member, (first, second) -> first));

        List<Seat> seats = new ArrayList<>();
        for (String username : party.members()) {
            String key = username.toLowerCase(Locale.ROOT);
            boolean self = localUsername != null && key.equals(localUsername.trim().toLowerCase(Locale.ROOT));
            GuildMemberPresence presence = byName.get(key);
            State state;
            if (self) {
                // Online by definition, even when the roster is a refresh behind.
                state = State.FREE;
            } else if (presence == null) {
                state = State.OFFLINE;
            } else {
                state = busy != null && busy.test(username) ? State.BUSY : State.FREE;
            }
            seats.add(new Seat(username, state, presence, self));
        }
        return new PremadeAvailability(seats);
    }

    public int size() {
        return seats.size();
    }

    public int count(State state) {
        return (int) seats.stream().filter(seat -> seat.state() == state).count();
    }

    public int onlineCount() {
        return size() - count(State.OFFLINE);
    }

    public boolean everyoneFree() {
        return !seats.isEmpty() && count(State.FREE) == size();
    }

    /** The seats an invite would go to: everyone free except you. */
    public List<String> freeToInvite() {
        return seats.stream()
                .filter(seat -> seat.state() == State.FREE && !seat.self())
                .map(Seat::username)
                .toList();
    }

    /** {@code 3/4 online, 1 busy}. */
    public String summary() {
        if (seats.isEmpty()) {
            return "empty";
        }
        int online = onlineCount();
        if (online == 0) {
            return "nobody online";
        }
        int busy = count(State.BUSY);
        String base = online + "/" + size() + " online";
        return busy == 0 ? base : base + ", " + busy + " busy";
    }

    /** {@code 1 busy and 2 offline left out}, or null when nothing was left out. */
    public String leftOutSummary() {
        int busy = count(State.BUSY);
        int offline = count(State.OFFLINE);
        if (busy == 0 && offline == 0) {
            return null;
        }
        if (busy > 0 && offline > 0) {
            return busy + " busy and " + offline + " offline left out";
        }
        return busy > 0 ? busy + " busy left out" : offline + " offline left out";
    }
}
