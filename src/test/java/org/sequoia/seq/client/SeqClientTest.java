package org.sequoia.seq.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.sequoia.seq.network.WynncraftServerPolicy;

class SeqClientTest {

    @Test
    void productionScopeRecoveryTriggersImmediateReconnect() {
        assertEquals(
                SeqClient.AutoConnectTrigger.SCOPE_RECOVERY,
                SeqClient.determineAutoConnectTrigger(
                        true,
                        WynncraftServerPolicy.Scope.MAIN,
                        WynncraftServerPolicy.Scope.UNKNOWN,
                        true,
                        61_000L,
                        60_500L,
                        60_000L));
    }

    @Test
    void periodicRecoveryTriggersAfterIntervalOnMain() {
        assertEquals(
                SeqClient.AutoConnectTrigger.PERIODIC_RECOVERY,
                SeqClient.determineAutoConnectTrigger(
                        true,
                        WynncraftServerPolicy.Scope.MAIN,
                        WynncraftServerPolicy.Scope.MAIN,
                        true,
                        120_000L,
                        60_000L,
                        60_000L));
    }

    @Test
    void periodicRecoveryWaitsUntilIntervalExpires() {
        assertEquals(
                SeqClient.AutoConnectTrigger.NONE,
                SeqClient.determineAutoConnectTrigger(
                        true,
                        WynncraftServerPolicy.Scope.MAIN,
                        WynncraftServerPolicy.Scope.MAIN,
                        true,
                        119_999L,
                        60_000L,
                        60_000L));
    }

    @Test
    void reconnectSkipsWhenAutoConnectCannotRun() {
        assertEquals(
                SeqClient.AutoConnectTrigger.NONE,
                SeqClient.determineAutoConnectTrigger(
                        true,
                        WynncraftServerPolicy.Scope.MAIN,
                        WynncraftServerPolicy.Scope.UNKNOWN,
                        false,
                        120_000L,
                        0L,
                        60_000L));
        assertEquals(
                SeqClient.AutoConnectTrigger.NONE,
                SeqClient.determineAutoConnectTrigger(
                        false,
                        WynncraftServerPolicy.Scope.MAIN,
                        WynncraftServerPolicy.Scope.UNKNOWN,
                        true,
                        120_000L,
                        0L,
                        60_000L));
        assertEquals(
                SeqClient.AutoConnectTrigger.NONE,
                SeqClient.determineAutoConnectTrigger(
                        true,
                        WynncraftServerPolicy.Scope.UNKNOWN,
                        WynncraftServerPolicy.Scope.UNKNOWN,
                        true,
                        120_000L,
                        0L,
                        60_000L));
    }
}
