package com.seqwawa.seq.events;

import com.collarmc.pounce.EventInfo;
import com.collarmc.pounce.Preference;

/** Fired once per debounced Sahur beam sound while the sidebar reads Challenges: 3/4. */
@EventInfo(preference = Preference.CALLER)
public record TnaSahurSoundProcEvent(SoundPlayedEvent sound) {}
