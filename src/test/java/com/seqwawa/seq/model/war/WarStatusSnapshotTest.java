package com.seqwawa.seq.model.war;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

class WarStatusSnapshotTest {
    @Test
    void parsesBackendSnapshotAndIgnoresOtherFeedSections() {
        WarStatusSnapshot snapshot = new Gson().fromJson(
                """
                {
                  "timestamp": 123,
                  "queues": [],
                  "wars": [],
                  "players": [{
                    "username": "Alice",
                    "class": "DARK_WIZARD",
                    "territory": null,
                    "pos": {"x": -1517, "z": -5130}
                  }]
                }
                """,
                WarStatusSnapshot.class);

        assertEquals(123, snapshot.timestamp());
        assertEquals("Alice", snapshot.players().getFirst().username());
        assertEquals("DARK_WIZARD", snapshot.players().getFirst().wynnClass());
        assertEquals(-1517, snapshot.players().getFirst().pos().x());
    }
}
