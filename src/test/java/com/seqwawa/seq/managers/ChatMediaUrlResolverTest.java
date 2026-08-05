package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.Test;

class ChatMediaUrlResolverTest {
    @Test
    void rewritesSevenTvAvifToItsAnimatedWebpSibling() {
        URI original = URI.create("https://cdn.7tv.app/emote/id/4x.avif?quality=lossless");

        assertEquals(
                URI.create("https://cdn.7tv.app/emote/id/4x.webp?quality=lossless"),
                ChatMediaUrlResolver.preferredMediaUri(original));
    }

    @Test
    void usesCrawlerPresentationForKlipyPages() {
        assertEquals(
                "Discordbot/2.0",
                ChatMediaUrlResolver.userAgent(URI.create("https://klipy.com/gifs/example")));
        assertEquals(
                "Sequoia-Mod-Chat-Preview/1",
                ChatMediaUrlResolver.userAgent(URI.create("https://example.com/image.gif")));
    }

    @Test
    void detectsOnlySupportedMediaCandidatesForChatFiltering() {
        assertTrue(ChatMediaUrlResolver.isDisplayableCandidate(
                URI.create("https://cdn.discordapp.com/attachments/example.gif")));
        assertTrue(ChatMediaUrlResolver.isDisplayableCandidate(
                URI.create("https://klipy.com/gifs/example")));
        assertTrue(ChatMediaUrlResolver.isDisplayableCandidate(
                URI.create("https://cdn.7tv.app/emote/id/4x.avif")));
        assertFalse(ChatMediaUrlResolver.isDisplayableCandidate(
                URI.create("https://example.com/docs")));
    }
}
