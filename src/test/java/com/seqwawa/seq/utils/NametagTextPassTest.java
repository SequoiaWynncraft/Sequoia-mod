package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import org.junit.jupiter.api.Test;

class NametagTextPassTest {
    @Test
    void nestedPassesRestoreTheirOuterContext() {
        assertFalse(NametagTextPass.isDrawingNametags());
        NametagTextPass.beginNametags();
        try {
            NametagTextPass.beginNametags();
            try {
                assertTrue(NametagTextPass.isDrawingNametags());
            } finally {
                NametagTextPass.endNametags();
            }
            assertTrue(NametagTextPass.isDrawingNametags());
        } finally {
            NametagTextPass.endNametags();
        }
        assertFalse(NametagTextPass.isDrawingNametags());
    }

    @Test
    void stylingRequiresAnEnabledFeatureAndAnActiveNametagPass() {
        Setting.BooleanSetting previous = SeqClient.showNametagRanksSetting;
        try {
            SeqClient.showNametagRanksSetting = new Setting.BooleanSetting("show_nametag_ranks", "nametags", true);
            assertFalse(NametagTextPass.isStylingNametags(), "chat rendering must remain unchanged");
            NametagTextPass.beginNametags();
            try {
                assertTrue(NametagTextPass.isStylingNametags());
                SeqClient.showNametagRanksSetting.setValue(false);
                assertFalse(NametagTextPass.isStylingNametags(), "turning the feature off also disables its render changes");
                SeqClient.showNametagRanksSetting = null;
                assertFalse(NametagTextPass.isStylingNametags());
            } finally {
                NametagTextPass.endNametags();
            }
        } finally {
            SeqClient.showNametagRanksSetting = previous;
        }
    }
}
