package com.seqwawa.seq.events;

import com.collarmc.pounce.EventInfo;
import com.collarmc.pounce.Preference;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

/** A resolved client sound that is about to be played. */
@EventInfo(preference = Preference.CALLER)
public record SoundPlayedEvent(
        Identifier eventId,
        Identifier soundId,
        SoundSource source,
        double x,
        double y,
        double z,
        float volume,
        float pitch,
        float audibleRange) {}
