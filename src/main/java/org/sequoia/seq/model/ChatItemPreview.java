package org.sequoia.seq.model;

import java.util.List;

public record ChatItemPreview(
        String name,
        String subtitle,
        Integer color,
        List<String> attributes,
        List<String> statLines) {}
