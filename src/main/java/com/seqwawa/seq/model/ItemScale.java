package com.seqwawa.seq.model;

import java.util.Map;

public record ItemScale(String itemName, Map<String, Double> weights) {
    public ItemScale {
        weights = weights == null ? Map.of() : Map.copyOf(weights);
    }

    /**
     * Weighted average of the item's rolls, on the same 0-100 scale as a roll percentage.
     * Weights are normalised, so they need not add up to 100. A negative weight scores the
     * stat inverted, which is how cost reductions and other "lower is better" stats are handled.
     * Returns {@code null} when the item carries none of the weighted stats.
     */
    public Float score(Map<String, Float> rolls) {
        if (rolls == null || rolls.isEmpty()) {
            return null;
        }

        double total = 0.0;
        double weightSum = 0.0;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            Float roll = rolls.get(entry.getKey());
            if (roll == null) {
                continue;
            }
            double weight = entry.getValue();
            total += (weight < 0 ? 100.0 - roll : roll) * Math.abs(weight);
            weightSum += Math.abs(weight);
        }
        return weightSum == 0.0 ? null : (float) (total / weightSum);
    }
}
