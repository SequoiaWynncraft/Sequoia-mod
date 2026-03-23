package org.sequoia.seq.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.utils.rendering.nvg.NVGContext;
import org.sequoia.seq.utils.rendering.nvg.NVGWrapper;

import java.awt.*;

import static org.lwjgl.nanovg.NanoVG.*;

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

        double guiScale = SeqClient.mc.getWindow().getGuiScale();
        nvgMouseX = (float) (mouseX * guiScale / 2.0);
        nvgMouseY = (float) (mouseY * guiScale / 2.0);

        NVGContext.renderDeferred(nvg -> {
            float screenWidth = (int) (SeqClient.mc.getWindow().getWidth() / 2f);
            float screenHeight = (int) (SeqClient.mc.getWindow().getHeight() / 2f);

            // Dark background
            NVGWrapper.drawRect(nvg, 0, 0, screenWidth, screenHeight, BG_COLOR);

            // Title
            String fontName = SeqClient.getFontManager().getSelectedFont();
            nvgFontFace(nvg, fontName);
            nvgFontSize(nvg, TITLE_FONT_SIZE);
            nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
            var titleColor = NVGContext.nvgColor(TITLE_COLOR);
            nvgFillColor(nvg, titleColor);
            float titleY = screenHeight * 0.3f;
            nvgText(nvg, screenWidth / 2f, titleY, "Sequoia");
            titleColor.free();

            // Buttons
            float startY = titleY + 40;
            float centerX = screenWidth / 2f - BUTTON_WIDTH / 2f;

            drawButton(nvg, centerX, startY, "Partyfinder");
            drawButton(nvg, centerX, startY + BUTTON_HEIGHT + BUTTON_SPACING, "Authentication");
            drawButton(nvg, centerX, startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 2, "Settings");
            drawButton(nvg, centerX, startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 3, "Github");
        });
    }

    private void drawButton(long nvg, float x, float y, String label) {
        boolean hovered = nvgMouseX >= x && nvgMouseX <= x + BUTTON_WIDTH
                && nvgMouseY >= y && nvgMouseY <= y + BUTTON_HEIGHT;

        Color bgColor = hovered ? BUTTON_HOVER_COLOR : BUTTON_COLOR;
        NVGWrapper.drawRect(nvg, x, y, BUTTON_WIDTH, BUTTON_HEIGHT, bgColor);

        String fontName = SeqClient.getFontManager().getSelectedFont();
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, BUTTON_FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var textColor = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, textColor);
        nvgText(nvg, x + BUTTON_WIDTH / 2f, y + BUTTON_HEIGHT / 2f, label);
        textColor.free();
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() == 0) {
            double guiScale = SeqClient.mc.getWindow().getGuiScale();
            float mx = (float) (click.x() * guiScale / 2.0);
            float my = (float) (click.y() * guiScale / 2.0);

            float screenWidth = (int) (SeqClient.mc.getWindow().getWidth() / 2f);
            float screenHeight = (int) (SeqClient.mc.getWindow().getHeight() / 2f);

            float titleY = screenHeight * 0.3f;
            float startY = titleY + 40;
            float centerX = screenWidth / 2f - BUTTON_WIDTH / 2f;

            if (isInButton(mx, my, centerX, startY)) {
                SeqClient.mc.setScreen(new PartyFinderScreen(this));
            } else if (isInButton(mx, my, centerX, startY + BUTTON_HEIGHT + BUTTON_SPACING)) {
                SeqClient.mc.setScreen(new AuthenticationScreen(this));
            } else if (isInButton(mx, my, centerX, startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 2)) {
                SeqClient.mc.setScreen(new SettingsScreen(this));
            } else if (isInButton(mx, my, centerX, startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 3)) {
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
