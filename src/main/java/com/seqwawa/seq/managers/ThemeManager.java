package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.ui.theme.Theme;
import com.seqwawa.seq.ui.theme.UiColor;
import com.seqwawa.seq.utils.ThemeReader;
import java.awt.Color;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class ThemeManager {
    private static final String THEMES_RESOURCE = "/assets/seq/themes";
    private static final Path EXTERNAL_THEMES_DIRECTORY = Path.of("config", "sequoia", "themes");
    private static final Map<String, Theme> LOADED_THEMES = new LinkedHashMap<>();
    private static volatile Theme currentTheme = Theme.defaults();

    private ThemeManager() {
    }

    public static synchronized void initialize() {
        initialize(EXTERNAL_THEMES_DIRECTORY);
    }

    static synchronized void initialize(Path externalThemesDirectory) {
        LOADED_THEMES.clear();
        Theme fallback = Theme.defaults();
        LOADED_THEMES.put(fallback.name(), fallback);
        currentTheme = fallback;

        loadBundledThemes();
        loadExternalThemes(externalThemesDirectory);

        Theme defaultTheme = LOADED_THEMES.get("default");
        if (defaultTheme == null) {
            SeqClient.LOGGER.warn("Bundled UI theme is missing; using built-in colors");
            return;
        }
        currentTheme = defaultTheme;
    }

    private static void loadBundledThemes() {
        URL resource = ThemeManager.class.getResource(THEMES_RESOURCE);
        if (resource == null) {
            SeqClient.LOGGER.warn("Bundled UI themes are missing; using built-in colors");
            return;
        }

        FileSystem openedFileSystem = null;
        try {
            URI uri = resource.toURI();
            Path directory;
            if ("jar".equals(uri.getScheme())) {
                FileSystem fileSystem;
                try {
                    fileSystem = FileSystems.getFileSystem(uri);
                } catch (FileSystemNotFoundException exception) {
                    fileSystem = FileSystems.newFileSystem(uri, Map.of());
                    openedFileSystem = fileSystem;
                }
                directory = fileSystem.getPath(THEMES_RESOURCE);
            } else {
                directory = Paths.get(uri);
            }

            List<Path> themeFiles;
            try (Stream<Path> paths = Files.walk(directory)) {
                themeFiles = paths.filter(Files::isRegularFile)
                        .filter(ThemeManager::isThemeFile)
                        .sorted(Comparator.comparing(Path::toString))
                        .toList();
            }
            for (Path path : themeFiles) {
                loadTheme(path);
            }
        } catch (IOException | URISyntaxException exception) {
            SeqClient.LOGGER.warn("Could not discover bundled UI themes; using built-in colors", exception);
        } finally {
            if (openedFileSystem != null) {
                try {
                    openedFileSystem.close();
                } catch (IOException exception) {
                    SeqClient.LOGGER.debug("Could not close theme resource filesystem", exception);
                }
            }
        }
    }

    private static void loadExternalThemes(Path directory) {
        try {
            Files.createDirectories(directory);
            List<Path> themeFiles;
            try (Stream<Path> paths = Files.walk(directory)) {
                themeFiles = paths.filter(Files::isRegularFile)
                        .filter(ThemeManager::isThemeFile)
                        .sorted(Comparator.comparing(Path::toString))
                        .toList();
            }
            for (Path path : themeFiles) {
                loadTheme(path);
            }
        } catch (IOException exception) {
            SeqClient.LOGGER.warn("Could not discover external UI themes in {}", directory, exception);
        }
    }

    private static boolean isThemeFile(Path path) {
        return path.getFileName().toString().endsWith(".theme.yml");
    }

    private static void loadTheme(Path path) {
        try {
            Theme theme = ThemeReader.fromFile(path);
            Theme existing = LOADED_THEMES.get(theme.name());
            if (existing != null && existing != currentTheme) {
                SeqClient.LOGGER.warn("Ignoring duplicate UI theme '{}' from {}", theme.name(), path);
                return;
            }
            LOADED_THEMES.put(theme.name(), theme);
        } catch (IOException exception) {
            SeqClient.LOGGER.warn("Could not load UI theme {}", path, exception);
        }
    }

    public static Theme currentTheme() {
        return currentTheme;
    }

    public static Color color(UiColor token) {
        return currentTheme.color(token);
    }

    public static Color color(UiColor token, int alpha) {
        return currentTheme.color(token, alpha);
    }

    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public static synchronized List<String> loadedThemeNames() {
        return List.copyOf(LOADED_THEMES.keySet());
    }

    public static synchronized boolean setCurrentTheme(String themeName) {
        Theme theme = LOADED_THEMES.get(themeName);
        if (theme == null) {
            return false;
        }
        currentTheme = theme;
        return true;
    }
}
