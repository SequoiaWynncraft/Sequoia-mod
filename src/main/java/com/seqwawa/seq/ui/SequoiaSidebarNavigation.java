package com.seqwawa.seq.ui;

import com.seqwawa.seq.client.SeqClient;
import java.net.URI;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;

final class SequoiaSidebarNavigation {
    private static final String GITHUB_URL = "https://github.com/SequoiaWynncraft/sequoia-mod";
    private static final List<Destination> STANDARD_DESTINATIONS = List.of(
            Destination.CONNECTION,
            Destination.GITHUB,
            Destination.INGREDIENTS,
            Destination.MAP,
            Destination.PARTY_FINDER,
            Destination.SETTINGS);
    private static final List<Destination> WAR_DESTINATIONS = List.of(
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

    static void open(Destination destination, Screen parent) {
        switch (destination) {
            case PARTY_FINDER -> SeqClient.mc.setScreen(new PartyFinderScreen(parent));
            case WAR -> SeqClient.openWarPlannerScreen();
            case CONNECTION -> SeqClient.mc.setScreen(new ConnectionScreen(parent));
            case SETTINGS -> SeqClient.mc.setScreen(new SettingsScreen(parent));
            case MAP -> SeqClient.mc.setScreen(new WorldMapScreen(parent));
            case INGREDIENTS -> SeqClient.mc.setScreen(new IngredientGuideScreen(parent));
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
}
