package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.model.SeqBadge;
import com.seqwawa.seq.model.SeqBadgeTier;
import com.seqwawa.seq.model.SeqBadgeType;

class SeqBadgeNametagRenderSupportTest {
    @Test
    void keepsWynntilsAndLowerBadgeOffsetsDistinct() {
        assertEquals(25f, SeqBadgeNametagRenderSupport.WYNNTILS_DEFAULT_BADGE_Y_OFFSET);
        assertEquals(10f, SeqBadgeNametagRenderSupport.LOWER_BADGE_Y_OFFSET);
        assertNotEquals(
                SeqBadgeNametagRenderSupport.WYNNTILS_DEFAULT_BADGE_Y_OFFSET,
                SeqBadgeNametagRenderSupport.LOWER_BADGE_Y_OFFSET);
    }

    @Test
    void filtersVisibleBadgesByCategorySettings() {
        Setting.BooleanSetting previousRaidSetting = SeqClient.showRaidBadgesSetting;
        Setting.BooleanSetting previousInsignaSetting = SeqClient.showInsignaBadgesSetting;
        try {
            SeqClient.showRaidBadgesSetting = new Setting.BooleanSetting("show_raid_badges", "leaderboard_badges", false);
            SeqClient.showInsignaBadgesSetting =
                    new Setting.BooleanSetting("show_insigna_badges", "leaderboard_badges", true);

            assertEquals(
                    List.of(new SeqBadge(SeqBadgeType.INSIGNA, SeqBadgeTier.DIAMOND)),
                    SeqBadgeNametagRenderSupport.visibleBadges(List.of(
                            new SeqBadge(SeqBadgeType.WTP, SeqBadgeTier.GOLD),
                            new SeqBadge(SeqBadgeType.NOL, SeqBadgeTier.SILVER),
                            new SeqBadge(SeqBadgeType.INSIGNA, SeqBadgeTier.DIAMOND))));
        } finally {
            SeqClient.showRaidBadgesSetting = previousRaidSetting;
            SeqClient.showInsignaBadgesSetting = previousInsignaSetting;
        }
    }
}
