package com.seqwawa.seq.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;

import java.awt.*;

public class SequoiaScreen extends Screen {
    private static final float BUTTON_WIDTH = 120;
    private static final float BUTTON_HEIGHT = 24;
    private static final float BUTTON_SPACING = 8;
    private static final float BUTTON_RADIUS = 6;
    private static final float TITLE_FONT_SIZE = 24;
    private static final float BUTTON_FONT_SIZE = 14;

    private static final Color BG_COLOR = new Color(0, 0, 0, 140);
    private static final Color BUTTON_COLOR = new Color(40, 40, 50, 200);
    private static final Color BUTTON_HOVER_COLOR = new Color(60, 60, 80, 220);
    private static final Color TEXT_COLOR = new Color(255, 255, 255, 255);
    private static final Color TITLE_COLOR = new Color(160, 130, 220, 255);

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
            canvas.fillRect(0, 0, screenWidth, screenHeight, BG_COLOR);

            // Title
            String fontName = SeqClient.getFontManager().getSelectedFont();
            float titleY = screenHeight * 0.3f;
            canvas.drawText("Sequoia", screenWidth / 2f, titleY, new UiCanvas.TextStyle(
                    fontName,
                    TITLE_FONT_SIZE,
                    TITLE_COLOR,
                    UiCanvas.HorizontalAlign.CENTER,
                    UiCanvas.VerticalAlign.MIDDLE));

            // Buttons
            float startY = titleY + 40;
            float centerX = screenWidth / 2f - BUTTON_WIDTH / 2f;

            drawButton(canvas, centerX, startY, "Partyfinder");
            drawButton(canvas, centerX, startY + BUTTON_HEIGHT + BUTTON_SPACING, "Connection");
            drawButton(canvas, centerX, startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 2, "Settings");
            drawButton(canvas, centerX, startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 3, "Map");
            drawButton(canvas, centerX, startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 4, "Github");
        });
    }

    private void drawButton(UiCanvas canvas, float x, float y, String label) {
        boolean hovered = nvgMouseX >= x && nvgMouseX <= x + BUTTON_WIDTH
                && nvgMouseY >= y && nvgMouseY <= y + BUTTON_HEIGHT;

        Color bgColor = hovered ? BUTTON_HOVER_COLOR : BUTTON_COLOR;
        canvas.fillRect(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, bgColor);

        String fontName = SeqClient.getFontManager().getSelectedFont();
        canvas.drawText(label, x + BUTTON_WIDTH / 2f, y + BUTTON_HEIGHT / 2f, new UiCanvas.TextStyle(
                fontName,
                BUTTON_FONT_SIZE,
                TEXT_COLOR,
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

            if (isInButton(mx, my, centerX, startY)) {
                SeqClient.mc.setScreen(new PartyFinderScreen(this));
            } else if (isInButton(mx, my, centerX, startY + BUTTON_HEIGHT + BUTTON_SPACING)) {
                SeqClient.mc.setScreen(new ConnectionScreen(this));
            } else if (isInButton(mx, my, centerX, startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 2)) {
                SeqClient.mc.setScreen(new SettingsScreen(this));
            } else if (isInButton(mx, my, centerX, startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 3)) {
                SeqClient.mc.setScreen(new WorldMapScreen(this));
            } else if (isInButton(mx, my, centerX, startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 4)) {
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }

}
