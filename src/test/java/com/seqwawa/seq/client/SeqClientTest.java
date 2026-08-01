package com.seqwawa.seq.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.seqwawa.seq.network.WynncraftServerPolicy;

class SeqClientTest {

    private static final UUID OPERATOR_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final UUID SHARED_ACCOUNT_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    void switchingToSharedTreasuryAccountDoesNotPreserveAnotherAccountsSession() {
        assertFalse(SeqClient.shouldPreserveOperatorSession(SHARED_ACCOUNT_UUID, OPERATOR_UUID.toString()));
    }

    @Test
    void switchingBackToAuthenticatedOperatorPreservesSession() {
        assertTrue(SeqClient.shouldPreserveOperatorSession(OPERATOR_UUID, OPERATOR_UUID.toString()));
    }

    @Test
    void unrelatedAccountStillDropsMismatchedOperatorSession() {
        assertFalse(SeqClient.shouldPreserveOperatorSession(SHARED_ACCOUNT_UUID, OPERATOR_UUID.toString()));
        assertFalse(SeqClient.shouldPreserveOperatorSession(SHARED_ACCOUNT_UUID, null));
    }

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
