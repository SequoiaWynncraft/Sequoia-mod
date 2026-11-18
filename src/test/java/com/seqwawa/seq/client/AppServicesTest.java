package com.seqwawa.seq.client;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.seqwawa.seq.managers.FontManager;
import com.seqwawa.seq.network.auth.MinecraftAuthService;
import org.junit.jupiter.api.Test;

class AppServicesTest {

    @Test
    void withNoAppTheGettersAnswerNothingAsBeforeStartUp() {
        AppServices previous = SeqClient.installApp(null);
        try {
            assertNull(SeqClient.app());
            assertNull(SeqClient.getConfigManager());
            assertNull(SeqClient.getFontManager());
            assertNull(SeqClient.getAssetManager());
        } finally {
            SeqClient.installApp(previous);
        }
    }

    @Test
    void aTestsAppAnswersWithWhatItWasGivenAndNothingElse() {
        FontManager fonts = new FontManager();
        MinecraftAuthService auth = new MinecraftAuthService();
        AppServices previous = SeqClient.installApp(AppServices.builder().fontManager(fonts).authService(auth).build());
        try {
            assertSame(fonts, SeqClient.getFontManager());
            assertSame(auth, SeqClient.getAuthService());
            assertNull(SeqClient.getConfigManager());
            assertNull(SeqClient.app().keys(), "no key bindings outside the game");
        } finally {
            SeqClient.installApp(previous);
        }
    }

    @Test
    void installingAnAppHandsBackTheOneItReplaces() {
        AppServices first = AppServices.builder().build();
        AppServices previous = SeqClient.installApp(first);
        try {
            assertSame(first, SeqClient.installApp(AppServices.builder().build()));
        } finally {
            SeqClient.installApp(previous);
        }
    }
}
