package com.seqwawa.seq.ui;

import com.seqwawa.seq.client.SeqClient;
import java.net.URI;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;

final class SequoiaSidebarNavigation {
    private static final String GITHUB_URL = "https://github.com/SequoiaWynncraft/sequoia-mod";
    private static final List<Destination> STANDARD_DESTINATIONS = List.of(
            Destination.ACHIEVEMENTS,
            Destination.CONNECTION,
            Destination.GITHUB,
            Destination.INGREDIENTS,
            Destination.MAP,
            Destination.PARTY_FINDER,
            Destination.SETTINGS);
    private static final List<Destination> WAR_DESTINATIONS = List.of(
            Destination.ACHIEVEMENTS,
            Destination.CONNECTION,
            Destination.GITHUB,
            Destination.INGREDIENTS,
            Destination.MAP,
            Destination.PARTY_FINDER,
            Destination.SETTINGS,
            Destination.WAR);

    private SequoiaSidebarNavigation() {}

    static List<Destination> destinations() {
        return destinations(SeqClient.getWarPlannerManager() != null
                && SeqClient.getWarPlannerManager().isAuthorized());
    }

    static List<Destination> destinations(boolean warPlannerAuthorized) {
        return warPlannerAuthorized ? WAR_DESTINATIONS : STANDARD_DESTINATIONS;
    }

    static SidebarLayout sidebarLayout(
            float screenHeight, int rowCount, float buttonHeight, float normalSpacing) {
        int rows = Math.max(1, rowCount);
        float startY = 50;
        float bottomMargin = 8;
        float minimumButtonHeight = 10;
        float minimumSpacing = 1;
        float availableHeight = Math.max(minimumButtonHeight, screenHeight - startY - bottomMargin);
        float normalBlockHeight = buttonHeight * rows + normalSpacing * (rows - 1);
        if (normalBlockHeight <= availableHeight) {
            return new SidebarLayout(startY, buttonHeight + normalSpacing, buttonHeight, rows);
        }

        float fittedButtonHeight = Math.max(
                minimumButtonHeight,
                Math.min(buttonHeight, (availableHeight - minimumSpacing * (rows - 1)) / rows));
        float fittedSpacing = rows == 1
                ? 0
                : Math.max(
                        minimumSpacing,
                        Math.min(normalSpacing, (availableHeight - fittedButtonHeight * rows) / (rows - 1)));
        return new SidebarLayout(startY, fittedButtonHeight + fittedSpacing, fittedButtonHeight, rows);
    }

    static void open(Destination destination, Screen parent) {
        switch (destination) {
            case PARTY_FINDER -> SeqClient.mc.setScreen(new PartyFinderScreen(parent));
            case WAR -> SeqClient.openWarPlannerScreen();
            case CONNECTION -> SeqClient.mc.setScreen(new ConnectionScreen(parent));
            case SETTINGS -> SeqClient.mc.setScreen(new SettingsScreen(parent));
            case MAP -> SeqClient.mc.setScreen(new WorldMapScreen(parent));
            case INGREDIENTS -> SeqClient.mc.setScreen(new IngredientGuideScreen(parent));
            case ACHIEVEMENTS -> SeqClient.mc.setScreen(new AchievementsScreen(parent));
            case GITHUB -> openGithub();
        }
    }

    private static void openGithub() {
        try {
            java.awt.Desktop.getDesktop().browse(URI.create(GITHUB_URL));
        } catch (Exception ignored) {
        }
    }

    enum Destination {
        PARTY_FINDER("Partyfinder"),
        WAR("War Planner"),
        CONNECTION("Connection"),
        ACHIEVEMENTS("Achievements"),
        SETTINGS("Settings"),
        MAP("Map"),
        INGREDIENTS("Ingredients"),
        GITHUB("Github");

        private final String label;

        Destination(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    record SidebarLayout(float startY, float rowStep, float buttonHeight, int rowCount) {
        float buttonY(int row) {
            return startY + rowStep * row;
        }

        float bottom() {
            return buttonY(rowCount - 1) + buttonHeight;
        }
    }
}
