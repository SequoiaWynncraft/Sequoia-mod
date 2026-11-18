package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConnectionScreenTest {

    @Test
    void aConnectedPlayerAccountGetsADisconnectButton() {
        // The click used to look only at the Treasury connection, so this button connected again.
        assertTrue(ConnectionScreen.showsConnected(false, false, true));
        assertFalse(ConnectionScreen.showsConnected(false, false, false));
    }

    @Test
    void theTreasuryAccountGoesByItsOwnConnection() {
        assertTrue(ConnectionScreen.showsConnected(true, true, false));
        assertFalse(ConnectionScreen.showsConnected(true, false, true));
    }
}
