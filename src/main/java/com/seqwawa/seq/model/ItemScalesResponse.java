package com.seqwawa.seq.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public record ItemScalesResponse(
        @SerializedName("schema_version") int schemaVersion,
        List<ScaleDefinition> scales) {

    /**
     * Weights are keyed by Wynncraft stat api name ({@code rawHealth}, {@code walkSpeed}, ...)
     * and need not add up to anything in particular: {@link ItemScale} normalises them.
     * {@code itemName} is the name the client sees on the item, not the internal one.
     */
    public record ScaleDefinition(
            @SerializedName("item_name") String itemName,
            String type,
            Map<String, Double> weights) {}
}
