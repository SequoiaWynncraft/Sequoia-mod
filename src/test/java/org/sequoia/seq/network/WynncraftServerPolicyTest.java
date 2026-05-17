package org.sequoia.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class WynncraftServerPolicyTest {

    @Test
    void classifyAddressAllowsMainWynncraftHosts() {
        assertEquals(WynncraftServerPolicy.Scope.MAIN, WynncraftServerPolicy.classifyAddress("wynncraft.com"));
        assertEquals(WynncraftServerPolicy.Scope.MAIN, WynncraftServerPolicy.classifyAddress("play.wynncraft.com"));
        assertEquals(
                WynncraftServerPolicy.Scope.MAIN,
                WynncraftServerPolicy.classifyAddress("wc3.wynncraft.com:25565"));
    }

    @Test
    void classifyAddressBlocksBetaHosts() {
        assertEquals(WynncraftServerPolicy.Scope.BLOCKED, WynncraftServerPolicy.classifyAddress("beta.wynncraft.com"));
        assertEquals(
                WynncraftServerPolicy.Scope.BLOCKED,
                WynncraftServerPolicy.classifyAddress("wc1.beta.wynncraft.com:25565"));
    }

    @Test
    void classifyAddressBlocksUnknownHosts() {
        assertEquals(WynncraftServerPolicy.Scope.BLOCKED, WynncraftServerPolicy.classifyAddress("localhost:25565"));
        assertEquals(WynncraftServerPolicy.Scope.BLOCKED, WynncraftServerPolicy.classifyAddress("example.com"));
        assertEquals(WynncraftServerPolicy.Scope.UNKNOWN, WynncraftServerPolicy.classifyAddress(null));
    }

    @Test
    void classifyCurrentServerTreatsSingleplayerAsBlocked() {
        assertEquals(
                WynncraftServerPolicy.Scope.BLOCKED,
                WynncraftServerPolicy.classifyCurrentServer(null, false, true, false));
        assertEquals(
                WynncraftServerPolicy.Scope.BLOCKED,
                WynncraftServerPolicy.classifyCurrentServer("play.wynncraft.com", true, false, true));
    }

    @Test
    void classifyCurrentServerTreatsMultiplayerTransferAsUnknown() {
        assertEquals(
                WynncraftServerPolicy.Scope.UNKNOWN,
                WynncraftServerPolicy.classifyCurrentServer(null, false, false, true));
    }

    @Test
    void classifyCurrentServerTreatsMenuWithoutServerAsBlocked() {
        assertEquals(
                WynncraftServerPolicy.Scope.BLOCKED,
                WynncraftServerPolicy.classifyCurrentServer(null, false, false, false));
    }

    @Test
    void normalizeHostStripsSchemePortAndTrailingDot() {
        assertEquals("play.wynncraft.com", WynncraftServerPolicy.normalizeHost("https://play.wynncraft.com:443/."));
        assertEquals("beta.wynncraft.com", WynncraftServerPolicy.normalizeHost("beta.wynncraft.com."));
        assertNull(WynncraftServerPolicy.normalizeHost("   "));
    }
}
