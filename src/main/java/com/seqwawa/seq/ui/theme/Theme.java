package com.seqwawa.seq.ui.theme;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class Theme {
    private final String name;
    private final Map<UiColor, Color> colors;

    public Theme(String name, Map<UiColor, Color> colors) {
        this.name = Objects.requireNonNull(name, "name");
        EnumMap<UiColor, Color> resolved = new EnumMap<>(UiColor.class);
        for (UiColor token : UiColor.values()) {
            resolved.put(token, colors.getOrDefault(token, token.fallback()));
        }
        this.colors = Map.copyOf(resolved);
    }

    public String name() {
        return name;
    }

    public Color color(UiColor token) {
        return colors.get(Objects.requireNonNull(token, "token"));
    }

    public Color color(UiColor token, int alpha) {
        Color color = color(token);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public Map<UiColor, Color> colors() {
        return colors;
    }

    public static Theme defaults() {
        return new Theme("default", Map.of());
    }
}
