package com.seqwawa.seq.integrations;

import com.wynntils.core.components.Models;
import com.wynntils.core.events.MixinHelper;
import com.wynntils.mc.event.AddGuiMessageLineEvent;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.GuiMessage;

/** Loaded reflectively only after Wynntils is detected. */
public final class WynntilsChatBridge implements WynntilsChatAccess.Bridge {
    @Override
    public CompletableFuture<String> lookupGuildTag(String username) {
        return Models.Player.getPlayer(username).thenApply(player -> player == null ? null
                : player.guildInfo().map(guild -> guild.guildPrefix()).orElse(""));
    }

    @Override
    public void postAddedLine(GuiMessage message, GuiMessage.Line line) {
        MixinHelper.post(new AddGuiMessageLineEvent(message, line));
    }
}
