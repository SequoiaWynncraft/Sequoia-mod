package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.model.GuildRaidProgress;
import com.seqwawa.seq.model.GuildRaidProgress.Entry;
import com.seqwawa.seq.model.SeqRaid;
import com.seqwawa.seq.model.SeqTier;
import com.seqwawa.seq.ui.AchievementsScreen.Row;
import com.seqwawa.seq.ui.theme.UiColor;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AchievementsScreenTest {

    @Test
    void oneRowPerGraidThenTheCombinedOne() {
        List<Row> rows = AchievementsScreen.buildRows(GuildRaidProgress.EMPTY);

        assertEquals(SeqRaid.values().length + 1, rows.size());
        assertEquals("Nest of the Grootslangs", rows.getFirst().name());
        assertEquals("All Guild Raids", rows.getLast().name());
        assertTrue(rows.getLast().total());
    }

    @Test
    void anEmptyRaidAimsAtBronze() {
        Row notg = rows(GuildRaidProgress.EMPTY).getFirst();

        assertNull(notg.tier());
        assertEquals(0, notg.count());
        assertEquals(SeqTier.BRONZE, notg.nextTier());
        assertEquals(25, notg.nextAt());
        assertEquals(0f, AchievementsScreen.progressRatio(notg));
    }

    @Test
    void theBarTracksTheCountAgainstTheNextThreshold() {
        Row tna = row(rows(progress(Map.of("TNA", 19))), "The Nameless Anomaly");

        assertNull(tna.tier());
        assertEquals(19, tna.count());
        assertEquals(25, tna.nextAt());
        assertEquals(19f / 25f, AchievementsScreen.progressRatio(tna), 0.0001f);
    }

    @Test
    void rankingUpMovesTheTargetToTheNextTier() {
        Row tna = row(rows(progress(Map.of("TNA", 60))), "The Nameless Anomaly");

        assertEquals(SeqTier.SILVER, tna.tier());
        assertEquals(SeqTier.GOLD, tna.nextTier());
        assertEquals(100, tna.nextAt());
    }

    @Test
    void theCombinedRowUsesTheDoubledLadder() {
        Row all = rows(progress(Map.of("TNA", 19, "TCC", 12))).getLast();

        assertEquals(31, all.count());
        assertNull(all.tier());
        assertEquals(SeqTier.BRONZE, all.nextTier());
        assertEquals(50, all.nextAt());
    }

    @Test
    void theTopTierFillsTheBarAndDropsTheTarget() {
        Row tna = row(rows(progress(Map.of("TNA", 4000))), "The Nameless Anomaly");

        assertEquals(SeqTier.MYTHRIL, tna.tier());
        assertNull(tna.nextTier());
        assertEquals(0, tna.nextAt());
        assertEquals(1f, AchievementsScreen.progressRatio(tna));
    }

    @Test
    void everyTierHasItsOwnColour() {
        Set<UiColor> tokens = new HashSet<>();
        for (SeqTier tier : SeqTier.ordered()) {
            UiColor token = AchievementsScreen.tierToken(tier);
            assertNotNull(token, tier + " has no colour");
            assertTrue(tokens.add(token), tier + " reuses another tier's colour");
        }
    }

    @Test
    void largeCountsAreGrouped() {
        assertEquals("0", AchievementsScreen.formatCount(0));
        assertEquals("999", AchievementsScreen.formatCount(999));
        assertEquals("2,500", AchievementsScreen.formatCount(2500));
        assertEquals("10,000", AchievementsScreen.formatCount(10_000));
    }

    private static GuildRaidProgress progress(Map<String, Integer> counts) {
        return new GuildRaidProgress(
                1, counts.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, entry -> new Entry(entry.getValue()))));
    }

    private static List<Row> rows(GuildRaidProgress progress) {
        return AchievementsScreen.buildRows(progress);
    }

    private static Row row(List<Row> rows, String name) {
        return rows.stream().filter(row -> row.name().equals(name)).findFirst().orElseThrow();
    }
}
