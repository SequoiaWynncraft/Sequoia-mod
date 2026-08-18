package com.seqwawa.seq.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import com.seqwawa.seq.model.GuildRaidProgress.Entry;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GuildRaidProgressTest {

    private static final Gson GSON = new Gson();

    private static final String BACKEND_PAYLOAD =
            """
            {"schema_version":1,"progress":{"NOTG":{"count":0,"tier":null},\
            "TNA":{"count":19,"tier":null},"TCC":{"count":0,"tier":null},\
            "NOL":{"count":0,"tier":null},"TWP":{"count":0,"tier":null},\
            "total":{"count":19,"tier":null}}}
            """;

    @Test
    void readsTheBackendPayload() {
        GuildRaidProgress progress = GSON.fromJson(BACKEND_PAYLOAD, GuildRaidProgress.class);

        assertEquals(1, progress.schemaVersion());
        assertEquals(19, progress.count(SeqRaid.TNA));
        assertEquals(0, progress.count(SeqRaid.NOTG));
        assertEquals(19, progress.totalCount());
    }

    @Test
    void whateverElseTheBackendSendsIsIgnored() {
        GuildRaidProgress progress = GSON.fromJson(
                "{\"schema_version\":2,\"progress\":{\"TNA\":{\"count\":19,\"tier\":\"bronze\",\"streak\":3}}}",
                GuildRaidProgress.class);

        assertEquals(19, progress.count(SeqRaid.TNA));
    }

    @Test
    void aRaidWithNoEntryReadsAsZero() {
        GuildRaidProgress progress =
                GSON.fromJson("{\"schema_version\":1,\"progress\":{\"TNA\":{\"count\":5}}}", GuildRaidProgress.class);

        assertEquals(0, progress.count(SeqRaid.TWP));
        assertEquals(5, progress.totalCount());
    }

    @Test
    void theTotalFallsBackToTheSumWhenTheBackendOmitsIt() {
        GuildRaidProgress progress = new GuildRaidProgress(1, Map.of("TNA", new Entry(19), "TCC", new Entry(3)));

        assertEquals(22, progress.totalCount());
    }

    @Test
    void raidKeysAreReadWhateverTheirCase() {
        GuildRaidProgress progress = GSON.fromJson(
                "{\"schema_version\":1,\"progress\":{\"tna\":{\"count\":19},\"Total\":{\"count\":42}}}",
                GuildRaidProgress.class);

        assertEquals(19, progress.count(SeqRaid.TNA));
        assertEquals(42, progress.totalCount());
    }

    @Test
    void aNegativeCountNeverLeaksIntoTheScreen() {
        GuildRaidProgress progress = new GuildRaidProgress(1, Map.of("TNA", new Entry(-4), "TOTAL", new Entry(-9)));

        assertEquals(0, progress.count(SeqRaid.TNA));
        assertEquals(0, progress.totalCount());
    }

    @Test
    void missingSectionsDoNotBlowUp() {
        GuildRaidProgress progress = GSON.fromJson("{\"schema_version\":1}", GuildRaidProgress.class);

        assertEquals(0, progress.count(SeqRaid.TNA));
        assertEquals(0, progress.totalCount());
    }

    @Test
    void emptyProgressIsSafeToRender() {
        assertEquals(0, GuildRaidProgress.EMPTY.totalCount());
        assertEquals(0, GuildRaidProgress.EMPTY.count(SeqRaid.NOL));
    }
}
