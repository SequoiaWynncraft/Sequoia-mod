package com.seqwawa.seq.model.war;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class WarCompositionRoleTest {
    @Test
    void ordersAndDeduplicatesCapabilitiesInContractOrder() {
        assertEquals(
                List.of(WarCompositionRole.SOLO, WarCompositionRole.DPS, WarCompositionRole.TANK),
                WarCompositionRole.ordered(List.of(
                        WarCompositionRole.TANK,
                        WarCompositionRole.DPS,
                        WarCompositionRole.SOLO,
                        WarCompositionRole.DPS)));
        assertEquals(List.of(), WarCompositionRole.ordered(null));
        assertEquals(List.of(), WarCompositionRole.ordered(List.of()));
    }

    @Test
    void mapsCapabilitiesToBundledWeaponAssets() {
        assertEquals("mage", WarCompositionRole.SOLO.assetKey());
        assertEquals("shaman", WarCompositionRole.DPS.assetKey());
        assertEquals("warrior", WarCompositionRole.TANK.assetKey());
    }
}
