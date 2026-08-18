package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import java.awt.Color;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;


public class SequoiaScreen extends Screen {
    private static final float BUTTON_WIDTH = 120;
    private static final float BUTTON_HEIGHT = 24;
    private static final float BUTTON_SPACING = 8;
    private static final float BUTTON_RADIUS = 6;
    private static final float TITLE_FONT_SIZE = 24;
    private static final float BUTTON_FONT_SIZE = 14;
    private static final float TITLE_GAP = 40;
    private static final float TITLE_MIN_Y = 24;
    private static final float BOTTOM_MARGIN = 16;

    private static final String GITHUB_URL = "https://github.com/SequoiaWynncraft/sequoia-mod";

    private float nvgMouseX;
    private float nvgMouseY;

    public SequoiaScreen() {
        super(Component.literal("Sequoia"));
    }

    /** Main menu entries, in the order they are drawn. */
    private enum MenuEntry {
        PARTY_FINDER("Partyfinder"),
        CONNECTION("Connection"),
        SETTINGS("Settings"),
        MAP("Map"),
        INGREDIENTS("Ingredients"),
        ACHIEVEMENTS("Achievements"),
        GITHUB("Github");

        private static final List<MenuEntry> ORDERED = List.of(values());

        private final String label;

        MenuEntry(String label) {
            this.label = label;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        nvgMouseX = MinecraftUiRenderer.mouseX(mouseX);
        nvgMouseY = MinecraftUiRenderer.mouseY(mouseY);

        UiRenderer.renderScreen(this, canvas -> {
            float screenWidth = canvas.metrics().width();
            float screenHeight = canvas.metrics().height();

            // Dark background
            canvas.fillRect(0, 0, screenWidth, screenHeight, color(BACKGROUND_MODAL_OVERLAY, 140));

            // Title
            String fontName = SeqClient.getFontManager().getSelectedFont();
            float titleY = titleY(screenHeight);
            canvas.drawText("Sequoia", screenWidth / 2f, titleY, new UiCanvas.TextStyle(
                    fontName,
                    TITLE_FONT_SIZE,
                    color(ACCENT_PRIMARY),
                    UiCanvas.HorizontalAlign.CENTER,
                    UiCanvas.VerticalAlign.MIDDLE));

            // Buttons
            float centerX = screenWidth / 2f - BUTTON_WIDTH / 2f;
            for (MenuEntry entry : MenuEntry.ORDERED) {
                drawButton(canvas, centerX, buttonY(screenHeight, entry), entry.label);
            }
        });
    }

    private static float titleY(float screenHeight) {
        float menuHeight = MenuEntry.ORDERED.size() * (BUTTON_HEIGHT + BUTTON_SPACING) - BUTTON_SPACING;
        float highestFittingY = screenHeight - menuHeight - TITLE_GAP - BOTTOM_MARGIN;
        return Math.max(TITLE_MIN_Y, Math.min(screenHeight * 0.3f, highestFittingY));
    }

    private static float buttonY(float screenHeight, MenuEntry entry) {
        return titleY(screenHeight) + TITLE_GAP + entry.ordinal() * (BUTTON_HEIGHT + BUTTON_SPACING);
    }

    private void drawButton(UiCanvas canvas, float x, float y, String label) {
        boolean hovered = nvgMouseX >= x && nvgMouseX <= x + BUTTON_WIDTH
                && nvgMouseY >= y && nvgMouseY <= y + BUTTON_HEIGHT;

        Color bgColor = hovered ? color(CONTROL_INPUT_HOVER) : color(BACKGROUND_POPUP, 200);
        canvas.fillRect(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, bgColor);

        String fontName = SeqClient.getFontManager().getSelectedFont();
        canvas.drawText(label, x + BUTTON_WIDTH / 2f, y + BUTTON_HEIGHT / 2f, new UiCanvas.TextStyle(
                fontName,
                BUTTON_FONT_SIZE,
                color(TEXT_PRIMARY),
                UiCanvas.HorizontalAlign.CENTER,
                UiCanvas.VerticalAlign.MIDDLE));
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() == 0) {
            float mx = MinecraftUiRenderer.mouseX(click.x());
            float my = MinecraftUiRenderer.mouseY(click.y());

            float screenWidth = MinecraftUiRenderer.screenWidth();
            float screenHeight = MinecraftUiRenderer.screenHeight();
            float centerX = screenWidth / 2f - BUTTON_WIDTH / 2f;

            for (MenuEntry entry : MenuEntry.ORDERED) {
                if (isInButton(mx, my, centerX, buttonY(screenHeight, entry))) {
                    open(entry);
                    break;
                }
            }
        }
        return super.mouseClicked(click, outsideScreen);
    }

    private void open(MenuEntry entry) {
        switch (entry) {
            case PARTY_FINDER -> SeqClient.mc.setScreen(new PartyFinderScreen(this));
            case CONNECTION -> SeqClient.mc.setScreen(new ConnectionScreen(this));
            case SETTINGS -> SeqClient.mc.setScreen(new SettingsScreen(this));
            case MAP -> SeqClient.mc.setScreen(new WorldMapScreen(this));
            case INGREDIENTS -> SeqClient.mc.setScreen(new IngredientGuideScreen(this));
            case ACHIEVEMENTS -> SeqClient.mc.setScreen(new AchievementsScreen(this));
            case GITHUB -> openGithub();
        }
    }

    private void openGithub() {
        try {
            java.net.URI uri = java.net.URI.create(GITHUB_URL);
            java.awt.Desktop.getDesktop().browse(uri);
        } catch (Exception ignored) {
        }
    }

    private boolean isInButton(float mx, float my, float bx, float by) {
        return mx >= bx && mx <= bx + BUTTON_WIDTH && my >= by && my <= by + BUTTON_HEIGHT;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

}
