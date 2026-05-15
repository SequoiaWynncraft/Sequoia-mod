package org.sequoia.seq.model;

import java.util.List;

public record ChatItemPreview(
        String name,
        String subtitle,
        Integer color,
        List<String> attributes,
        List<String> statLines,
        List<StatRoll> statRolls,
        ShinyStat shinyStat) {
    public ChatItemPreview {
        attributes = attributes == null ? List.of() : attributes;
        statLines = statLines == null ? List.of() : statLines;
        statRolls = statRolls == null ? List.of() : statRolls;
    }

    public ChatItemPreview(
            String name,
            String subtitle,
            Integer color,
            List<String> attributes,
            List<String> statLines,
            List<StatRoll> statRolls) {
        this(name, subtitle, color, attributes, statLines, statRolls, null);
    }

    public ChatItemPreview(
            String name,
            String subtitle,
            Integer color,
            List<String> attributes,
            List<String> statLines) {
        this(name, subtitle, color, attributes, statLines, List.of(), null);
    }

    public record StatRoll(String apiName, String key, String displayName, int value, Float percentage) {}

    public record ShinyStat(String key, String displayName, long value, int rerolls) {}
}
