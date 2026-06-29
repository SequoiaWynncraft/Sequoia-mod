package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;

final class SeqBadgeNametagRenderSupport {
    static final float BADGE_WIDTH = 19f;
    static final float BADGE_HEIGHT = 18f;
    static final float BADGE_STEP = 21f;
    static final float WYNNTILS_DEFAULT_BADGE_Y_OFFSET = 25f;
    static final float LOWER_BADGE_Y_OFFSET = 10f;

    private SeqBadgeNametagRenderSupport() {}

    static boolean showLeaderboardBadges() {
        return SeqClient.getShowLeaderboardBadgesSetting() == null
                || SeqClient.getShowLeaderboardBadgesSetting().getValue();
    }

    static boolean showOwnLeaderboardBadge() {
        return SeqClient.getShowOwnLeaderboardBadgeSetting() == null
                || SeqClient.getShowOwnLeaderboardBadgeSetting().getValue();
    }
}
