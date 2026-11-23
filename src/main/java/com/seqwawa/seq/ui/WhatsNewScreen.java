package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.update.ReleaseNotes;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * A release's full notes, opened from the update prompt's "What's new": the summary, then each
 * section's items, in a panel the size of the window that scrolls with the wheel, the scrollbar
 * or by dragging the text. Drawn like the prompt, which Back and Esc return to.
 */
public class WhatsNewScreen extends Screen {
    private static final float MAX_PANEL_WIDTH = 460;
    private static final float MAX_PANEL_HEIGHT = 420;
    /** Space kept around the panel, so the game shows at its edges like around the prompt. */
    private static final float SCREEN_MARGIN = 12;
    private static final float PADDING = 16;
    private static final float TITLE_HEIGHT = 44;
    private static final float FOOTER_HEIGHT = 40;
    private static final float BUTTON_WIDTH = 108;
    private static final float BUTTON_HEIGHT = 22;
    private static final float SCROLL_SPEED = 30;
    private static final float MARKER_SIZE = 3;
    private static final float LINE = ReleaseNotesLayout.LINE_HEIGHT;

    private final Screen parent;
    private final String heading;
    private final ReleaseNotes notes;
    private final ScrollPane scroll = new ScrollPane();
    private ReleaseNotesLayout.Layout laidOut;
    private float laidOutWidth = -1;
    private UiPointer pointer = UiPointer.ORIGIN;

    /** Where everything goes at one window size; drawing and the mouse both use it. */
    record Frame(UiBounds panel, UiBounds view, UiBounds track, UiBounds back) {}

    /** @param heading what the panel is titled, such as "What's new in Sequoia 1.16.0" */
    public WhatsNewScreen(Screen parent, String heading, ReleaseNotes notes) {
        super(Component.literal(heading));
        this.parent = parent;
        this.heading = heading;
        this.notes = notes;
    }

    static Frame frame(float screenWidth, float screenHeight) {
        float width = Math.max(1, Math.min(MAX_PANEL_WIDTH, screenWidth - 2 * SCREEN_MARGIN));
        float height = Math.max(TITLE_HEIGHT + FOOTER_HEIGHT + LINE, Math.min(MAX_PANEL_HEIGHT, screenHeight - 2 * SCREEN_MARGIN));
        UiBounds panel = new UiBounds((screenWidth - width) / 2f, (screenHeight - height) / 2f, width, height);
        UiBounds view = new UiBounds(panel.x() + PADDING, panel.y() + TITLE_HEIGHT, width - 2 * PADDING,
                height - TITLE_HEIGHT - FOOTER_HEIGHT);
        UiBounds track = new UiBounds(panel.right() - 7, view.y(), 3, view.height());
        UiBounds back = new UiBounds(panel.centerX() - BUTTON_WIDTH / 2f, panel.bottom() - FOOTER_HEIGHT / 2f - BUTTON_HEIGHT / 2f,
                BUTTON_WIDTH, BUTTON_HEIGHT);
        return new Frame(panel, view, track, back);
    }

    /** The notes laid out for a column {@code width} wide; laid out again only when the width changes. */
    private ReleaseNotesLayout.Layout layout(float width) {
        if (laidOut == null || width != laidOutWidth) {
            String font = SeqClient.getFontManager().getSelectedFont();
            laidOut = ReleaseNotesLayout.layout(notes, width,
                    (text, size) -> UiRenderer.measureText(text, font, size).width());
            laidOutWidth = width;
        }
        return laidOut;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        pointer = UiPointer.at(mouseX, mouseY);
        UiRenderer.renderScreen(this, canvas -> {
            float screenWidth = canvas.metrics().width();
            float screenHeight = canvas.metrics().height();
            Frame frame = frame(screenWidth, screenHeight);
            canvas.fillRect(0, 0, screenWidth, screenHeight, color(BACKGROUND_MODAL_OVERLAY));
            frame.panel().fill(canvas, color(BACKGROUND_BODY_OPAQUE));
            String font = SeqClient.getFontManager().getSelectedFont();
            centered(canvas, font, 16, color(ACCENT_PRIMARY_HOVER), frame.panel().centerX(), frame.panel().y() + 24, heading);

            // Room for the scrollbar is kept whether or not it shows, so the text never moves under it.
            UiBounds view = frame.view();
            ReleaseNotesLayout.Layout layout = layout(view.width() - 6);
            scroll.setMax(layout.height() - view.height());
            canvas.save();
            canvas.scissor(view.x(), view.y(), view.width(), view.height());
            float top = view.y() - scroll.offset();
            for (ReleaseNotesLayout.Line line : layout.lines()) {
                float y = top + line.y();
                if (y + line.height() < view.y() || y > view.bottom()) continue;
                drawLine(canvas, font, line, view.x(), y);
            }
            canvas.restore();
            scroll.draw(canvas, frame.track());

            UiBounds back = frame.back();
            back.fill(canvas, back.contains(pointer) ? color(ACCENT_PRIMARY_DARK_HOVER) : color(CONTROL_INPUT_HOVER));
            centered(canvas, font, 11, color(TEXT_SECONDARY), back.centerX(), back.centerY(), "Back");
        });
    }

    private static void drawLine(UiCanvas canvas, String font, ReleaseNotesLayout.Line line, float left, float top) {
        float middle = top + line.height() / 2f;
        switch (line.marker()) {
            case ITEM -> canvas.fillRect(left + line.markerX() + 2, middle - MARKER_SIZE / 2f - 0.5f, MARKER_SIZE, MARKER_SIZE,
                    color(ACCENT_PRIMARY));
            case SUB_ITEM -> canvas.fillRect(left + line.markerX() + 2.5f, middle - 1.5f, 2, 2, color(TEXT_MUTED));
            case NONE -> {}
        }
        float size = line.style() == ReleaseNotesLayout.Style.HEADING ? ReleaseNotesLayout.HEADING_SIZE : ReleaseNotesLayout.TEXT_SIZE;
        for (ReleaseNotesLayout.Run run : line.runs()) {
            Color shade = switch (line.style()) {
                case HEADING -> color(ACCENT_PRIMARY_HOVER);
                case SUMMARY -> color(TEXT_PRIMARY);
                case BODY -> run.bold() ? color(TEXT_PRIMARY) : color(TEXT_SECONDARY);
            };
            UiCanvas.TextStyle style = new UiCanvas.TextStyle(font, size, shade, UiCanvas.HorizontalAlign.LEFT,
                    UiCanvas.VerticalAlign.MIDDLE);
            canvas.drawText(run.text(), left + run.x(), middle, style);
            if (run.bold()) canvas.drawText(run.text(), left + run.x() + ReleaseNotesLayout.BOLD_OFFSET, middle, style);
        }
    }

    private static void centered(UiCanvas canvas, String font, float size, Color color, float x, float y, String text) {
        canvas.drawText(text, x, y, new UiCanvas.TextStyle(font, size, color, UiCanvas.HorizontalAlign.CENTER,
                UiCanvas.VerticalAlign.MIDDLE));
    }

    private static Frame currentFrame() {
        return frame(MinecraftUiRenderer.screenWidth(), MinecraftUiRenderer.screenHeight());
    }

    /** Scrolls by {@code amount} UI pixels, down for positive, as far as the notes go. */
    public void scrollBy(float amount) {
        scroll.scrollBy(amount);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll.scrollBy(-(float) scrollY * SCROLL_SPEED);
        return true;
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() != 0) return super.mouseClicked(click, outsideScreen);
        UiPointer at = UiPointer.at(click);
        Frame frame = currentFrame();
        if (frame.back().contains(at)) {
            onClose();
            return true;
        }
        // The scrollbar's hit area is a few pixels wider than the bar.
        if (scroll.startDrag(at, frame.track().inset(-3, 0))) return true;
        if (scroll.startContentDrag(at, frame.view())) return true;
        return super.mouseClicked(click, outsideScreen);
    }

    @Override
    public boolean mouseDragged(@NotNull MouseButtonEvent click, double deltaX, double deltaY) {
        if (scroll.drag(UiPointer.at(click), currentFrame().track())) return true;
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent click) {
        if (scroll.release()) return true;
        return super.mouseReleased(click);
    }

    /** Esc and Back return to the screen it was opened from. */
    @Override
    public void onClose() {
        SeqClient.mc.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public @NotNull Component getNarrationMessage() {
        StringBuilder narration = new StringBuilder(heading).append(". ");
        for (String line : notes.summary()) narration.append(line).append(' ');
        for (ReleaseNotes.Block block : notes.blocks()) {
            narration.append(switch (block) {
                case ReleaseNotes.Heading h -> h.text() + ".";
                case ReleaseNotes.Item item -> ReleaseNotes.plain(item.text());
                case ReleaseNotes.Paragraph paragraph -> ReleaseNotes.plain(paragraph.text());
            }).append(' ');
        }
        return Component.literal(narration.toString().strip());
    }
}
