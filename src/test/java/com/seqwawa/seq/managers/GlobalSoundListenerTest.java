package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.SampledFloat;
import org.junit.jupiter.api.Test;

class GlobalSoundListenerTest {
    @Test
    void handsEachResolvedSoundOnAsItsEventAndFile() {
        List<List<Identifier>> heard = new ArrayList<>();
        var listener = new GlobalSoundListener((eventId, soundId) -> heard.add(List.of(eventId, soundId)));

        listener.onPlaySound(sound("item.trident.thunder", "item/trident/thunder1"), null, 16f);
        listener.onPlaySound(sound("entity.evoker.prepare_summon", null), null, 16f);

        assertEquals(List.of(List.of(id("item.trident.thunder"), id("item/trident/thunder1"))), heard);
    }

    /** A sound instance as Minecraft hands it to listeners, resolved to {@code file} (null: unresolved). */
    private static AbstractSoundInstance sound(String event, String file) {
        return new AbstractSoundInstance(id(event), SoundSource.HOSTILE, RandomSource.create()) {
            {
                if (file != null) {
                    SampledFloat one = random -> 1f;
                    sound = new Sound(id(file), one, one, 1, Sound.Type.FILE, false, false, 16);
                }
            }
        };
    }

    private static Identifier id(String path) {
        return Identifier.withDefaultNamespace(path);
    }
}
