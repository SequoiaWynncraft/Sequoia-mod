package com.seqwawa.seq.model;

import com.google.gson.annotations.SerializedName;
import java.util.UUID;

/** Completed shop purchase and refreshed state. */
public record SeqPointsPurchase(
        @SerializedName("order_id") UUID orderId,
        String message,
        SeqPointsShop shop) {}
