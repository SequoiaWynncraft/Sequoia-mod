package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.SeqPointsShopManager;
import com.seqwawa.seq.model.SeqPointsShop;
import com.seqwawa.seq.utils.PlayerNameCache;
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

/** Collects a target and safe alias for the temporary-rename product. */
final class SeqPointsRenameScreen extends Screen {
    private static final float PANEL_WIDTH = 360;
    private static final float PANEL_HEIGHT = 190;
    private static final float BUTTON_HEIGHT = 24;

    private final Screen parent;
    private final SeqPointsShop.Item item;
    private String target = "";
    private String alias = "";
    private int focusedField;
    private boolean saving;
    private String message;
    private float mouseX;
    private float mouseY;

    SeqPointsRenameScreen(Screen parent, SeqPointsShop.Item item) {
        super(Component.literal("Temporary Rename"));
        this.parent = parent;
        this.item = item;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.mouseX = MinecraftUiRenderer.mouseX(mouseX);
        this.mouseY = MinecraftUiRenderer.mouseY(mouseY);
        UiRenderer.renderScreen(this, this::renderEditor);
    }

    private void renderEditor(UiCanvas canvas) {
        float x = (canvas.metrics().width() - PANEL_WIDTH) / 2;
        float y = (canvas.metrics().height() - PANEL_HEIGHT) / 2;
        canvas.fillRect(0, 0, canvas.metrics().width(), canvas.metrics().height(), new Color(0, 0, 0, 150));
        canvas.fillRoundedRect(x, y, PANEL_WIDTH, PANEL_HEIGHT, 7, color(BACKGROUND_BODY_OPAQUE));
        text(canvas, "Temporary Rename · " + item.price() + " SP", x + 14, y + 20, 14, color(ACCENT_PRIMARY));
        text(canvas, "Player", x + 14, y + 46, 9, color(TEXT_MUTED));
        input(canvas, x + 14, y + 54, target, focusedField == 0);
        text(canvas, "Alias (shown as Alias (RealName))", x + 14, y + 92, 9, color(TEXT_MUTED));
        input(canvas, x + 14, y + 100, alias, focusedField == 1);
        if (message != null) text(canvas, message, x + 14, y + 139, 9, color(CONTROL_WARNING));
        button(canvas, x + PANEL_WIDTH - 154, y + PANEL_HEIGHT - 35, 66, "Cancel", false);
        button(canvas, x + PANEL_WIDTH - 80, y + PANEL_HEIGHT - 35, 66, saving ? "Buying…" : "Buy", saving);
    }

    private void input(UiCanvas canvas, float x, float y, String value, boolean focused) {
        canvas.fillRoundedRect(x, y, PANEL_WIDTH - 28, 26, 4, color(focused ? CONTROL_INPUT_HOVER : CONTROL_INPUT));
        text(canvas, value + (focused && !saving ? "│" : ""), x + 8, y + 13, 11, color(TEXT_PRIMARY));
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() != 0) return super.mouseClicked(click, outsideScreen);
        float x = (MinecraftUiRenderer.screenWidth() - PANEL_WIDTH) / 2;
        float y = (MinecraftUiRenderer.screenHeight() - PANEL_HEIGHT) / 2;
        if (hit(mouseX, mouseY, x + 14, y + 54, PANEL_WIDTH - 28, 26)) focusedField = 0;
        else if (hit(mouseX, mouseY, x + 14, y + 100, PANEL_WIDTH - 28, 26)) focusedField = 1;
        else if (hit(mouseX, mouseY, x + PANEL_WIDTH - 154, y + PANEL_HEIGHT - 35, 66, BUTTON_HEIGHT)) onClose();
        else if (hit(mouseX, mouseY, x + PANEL_WIDTH - 80, y + PANEL_HEIGHT - 35, 66, BUTTON_HEIGHT)) save();
        return true;
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_TAB) {
            focusedField = 1 - focusedField;
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        if (!saving && event.key() == GLFW.GLFW_KEY_BACKSPACE) {
            if (focusedField == 0 && !target.isEmpty()) target = target.substring(0, target.length() - 1);
            if (focusedField == 1 && !alias.isEmpty()) alias = alias.substring(0, alias.length() - 1);
            message = null;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NotNull CharacterEvent event) {
        if (saving) return true;
        String typed = TextInputHelper.getTypedText(event);
        if (typed == null || typed.length() != 1 || Character.isISOControl(typed.charAt(0))) return true;
        if (focusedField == 0 && target.length() < 16 && typed.matches("[A-Za-z0-9_]")) target += typed;
        if (focusedField == 1 && alias.length() < 24 && typed.matches("[A-Za-z0-9 _-]")) alias += typed;
        message = null;
        return true;
    }

    private void save() {
        if (saving) return;
        if (!target.matches("[A-Za-z0-9_]{3,16}") || !alias.trim().matches("[A-Za-z0-9 _-]{3,24}")) {
            message = "Enter a valid player and a 3-24 character alias.";
            return;
        }
        saving = true;
        message = "Resolving player…";
        PlayerNameCache.resolveUUID(target).thenCompose(uuid -> {
            if (uuid == null) throw new IllegalArgumentException("Player could not be resolved.");
            return SeqPointsShopManager.getInstance().purchase(item.key(), uuid, alias.trim());
        }).whenComplete((result, error) -> SeqClient.mc.execute(() -> {
            saving = false;
            if (error != null || result == null) {
                message = SeqPointsShopScreen.errorMessage(error);
                return;
            }
            SeqClient.mc.setScreen(parent);
        }));
    }

    @Override
    public void onClose() {
        SeqClient.mc.setScreen(parent);
    }

    private void button(UiCanvas canvas, float x, float y, float width, String label, boolean disabled) {
        boolean hovered = !disabled && hit(mouseX, mouseY, x, y, width, BUTTON_HEIGHT);
        canvas.fillRoundedRect(x, y, width, BUTTON_HEIGHT, 4,
                color(disabled ? ACCENT_PRIMARY_DARK : hovered ? CONTROL_INPUT_HOVER : CONTROL_INPUT));
        canvas.drawText(label, x + width / 2, y + BUTTON_HEIGHT / 2, new UiCanvas.TextStyle(
                SeqClient.getFontManager().getSelectedFont(), 10, color(disabled ? TEXT_MUTED : TEXT_PRIMARY),
                UiCanvas.HorizontalAlign.CENTER, UiCanvas.VerticalAlign.MIDDLE));
    }

    private static void text(UiCanvas canvas, String value, float x, float y, float size, Color textColor) {
        canvas.drawText(value, x, y, new UiCanvas.TextStyle(
                SeqClient.getFontManager().getSelectedFont(), size, textColor,
                UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE));
    }

    private static boolean hit(float mx, float my, float x, float y, float width, float height) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }
}
