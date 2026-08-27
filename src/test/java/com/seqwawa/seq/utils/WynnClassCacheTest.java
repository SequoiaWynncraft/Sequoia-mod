package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.seqwawa.seq.model.WynnClassType;
import org.junit.jupiter.api.Test;

class WynnClassCacheTest {
    @Test
    void parsesCanonicalAndReskinnedClassNamesWithoutRuntimeLookup() {
        assertEquals(WynnClassType.WARRIOR, WynnClassCache.parseClassType("WARRIOR"));
        assertEquals(WynnClassType.WARRIOR, WynnClassCache.parseClassType("Knight"));
        assertEquals(WynnClassType.ARCHER, WynnClassCache.parseClassType("hunter"));
        assertEquals(WynnClassType.MAGE, WynnClassCache.parseClassType("Wizard"));
        assertEquals(WynnClassType.ASSASSIN, WynnClassCache.parseClassType("ninja"));
        assertEquals(WynnClassType.SHAMAN, WynnClassCache.parseClassType("Skyseer"));
        assertEquals(WynnClassType.MAGE, WynnClassCache.parseClassType("Dark Wizard"));
        assertNull(WynnClassCache.parseClassType("NONE"));
        assertNull(WynnClassCache.parseClassType("Mage Island"));
        assertNull(WynnClassCache.parseClassType("Class Req: Archer"));
        assertNull(WynnClassCache.parseClassType(null));
    }
}
