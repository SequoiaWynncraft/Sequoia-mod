package com.seqwawa.seq.network;

import com.google.gson.annotations.SerializedName;

/** Request to append an OUT entry; the backend parses and normalizes {@code amount}. */
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
