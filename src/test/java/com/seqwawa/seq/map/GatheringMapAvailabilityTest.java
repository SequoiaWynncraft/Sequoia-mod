package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GatheringMapAvailabilityTest {

    @Test
    void reportsBackendFailureWhenNoCacheExists() {
        assertEquals(
                GatheringMapAvailability.NO_CACHE_BACKEND_ERROR,
                GatheringMapAvailability.afterBackendFailure(false).orElseThrow());
        assertTrue(GatheringMapAvailability.NO_CACHE_BACKEND_ERROR.contains("/seq map refresh"));
    }

    @Test
    void keepsUsingCacheWhenBackendFails() {
        assertTrue(GatheringMapAvailability.afterBackendFailure(true).isEmpty());
    }
}
