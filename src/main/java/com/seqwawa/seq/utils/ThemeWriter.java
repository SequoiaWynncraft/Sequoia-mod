package com.seqwawa.seq.utils;

import com.seqwawa.seq.ui.theme.Theme;
import com.seqwawa.seq.ui.theme.UiColor;
import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ThemeWriter {
    private ThemeWriter() {
    }

    public static void write(Theme theme, Path destination) throws IOException {
        Path directory = destination.toAbsolutePath().normalize().getParent();
        if (directory == null) {
            throw new IOException("Theme destination must have a parent directory");
        }
        Files.createDirectories(directory);

        String temporaryPrefix = (theme.name() + "---").substring(0, 3);
        Path temporary = Files.createTempFile(directory, temporaryPrefix, ".theme.yml.tmp");
        try {
            Files.writeString(temporary, toYaml(theme), StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        destination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static String toYaml(Theme theme) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("# Created with the Sequoia in-game theme editor.\n");
        yaml.append("name: ").append(theme.name()).append("\n\n");

        String previousGroup = null;
        for (UiColor token : UiColor.values()) {
            String path = token.key();
            int separator = path.indexOf('.');
            String group = path.substring(0, separator);
            String key = path.substring(separator + 1);
            if (!group.equals(previousGroup)) {
                if (previousGroup != null) {
                    yaml.append('\n');
                }
                yaml.append(group).append(":\n");
                previousGroup = group;
            }

            Color color = theme.color(token);
            yaml.append("  ")
                    .append(key)
                    .append(": [")
                    .append(color.getRed())
                    .append(", ")
                    .append(color.getGreen())
                    .append(", ")
                    .append(color.getBlue())
                    .append(", ")
                    .append(color.getAlpha())
                    .append("]\n");
        }
        return yaml.toString();
    }
}
