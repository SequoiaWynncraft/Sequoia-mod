package com.seqwawa.seq.network;

import com.google.gson.annotations.SerializedName;

/** Authenticated request to append an OUT entry to the Sequoia treasury sheet. */
public record TreasuryOutRequest(
        String type,
        @SerializedName("request_id") String requestId,
        String amount,
        String payouter,
        String reason) {

    public static final String TYPE = "treasury_out";

    public TreasuryOutRequest(String requestId, String amount, String payouter, String reason) {
        this(TYPE, requestId, amount, payouter, reason);
    }
}
