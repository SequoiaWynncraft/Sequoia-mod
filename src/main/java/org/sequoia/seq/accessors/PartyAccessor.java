package org.sequoia.seq.accessors;

import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.managers.PartyFinderManager;

public interface PartyAccessor {
    default PartyFinderManager party() {
        return SeqClient.getPartyFinderManager();
    }
}
