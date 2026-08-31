package com.seqwawa.seq.model;

import java.util.List;

public record ChatItemPreview(
        String name,
        String subtitle,
        Integer color,
        List<String> attributes,
        List<String> statLines,
        List<StatRoll> statRolls,
        ShinyStat shinyStat,
        List<Section> sections) {
    public ChatItemPreview {
        attributes = attributes == null ? List.of() : attributes;
        statLines = statLines == null ? List.of() : statLines;
        statRolls = statRolls == null ? List.of() : statRolls;
        sections = sections == null ? List.of() : sections;
    }

    public ChatItemPreview(
            String name,
            String subtitle,
            Integer color,
            List<String> attributes,
            List<String> statLines,
            List<StatRoll> statRolls,
            ShinyStat shinyStat) {
        this(name, subtitle, color, attributes, statLines, statRolls, shinyStat, List.of());
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

    /** Additive, display-oriented data for item-type-specific encoding blocks. */
    public record Section(String title, List<String> lines) {
        public Section {
            lines = lines == null ? List.of() : lines;
        }
    }
}
