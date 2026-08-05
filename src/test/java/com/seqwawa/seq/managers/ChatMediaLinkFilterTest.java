package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class ChatMediaLinkFilterTest {
    @Test
    void removesSupportedMediaUrlsWhilePreservingOtherTextAndStyles() {
        withSettings(true, true, () -> {
            Component message = Component.empty()
                    .append(Component.literal("Name: "))
                    .append(Component.literal("look https://cdn.example/image.gif now"));

            Component filtered = ChatMediaLinkFilter.filter(message);

            assertEquals("Name: look  now", filtered.getString());
        });
    }

    @Test
    void leavesOrdinaryLinksVisible() {
        withSettings(true, true, () -> assertEquals(
                "read https://example.com/docs",
                ChatMediaLinkFilter.filter(Component.literal("read https://example.com/docs"))
                        .getString()));
    }

    @Test
    void neverHidesLinksWhenEmbedsAreDisabled() {
        withSettings(false, true, () -> assertEquals(
                "https://cdn.example/image.webp",
                ChatMediaLinkFilter.filter(Component.literal("https://cdn.example/image.webp"))
                        .getString()));
    }

    @Test
    void leavesMediaLinksVisibleWhenFilteringIsDisabled() {
        withSettings(true, false, () -> assertEquals(
                "https://cdn.example/image.gif",
                ChatMediaLinkFilter.filter(Component.literal("https://cdn.example/image.gif"))
                        .getString()));
    }

    @Test
    void mediaOnlyBridgeMessagesDoNotLeaveAnEmptySenderLine() {
        withSettings(true, true, () -> {
            assertFalse(ChatMediaLinkFilter.hasVisibleText(
                    "\u2064\u2064 https://cdn.discordapp.com/attachments/example/image.gif"));
            assertTrue(ChatMediaLinkFilter.hasVisibleText(
                    "look https://cdn.discordapp.com/attachments/example/image.gif"));
        });
    }

    @Test
    void zeroDurationKeepsMediaLinksVisible() {
        withSettings(true, true, () -> {
            SeqClient.chatMediaEmbedDurationSetting.setValue(0);
            assertTrue(ChatMediaLinkFilter.hasVisibleText("https://cdn.example/image.gif"));
        });
    }

    private static void withSettings(boolean embeds, boolean filtering, Runnable assertion) {
        Setting.BooleanSetting previousEmbeds = SeqClient.showChatMediaEmbedsSetting;
        Setting.BooleanSetting previousFilter = SeqClient.hideEmbeddedMediaLinksSetting;
        Setting.IntSetting previousDuration = SeqClient.chatMediaEmbedDurationSetting;
        try {
            SeqClient.showChatMediaEmbedsSetting =
                    new Setting.BooleanSetting("show_chat_media_embeds", "chat", embeds);
            SeqClient.hideEmbeddedMediaLinksSetting =
                    new Setting.BooleanSetting("hide_embedded_media_links", "chat", filtering);
            SeqClient.chatMediaEmbedDurationSetting =
                    new Setting.IntSetting("chat_media_embed_duration_seconds", "chat", 5, 0, 10);
            assertion.run();
        } finally {
            SeqClient.showChatMediaEmbedsSetting = previousEmbeds;
            SeqClient.hideEmbeddedMediaLinksSetting = previousFilter;
            SeqClient.chatMediaEmbedDurationSetting = previousDuration;
        }
    }
}
