package com.seqwawa.seq.model.war;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/** Live locations of guild members who opted into war telemetry. */
public record WarStatusSnapshot(long timestamp, List<Player> players) {
    public static final WarStatusSnapshot EMPTY = new WarStatusSnapshot(0, List.of());

    public WarStatusSnapshot {
        players = players == null ? List.of() : List.copyOf(players);
    }

    public record Player(
            String username,
            @SerializedName("class") String wynnClass,
            String territory,
            Position pos) {}

    public record Position(int x, int z) {}
}
