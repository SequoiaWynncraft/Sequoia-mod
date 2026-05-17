package org.sequoia.seq.managers;

import net.minecraft.network.chat.Component;

/**
 * Minimal guild war tracker contract exposed to the rest of the mod without
 * requiring Wynntils classes to be present.
 */
public interface GuildWarTrackerHandle {
    void tick();

    void onSystemChat(Component message);

    void reset();
}
