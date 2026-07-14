package com.seqwawa.seq.utils;

import com.seqwawa.seq.ui.theme.Theme;
import com.seqwawa.seq.ui.theme.UiColor;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

public final class ThemeReader {
    private static final int MAX_THEME_CODE_POINTS = 256 * 1024;
    private static final Pattern THEME_NAME = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,63}$");
    private static final Set<String> ROOT_KEYS = Set.of(
            "name", "palette", "background", "accent", "text", "control", "status", "map");
    private static final Map<String, UiColor> COLORS_BY_PATH = colorsByPath();
    private static final Map<String, Set<String>> KEYS_BY_GROUP = keysByGroup();
    private static final LoadSettings LOAD_SETTINGS = LoadSettings.builder()
            .setLabel("Sequoia UI theme")
            .setAllowDuplicateKeys(false)
            .setAllowRecursiveKeys(false)
            .setMaxAliasesForCollections(32)
            .setCodePointLimit(MAX_THEME_CODE_POINTS)
            .build();

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
        Object document;
        try {
            document = new Load(LOAD_SETTINGS).loadFromReader(input);
        } catch (RuntimeException exception) {
            throw new IOException(source + ": invalid YAML: " + exception.getMessage(), exception);
        }

        Map<?, ?> root = requireMap(document, source, "theme document");
        validateRootKeys(root, source);
        String name = requireThemeName(root.get("name"), source);
        validatePalette(root.get("palette"), source);

        EnumMap<UiColor, Color> colors = new EnumMap<>(UiColor.class);
        for (Map.Entry<?, ?> rootEntry : root.entrySet()) {
            String group = requireStringKey(rootEntry.getKey(), source, "top-level key");
            if (group.equals("name") || group.equals("palette")) {
                continue;
            }
            parseGroup(group, rootEntry.getValue(), colors, source);
        }

        for (UiColor token : UiColor.values()) {
            if (token.required() && !colors.containsKey(token)) {
                throw new IOException(source + ": missing required color '" + token.key() + "'");
            }
        }
        return new Theme(name, colors);
    }

    private static void validateRootKeys(Map<?, ?> root, String source) throws IOException {
        for (Object rawKey : root.keySet()) {
            String key = requireStringKey(rawKey, source, "top-level key");
            if (!ROOT_KEYS.contains(key)) {
                throw new IOException(source + ": unknown top-level key '" + key + "'");
            }
        }
    }

    private static String requireThemeName(Object value, String source) throws IOException {
        if (!(value instanceof String name) || !THEME_NAME.matcher(name).matches()) {
            throw new IOException(source
                    + ": theme name must match "
                    + THEME_NAME.pattern());
        }
        return name;
    }

    private static void validatePalette(Object value, String source) throws IOException {
        if (value == null) {
            return;
        }
        Map<?, ?> palette = requireMap(value, source, "palette");
        for (Map.Entry<?, ?> entry : palette.entrySet()) {
            String name = requireStringKey(entry.getKey(), source, "palette key");
            if (!THEME_NAME.matcher(name).matches()) {
                throw new IOException(source + ": invalid palette key '" + name + "'");
            }
            parseColor(entry.getValue(), source, "palette." + name);
        }
    }

    private static void parseGroup(
            String group,
            Object value,
            EnumMap<UiColor, Color> colors,
            String source) throws IOException {
        Map<?, ?> entries = requireMap(value, source, "group '" + group + "'");
        Set<String> allowedKeys = KEYS_BY_GROUP.getOrDefault(group, Set.of());
        for (Map.Entry<?, ?> entry : entries.entrySet()) {
            String key = requireStringKey(entry.getKey(), source, "key in group '" + group + "'");
            if (!allowedKeys.contains(key)) {
                throw new IOException(source + ": unknown color '" + group + "." + key + "'");
            }
            String path = group + "." + key;
            colors.put(COLORS_BY_PATH.get(path), parseColor(entry.getValue(), source, path));
        }
    }

    private static Color parseColor(Object value, String source, String path) throws IOException {
        if (!(value instanceof List<?> components) || components.size() != 4) {
            throw new IOException(source + ": color '" + path + "' must be [red, green, blue, alpha]");
        }
        int[] rgba = new int[4];
        for (int index = 0; index < components.size(); index++) {
            Object component = components.get(index);
            if (!(component instanceof Byte
                    || component instanceof Short
                    || component instanceof Integer
                    || component instanceof Long)) {
                throw new IOException(source + ": color '" + path + "' components must be integers");
            }
            long number = ((Number) component).longValue();
            if (number < 0 || number > 255) {
                throw new IOException(source + ": color '" + path + "' components must be between 0 and 255");
            }
            rgba[index] = (int) number;
        }
        return new Color(rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    private static Map<?, ?> requireMap(Object value, String source, String description) throws IOException {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IOException(source + ": " + description + " must be a mapping");
        }
        return map;
    }

    private static String requireStringKey(Object value, String source, String description) throws IOException {
        if (!(value instanceof String key) || key.isBlank()) {
            throw new IOException(source + ": " + description + " must be a non-blank string");
        }
        return key;
    }

    private static Map<String, UiColor> colorsByPath() {
        Map<String, UiColor> colors = new HashMap<>();
        for (UiColor token : UiColor.values()) {
            UiColor previous = colors.put(token.key(), token);
            if (previous != null) {
                throw new IllegalStateException("Duplicate theme color path: " + token.key());
            }
        }
        return Map.copyOf(colors);
    }

    private static Map<String, Set<String>> keysByGroup() {
        Map<String, Set<String>> keys = new HashMap<>();
        for (String path : COLORS_BY_PATH.keySet()) {
            int separator = path.indexOf('.');
            String group = path.substring(0, separator);
            String key = path.substring(separator + 1);
            keys.computeIfAbsent(group, ignored -> new HashSet<>()).add(key);
        }
        keys.replaceAll((group, values) -> Set.copyOf(values));
        return Map.copyOf(keys);
    }
}
