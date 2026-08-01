package com.seqwawa.seq.managers;

/** Controls when a guild reward automation should wait, click, or finish. */
interface RewardClickController {
    NextAction tick();

    void recordClick(long amount);

    default ConfirmationProgress recordRewardGrant(GuildStorageTracker.RewardGrant rewardGrant) {
        return null;
    }

    default boolean recordInsufficientEmeralds() {
        return false;
    }

    default String stalledMessage() {
        return "Guild reward automation stalled.";
    }

    enum NextAction {
        WAIT,
        CLICK,
        COMPLETE,
        STALLED
    }

    record ConfirmationProgress(long confirmedAmount, long targetAmount) {}
}
