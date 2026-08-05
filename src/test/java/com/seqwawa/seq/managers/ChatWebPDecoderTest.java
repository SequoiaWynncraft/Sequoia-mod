package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ChatWebPDecoderTest {
    private static final String TWO_BY_TWO_RED_WEBP =
            "UklGRjwAAABXRUJQVlA4IDAAAADQAQCdASoCAAIAAgA0JaACdLoB+AADsAD+8Oj3/yC5YXXI1/8gP+QH/ID/+PIAAAA=";

    @Test
    void convertsWebPToNanoVgFriendlyPngFrames() throws Exception {
        ChatWebPDecoder.DecodedWebP decoded =
                ChatWebPDecoder.decode(Base64.getDecoder().decode(TWO_BY_TWO_RED_WEBP));

        assertEquals(1, decoded.pngFrames().size());
        assertEquals(1, decoded.delaysMs().size());
        assertTrue(decoded.pngFrames().getFirst().length > 0);
    }

    @Test
    void samplesLongAnimationsWithinTheGpuFrameLimit() {
        assertEquals(1, ChatWebPDecoder.samplingStride(120));
        assertEquals(2, ChatWebPDecoder.samplingStride(121));
        assertEquals(4, ChatWebPDecoder.samplingStride(395));
    }

    @Test
    void animationFrameSelectionAdvancesAndLoopsFromVisibleTime() {
        java.util.List<Integer> delays = java.util.List.of(37, 36, 37);

        assertEquals(0, ChatMediaEmbedManager.frameIndexAt(delays, 110, 0));
        assertEquals(0, ChatMediaEmbedManager.frameIndexAt(delays, 110, 36));
        assertEquals(1, ChatMediaEmbedManager.frameIndexAt(delays, 110, 37));
        assertEquals(2, ChatMediaEmbedManager.frameIndexAt(delays, 110, 73));
        assertEquals(0, ChatMediaEmbedManager.frameIndexAt(delays, 110, 110));
    }

    @Test
    void discordProxyIsFallbackAfterLiteralAnimatedMediaUrl() {
        URI original = URI.create("https://gif.fxtwitter.com/tweet_video/example.webp");
        URI discordProxy = URI.create("https://images-ext-1.discordapp.net/external/example.webp");

        assertEquals(
                java.util.List.of(original, discordProxy),
                ChatMediaEmbedManager.fallbackCandidates(original, java.util.List.of(discordProxy)));
    }
}
