package org.sequoia.seq.utils;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;

import org.sequoia.seq.ui.values.Theme;

public class ThemeReader {
    private String themeName;
    private HashMap<String, Color> themeValues;
    private static final String[] colorList = {
        "background_overlay",
        "background_sidebar",
        "background_body",
        "background_body_opaque",
        "background_header",
        "background_content",
        "background_content_focused",

        "accent_main_light",
        "accent_main_light_hover",
        "accent_main_dark",
        "accent_main_dark_hover",
        "accent_main_inactive",
        "accent_alt_light",
        "accent_alt_dark",

        "text_primary",
        "text_pleasant",
        "text_faint",
        "text_inactive",

        "element_input_primary",
        "element_input_secondary",
        "element_input_hover",
        "element_scrollbar_track",
        "element_scrollbar_thumb",
        "element_danger_primary",
        "element_danger_hover",
        "element_warning_primary",
        "element_good_primary"
    };

    public Theme fromFile(Path path) throws IOException {

        Theme theme;
        List<String> fileInput = Files.readAllLines(path);

        for (String string : fileInput) {
            if (string.matches("name=.*")) {
                themeName = string.split("=")[1];
                break;
            }
        }

        // Check if theme file contains a name
        if (themeName.isBlank()) {
            throw new IOException(); // TODO: Create custom exception to throw if theme file doesn't contain all required data
        }

        for (String string : fileInput) {
            parseColor(string);
        }

        // Check if theme file contains all required colors
        for (String colorName : colorList) {
            if (!themeValues.containsKey(colorName)) {
                throw new IOException(); // TODO: Use the custom exception mentioned above if implemented
            }
        }
        
        theme = new Theme(
            themeName,

            themeValues.get(colorList[0]),
            themeValues.get(colorList[1]),
            themeValues.get(colorList[2]),
            themeValues.get(colorList[3]),
            themeValues.get(colorList[4]),
            themeValues.get(colorList[5]),
            themeValues.get(colorList[6]),

            themeValues.get(colorList[7]),
            themeValues.get(colorList[8]),
            themeValues.get(colorList[9]),
            themeValues.get(colorList[10]),
            themeValues.get(colorList[11]),
            themeValues.get(colorList[12]),
            themeValues.get(colorList[13]),
            
            themeValues.get(colorList[14]),
            themeValues.get(colorList[15]),
            themeValues.get(colorList[16]),
            themeValues.get(colorList[17]),

            themeValues.get(colorList[18]),
            themeValues.get(colorList[19]),
            themeValues.get(colorList[20]),
            themeValues.get(colorList[21]),
            themeValues.get(colorList[22]),
            themeValues.get(colorList[23]),
            themeValues.get(colorList[24]),
            themeValues.get(colorList[25]),
            themeValues.get(colorList[26])
        );
        
        // Wipe values to prepare for reading next file
        themeName = "";
        themeValues.clear();

        return theme;
    }

    // Recieves a string in the format of the lines used in theme files and
    // parses it into a color class, along with the variable name.
    private void parseColor(String line) {
        if (line.matches(".*=\\([0-9]*,[0-9]*,[0-9]*,[0-9]*\\)")) { // Regex to only operate on lines with color definitions
            
            String[] keyval = line.split("=");
            keyval[1] = keyval[1].replaceAll("\\(|\\)", "");
            String[] stringColorValues = keyval[1].split(",");
            int[] colorValues = new int[4];

            for (int i = 0; i < colorValues.length; i++) {
                colorValues[i] = Integer.parseInt(stringColorValues[i]);
            }
            
            themeValues.put(keyval[0], new Color(colorValues[0], colorValues[1], colorValues[2], colorValues[3]));
        }
    }

    public ThemeReader() {
        themeValues = new HashMap<String, Color>();
        themeName = "";
    }
}
