package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_PRIMARY;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_PRIMARY_DARK;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_BODY_OPAQUE;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_CONTENT;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_INPUT;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_INPUT_HOVER;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_MUTED;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_PRIMARY;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.WarPlannerManager;
import com.seqwawa.seq.model.war.WarPlannerDrafts.ZoneCategoryDraft;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.ZoneCategory;
import com.seqwawa.seq.utils.TextInputHelper;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/** Small focused name editor for shared War Map categories. */
final class WarZoneCategoryEditorScreen extends Screen {
    private static final float PANEL_WIDTH = 320;
    private static final float PANEL_HEIGHT = 126;
    private static final float BUTTON_HEIGHT = 24;

    private final Screen parent;
    private final ZoneCategory original;
    private final WarPlannerManager manager;
    private String name;
    private String message;
    private boolean replaceOnType;
    private boolean saving;
    private float mouseX;
    private float mouseY;

    WarZoneCategoryEditorScreen(Screen parent, ZoneCategory original) {
        super(Component.literal(original == null ? "New zone category" : "Rename zone category"));
        this.parent = parent;
        this.original = original;
        this.manager = SeqClient.getWarPlannerManager();
        this.name = original == null ? "New category" : original.name();
        this.replaceOnType = true;
    }

    @Override
    public void tick() {
        if (manager == null || !manager.isAuthorized() || !manager.canManage()) onClose();
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
        text(canvas, original == null ? "New zone category" : "Rename zone category",
                x + 14, y + 18, 14, color(ACCENT_PRIMARY), false);
        canvas.fillRoundedRect(x + 14, y + 38, PANEL_WIDTH - 28, 26, 4, color(CONTROL_INPUT));
        text(canvas, name + (saving ? "" : "│"), x + 22, y + 51, 11, color(TEXT_PRIMARY), false);
        if (message != null) text(canvas, message, x + 14, y + 76, 9, color(TEXT_MUTED), false);
        button(canvas, x + PANEL_WIDTH - 150, y + PANEL_HEIGHT - 34, 62, "Cancel", false);
        button(canvas, x + PANEL_WIDTH - 80, y + PANEL_HEIGHT - 34, 66,
                saving ? "Saving…" : "Save", saving);
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
        if (hit(mx, my, x + 14, y + 38, PANEL_WIDTH - 28, 26)) {
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
                name = "";
                replaceOnType = false;
            } else if (!name.isEmpty()) {
                name = name.substring(0, name.length() - 1);
            }
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
                name = "";
                replaceOnType = false;
            }
            if (name.length() < 64) name += typed;
        }
        return true;
    }

    private void save() {
        if (saving) return;
        try {
            ZoneCategoryDraft draft = new ZoneCategoryDraft(
                    name, original == null ? null : original.version());
            saving = true;
            manager.saveZoneCategory(original == null ? null : original.id(), draft).whenComplete((result, error) ->
                    SeqClient.mc.execute(() -> {
                        saving = false;
                        if (error != null || result == null || !result.success()) {
                            message = error != null
                                    ? "War planner request failed."
                                    : result == null ? "No response." : result.message();
                            return;
                        }
                        SeqClient.mc.setScreen(parent);
                    }));
        } catch (IllegalArgumentException exception) {
            message = exception.getMessage();
        }
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
        canvas.drawText(value == null ? "" : value, x, y, new UiCanvas.TextStyle(
                SeqClient.getFontManager().getSelectedFont(), size, textColor,
                centered ? UiCanvas.HorizontalAlign.CENTER : UiCanvas.HorizontalAlign.LEFT,
                UiCanvas.VerticalAlign.MIDDLE));
    }

    private static boolean hit(float mx, float my, float x, float y, float width, float height) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }
}
