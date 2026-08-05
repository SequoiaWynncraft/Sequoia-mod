package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private static void withSettings(boolean embeds, boolean filtering, Runnable assertion) {
        Setting.BooleanSetting previousEmbeds = SeqClient.showChatMediaEmbedsSetting;
        Setting.BooleanSetting previousFilter = SeqClient.hideEmbeddedMediaLinksSetting;
        try {
            SeqClient.showChatMediaEmbedsSetting =
                    new Setting.BooleanSetting("show_chat_media_embeds", "chat", embeds);
            SeqClient.hideEmbeddedMediaLinksSetting =
                    new Setting.BooleanSetting("hide_embedded_media_links", "chat", filtering);
            assertion.run();
        } finally {
            SeqClient.showChatMediaEmbedsSetting = previousEmbeds;
            SeqClient.hideEmbeddedMediaLinksSetting = previousFilter;
        }
    }
}
