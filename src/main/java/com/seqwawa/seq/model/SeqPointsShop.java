package com.seqwawa.seq.model;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;
import java.util.List;

/** Server-authoritative Seq Points wallet and catalog snapshot. */
public record SeqPointsShop(
        @SerializedName("schema_version") int schemaVersion,
        @SerializedName("server_time") Instant serverTime,
        Balance balance,
        List<Item> items,
        @SerializedName("recent_transactions") List<Transaction> recentTransactions,
        @SerializedName("active_effects") List<SeqPointsShopEffect> activeEffects) {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;
    public static final SeqPointsShop EMPTY =
            new SeqPointsShop(0, null, new Balance(0, 0, 0, 0), List.of(), List.of(), List.of());

    public SeqPointsShop {
        balance = balance == null ? new Balance(0, 0, 0, 0) : balance;
        items = items == null ? List.of() : List.copyOf(items);
        recentTransactions = recentTransactions == null ? List.of() : List.copyOf(recentTransactions);
        activeEffects = activeEffects == null ? List.of() : List.copyOf(activeEffects);
    }

    public boolean isSupported() {
        return schemaVersion == SUPPORTED_SCHEMA_VERSION;
    }

    public record Balance(long bonus, long war, long total, long version) {}

    public record Item(
            String key,
            String name,
            String description,
            long price,
            String category,
            @SerializedName("fulfillment_type") String fulfillmentType,
            @SerializedName("allow_war_points") boolean allowWarPoints,
            @SerializedName("current_period") String currentPeriod,
            @SerializedName("purchased_this_period") boolean purchasedThisPeriod,
            @SerializedName("ticket_count_this_period") Long ticketCountThisPeriod) {

        public boolean isRename() {
            return "TEMPORARY_RENAME".equals(fulfillmentType);
        }

        public boolean isDraft() {
            return "DRAFT_ENTRY".equals(fulfillmentType);
        }

        public boolean isPayout() {
            return "PAYOUT".equals(fulfillmentType);
        }
    }

    public record Transaction(
            long id,
            String bucket,
            long amount,
            @SerializedName("source_type") String sourceType,
            String description,
            @SerializedName("created_at") Instant createdAt) {}
}
