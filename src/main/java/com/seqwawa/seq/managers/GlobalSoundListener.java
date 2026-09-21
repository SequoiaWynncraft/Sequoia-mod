package com.seqwawa.seq.managers;

import com.collarmc.pounce.EventBus;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.events.SoundPlayedEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;

/** Publishes all resolved client sounds through Sequoia's event bus. */
public final class GlobalSoundListener implements SoundEventListener {
    private static final GlobalSoundListener INSTANCE = new GlobalSoundListener();
    private static boolean registered;

    private GlobalSoundListener() {}

    public static void initialize() {
        if (registered) {
            return;
        }
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        if (soundManager == null) {
            SeqClient.LOGGER.warn("Global sound listener skipped because the sound manager is not ready");
            return;
        }
        soundManager.addListener(INSTANCE);
        registered = true;
    }

    public static void shutdown() {
        if (registered) {
            Minecraft.getInstance().getSoundManager().removeListener(INSTANCE);
            registered = false;
        }
    }

    @Override
    public void onPlaySound(SoundInstance instance, WeighedSoundEvents soundEvent, float audibleRange) {
        EventBus eventBus = SeqClient.getEventBus();
        Sound sound = instance.getSound();
        if (eventBus == null || sound == null) {
            return;
        }
        eventBus.dispatch(new SoundPlayedEvent(
                instance.getIdentifier(),
                sound.getLocation(),
                instance.getSource(),
                instance.getX(),
                instance.getY(),
                instance.getZ(),
                instance.getVolume(),
                instance.getPitch(),
                audibleRange));
    }
}
