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
                drawButton(canvas, centerX, menu.buttonY(row), menu.buttonHeight(), destinations.get(row).label());
            }
        });
    }

    private void drawButton(UiCanvas canvas, float x, float y, float height, String label) {
        boolean hovered = nvgMouseX >= x && nvgMouseX <= x + BUTTON_WIDTH
                && nvgMouseY >= y && nvgMouseY <= y + height;

        Color bgColor = hovered ? color(CONTROL_INPUT_HOVER) : color(BACKGROUND_POPUP, 200);
        canvas.fillRect(x, y, BUTTON_WIDTH, height, bgColor);

        String fontName = SeqClient.getFontManager().getSelectedFont();
        canvas.drawText(label, x + BUTTON_WIDTH / 2f, y + height / 2f, new UiCanvas.TextStyle(
                fontName,
                Math.min(BUTTON_FONT_SIZE, Math.max(8, height - 2)),
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
                if (isInButton(mx, my, centerX, menu.buttonY(row), menu.buttonHeight())) {
                    SequoiaSidebarNavigation.open(destinations.get(row), this);
                    return true;
                }
            }
        }
        return super.mouseClicked(click, outsideScreen);
    }

    private boolean isInButton(float mx, float my, float bx, float by, float buttonHeight) {
        return mx >= bx && mx <= bx + BUTTON_WIDTH && my >= by && my <= by + buttonHeight;
    }

    static MenuLayout menuLayout(float screenHeight, int rowCount) {
        int rows = Math.max(1, rowCount);
        float bottomPadding = 12;
        float minimumStartY = 34;
        float availableHeight = Math.max(BUTTON_HEIGHT, screenHeight - minimumStartY - bottomPadding);
        float normalBlockHeight = BUTTON_HEIGHT * rows + BUTTON_SPACING * (rows - 1);
        float buttonHeight;
        float rowStep;
        if (normalBlockHeight <= availableHeight) {
            buttonHeight = BUTTON_HEIGHT;
            rowStep = BUTTON_HEIGHT + BUTTON_SPACING;
        } else {
            float minimumButtonHeight = 10;
            float minimumSpacing = 1;
            buttonHeight = Math.max(
                    minimumButtonHeight,
                    Math.min(BUTTON_HEIGHT, (availableHeight - minimumSpacing * (rows - 1)) / rows));
            float spacing = rows == 1
                    ? 0
                    : Math.max(
                            minimumSpacing,
                            Math.min(BUTTON_SPACING, (availableHeight - buttonHeight * rows) / (rows - 1)));
            rowStep = buttonHeight + spacing;
        }
        float blockHeight = buttonHeight + rowStep * (rows - 1);
        float startY = Math.max(8, Math.min(screenHeight * .3f + 40, screenHeight - bottomPadding - blockHeight));
        float titleY = Math.max(18, Math.min(screenHeight * .3f, startY - 28));
        return new MenuLayout(titleY, startY, rowStep, buttonHeight, rows);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    record MenuLayout(float titleY, float startY, float rowStep, float buttonHeight, int rowCount) {
        float buttonY(int row) {
            return startY + rowStep * row;
        }

        float bottom() {
            return buttonY(rowCount - 1) + buttonHeight;
        }
    }
}
