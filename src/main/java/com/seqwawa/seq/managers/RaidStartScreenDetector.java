package com.seqwawa.seq.managers;

import net.minecraft.network.chat.Component;

/** Identifies Wynncraft's raid-start container without relying on Wynntils classes. */
public final class RaidStartScreenDetector {
    // This is the exact private-use title matched by Wynntils' RaidStartContainer.
    // U+CFFE1 requires a surrogate pair; U+E00C is a BMP private-use character.
    private static final String RAID_START_TITLE = "\uDAFF\uDFE1\uE00C";

    private RaidStartScreenDetector() {}

    public static boolean isRaidStartScreen(Component title) {
        return title != null && isRaidStartTitle(title.getString());
    }

    static boolean isRaidStartTitle(String title) {
        return RAID_START_TITLE.equals(title);
    }
}
