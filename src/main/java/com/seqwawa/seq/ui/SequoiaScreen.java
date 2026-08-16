package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import java.awt.Color;
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

    private static final String GITHUB_URL = "https://github.com/SequoiaWynncraft/sequoia-mod";

    private float nvgMouseX;
    private float nvgMouseY;

    public SequoiaScreen() {
        super(Component.literal("Sequoia"));
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
            float titleY = screenHeight * 0.3f;
            canvas.drawText("Sequoia", screenWidth / 2f, titleY, new UiCanvas.TextStyle(
                    fontName,
                    TITLE_FONT_SIZE,
                    color(ACCENT_PRIMARY),
                    UiCanvas.HorizontalAlign.CENTER,
                    UiCanvas.VerticalAlign.MIDDLE));

            // Buttons
            float startY = titleY + 40;
            float centerX = screenWidth / 2f - BUTTON_WIDTH / 2f;

            int row = 0;
            drawButton(canvas, centerX, buttonY(startY, row++), "Partyfinder");
            if (warPlannerAuthorized()) {
                drawButton(canvas, centerX, buttonY(startY, row++), "War Planner");
            }
            drawButton(canvas, centerX, buttonY(startY, row++), "Connection");
            drawButton(canvas, centerX, buttonY(startY, row++), "Settings");
            drawButton(canvas, centerX, buttonY(startY, row++), "Map");
            drawButton(canvas, centerX, buttonY(startY, row++), "Ingredients");
            drawButton(canvas, centerX, buttonY(startY, row), "Github");
        });
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

            float titleY = screenHeight * 0.3f;
            float startY = titleY + 40;
            float centerX = screenWidth / 2f - BUTTON_WIDTH / 2f;

            int row = 0;
            if (isInButton(mx, my, centerX, buttonY(startY, row++))) {
                SeqClient.mc.setScreen(new PartyFinderScreen(this));
                return true;
            }
            if (warPlannerAuthorized()) {
                if (isInButton(mx, my, centerX, buttonY(startY, row++))) {
                    SeqClient.openWarPlannerScreen();
                    return true;
                }
            }
            if (isInButton(mx, my, centerX, buttonY(startY, row++))) {
                SeqClient.mc.setScreen(new ConnectionScreen(this));
            } else if (isInButton(mx, my, centerX, buttonY(startY, row++))) {
                SeqClient.mc.setScreen(new SettingsScreen(this));
            } else if (isInButton(mx, my, centerX, buttonY(startY, row++))) {
                SeqClient.mc.setScreen(new WorldMapScreen(this));
            } else if (isInButton(mx, my, centerX, buttonY(startY, row++))) {
                SeqClient.mc.setScreen(new IngredientGuideScreen(this));
            } else if (isInButton(mx, my, centerX, buttonY(startY, row))) {
                try {
                    java.net.URI uri = java.net.URI.create(GITHUB_URL);
                    java.awt.Desktop.getDesktop().browse(uri);
                } catch (Exception ignored) {
                }
            }
        }
        return super.mouseClicked(click, outsideScreen);
    }

    private boolean isInButton(float mx, float my, float bx, float by) {
        return mx >= bx && mx <= bx + BUTTON_WIDTH && my >= by && my <= by + BUTTON_HEIGHT;
    }

    private static float buttonY(float startY, int row) {
        return startY + (BUTTON_HEIGHT + BUTTON_SPACING) * row;
    }

    private static boolean warPlannerAuthorized() {
        return SeqClient.getWarPlannerManager() != null && SeqClient.getWarPlannerManager().isAuthorized();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

}
