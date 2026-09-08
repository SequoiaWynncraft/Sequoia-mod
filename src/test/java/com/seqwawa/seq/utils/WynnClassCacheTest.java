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

    @Test
    void readsCanonicalEnumInsteadOfWynntilsCombinedDisplayName() throws ReflectiveOperationException {
        var model = new CharacterModelStub();
        model.hasCharacter = true;
        for (var value : ProviderClass.values()) {
            model.classType = value;
            assertEquals(WynnClassType.valueOf(value.name()), WynnClassCache.readWynntilsClass(model));
        }
    }

    @Test
    void continuedPollingPicksUpLateDetectionAndClassSwitches() throws ReflectiveOperationException {
        var model = new CharacterModelStub();
        assertNull(WynnClassCache.readWynntilsClass(model));
        model.hasCharacter = true;
        assertNull(WynnClassCache.readWynntilsClass(model));
        model.classType = ProviderClass.MAGE;
        assertEquals(WynnClassType.MAGE, WynnClassCache.readWynntilsClass(model));
        model.classType = ProviderClass.SHAMAN;
        assertEquals(WynnClassType.SHAMAN, WynnClassCache.readWynntilsClass(model));
        model.hasCharacter = false;
        assertNull(WynnClassCache.readWynntilsClass(model));
    }

    public static final class CharacterModelStub {
        boolean hasCharacter;
        ProviderClass classType;

        public boolean hasCharacter() { return hasCharacter; }
        public ProviderClass getClassType() { return classType; }
    }

    public enum ProviderClass {
        WARRIOR("Warrior/Knight"), ARCHER("Archer/Hunter"), MAGE("Mage/Dark Wizard"),
        ASSASSIN("Assassin/Ninja"), SHAMAN("Shaman/Skyseer");

        private final String label;
        ProviderClass(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
}
