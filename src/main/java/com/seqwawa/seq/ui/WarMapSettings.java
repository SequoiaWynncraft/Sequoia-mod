package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import static com.seqwawa.seq.ui.WarPlannerDialogUi.*;
import static com.seqwawa.seq.ui.WarPingPicker.pingCandidates;

import com.seqwawa.seq.client.SeqClient;


/** Persisted war-map display preferences. */
final class WarMapSettings {

    private static final float HEADER_HEIGHT = SequoiaUiStyle.HEADER_HEIGHT;

    static final int RESOURCE_FILL_ALPHA = 96;

    static boolean onlyMyWarQueuesEnabled() {
        return WarTerritoryQueueHudRenderer.onlyOwnedOrJoined(
                SeqClient.getWarQueueHudOnlyOwnedOrJoinedSetting());
    }

    static boolean resourceColorsEnabled() {
        return SeqClient.getWarPlannerResourceColorsSetting() != null
                && SeqClient.getWarPlannerResourceColorsSetting().getValue();
    }

    static boolean warMapPlayersEnabled() {
        return SeqClient.getWarPlannerShowPlayersSetting() == null
                || SeqClient.getWarPlannerShowPlayersSetting().getValue();
    }

    static boolean territoriesLocked() {
        return SeqClient.getWarPlannerLockTerritoriesSetting() != null
                && SeqClient.getWarPlannerLockTerritoriesSetting().getValue();
    }
}
