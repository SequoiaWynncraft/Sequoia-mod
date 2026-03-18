package org.sequoia.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

class RaidTrackerTest {

    @Test
    void parseRaidCompletionHandlesSplitNamesAndRewards() {
        Component message = Component.empty()
                .append(Component.literal("󏿼󐀆 "))
                .append(Component.literal("bubblebouncy").withStyle(Style.EMPTY.withInsertion("Visroul")))
                .append(Component.literal(", xmattypazox, "))
                .append(Component.literal("death by choking").withStyle(Style.EMPTY.withInsertion("a3pki")))
                .append(Component.literal(", and divvy\n󏿼󐀆 "))
                .append(Component.literal("lunne").withStyle(Style.EMPTY.withInsertion("blousy")))
                .append(Component.literal(" finished The Nameless Anomaly and claimed 2x Aspects\n󏿼󐀆 , 2048x Emeralds, and +10367m Guild Experience"));

        RaidTracker.ParsedRaidCompletion parsed = RaidTracker.parseRaidCompletion(message);

        assertNotNull(parsed);
        assertEquals(List.of("Visroul", "xmattypazox", "a3pki", "blousy"), parsed.partyMembers());
        assertEquals("The Nameless Anomaly", parsed.raidName());
        assertEquals(2, parsed.aspects());
        assertEquals(2048, parsed.emeralds());
        assertEquals(10.367, parsed.guildExp(), 0.000001);
        assertEquals(0, parsed.seasonalRating());
    }
}
