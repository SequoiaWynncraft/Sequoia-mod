package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PlayerHeadCacheTest {

    @Test
    void aUuidWithOrWithoutDashesIsOneKey() {
        String expected = "806c8e32aaaabbbbccccdddddddddddd";
        assertEquals(expected, PlayerHeadCache.normalize("806c8e32-aaaa-bbbb-cccc-dddddddddddd"));
        assertEquals(expected, PlayerHeadCache.normalize(" 806C8E32AAAABBBBCCCCDDDDDDDDDDDD "));
    }

    @Test
    void anythingThatIsNotAUuidNeverReachesTheUrl() {
        assertNull(PlayerHeadCache.normalize(null));
        assertNull(PlayerHeadCache.normalize(""));
        assertNull(PlayerHeadCache.normalize("Notch"), "a username is not a uuid");
        assertNull(PlayerHeadCache.normalize("806c8e32aaaabbbbccccdddddddddd/x"), "a slash would change the path");
        assertNull(PlayerHeadCache.normalize("806c8e32 aaaabbbbccccdddddddddddd"), "a space would throw in URI.create");
        assertNull(PlayerHeadCache.normalize("806c8e32aaaabbbbccccddddddddddd?"), "a query would change the request");
    }
}
