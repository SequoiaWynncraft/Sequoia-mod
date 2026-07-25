package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public final class GazDeathMessageEffect {
    private static final int FADE_IN_TICKS = 10;
    private static final int STAY_TICKS = 70;
    private static final int FADE_OUT_TICKS = 20;

    private GazDeathMessageEffect() {}

    public static void showForLocalPlayer() {
        Minecraft minecraft = SeqClient.mc;
        minecraft.execute(() -> {
            if (minecraft.player == null) {
                return;
            }

            String ign = minecraft.player.getGameProfile().name();
            minecraft.gui.setTimes(FADE_IN_TICKS, STAY_TICKS, FADE_OUT_TICKS);
            minecraft.gui.setTitle(title());
            minecraft.gui.setSubtitle(subtitleFor(ign));
            minecraft.player.playSound(SoundEvents.WITHER_SPAWN, 1.0f, 0.8f);
        });
    }

    static Component title() {
        return Component.literal("Gaz has issued a death message")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
    }

    static Component subtitleFor(String ign) {
        return Component.literal("to " + ign)
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
    }
}
