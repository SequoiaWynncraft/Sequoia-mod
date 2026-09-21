package com.seqwawa.seq.map;

import java.util.Optional;

final class GatheringMapAvailability {
    static final String REFRESH_COMMAND = "/seq map refresh";
    static final String NO_CACHE_BACKEND_ERROR =
            "No cached map is available and the Sequoia backend could not provide one. Run "
                    + REFRESH_COMMAND
                    + " to try again.";

    private GatheringMapAvailability() {}

    static Optional<String> afterBackendFailure(boolean cachedMapAvailable) {
        return cachedMapAvailable ? Optional.empty() : Optional.of(NO_CACHE_BACKEND_ERROR);
    }
}
