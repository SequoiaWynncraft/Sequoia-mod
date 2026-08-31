package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_PRIMARY;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_BODY;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_HEADER;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_BORDER;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_INPUT;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_INPUT_HOVER;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_MUTED;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_PRIMARY;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/** Drag editor scoped to the war territory queue HUD. */
public final class WarQueueHudEditorScreen extends Screen {
    private static final float HEADER_HEIGHT = 30f;
    private static final float BUTTON_WIDTH = 64f;
    private static final float BUTTON_HEIGHT = 18f;
    private static final float PADDING = 7f;

    private final Screen parent;
    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;
    private float mouseX;
    private float mouseY;

    public WarQueueHudEditorScreen(Screen parent) {
        super(Component.literal("War queue HUD layout"));
        this.parent = parent;
    }

    @Override
    public void render(GuiGraphics graphics, int rawMouseX, int rawMouseY, float partialTick) {
        super.render(graphics, rawMouseX, rawMouseY, partialTick);
        mouseX = MinecraftUiRenderer.mouseX(rawMouseX);
        mouseY = MinecraftUiRenderer.mouseY(rawMouseY);
        UiRenderer.renderScreen(this, canvas -> {
            float width = canvas.metrics().width();
            float height = canvas.metrics().height();
            String font = SeqClient.getFontManager().getSelectedFont();

            canvas.fillRect(0, 0, width, height, color(BACKGROUND_BODY));
            canvas.fillRect(0, 0, width, HEADER_HEIGHT, color(BACKGROUND_HEADER));
            drawButton(canvas, font, resetButton(), "Reset");
            drawButton(canvas, font, doneButton(), "Done");
            drawText(canvas, font, 16f, color(ACCENT_PRIMARY), width / 2f, HEADER_HEIGHT / 2f, "War queue HUD");

            WarTerritoryQueueHudRenderer.Bounds bounds =
                    WarTerritoryQueueHudRenderer.previewBounds(width, height);
            canvas.fillRoundedRect(
                    bounds.x() - PADDING,
                    bounds.y() - PADDING,
                    bounds.width() + PADDING * 2f,
                    bounds.height() + PADDING * 2f,
                    4f,
                    color(CONTROL_INPUT, 210));
            canvas.strokeRect(
                    bounds.x() - PADDING,
                    bounds.y() - PADDING,
                    bounds.width() + PADDING * 2f,
                    bounds.height() + PADDING * 2f,
                    1f,
                    color(dragging ? ACCENT_PRIMARY : CONTROL_BORDER));
            WarTerritoryQueueHudRenderer.renderPreview(canvas);

            drawText(
                    canvas,
                    font,
                    11f,
                    color(TEXT_MUTED),
                    width / 2f,
                    height - 12f,
                    "Drag the preview to reposition the war territory queue feed");
        });
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() != 0) {
            return super.mouseClicked(click, outsideScreen);
        }
        float x = MinecraftUiRenderer.mouseX(click.x());
        float y = MinecraftUiRenderer.mouseY(click.y());
        float width = MinecraftUiRenderer.screenWidth();
        float height = MinecraftUiRenderer.screenHeight();
        if (resetButton().contains(x, y)) {
            SeqClient.getWarQueueHudXSetting().reset();
            SeqClient.getWarQueueHudYSetting().reset();
            SeqClient.getConfigManager().save();
            return true;
        }
        if (doneButton().contains(x, y)) {
            onClose();
            return true;
        }

        WarTerritoryQueueHudRenderer.Bounds bounds =
                WarTerritoryQueueHudRenderer.previewBounds(width, height);
        if (bounds.contains(x, y, PADDING)) {
            dragging = true;
            dragOffsetX = x - bounds.x();
            dragOffsetY = y - bounds.y();
            return true;
        }
        return super.mouseClicked(click, outsideScreen);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (!dragging) {
            return super.mouseDragged(click, deltaX, deltaY);
        }
        float x = MinecraftUiRenderer.mouseX(click.x());
        float y = MinecraftUiRenderer.mouseY(click.y());
        float width = MinecraftUiRenderer.screenWidth();
        float height = MinecraftUiRenderer.screenHeight();
        WarTerritoryQueueHudRenderer.Bounds bounds =
                WarTerritoryQueueHudRenderer.previewBounds(width, height);
        WarTerritoryQueueHudRenderer.Position position = WarTerritoryQueueHudRenderer.positionForTopLeft(
                width,
                height,
                bounds.width(),
                bounds.height(),
                x - dragOffsetX,
                y - dragOffsetY);
        SeqClient.getWarQueueHudXSetting().setValue(position.x());
        SeqClient.getWarQueueHudYSetting().setValue(position.y());
        return true;
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent click) {
        if (dragging && click.button() == 0) {
            dragging = false;
            SeqClient.getConfigManager().save();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public void removed() {
        SeqClient.getConfigManager().save();
        super.removed();
    }

    @Override
    public void onClose() {
        SeqClient.mc.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawButton(UiCanvas canvas, String font, Rect bounds, String label) {
        canvas.fillRect(
                bounds.x(),
                bounds.y(),
                bounds.width(),
                bounds.height(),
                color(bounds.contains(mouseX, mouseY) ? CONTROL_INPUT_HOVER : CONTROL_INPUT));
        drawText(
                canvas,
                font,
                11f,
                color(TEXT_PRIMARY),
                bounds.x() + bounds.width() / 2f,
                bounds.y() + bounds.height() / 2f,
                label);
    }

    private static void drawText(
            UiCanvas canvas, String font, float size, java.awt.Color textColor, float x, float y, String text) {
        canvas.drawText(
                text,
                x,
                y,
                new UiCanvas.TextStyle(
                        font,
                        size,
                        textColor,
                        UiCanvas.HorizontalAlign.CENTER,
                        UiCanvas.VerticalAlign.MIDDLE));
    }

    private static Rect resetButton() {
        return new Rect(7f, 6f, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    private static Rect doneButton() {
        return new Rect(14f + BUTTON_WIDTH, 6f, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    private record Rect(float x, float y, float width, float height) {
        boolean contains(float pointX, float pointY) {
            return pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height;
        }
    }
}
