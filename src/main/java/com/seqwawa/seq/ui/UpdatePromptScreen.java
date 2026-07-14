package com.seqwawa.seq.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.update.UpdateManager;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;

import java.awt.*;

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

        nvgMouseX = MinecraftUiRenderer.mouseX(mouseX);
        nvgMouseY = MinecraftUiRenderer.mouseY(mouseY);

        UiRenderer.renderScreen(this, canvas -> {
            float screenWidth = canvas.metrics().width();
            float screenHeight = canvas.metrics().height();

            canvas.fillRect(0, 0, screenWidth, screenHeight, BG_OVERLAY);

            float panelX = (screenWidth - PANEL_WIDTH) / 2f;
            float panelY = (screenHeight - PANEL_HEIGHT) / 2f;
            canvas.fillRect(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, PANEL_BG);

            String fontName = SeqClient.getFontManager().getSelectedFont();
            drawCenteredText(canvas, fontName, 16, TITLE_COLOR, panelX + PANEL_WIDTH / 2f, panelY + 26,
                    "Sequoia update available");
            drawCenteredText(canvas, fontName, 12, TEXT_COLOR, panelX + PANEL_WIDTH / 2f, panelY + 62,
                    "Current: " + installedVersion + "   Latest: " + release.tagName());
            drawCenteredText(canvas, fontName, 12, TEXT_COLOR, panelX + PANEL_WIDTH / 2f, panelY + 82,
                    "Update downloads and installs the new jar.");
            drawCenteredText(canvas, fontName, 12, TEXT_COLOR, panelX + PANEL_WIDTH / 2f, panelY + 100,
                    "Restart is required after install.");

            float buttonsTotalWidth = BUTTON_WIDTH * 3 + BUTTON_SPACING * 2;
            float startX = panelX + (PANEL_WIDTH - buttonsTotalWidth) / 2f;
            float buttonY = panelY + PANEL_HEIGHT - 42;

            drawButton(canvas, fontName, startX, buttonY, "Ignore");
            drawButton(canvas, fontName, startX + BUTTON_WIDTH + BUTTON_SPACING, buttonY, "Update");
            drawButton(canvas, fontName, startX + (BUTTON_WIDTH + BUTTON_SPACING) * 2, buttonY, "Update+Exit");
        });
    }

    private void drawButton(UiCanvas canvas, String fontName, float x, float y, String label) {
        boolean hovered = nvgMouseX >= x && nvgMouseX <= x + BUTTON_WIDTH
                && nvgMouseY >= y && nvgMouseY <= y + BUTTON_HEIGHT;

        canvas.fillRect(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, hovered ? BUTTON_HOVER : BUTTON_COLOR);
        drawCenteredText(canvas, fontName, 11, TEXT_COLOR,
                x + BUTTON_WIDTH / 2f, y + BUTTON_HEIGHT / 2f, label);
    }

    private static void drawCenteredText(
            UiCanvas canvas, String font, float size, Color color, float x, float y, String text) {
        canvas.drawText(text, x, y, new UiCanvas.TextStyle(
                font,
                size,
                color,
                UiCanvas.HorizontalAlign.CENTER,
                UiCanvas.VerticalAlign.MIDDLE));
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() != 0) {
            return super.mouseClicked(click, outsideScreen);
        }

        float mx = MinecraftUiRenderer.mouseX(click.x());
        float my = MinecraftUiRenderer.mouseY(click.y());

        float screenWidth = MinecraftUiRenderer.screenWidth();
        float screenHeight = MinecraftUiRenderer.screenHeight();
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
