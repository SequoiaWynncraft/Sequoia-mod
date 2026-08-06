package com.seqwawa.seq.ui;

import java.util.Objects;
import java.util.Random;

/** Randomly cycles a settings-sidebar prompt through hidden and sliding phases. */
final class PrincessSidebarPrompt {
    static final long SLIDE_DURATION_MS = 400;
    private static final long MIN_HIDDEN_MS = 3_000;
    private static final long MAX_HIDDEN_MS = 9_000;
    private static final long MIN_VISIBLE_MS = 4_000;
    private static final long MAX_VISIBLE_MS = 10_000;

    enum Phase {
        HIDDEN,
        ENTERING,
        VISIBLE,
        EXITING
    }

    private final Random random;
    private Phase phase = Phase.HIDDEN;
    private long phaseStartedAtMs;
    private long phaseEndsAtMs;

    PrincessSidebarPrompt(Random random, long nowMs) {
        this.random = Objects.requireNonNull(random);
        phaseStartedAtMs = nowMs;
        phaseEndsAtMs = nowMs + randomDuration(MIN_HIDDEN_MS, MAX_HIDDEN_MS);
    }

    void update(long nowMs) {
        while (nowMs >= phaseEndsAtMs) {
            long nextPhaseStartedAt = phaseEndsAtMs;
            phase = switch (phase) {
                case HIDDEN -> Phase.ENTERING;
                case ENTERING -> Phase.VISIBLE;
                case VISIBLE -> Phase.EXITING;
                case EXITING -> Phase.HIDDEN;
            };
            phaseStartedAtMs = nextPhaseStartedAt;
            phaseEndsAtMs = nextPhaseStartedAt + durationFor(phase);
        }
    }

    float slideProgress(long nowMs) {
        update(nowMs);
        return switch (phase) {
            case HIDDEN -> 0f;
            case VISIBLE -> 1f;
            case ENTERING -> ease(progressThroughPhase(nowMs));
            case EXITING -> 1f - ease(progressThroughPhase(nowMs));
        };
    }

    Phase phase() {
        return phase;
    }

    long phaseEndsAtMs() {
        return phaseEndsAtMs;
    }

    private long durationFor(Phase newPhase) {
        return switch (newPhase) {
            case HIDDEN -> randomDuration(MIN_HIDDEN_MS, MAX_HIDDEN_MS);
            case ENTERING, EXITING -> SLIDE_DURATION_MS;
            case VISIBLE -> randomDuration(MIN_VISIBLE_MS, MAX_VISIBLE_MS);
        };
    }

    private long randomDuration(long minimum, long maximum) {
        return random.nextLong(minimum, maximum + 1);
    }

    private float progressThroughPhase(long nowMs) {
        long duration = phaseEndsAtMs - phaseStartedAtMs;
        return Math.clamp((float) (nowMs - phaseStartedAtMs) / duration, 0f, 1f);
    }

    private static float ease(float progress) {
        return 1f - (1f - progress) * (1f - progress);
    }
}
