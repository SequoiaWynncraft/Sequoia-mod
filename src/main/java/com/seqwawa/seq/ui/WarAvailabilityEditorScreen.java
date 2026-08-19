package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.WarPlannerManager;
import com.seqwawa.seq.utils.TextInputHelper;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/** Focused editor for a caller's custom war availability duration. */
final class WarAvailabilityEditorScreen extends Screen {
    private static final float PANEL_WIDTH = 340;
    private static final float PANEL_HEIGHT = 140;
    private static final float BUTTON_HEIGHT = 24;
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "^(?:([1-9]\\d*)\\s*h(?:r|rs)?(?:\\s*([0-9]\\d*)\\s*m(?:in|ins)?)?|"
                    + "([1-9]\\d*)\\s*m(?:in|ins)?)$",
            Pattern.CASE_INSENSITIVE);

    private final Screen parent;
    private final WarPlannerManager manager;
    private String duration = "1h";
    private String message;
    private boolean replaceOnType = true;
    private boolean saving;
    private float mouseX;
    private float mouseY;

    WarAvailabilityEditorScreen(Screen parent) {
        super(Component.literal("Custom war availability"));
        this.parent = parent;
        this.manager = SeqClient.getWarPlannerManager();
    }

    @Override
    public void tick() {
        if (manager == null || !manager.isAuthorized()) onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.mouseX = MinecraftUiRenderer.mouseX(mouseX);
        this.mouseY = MinecraftUiRenderer.mouseY(mouseY);
        UiRenderer.renderScreen(this, this::renderEditor);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    private void renderEditor(UiCanvas canvas) {
        float width = canvas.metrics().width();
        float height = canvas.metrics().height();
        float x = (width - PANEL_WIDTH) / 2;
        float y = (height - PANEL_HEIGHT) / 2;
        canvas.fillRect(0, 0, width, height, new Color(0, 0, 0, 145));
        canvas.fillRoundedRect(x, y, PANEL_WIDTH, PANEL_HEIGHT, 6, color(BACKGROUND_BODY_OPAQUE));
        text(canvas, "Custom availability", x + 14, y + 19, 14, color(ACCENT_PRIMARY), false);
        canvas.fillRoundedRect(x + 14, y + 36, PANEL_WIDTH - 28, 26, 4, color(CONTROL_INPUT));
        text(canvas, duration + (saving ? "" : "│"), x + 22, y + 49, 11, color(TEXT_PRIMARY), false);
        text(canvas, message == null ? "Examples: 45m, 1hr 30min, 2h · maximum 24h" : message,
                x + 14, y + 76, 9, color(message == null ? TEXT_MUTED : CONTROL_WARNING), false);
        button(canvas, x + PANEL_WIDTH - 150, y + PANEL_HEIGHT - 34, 62, "Cancel", false);
        button(canvas, x + PANEL_WIDTH - 80, y + PANEL_HEIGHT - 34, 66,
                saving ? "Saving…" : "Set", saving);
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() != 0) return super.mouseClicked(click, outsideScreen);
        float width = MinecraftUiRenderer.screenWidth();
        float height = MinecraftUiRenderer.screenHeight();
        float x = (width - PANEL_WIDTH) / 2;
        float y = (height - PANEL_HEIGHT) / 2;
        float mx = MinecraftUiRenderer.mouseX(click.x());
        float my = MinecraftUiRenderer.mouseY(click.y());
        if (hit(mx, my, x + PANEL_WIDTH - 150, y + PANEL_HEIGHT - 34, 62, BUTTON_HEIGHT)) {
            onClose();
            return true;
        }
        if (hit(mx, my, x + PANEL_WIDTH - 80, y + PANEL_HEIGHT - 34, 66, BUTTON_HEIGHT)) {
            save();
            return true;
        }
        if (hit(mx, my, x + 14, y + 36, PANEL_WIDTH - 28, 26)) {
            replaceOnType = false;
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        if (!saving && event.key() == GLFW.GLFW_KEY_BACKSPACE) {
            if (replaceOnType) {
                duration = "";
                replaceOnType = false;
            } else if (!duration.isEmpty()) {
                duration = duration.substring(0, duration.length() - 1);
            }
            message = null;
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(@NotNull CharacterEvent event) {
        if (saving) return true;
        String typed = TextInputHelper.getTypedText(event);
        if (typed != null && typed.length() == 1 && !Character.isISOControl(typed.charAt(0))) {
            if (replaceOnType) {
                duration = "";
                replaceOnType = false;
            }
            if (duration.length() < 12) duration += typed;
            message = null;
        }
        return true;
    }

    private void save() {
        if (saving) return;
        if (manager == null || !manager.isAuthorized()) {
            message = "War planner access is no longer available.";
            return;
        }
        final int minutes;
        try {
            minutes = parseDurationMinutes(duration);
        } catch (IllegalArgumentException exception) {
            message = exception.getMessage();
            return;
        }
        saving = true;
        manager.setAvailability(minutes).whenComplete((result, error) -> SeqClient.mc.execute(() -> {
            saving = false;
            if (error != null || result == null || !result.success()) {
                message = error != null
                        ? "War planner request failed."
                        : result == null ? "No response." : result.message();
                return;
            }
            SeqClient.mc.setScreen(parent);
        }));
    }

    static int parseDurationMinutes(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        Matcher matcher = DURATION_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Use a duration such as 45m, 90min, 2h, or 2hrs.");
        }
        final long hours;
        final long minutesPart;
        try {
            hours = matcher.group(1) == null ? 0 : Long.parseLong(matcher.group(1));
            minutesPart = Long.parseLong(matcher.group(1) == null ? matcher.group(3) : valueOrZero(matcher.group(2)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Availability must be between 1 minute and 24 hours.");
        }
        if (minutesPart >= 60 && hours > 0) {
            throw new IllegalArgumentException("Use fewer than 60 minutes after the hour value.");
        }
        if (hours > 24 || minutesPart > 1440) {
            throw new IllegalArgumentException("Availability must be between 1 minute and 24 hours.");
        }
        long totalMinutes = hours * 60 + minutesPart;
        if (totalMinutes < 1 || totalMinutes > 1440) {
            throw new IllegalArgumentException("Availability must be between 1 minute and 24 hours.");
        }
        return (int) totalMinutes;
    }

    private static String valueOrZero(String value) {
        return value == null ? "0" : value;
    }

    @Override
    public void onClose() {
        SeqClient.mc.setScreen(parent);
    }

    private void button(UiCanvas canvas, float x, float y, float width, String label, boolean disabled) {
        boolean hovered = !disabled && hit(mouseX, mouseY, x, y, width, BUTTON_HEIGHT);
        canvas.fillRoundedRect(x, y, width, BUTTON_HEIGHT, 4,
                disabled ? color(ACCENT_PRIMARY_DARK) : color(hovered ? CONTROL_INPUT_HOVER : CONTROL_INPUT));
        text(canvas, label, x + width / 2, y + BUTTON_HEIGHT / 2, 10,
                color(disabled ? TEXT_MUTED : TEXT_PRIMARY), true);
    }

    private static void text(
            UiCanvas canvas, String value, float x, float y, float size, Color textColor, boolean centered) {
        canvas.drawText(value, x, y, new UiCanvas.TextStyle(
                SeqClient.getFontManager().getSelectedFont(), size, textColor,
                centered ? UiCanvas.HorizontalAlign.CENTER : UiCanvas.HorizontalAlign.LEFT,
                UiCanvas.VerticalAlign.MIDDLE));
    }

    private static boolean hit(float mx, float my, float x, float y, float width, float height) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }
}
