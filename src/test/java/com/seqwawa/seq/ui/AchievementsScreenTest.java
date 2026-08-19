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
import java.util.LinkedHashMap;
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
    void theRankIsWhateverTheBackendSaidItWas() {
        Row tna = row(progress(entry("TNA", 19, "platinum")), "The Nameless Anomaly");

        assertEquals(SeqTier.PLATINUM, tna.tier());
    }

    @Test
    void anUnrankedRaidStaysUnrankedEvenPastAThreshold() {
        Row tna = row(progress(entry("TNA", 400, null)), "The Nameless Anomaly");

        assertNull(tna.tier());
        assertEquals(400, tna.count());
    }

    @Test
    void anEmptyRaidAimsAtBronze() {
        Row notg = AchievementsScreen.buildRows(GuildRaidProgress.EMPTY).getFirst();

        assertEquals(SeqTier.BRONZE, notg.nextTier());
        assertEquals(25, notg.nextAt());
        assertEquals(0f, AchievementsScreen.progressRatio(notg));
    }

    @Test
    void theBarTracksTheCountAgainstTheNextThreshold() {
        Row tna = row(progress(entry("TNA", 19, null)), "The Nameless Anomaly");

        assertEquals(25, tna.nextAt());
        assertEquals(19f / 25f, AchievementsScreen.progressRatio(tna), 0.0001f);
    }

    @Test
    void theTargetFollowsTheCountUpTheLadder() {
        Row tna = row(progress(entry("TNA", 120, "gold")), "The Nameless Anomaly");

        assertEquals(SeqTier.GOLD, tna.tier());
        assertEquals(SeqTier.PLATINUM, tna.nextTier());
        assertEquals(500, tna.nextAt());
    }

    @Test
    void theCombinedRowUsesTheDoubledLadder() {
        List<Row> rows = AchievementsScreen.buildRows(
                progress(entry("TNA", 19, null), entry("TCC", 12, null), entry("total", 31, null)));
        Row all = rows.getLast();

        assertEquals(31, all.count());
        assertEquals(SeqTier.BRONZE, all.nextTier());
        assertEquals(50, all.nextAt());
    }

    @Test
    void theTopOfTheLadderFillsTheBarAndDropsTheTarget() {
        Row tna = row(progress(entry("TNA", 5_000, "mythril")), "The Nameless Anomaly");

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

    private static Map.Entry<String, Entry> entry(String key, int count, String tier) {
        return Map.entry(key, new Entry(count, tier == null ? "" : tier));
    }

    @SafeVarargs
    private static GuildRaidProgress progress(Map.Entry<String, Entry>... entries) {
        Map<String, Entry> map = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return new GuildRaidProgress(1, map);
    }

    private static Row row(GuildRaidProgress progress, String name) {
        return AchievementsScreen.buildRows(progress).stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
