package org.sequoia.seq.managers;

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
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Stream;

import org.sequoia.seq.ui.values.Theme;
import org.sequoia.seq.utils.ThemeReader;

public class ThemeManager {

    private static Theme currentTheme;
    private final static ArrayList<Theme> loadedThemes = new ArrayList<Theme>();

    public static Theme getCurrentTheme() {
        return currentTheme;
    }

    public static ArrayList<String> getLoadedThemeNames() {
        ArrayList<String> names = new ArrayList<String>();

        for (Theme theme : loadedThemes) {
            names.add(theme.NAME);
        }

        return names;
    }

    public static void setCurrentTheme(String themeName) {
        for (Theme theme : loadedThemes) {
            if (theme.NAME.equals(themeName)) {
                currentTheme = theme; 
            }
        }
    }

    private static void loadFromPath(Path path) {
        ThemeReader themeReader = new ThemeReader();

        try {
            loadedThemes.add(themeReader.fromFile(path));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void loadThemes() {
        try {
            URI uri = ThemeManager.class.getClassLoader().getResource("assets/seq/themes").toURI();
            Path themesPath;

            if ("jar".equals(uri.getScheme())) {
                FileSystem fs;
                try {
                    fs = FileSystems.getFileSystem(uri);
                } catch (FileSystemNotFoundException e) {
                    fs = FileSystems.newFileSystem(uri, Map.of());
                }

                themesPath = fs.getPath("/assets/seq/themes");
            } else {
                themesPath = Paths.get(uri);
            }

            try (Stream<Path> paths = Files.walk(themesPath)) {
                paths.filter(Files::isRegularFile)
                     .forEach(ThemeManager::loadFromPath);
            }

        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public static void unloadThemes() {
        loadedThemes.clear();
    }
}
