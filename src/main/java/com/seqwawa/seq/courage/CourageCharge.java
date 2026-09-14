package com.seqwawa.seq.courage;

import com.seqwawa.seq.integrations.WynntilsPowderSpecialAccess;
import com.seqwawa.seq.model.PowderSpecialReading;
import java.util.Optional;

/** Whether the local player's Courage powder special is ready to fire. */
public final class CourageCharge {
    /** Wynncraft counts the special as ready only at a full bar. */
    static final double FULL = 1.0;

    /** Charges arrive as a rounded fraction, so a hair under a full bar still counts. */
    static final double EPSILON = 1.0e-4;

    /**
     * Latched once the powder bar has been read even once. Before that the gate has
     * no way to tell "not charged" from "no Wynntils to read it with".
     */
    private static boolean everRead;

    private CourageCharge() {}

    /** True only when Courage is known to be fully charged. */
    public static boolean isFull() {
        return isFull(read());
    }

    /** Whether the local player's own aura may be drawn this frame. */
    public static boolean allowsOwnAura() {
        return allowsOwnAura(read(), everRead);
    }

    static boolean isFull(Optional<PowderSpecialReading> reading) {
        return reading.isPresent()
                && reading.get().fire()
                && reading.get().charge() >= FULL - EPSILON;
    }

    /**
     * Swapping weapons blanks the powder bar for a few frames. Once any reading has
     * been seen, that gap is a swap rather than a missing integration, so it counts
     * as "not charged" and the ring cannot flash back on mid-swap.
     *
     * <p>Only a client that has never produced a reading at all — no Wynntils, or a
     * version whose API moved — skips the gate entirely, so the aura still works
     * there instead of vanishing for good.
     */
    static boolean allowsOwnAura(Optional<PowderSpecialReading> reading, boolean hasEverRead) {
        return reading.isPresent() ? isFull(reading) : !hasEverRead;
    }

    private static Optional<PowderSpecialReading> read() {
        Optional<PowderSpecialReading> reading = WynntilsPowderSpecialAccess.read();
        if (reading.isPresent()) {
            everRead = true;
        }
        return reading;
    }
}
