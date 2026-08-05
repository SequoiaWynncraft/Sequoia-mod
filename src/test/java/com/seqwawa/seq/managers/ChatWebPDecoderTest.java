package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
