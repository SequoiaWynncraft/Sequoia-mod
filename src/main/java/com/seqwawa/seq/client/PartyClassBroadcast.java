package com.seqwawa.seq.client;

import com.seqwawa.seq.model.WynnClassType;
import com.seqwawa.seq.network.ConnectionManager;
import com.seqwawa.seq.utils.WynnClassCache;

/**
 * Tells the party finder which class you are playing whenever you join a party or switch
 * class in one, and not again until either happens. Game thread only.
 */
public final class PartyClassBroadcast {

    private boolean wasInParty;
    private WynnClassType lastSent;

    /** Forgets what was sent, so the next party hears it again: the session that heard it ended. */
    public void forget() {
        wasInParty = false;
        lastSent = null;
    }

    /** Called each tick on Wynncraft's main servers. */
    void tick(boolean inParty) {
        if (!inParty) {
            forget();
            return;
        }
        if (!ConnectionManager.isConnected()) {
            wasInParty = true;
            return;
        }
        WynnClassType current = WynnClassCache.resolveLocalClassType();
        if (current == null) {
            wasInParty = true;
            return;
        }
        if (!wasInParty || current != lastSent) {
            ConnectionManager.getInstance().sendPartyClassUpdate(current);
            lastSent = current;
        }
        wasInParty = true;
    }
}
