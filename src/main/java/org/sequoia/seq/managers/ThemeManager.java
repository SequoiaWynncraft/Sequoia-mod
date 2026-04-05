package org.sequoia.seq.managers;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

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
		URI uri;

		try {
			uri = ThemeManager.class.getClassLoader().getResource("assets/seq/themes").toURI();
		} catch (URISyntaxException e) {
			e.printStackTrace(); // TODO Auto-generated catch block
			return;
		}
		
		Path themesPath = Paths.get(uri);

		try {
			Files.walk(themesPath).filter(Files::isRegularFile).forEach(ThemeManager::loadFromPath);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
	}

	public static void unloadThemes() {
		loadedThemes.clear();
	}
}
