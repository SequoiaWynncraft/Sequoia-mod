package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.seqwawa.seq.integrations.WynntilsGearSharingAccess;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GearSharingProtectionManagerTest {
    @Test
    void immediatelyInvokesOncePerEnabledWorldChangeWithoutPlayerOrTicks() {
        AtomicInteger invocations = new AtomicInteger();
        var manager = new GearSharingProtectionManager(() -> {
            invocations.incrementAndGet();
            return WynntilsGearSharingAccess.Result.CHANGED;
        });
        manager.onWorldChange(true);
        assertEquals(1, invocations.get());
        manager.onWorldChange(true);
        assertEquals(2, invocations.get());
    }

    @Test
    void disabledSettingSkipsIntegrationAndEnablingAppliesOnNextCallback() {
        AtomicInteger invocations = new AtomicInteger();
        var manager = new GearSharingProtectionManager(() -> {
            invocations.incrementAndGet();
            return WynntilsGearSharingAccess.Result.UNAVAILABLE;
        });
        manager.onWorldChange(false);
        assertEquals(0, invocations.get());
        manager.onWorldChange(true);
        assertEquals(1, invocations.get());
        manager.onWorldChange(false);
        assertEquals(1, invocations.get());
    }
}
