package com.seqwawa.seq.managers;

/** Fixed-interval click scheduling for emerald and tome rewards. */
final class PacedRewardClickController implements RewardClickController {
    static final int EMERALD_CLICK_INTERVAL_TICKS = 3;
    static final int TOME_CLICK_INTERVAL_TICKS = 9;

    private final int clickIntervalTicks;
    private final boolean completeOnInsufficientEmeralds;

    private long remainingAmount;
    private int ticksUntilNextClick;
    private int clicksSent;
    private boolean serverReportedComplete;

    static PacedRewardClickController emeralds(long amount) {
        return new PacedRewardClickController(
                amount, EMERALD_CLICK_INTERVAL_TICKS, amount == Long.MAX_VALUE);
    }

    static PacedRewardClickController tomes(long amount) {
        return new PacedRewardClickController(amount, TOME_CLICK_INTERVAL_TICKS, false);
    }

    private PacedRewardClickController(
            long amount, int clickIntervalTicks, boolean completeOnInsufficientEmeralds) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (clickIntervalTicks <= 0) {
            throw new IllegalArgumentException("clickIntervalTicks must be positive");
        }
        this.remainingAmount = amount;
        this.clickIntervalTicks = clickIntervalTicks;
        this.completeOnInsufficientEmeralds = completeOnInsufficientEmeralds;
    }

    @Override
    public NextAction tick() {
        if (serverReportedComplete) {
            return NextAction.COMPLETE;
        }
        if (ticksUntilNextClick > 0) {
            ticksUntilNextClick--;
            if (ticksUntilNextClick > 0) {
                return NextAction.WAIT;
            }
        }
        return remainingAmount <= 0 ? NextAction.COMPLETE : NextAction.CLICK;
    }

    @Override
    public void recordClick(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        clicksSent++;
        if (remainingAmount != Long.MAX_VALUE) {
            remainingAmount = Math.max(0L, remainingAmount - amount);
        }
        ticksUntilNextClick = clickIntervalTicks;
    }

    @Override
    public boolean recordInsufficientEmeralds() {
        if (!completeOnInsufficientEmeralds || clicksSent == 0) {
            return false;
        }
        serverReportedComplete = true;
        return true;
    }
}
