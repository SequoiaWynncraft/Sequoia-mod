package com.seqwawa.seq.managers;

/**
 * Schedules fast aspect-award waves while using Wynncraft's reward messages as
 * the success signal. Constants describe real client-tick intervals: at the
 * normal 20 ticks per second, 2 ticks is 100 ms and 10 ticks is 500 ms.
 */
final class AspectAwardWaveController implements RewardClickController {
    static final int CLICK_INTERVAL_TICKS = 2;
    static final int WAVE_SETTLE_TICKS = 10;
    static final int NO_PROGRESS_TIMEOUT_TICKS = 20;

    private final long targetAmount;
    private final String localUsername;
    private final String recipientUsername;

    private Phase phase = Phase.CLICKING;
    private long confirmedAmount;
    private long confirmedAtWaveStart;
    private long waveAmountRemaining;
    private int ticksUntilNextClick;
    private int settleTicks;

    AspectAwardWaveController(long targetAmount, String localUsername, String recipientUsername) {
        if (targetAmount <= 0) {
            throw new IllegalArgumentException("targetAmount must be positive");
        }
        this.targetAmount = targetAmount;
        this.localUsername = localUsername;
        this.recipientUsername = recipientUsername;
        this.waveAmountRemaining = targetAmount;
    }

    @Override
    public NextAction tick() {
        if (targetReached()) {
            return NextAction.COMPLETE;
        }

        if (phase == Phase.CLICKING) {
            if (ticksUntilNextClick > 0) {
                ticksUntilNextClick--;
                if (ticksUntilNextClick > 0) {
                    return NextAction.WAIT;
                }
            }
            return NextAction.CLICK;
        }

        settleTicks++;
        if (targetReached()) {
            return NextAction.COMPLETE;
        }
        if (settleTicks < WAVE_SETTLE_TICKS) {
            return NextAction.WAIT;
        }
        if (confirmedAmount > confirmedAtWaveStart) {
            startCorrectionWave();
            return NextAction.CLICK;
        }
        if (settleTicks >= NO_PROGRESS_TIMEOUT_TICKS) {
            return NextAction.STALLED;
        }
        return NextAction.WAIT;
    }

    @Override
    public void recordClick(long amount) {
        if (phase != Phase.CLICKING || amount <= 0) {
            throw new IllegalStateException("No positive aspect click is currently scheduled");
        }

        waveAmountRemaining = Math.max(0L, waveAmountRemaining - amount);
        if (waveAmountRemaining == 0L) {
            phase = Phase.SETTLING;
            settleTicks = 0;
            ticksUntilNextClick = 0;
            return;
        }
        ticksUntilNextClick = CLICK_INTERVAL_TICKS;
    }

    @Override
    public ConfirmationProgress recordRewardGrant(GuildStorageTracker.RewardGrant rewardGrant) {
        if (!matchesConfirmation(rewardGrant)) {
            return null;
        }
        recordConfirmation(rewardGrant.amount());
        return new ConfirmationProgress(confirmedAmount, targetAmount);
    }

    @Override
    public String stalledMessage() {
        return "Aspect awarding stalled at %d/%d confirmed; stopped to avoid uncontrolled overshoot."
                .formatted(confirmedAmount, targetAmount);
    }

    private void recordConfirmation(long amount) {
        if (amount <= 0 || confirmedAmount == Long.MAX_VALUE) {
            return;
        }
        if (Long.MAX_VALUE - confirmedAmount < amount) {
            confirmedAmount = Long.MAX_VALUE;
        } else {
            confirmedAmount += amount;
        }
    }

    long targetAmount() {
        return targetAmount;
    }

    long confirmedAmount() {
        return confirmedAmount;
    }

    long remainingToConfirm() {
        return Math.max(0L, targetAmount - confirmedAmount);
    }

    private boolean targetReached() {
        return confirmedAmount >= targetAmount;
    }

    private void startCorrectionWave() {
        phase = Phase.CLICKING;
        waveAmountRemaining = remainingToConfirm();
        confirmedAtWaveStart = confirmedAmount;
        settleTicks = 0;
        ticksUntilNextClick = 0;
    }

    private boolean matchesConfirmation(GuildStorageTracker.RewardGrant rewardGrant) {
        return rewardGrant != null
                && rewardGrant.resourceType() == GuildStorageTracker.ResourceType.ASPECTS
                && equalsIgnoreCase(rewardGrant.senderUsername(), localUsername)
                && equalsIgnoreCase(rewardGrant.recipientUsername(), recipientUsername);
    }

    private static boolean equalsIgnoreCase(String first, String second) {
        return first != null && second != null && first.equalsIgnoreCase(second);
    }

    private enum Phase {
        CLICKING,
        SETTLING
    }
}
