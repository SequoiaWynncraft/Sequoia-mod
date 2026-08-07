package com.seqwawa.seq.managers;

import java.util.Set;
import net.minecraft.network.chat.Component;

/** Identifies Wynncraft's raid-start container without relying on Wynntils classes. */
public final class RaidStartScreenDetector {
    /** Menu slot indexes used by Wynncraft for the local player's four gambit choices. */
    public static final Set<Integer> GAMBIT_SLOTS = Set.of(1, 3, 5, 7);

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

    public static boolean isGambitSlot(int menuSlotIndex) {
        return GAMBIT_SLOTS.contains(menuSlotIndex);
    }
}
