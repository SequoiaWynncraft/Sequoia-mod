package org.sequoia.seq.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.update.UpdateManager;
import org.sequoia.seq.utils.rendering.nvg.NVGContext;
import org.sequoia.seq.utils.rendering.nvg.NVGWrapper;

import java.awt.*;

import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE;
import static org.lwjgl.nanovg.NanoVG.nvgFillColor;
import static org.lwjgl.nanovg.NanoVG.nvgFontFace;
import static org.lwjgl.nanovg.NanoVG.nvgFontSize;
import static org.lwjgl.nanovg.NanoVG.nvgText;
import static org.lwjgl.nanovg.NanoVG.nvgTextAlign;

public class UpdatePromptScreen extends Screen {
    private static final float PANEL_WIDTH = 340;
    private static final float PANEL_HEIGHT = 180;
    private static final float BUTTON_WIDTH = 96;
    private static final float BUTTON_HEIGHT = 22;
    private static final float BUTTON_SPACING = 12;

    private static final Color BG_OVERLAY = new Color(0, 0, 0, 150);
    private static final Color PANEL_BG = new Color(24, 24, 34, 240);
    private static final Color TITLE_COLOR = new Color(190, 150, 255, 255);
    private static final Color TEXT_COLOR = new Color(225, 225, 235, 255);
    private static final Color BUTTON_COLOR = new Color(55, 55, 70, 220);
    private static final Color BUTTON_HOVER = new Color(85, 70, 130, 230);

    private final Screen parent;
    private final String installedVersion;
    private final UpdateManager.ReleaseCandidate release;

    private float nvgMouseX;
    private float nvgMouseY;

    public UpdatePromptScreen(Screen parent, String installedVersion, UpdateManager.ReleaseCandidate release) {
        super(Component.literal("Update Available"));
        this.parent = parent;
        this.installedVersion = installedVersion;
        this.release = release;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        double guiScale = SeqClient.mc.getWindow().getGuiScale();
        nvgMouseX = (float) (mouseX * guiScale / 2.0);
        nvgMouseY = (float) (mouseY * guiScale / 2.0);

        NVGContext.renderDeferred(nvg -> {
            float screenWidth = SeqClient.mc.getWindow().getWidth() / 2f;
            float screenHeight = SeqClient.mc.getWindow().getHeight() / 2f;

            NVGWrapper.drawRect(nvg, 0, 0, screenWidth, screenHeight, BG_OVERLAY);

            float panelX = (screenWidth - PANEL_WIDTH) / 2f;
            float panelY = (screenHeight - PANEL_HEIGHT) / 2f;
            NVGWrapper.drawRect(nvg, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, PANEL_BG);

            String fontName = SeqClient.getFontManager().getSelectedFont();
            nvgFontFace(nvg, fontName);
            nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);

            nvgFontSize(nvg, 16);
            var titleCol = NVGContext.nvgColor(TITLE_COLOR);
            nvgFillColor(nvg, titleCol);
            nvgText(nvg, panelX + PANEL_WIDTH / 2f, panelY + 26, "Sequoia update available");
            titleCol.free();

            nvgFontSize(nvg, 12);
            var textCol = NVGContext.nvgColor(TEXT_COLOR);
            nvgFillColor(nvg, textCol);
            nvgText(nvg, panelX + PANEL_WIDTH / 2f, panelY + 62,
                    "Current: " + installedVersion + "   Latest: " + release.tagName());
            nvgText(nvg, panelX + PANEL_WIDTH / 2f, panelY + 82,
                    "Update downloads and installs the new jar.");
            nvgText(nvg, panelX + PANEL_WIDTH / 2f, panelY + 100,
                    "Restart is required after install.");
            textCol.free();

            float buttonsTotalWidth = BUTTON_WIDTH * 3 + BUTTON_SPACING * 2;
            float startX = panelX + (PANEL_WIDTH - buttonsTotalWidth) / 2f;
            float buttonY = panelY + PANEL_HEIGHT - 42;

            drawButton(nvg, fontName, startX, buttonY, "Ignore");
            drawButton(nvg, fontName, startX + BUTTON_WIDTH + BUTTON_SPACING, buttonY, "Update");
            drawButton(nvg, fontName, startX + (BUTTON_WIDTH + BUTTON_SPACING) * 2, buttonY, "Update+Exit");
        });
    }

    private void drawButton(long nvg, String fontName, float x, float y, String label) {
        boolean hovered = nvgMouseX >= x && nvgMouseX <= x + BUTTON_WIDTH
                && nvgMouseY >= y && nvgMouseY <= y + BUTTON_HEIGHT;

        NVGWrapper.drawRect(nvg, x, y, BUTTON_WIDTH, BUTTON_HEIGHT, hovered ? BUTTON_HOVER : BUTTON_COLOR);

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, 11);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var textColor = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, textColor);
        nvgText(nvg, x + BUTTON_WIDTH / 2f, y + BUTTON_HEIGHT / 2f, label);
        textColor.free();
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() != 0) {
            return super.mouseClicked(click, outsideScreen);
        }

        double guiScale = SeqClient.mc.getWindow().getGuiScale();
        float mx = (float) (click.x() * guiScale / 2.0);
        float my = (float) (click.y() * guiScale / 2.0);

        float screenWidth = SeqClient.mc.getWindow().getWidth() / 2f;
        float screenHeight = SeqClient.mc.getWindow().getHeight() / 2f;
        float panelX = (screenWidth - PANEL_WIDTH) / 2f;
        float panelY = (screenHeight - PANEL_HEIGHT) / 2f;

        float buttonsTotalWidth = BUTTON_WIDTH * 3 + BUTTON_SPACING * 2;
        float startX = panelX + (PANEL_WIDTH - buttonsTotalWidth) / 2f;
        float buttonY = panelY + PANEL_HEIGHT - 42;

        if (isInButton(mx, my, startX, buttonY)) {
            UpdateManager.getInstance().ignoreForSession(release.tagName());
            SeqClient.mc.setScreen(parent);
            return true;
        }

        if (isInButton(mx, my, startX + BUTTON_WIDTH + BUTTON_SPACING, buttonY)) {
            UpdateManager.getInstance().applyPendingUpdate(false);
            SeqClient.mc.setScreen(parent);
            return true;
        }

        if (isInButton(mx, my, startX + (BUTTON_WIDTH + BUTTON_SPACING) * 2, buttonY)) {
            UpdateManager.getInstance().applyPendingUpdate(true);
            SeqClient.mc.setScreen(parent);
            return true;
        }

        return super.mouseClicked(click, outsideScreen);
    }

    private boolean isInButton(float mouseX, float mouseY, float x, float y) {
        return mouseX >= x && mouseX <= x + BUTTON_WIDTH && mouseY >= y && mouseY <= y + BUTTON_HEIGHT;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
