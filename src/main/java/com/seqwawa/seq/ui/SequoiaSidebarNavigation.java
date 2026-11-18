package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import java.net.URI;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;

/**
 * The Sequoia sidebar: the title, a button for each Sequoia screen, and the profile
 * card at its foot. Screens draw and click it through here, so its buttons are laid
 * out, drawn and hit-tested in one place.
 */
final class SequoiaSidebarNavigation {
    static final float WIDTH = 140;
    /** The buttons' inset from the sidebar's sides. */
    static final float PADDING = 10;
    static final float BUTTON_HEIGHT = 22;
    static final float BUTTON_SPACING = 6;
    private static final String GITHUB_URL = "https://github.com/SequoiaWynncraft/sequoia-mod";
    private static final List<Destination> MAIN_MENU_DESTINATIONS = List.of(
            Destination.PARTY_FINDER,
            Destination.SETTINGS,
            Destination.CONNECTION,
            Destination.INGREDIENTS,
            Destination.MAP,
            Destination.MEMBERS);
    private static final List<Destination> STANDARD_DESTINATIONS = List.of(
            Destination.PARTY_FINDER,
            Destination.SETTINGS,
            Destination.ACHIEVEMENTS,
            Destination.CONNECTION,
            Destination.INGREDIENTS,
            Destination.MAP,
            Destination.MEMBERS,
            Destination.QUESTS,
            Destination.GITHUB);
    private static final List<Destination> WAR_DESTINATIONS = List.of(
            Destination.PARTY_FINDER,
            Destination.SETTINGS,
            Destination.ACHIEVEMENTS,
            Destination.CONNECTION,
            Destination.INGREDIENTS,
            Destination.MAP,
            Destination.MEMBERS,
            Destination.QUESTS,
            Destination.WAR,
            Destination.GITHUB);

    private SequoiaSidebarNavigation() {}

    static List<Destination> mainMenuDestinations() {
        return MAIN_MENU_DESTINATIONS;
    }

    static List<Destination> destinations() {
        return destinations(warPlannerAuthorized());
    }

    static List<Destination> destinations(boolean warPlannerAuthorized) {
        return warPlannerAuthorized ? WAR_DESTINATIONS : STANDARD_DESTINATIONS;
    }

    /** The buttons' layout, kept clear of the profile card at the sidebar's foot while it shows. */
    static SidebarLayout sidebarLayout(
            float screenHeight, int rowCount, float buttonHeight, float normalSpacing) {
        return sidebarLayout(screenHeight, rowCount, buttonHeight, normalSpacing, SidebarProfile.reserved(screenHeight));
    }

    /** The buttons' layout with {@code reservedBelow} UI pixels at the sidebar's foot left free. */
    static SidebarLayout sidebarLayout(
            float screenHeight, int rowCount, float buttonHeight, float normalSpacing, float reservedBelow) {
        screenHeight = Math.max(0, screenHeight - reservedBelow);
        int rows = Math.max(1, rowCount);
        float startY = 50;
        // A cramped sidebar starts its rows just under the title's divider at y=40.
        float compactStartY = 43;
        float bottomMargin = 8;
        float minimumButtonHeight = 10;
        float minimumSpacing = 1;
        float normalBlockHeight = buttonHeight * rows + normalSpacing * (rows - 1);
        if (normalBlockHeight <= screenHeight - startY - bottomMargin) {
            return new SidebarLayout(startY, buttonHeight + normalSpacing, buttonHeight, rows);
        }

        float availableHeight = Math.max(minimumButtonHeight, screenHeight - compactStartY - bottomMargin);
        float fittedButtonHeight = Math.max(
                minimumButtonHeight,
                Math.min(buttonHeight, (availableHeight - minimumSpacing * (rows - 1)) / rows));
        float fittedSpacing = rows == 1
                ? 0
                : Math.max(
                        minimumSpacing,
                        Math.min(normalSpacing, (availableHeight - fittedButtonHeight * rows) / (rows - 1)));
        return new SidebarLayout(compactStartY, fittedButtonHeight + fittedSpacing, fittedButtonHeight, rows);
    }

    /** Where the button on {@code row} is, in a sidebar laid out as {@code layout}. */
    static UiBounds buttonBounds(SidebarLayout layout, int row) {
        return new UiBounds(PADDING, layout.buttonY(row), WIDTH - PADDING * 2, layout.buttonHeight());
    }

    static void render(UiCanvas canvas, Destination active, UiPointer pointer, Foot foot) {
        String font = SeqClient.getFontManager().getSelectedFont();
        float height = canvas.metrics().height();
        canvas.fillRect(0, 0, WIDTH, height, color(BACKGROUND_SIDEBAR));
        SequoiaUiStyle.drawSidebarTitle(canvas, font, WIDTH);
        canvas.fillRect(PADDING, 40, WIDTH - PADDING * 2, 1, color(ACCENT_PRIMARY_DARK));
        var destinations = destinations();
        var layout = sidebarLayout(height, destinations.size(), BUTTON_HEIGHT, BUTTON_SPACING, foot.reserved());
        for (int row = 0; row < destinations.size(); row++) {
            var destination = destinations.get(row);
            UiBounds button = buttonBounds(layout, row);
            button.fill(canvas, SequoiaUiStyle.sidebarButtonColor(destination == active, button.contains(pointer)));
            canvas.drawText(destination.label(), button.centerX(), button.centerY(),
                    new UiCanvas.TextStyle(font, Math.min(12, Math.max(8, button.height() - 2)),
                            color(TEXT_PRIMARY), UiCanvas.HorizontalAlign.CENTER, UiCanvas.VerticalAlign.MIDDLE));
        }
        if (foot.profile()) SidebarProfile.render(canvas, height, pointer.x(), pointer.y());
    }

    /** The button at a point, in a sidebar keeping {@code reserved} UI pixels free at its foot, or null. */
    static Destination destinationAt(float mouseX, float mouseY, float height, boolean authorized, float reserved) {
        var destinations = destinations(authorized);
        var layout = sidebarLayout(height, destinations.size(), BUTTON_HEIGHT, BUTTON_SPACING, reserved);
        for (int row = 0; row < destinations.size(); row++) {
            if (buttonBounds(layout, row).contains(mouseX, mouseY)) return destinations.get(row);
        }
        return null;
    }

    /**
     * Handles a click on the sidebar's buttons and profile card: a button opens its
     * screen, handing on {@code parent} for Esc there to go back to, and the card opens
     * the quest board. Returns whether a button or the card took the click.
     */
    static boolean press(UiPointer at, float height, Destination active, Screen parent, Foot foot) {
        if (foot.profile() && SidebarProfile.click(at.x(), at.y(), height, parent)) return true;
        var destination = destinationAt(at.x(), at.y(), height, warPlannerAuthorized(), foot.reserved());
        if (destination == null) return false;
        if (destination != active) {
            open(destination, destination == Destination.MAP ? SeqClient.mc.screen : parent);
        }
        return true;
    }

    /**
     * {@link #press} for a screen with the usual foot, which also claims every other
     * click on the sidebar, so the screen behind it does nothing with them.
     */
    static boolean click(UiPointer at, float height, Destination active, Screen parent) {
        if (at.x() < 0 || at.x() >= WIDTH) return false;
        press(at, height, active, parent, Foot.profileCard(height));
        return true;
    }

    private static boolean warPlannerAuthorized() {
        return SeqClient.getWarPlannerManager() != null && SeqClient.getWarPlannerManager().isAuthorized();
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
            case QUESTS -> SeqClient.mc.setScreen(new QuestBoardScreen(parent));
            // Routed through SeqClient, which sends a member with no profile to setup first.
            case MEMBERS -> SeqClient.openGuildMembersScreen(parent);
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
        MEMBERS("Player List"),
        QUESTS("Quests"),
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

    /**
     * What one screen keeps at the sidebar's foot: {@code reserved} UI pixels that the
     * buttons keep clear of, and whether the profile card shows there.
     */
    record Foot(float reserved, boolean profile) {
        /** The usual foot: the profile card, where it fits. */
        static Foot profileCard(float screenHeight) {
            return new Foot(SidebarProfile.reserved(screenHeight), true);
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
