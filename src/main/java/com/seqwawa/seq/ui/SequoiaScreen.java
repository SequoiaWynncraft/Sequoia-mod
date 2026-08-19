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
            var destinations = SequoiaSidebarNavigation.destinations();
            MenuLayout menu = menuLayout(screenHeight, destinations.size());
            float titleY = menu.titleY();
            canvas.drawText("Sequoia", screenWidth / 2f, titleY, new UiCanvas.TextStyle(
                    fontName,
                    TITLE_FONT_SIZE,
                    color(ACCENT_PRIMARY),
                    UiCanvas.HorizontalAlign.CENTER,
                    UiCanvas.VerticalAlign.MIDDLE));

            // Buttons
            float centerX = screenWidth / 2f - BUTTON_WIDTH / 2f;

            for (int row = 0; row < destinations.size(); row++) {
                drawButton(canvas, centerX, menu.buttonY(row), destinations.get(row).label());
            }
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

            var destinations = SequoiaSidebarNavigation.destinations();
            MenuLayout menu = menuLayout(screenHeight, destinations.size());
            float centerX = screenWidth / 2f - BUTTON_WIDTH / 2f;

            for (int row = 0; row < destinations.size(); row++) {
                if (isInButton(mx, my, centerX, menu.buttonY(row))) {
                    SequoiaSidebarNavigation.open(destinations.get(row), this);
                    return true;
                }
            }
        }
        return super.mouseClicked(click, outsideScreen);
    }

    private boolean isInButton(float mx, float my, float bx, float by) {
        return mx >= bx && mx <= bx + BUTTON_WIDTH && my >= by && my <= by + BUTTON_HEIGHT;
    }

    static MenuLayout menuLayout(float screenHeight, int rowCount) {
        int rows = Math.max(1, rowCount);
        float bottomPadding = 12;
        float minimumStartY = 48;
        float availableHeight = Math.max(BUTTON_HEIGHT, screenHeight - minimumStartY - bottomPadding);
        float rowStep = rows == 1
                ? BUTTON_HEIGHT
                : Math.min(BUTTON_HEIGHT + BUTTON_SPACING,
                        Math.max(BUTTON_HEIGHT + 2, (availableHeight - BUTTON_HEIGHT) / (rows - 1)));
        float blockHeight = BUTTON_HEIGHT + rowStep * (rows - 1);
        float startY = Math.max(8, Math.min(screenHeight * .3f + 40, screenHeight - bottomPadding - blockHeight));
        float titleY = Math.max(18, Math.min(screenHeight * .3f, startY - 28));
        return new MenuLayout(titleY, startY, rowStep, rows);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    record MenuLayout(float titleY, float startY, float rowStep, int rowCount) {
        float buttonY(int row) {
            return startY + rowStep * row;
        }

        float bottom() {
            return buttonY(rowCount - 1) + BUTTON_HEIGHT;
        }
    }
}
