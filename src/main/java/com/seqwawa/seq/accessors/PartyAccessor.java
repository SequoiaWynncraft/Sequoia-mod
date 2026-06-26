package com.seqwawa.seq.accessors;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.PartyFinderManager;

public interface PartyAccessor {
    default PartyFinderManager party() {
        return SeqClient.getPartyFinderManager();
    }
}
