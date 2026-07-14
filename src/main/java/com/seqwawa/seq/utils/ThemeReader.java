package com.seqwawa.seq.utils;

import com.seqwawa.seq.ui.theme.Theme;
import com.seqwawa.seq.ui.theme.UiColor;
import java.awt.Color;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;

public final class ThemeReader {
    private ThemeReader() {
    }

    public static Theme fromFile(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return fromReader(reader, path.toString());
        }
    }

    public static Theme fromStream(InputStream input, String source) throws IOException {
        return fromReader(new InputStreamReader(input, StandardCharsets.UTF_8), source);
    }

    static Theme fromReader(Reader input, String source) throws IOException {
        EnumMap<UiColor, Color> colors = new EnumMap<>(UiColor.class);
        Set<String> seenKeys = new HashSet<>();
        String name = null;

        try (BufferedReader reader = new BufferedReader(input)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String value = line.trim();
                if (value.isEmpty() || value.startsWith("#")) {
                    continue;
                }

                int separator = value.indexOf('=');
                if (separator <= 0 || separator == value.length() - 1) {
                    throw invalid(source, lineNumber, "expected key=value");
                }
                String key = value.substring(0, separator).trim();
                String rawValue = value.substring(separator + 1).trim();
                if (!seenKeys.add(key)) {
                    throw invalid(source, lineNumber, "duplicate key '" + key + "'");
                }
                if (key.equals("name")) {
                    if (rawValue.isBlank()) {
                        throw invalid(source, lineNumber, "theme name cannot be blank");
                    }
                    name = rawValue;
                    continue;
                }

                UiColor token = UiColor.fromKey(key);
                if (token == null) {
                    throw invalid(source, lineNumber, "unknown color '" + key + "'");
                }
                colors.put(token, parseColor(rawValue, source, lineNumber));
            }
        }

        if (name == null) {
            throw new IOException(source + ": missing theme name");
        }
        for (UiColor token : UiColor.values()) {
            if (token.required() && !colors.containsKey(token)) {
                throw new IOException(source + ": missing required color '" + token.key() + "'");
            }
        }
        return new Theme(name, colors);
    }

    private static Color parseColor(String value, String source, int lineNumber) throws IOException {
        if (!value.startsWith("(") || !value.endsWith(")")) {
            throw invalid(source, lineNumber, "expected color as (red,green,blue,alpha)");
        }
        String[] components = value.substring(1, value.length() - 1).split(",", -1);
        if (components.length != 4) {
            throw invalid(source, lineNumber, "expected four color components");
        }

        int[] rgba = new int[4];
        for (int index = 0; index < components.length; index++) {
            try {
                rgba[index] = Integer.parseInt(components[index].trim());
            } catch (NumberFormatException exception) {
                throw invalid(source, lineNumber, "color components must be integers");
            }
            if (rgba[index] < 0 || rgba[index] > 255) {
                throw invalid(source, lineNumber, "color components must be between 0 and 255");
            }
        }
        return new Color(rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    private static IOException invalid(String source, int lineNumber, String message) {
        return new IOException(source + ":" + lineNumber + ": " + message);
    }
}
