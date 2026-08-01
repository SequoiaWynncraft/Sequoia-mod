package com.seqwawa.seq.network;

import com.google.gson.annotations.SerializedName;

/** Successful response to a {@link TreasuryOutRequest}. */
public record TreasuryOutRecordedMessage(
        String type,
        @SerializedName("request_id") String requestId,
        @SerializedName("sheet_name") String sheetName,
        int row,
        String amount,
        String payouter,
        String reason,
        String date) {}
