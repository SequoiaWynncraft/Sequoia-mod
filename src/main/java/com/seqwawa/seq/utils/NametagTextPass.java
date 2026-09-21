package com.seqwawa.seq.utils;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;

/** Scopes nametag-only styling; pills participate in every pass used by their name. */
public final class NametagTextPass {
    private static final ThreadLocal<Integer> NAMETAG_DEPTH = ThreadLocal.withInitial(() -> 0);

    private NametagTextPass() {}

    public static void beginNametags() {
        NAMETAG_DEPTH.set(NAMETAG_DEPTH.get() + 1);
    }

    public static void endNametags() {
        int remaining = NAMETAG_DEPTH.get() - 1;
        if (remaining <= 0) {
            NAMETAG_DEPTH.remove();
        } else {
            NAMETAG_DEPTH.set(remaining);
        }
    }

    public static boolean isDrawingNametags() {
        return NAMETAG_DEPTH.get() > 0;
    }

    public static boolean isStylingNametags() {
        Setting.BooleanSetting setting = SeqClient.getShowNametagRanksSetting();
        return isDrawingNametags() && setting != null && setting.getValue();
    }

}
