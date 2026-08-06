package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.ui.theme.Theme;
import com.seqwawa.seq.ui.theme.UiColor;
import com.seqwawa.seq.utils.ThemeReader;
import com.seqwawa.seq.utils.ThemeWriter;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class ThemeManager {
    private static final String THEMES_RESOURCE = "/assets/seq/themes";
    private static final Path EXTERNAL_THEMES_DIRECTORY = Path.of("config", "sequoia", "themes");
    private static final Map<String, Theme> LOADED_THEMES = new LinkedHashMap<>();
    private static final Set<String> BUNDLED_THEME_NAMES = new HashSet<>();
    private static final Set<String> HIDDEN_THEME_NAMES = Set.of(PrincessMode.THEME_NAME);
    private static final Map<String, Path> PERSONAL_THEME_PATHS = new LinkedHashMap<>();
    private static Path externalThemesDirectory = EXTERNAL_THEMES_DIRECTORY;
    private static volatile Theme currentTheme = Theme.defaults();
    private static String selectedThemeName = "default";
    private static String overrideThemeName;

    private ThemeManager() {
    }

    public static synchronized void initialize() {
        initialize(EXTERNAL_THEMES_DIRECTORY);
    }

    static synchronized void initialize(Path externalThemesDirectory) {
        LOADED_THEMES.clear();
        BUNDLED_THEME_NAMES.clear();
        PERSONAL_THEME_PATHS.clear();
        overrideThemeName = null;
        PrincessMode.resetForThemeInitialization();
        ThemeManager.externalThemesDirectory = externalThemesDirectory;
        Theme fallback = Theme.defaults();
        LOADED_THEMES.put(fallback.name(), fallback);
        BUNDLED_THEME_NAMES.add(fallback.name());
        currentTheme = fallback;
        selectedThemeName = fallback.name();

        loadBundledThemes();
        loadExternalThemes(externalThemesDirectory);

        Theme defaultTheme = LOADED_THEMES.get("default");
        if (defaultTheme == null) {
            SeqClient.LOGGER.warn("Bundled UI theme is missing; using built-in colors");
            return;
        }
        currentTheme = defaultTheme;
        selectedThemeName = defaultTheme.name();
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
                loadTheme(path, false);
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
                loadTheme(path, true);
            }
        } catch (IOException exception) {
            SeqClient.LOGGER.warn("Could not discover external UI themes in {}", directory, exception);
        }
    }

    private static boolean isThemeFile(Path path) {
        return path.getFileName().toString().endsWith(".theme.yml");
    }

    private static void loadTheme(Path path, boolean personal) {
        try {
            Theme theme = ThemeReader.fromFile(path);
            Theme existing = LOADED_THEMES.get(theme.name());
            if (existing != null && existing != currentTheme) {
                SeqClient.LOGGER.warn("Ignoring duplicate UI theme '{}' from {}", theme.name(), path);
                return;
            }
            LOADED_THEMES.put(theme.name(), theme);
            if (personal) {
                PERSONAL_THEME_PATHS.put(theme.name(), path);
            } else {
                BUNDLED_THEME_NAMES.add(theme.name());
            }
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
        return LOADED_THEMES.keySet().stream()
                .filter(name -> !HIDDEN_THEME_NAMES.contains(name))
                .toList();
    }

    public static synchronized Optional<Theme> theme(String themeName) {
        return Optional.ofNullable(LOADED_THEMES.get(themeName));
    }

    public static synchronized boolean isPersonalTheme(String themeName) {
        return PERSONAL_THEME_PATHS.containsKey(themeName);
    }

    public static synchronized void previewTheme(Theme theme) {
        if (overrideThemeName == null) {
            currentTheme = theme;
        }
    }

    public static synchronized Path savePersonalTheme(Theme theme) throws IOException {
        String name = theme.name();
        if (!ThemeReader.isValidThemeName(name)) {
            throw new IOException("Theme name must use lowercase letters, numbers, underscores, or hyphens");
        }
        if (BUNDLED_THEME_NAMES.contains(name)) {
            throw new IOException("Bundled theme '" + name + "' cannot be overwritten");
        }

        Path directory = externalThemesDirectory.toAbsolutePath().normalize();
        Path destination = PERSONAL_THEME_PATHS
                .getOrDefault(name, directory.resolve(name + ".theme.yml"))
                .toAbsolutePath()
                .normalize();
        if (!destination.startsWith(directory)) {
            throw new IOException("Theme destination must stay inside " + directory);
        }

        ThemeWriter.write(theme, destination);
        LOADED_THEMES.put(name, theme);
        PERSONAL_THEME_PATHS.put(name, destination);
        return destination;
    }

    public static synchronized boolean setCurrentTheme(String themeName) {
        Theme theme = LOADED_THEMES.get(themeName);
        if (theme == null || HIDDEN_THEME_NAMES.contains(themeName)) {
            return false;
        }
        selectedThemeName = themeName;
        if (overrideThemeName == null) {
            currentTheme = theme;
        }
        return true;
    }

    static synchronized boolean setThemeOverride(String themeName) {
        Theme theme = LOADED_THEMES.get(themeName);
        if (theme == null) {
            return false;
        }
        overrideThemeName = themeName;
        currentTheme = theme;
        return true;
    }

    static synchronized void clearThemeOverride() {
        overrideThemeName = null;
        currentTheme = LOADED_THEMES.getOrDefault(selectedThemeName, Theme.defaults());
    }
}
