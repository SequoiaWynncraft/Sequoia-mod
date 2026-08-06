package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;

/** Applies the private rank-pill label to the local player while Princess mode is active. */
final class PrincessRankEasterEgg {
    static final String PILL_LABEL = "PRINCESS";

    private PrincessRankEasterEgg() {}

    static boolean isLocalSpeaker(String speakerUsername) {
        return isLocalSpeaker(speakerUsername, localUsername());
    }

    static String pillLabel(String defaultLabel, String speakerUsername) {
        return pillLabel(defaultLabel, speakerUsername, localUsername(), PrincessMode.isEnabled());
    }

    static String pillLabel(String defaultLabel, String speakerUsername, String localUsername, boolean modeEnabled) {
        return modeEnabled && isLocalSpeaker(speakerUsername, localUsername) ? PILL_LABEL : defaultLabel;
    }

    static boolean isLocalSpeaker(String speakerUsername, String localUsername) {
        return speakerUsername != null
                && localUsername != null
                && speakerUsername.equalsIgnoreCase(localUsername);
    }

    private static String localUsername() {
        return SeqClient.mc == null || SeqClient.mc.getUser() == null
                ? null
                : SeqClient.mc.getUser().getName();
    }
}
