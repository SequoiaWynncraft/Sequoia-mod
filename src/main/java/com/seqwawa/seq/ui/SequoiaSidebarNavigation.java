package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import java.net.URI;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;

final class SequoiaSidebarNavigation {
    static final float WIDTH = 140;
    private static final String GITHUB_URL = "https://github.com/SequoiaWynncraft/sequoia-mod";
    private static final List<Destination> MAIN_MENU_DESTINATIONS = List.of(
            Destination.PARTY_FINDER,
            Destination.CONNECTION,
            Destination.INGREDIENTS,
            Destination.MAP,
            Destination.SETTINGS);
    private static final List<Destination> STANDARD_DESTINATIONS = List.of(
            Destination.PARTY_FINDER,
            Destination.ACHIEVEMENTS,
            Destination.CONNECTION,
            Destination.INGREDIENTS,
            Destination.MAP,
            Destination.SETTINGS,
            Destination.GITHUB);
    private static final List<Destination> WAR_DESTINATIONS = List.of(
            Destination.PARTY_FINDER,
            Destination.ACHIEVEMENTS,
            Destination.CONNECTION,
            Destination.INGREDIENTS,
            Destination.MAP,
            Destination.SETTINGS,
            Destination.WAR,
            Destination.GITHUB);

    private SequoiaSidebarNavigation() {}

    static List<Destination> mainMenuDestinations() {
        return MAIN_MENU_DESTINATIONS;
    }

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

    static void render(UiCanvas canvas, Destination active, float mouseX, float mouseY) {
        String font = SeqClient.getFontManager().getSelectedFont();
        canvas.fillRect(0, 0, WIDTH, canvas.metrics().height(), color(BACKGROUND_SIDEBAR));
        SequoiaUiStyle.drawSidebarTitle(canvas, font, WIDTH);
        canvas.fillRect(10, 40, WIDTH - 20, 1, color(ACCENT_PRIMARY_DARK));
        var destinations = destinations();
        var layout = sidebarLayout(canvas.metrics().height(), destinations.size(), 22, 6);
        for (int row = 0; row < destinations.size(); row++) {
            var destination = destinations.get(row);
            float y = layout.buttonY(row);
            boolean hovered = mouseX >= 10 && mouseX < WIDTH - 10
                    && mouseY >= y && mouseY < y + layout.buttonHeight();
            canvas.fillRect(10, y, WIDTH - 20, layout.buttonHeight(),
                    SequoiaUiStyle.sidebarButtonColor(destination == active, hovered));
            canvas.drawText(destination.label(), WIDTH / 2, y + layout.buttonHeight() / 2,
                    new UiCanvas.TextStyle(font, Math.min(12, Math.max(8, layout.buttonHeight() - 2)),
                            color(TEXT_PRIMARY), UiCanvas.HorizontalAlign.CENTER, UiCanvas.VerticalAlign.MIDDLE));
        }
    }

    static Destination destinationAt(float mouseX, float mouseY, float height, boolean authorized) {
        if (mouseX < 10 || mouseX >= WIDTH - 10) return null;
        var destinations = destinations(authorized);
        var layout = sidebarLayout(height, destinations.size(), 22, 6);
        for (int row = 0; row < destinations.size(); row++) {
            if (mouseY >= layout.buttonY(row) && mouseY < layout.buttonY(row) + layout.buttonHeight()) {
                return destinations.get(row);
            }
        }
        return null;
    }

    static boolean click(float mouseX, float mouseY, float height, Destination active, Screen parent) {
        if (mouseX < 0 || mouseX >= WIDTH) return false;
        var destination = destinationAt(mouseX, mouseY, height,
                SeqClient.getWarPlannerManager() != null && SeqClient.getWarPlannerManager().isAuthorized());
        if (destination != null && destination != active) open(destination, parent);
        return true;
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
