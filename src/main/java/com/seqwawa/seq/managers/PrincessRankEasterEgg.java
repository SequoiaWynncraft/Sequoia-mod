package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;

/** Unlocks a private rank-pill label when the local raid palette matches the secret color. */
final class PrincessRankEasterEgg {
    static final int SECRET_COLOR = 0xFF5DD6;
    static final String PILL_LABEL = "PRINCESS";

    private PrincessRankEasterEgg() {}

    static boolean isLocalSpeaker(String speakerUsername) {
        return isLocalSpeaker(speakerUsername, localUsername());
    }

    static String pillLabel(String defaultLabel, String speakerUsername) {
        boolean easterEggsEnabled = SeqClient.getEasterEggsSetting() != null
                && SeqClient.getEasterEggsSetting().getValue();
        int halcyonColor = colorValue(SeqClient.getHalcyonRingColorSetting());
        int radianceColor = colorValue(SeqClient.getRadianceMarkerColorSetting());
        int lightColor = colorValue(SeqClient.getLightRoomRingColorSetting());

        return pillLabel(
                defaultLabel,
                speakerUsername,
                localUsername(),
                easterEggsEnabled,
                halcyonColor,
                radianceColor,
                lightColor);
    }

    static String pillLabel(
            String defaultLabel,
            String speakerUsername,
            String localUsername,
            boolean easterEggsEnabled,
            int halcyonColor,
            int radianceColor,
            int lightColor) {
        boolean localSpeaker = isLocalSpeaker(speakerUsername, localUsername);
        boolean secretPalette = halcyonColor == SECRET_COLOR
                && radianceColor == SECRET_COLOR
                && lightColor == SECRET_COLOR;
        return easterEggsEnabled && localSpeaker && secretPalette ? PILL_LABEL : defaultLabel;
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

    private static int colorValue(Setting.ColorSetting setting) {
        return setting == null ? -1 : setting.getValue();
    }
}
