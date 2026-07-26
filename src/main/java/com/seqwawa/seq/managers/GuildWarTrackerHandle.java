package com.seqwawa.seq.managers;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Minimal guild war tracker contract exposed to the rest of the mod without
 * requiring Wynntils classes to be present.
 */
public interface GuildWarTrackerHandle {
    void tick();

    void onSystemChat(Component message);

    void reset();

    void onSlotClick(String screenName, ItemStack item);
}
