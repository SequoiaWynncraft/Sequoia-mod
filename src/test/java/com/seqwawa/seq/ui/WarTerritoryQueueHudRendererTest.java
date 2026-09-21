package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.model.war.WarTerritoryQueueFeed.Participant;
import com.seqwawa.seq.model.war.WarTerritoryQueueFeed.TerritoryQueue;
import java.awt.Color;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class WarTerritoryQueueHudRendererTest {
    private static final Instant NOW = Instant.parse("2026-08-24T12:12:19Z");

    @Test
    void formatsScreenshotStyleQueueLineWithBothDefenseReadings() {
        TerritoryQueue queue = queue("Very Low", "Very High", NOW.plusSeconds(160));

        assertEquals(
                "xiaolongbao/Soup Person 12m 19s ago - Alekin (Very Low/Very High) 02:40  3/5",
                WarTerritoryQueueHudRenderer.formatLine(queue, NOW));
    }

    @Test
    void formatsKnownAndTimerOnlyUnknownDefense() {
        assertEquals("(Low)", WarTerritoryQueueHudRenderer.formatDefenses("Low", null));
        assertEquals("(High)", WarTerritoryQueueHudRenderer.formatDefenses(null, "High"));
        assertEquals("(Very Low)", WarTerritoryQueueHudRenderer.formatDefenses("Very Low", "Very Low"));
        assertEquals("(Very Low)", WarTerritoryQueueHudRenderer.formatDefenses(" Very Low ", "very low"));
        assertEquals("(Unknown)", WarTerritoryQueueHudRenderer.formatDefenses(null, null));
        assertEquals(
                new WarTerritoryQueueHudRenderer.DefenseRatings("Very Low", null),
                WarTerritoryQueueHudRenderer.defenseRatings(" Very Low ", "very low"));
    }

    @Test
    void timerOnlyQueueLineShowsUnknownIdentityDefenseAndImplicitOwnerCount() {
        TerritoryQueue queue = new TerritoryQueue(
                8L,
                "Detlas",
                null,
                null,
                null,
                null,
                null,
                NOW.minusSeconds(19),
                NOW.plusSeconds(101),
                List.of());

        assertEquals(
                "Unknown 19s ago - Detlas (Unknown) 01:41  1/5",
                WarTerritoryQueueHudRenderer.formatLine(queue, NOW));
    }

    @Test
    void ageAndCountdownAreClampedAndCompact() {
        assertEquals("19s ago", WarTerritoryQueueHudRenderer.formatAge(NOW.minusSeconds(19), NOW));
        assertEquals("2m 19s ago", WarTerritoryQueueHudRenderer.formatAge(NOW.minusSeconds(139), NOW));
        assertEquals("1h 2m ago", WarTerritoryQueueHudRenderer.formatAge(NOW.minusSeconds(3739), NOW));
        assertEquals("00:00", WarTerritoryQueueHudRenderer.formatCountdown(NOW.minusSeconds(1), NOW));
        assertEquals("--:--", WarTerritoryQueueHudRenderer.formatCountdown(null, NOW));
        assertEquals("5/5", WarTerritoryQueueHudRenderer.participantLabel(9));
        assertEquals(6, WarTerritoryQueueHudRenderer.DEFAULT_MAX_ROWS);
    }

    @Test
    void usesWynntilsDefenseColors() {
        assertEquals(new Color(0x00AA00), WarTerritoryQueueHudRenderer.defenseColor("Very Low"));
        assertEquals(new Color(0x55FF55), WarTerritoryQueueHudRenderer.defenseColor("Low"));
        assertEquals(new Color(0xFFFF55), WarTerritoryQueueHudRenderer.defenseColor("Medium"));
        assertEquals(new Color(0xFF5555), WarTerritoryQueueHudRenderer.defenseColor("High"));
        assertEquals(new Color(0xAA0000), WarTerritoryQueueHudRenderer.defenseColor("Very High"));
    }

    @Test
    void queueHudTextSizeFallsBackAndControlsRowSpacing() {
        assertEquals(9f, WarTerritoryQueueHudRenderer.textSize(null));

        Setting.IntSetting setting = new Setting.IntSetting("test", "test", 14, 6, 18);
        assertEquals(14f, WarTerritoryQueueHudRenderer.textSize(setting));
        assertEquals(17f, WarTerritoryQueueHudRenderer.rowHeight(14f));
    }

    @Test
    void queueHudSettingsFallBackAndClampMaximumRows() {
        assertFalse(WarTerritoryQueueHudRenderer.onlyOwnedOrJoined(null));
        assertEquals(6, WarTerritoryQueueHudRenderer.maxRows(null));

        Setting.BooleanSetting onlyMine = new Setting.BooleanSetting("test", "test", true);
        Setting.IntSetting maximum = new Setting.IntSetting("test", "test", 14, 1, 20);
        assertTrue(WarTerritoryQueueHudRenderer.onlyOwnedOrJoined(onlyMine));
        assertEquals(14, WarTerritoryQueueHudRenderer.maxRows(maximum));
        assertEquals(0, WarTerritoryQueueHudRenderer.rowsFittingHeight(12.99f, 6f));
        assertEquals(1, WarTerritoryQueueHudRenderer.rowsFittingHeight(13f, 6f));
        assertEquals(1, WarTerritoryQueueHudRenderer.rowsFittingHeight(21.99f, 6f));
        assertEquals(2, WarTerritoryQueueHudRenderer.rowsFittingHeight(22f, 6f));
        assertEquals(1, WarTerritoryQueueHudRenderer.rowsFittingHeight(25f, 18f));
        assertEquals(2, WarTerritoryQueueHudRenderer.rowsFittingHeight(46f, 18f));
    }

    @Test
    void positionsQueueHudResponsivelyAndClampsDraggedCoordinates() {
        assertEquals(
                new WarTerritoryQueueHudRenderer.Bounds(153f, 7f, 40f, 20f),
                WarTerritoryQueueHudRenderer.positionBounds(200f, 100f, 40f, 20f, 1f, 0f));
        assertEquals(
                new WarTerritoryQueueHudRenderer.Bounds(80f, 40f, 40f, 20f),
                WarTerritoryQueueHudRenderer.positionBounds(200f, 100f, 40f, 20f, 0.5f, 0.5f));
        assertEquals(
                new WarTerritoryQueueHudRenderer.Position(0f, 1f),
                WarTerritoryQueueHudRenderer.positionForTopLeft(200f, 100f, 40f, 20f, -50f, 500f));
    }

    @Test
    void filtersToQueuesTheLocalPlayerQueuedOrJoinedBeforeApplyingLimit() {
        TerritoryQueue owned = queue(1L, "Owned", "SELF", List.of(new Participant("other", "Other", 0)));
        TerritoryQueue joined = queue(2L, "Joined", "other", List.of(new Participant("self", "Self", 1)));
        TerritoryQueue joinedProvisional =
                provisionalQueue(4L, "Unknown joined", List.of(new Participant("self", "Self", 1)));
        TerritoryQueue unjoinedProvisional = provisionalQueue(5L, "Unknown unjoined", List.of());
        TerritoryQueue unrelated =
                queue(3L, "Unrelated", "other", List.of(new Participant("third", "Third", 0)));
        List<TerritoryQueue> queues = List.of(unrelated, unjoinedProvisional, owned, joined, joinedProvisional);

        assertEquals(
                List.of(owned, joined, joinedProvisional),
                WarTerritoryQueueHudRenderer.displayedQueues(queues, "self", true, 20));
        assertEquals(
                List.of(owned),
                WarTerritoryQueueHudRenderer.displayedQueues(queues, "self", true, 1));
        assertEquals(
                List.of(unrelated, unjoinedProvisional),
                WarTerritoryQueueHudRenderer.displayedQueues(queues, null, false, 2));
        assertEquals(
                List.of(),
                WarTerritoryQueueHudRenderer.displayedQueues(queues, null, true, 20));
    }

    private static TerritoryQueue queue(String queuedDefense, String reportedDefense, Instant expiresAt) {
        return new TerritoryQueue(
                7L,
                "Alekin",
                "queuer-uuid",
                "xiaolongbao",
                "Soup Person",
                queuedDefense,
                reportedDefense,
                NOW.minusSeconds(12 * 60 + 19),
                expiresAt,
                List.of(
                        new Participant("one", "One", 0),
                        new Participant("two", "Two", 1),
                        new Participant("three", "Three", 2)));
    }

    private static TerritoryQueue queue(
            long id, String territory, String queuedBy, List<Participant> participants) {
        return new TerritoryQueue(
                id,
                territory,
                queuedBy,
                "Player" + id,
                null,
                "Low",
                null,
                NOW.minusSeconds(id),
                NOW.plusSeconds(120),
                participants);
    }

    private static TerritoryQueue provisionalQueue(
            long id, String territory, List<Participant> participants) {
        return new TerritoryQueue(
                id,
                territory,
                null,
                null,
                null,
                null,
                null,
                NOW.minusSeconds(id),
                NOW.plusSeconds(120),
                participants);
    }
}
