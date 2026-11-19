package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.raids.tna.TnaSahurSoundDetector;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;

/** Hands every resolved client sound, as its event and file, to the TNA Sahur detector. */
public final class GlobalSoundListener implements SoundEventListener {
    private static final GlobalSoundListener INSTANCE = new GlobalSoundListener(TnaSahurSoundDetector::onSoundPlayed);
    private static boolean registered;

    private final BiConsumer<Identifier, Identifier> sounds;

    GlobalSoundListener(BiConsumer<Identifier, Identifier> sounds) {
        this.sounds = sounds;
    }

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
        Sound sound = instance.getSound();
        if (sound != null) {
            sounds.accept(instance.getIdentifier(), sound.getLocation());
        }
    }
}
