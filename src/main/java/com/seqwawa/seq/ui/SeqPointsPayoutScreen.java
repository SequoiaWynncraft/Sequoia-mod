package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.SeqPointsShopManager;
import com.seqwawa.seq.model.SeqPointsShop;
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

/** Collects a whole-LE Seq Points payout request. */
final class SeqPointsPayoutScreen extends Screen {
    private static final float PANEL_WIDTH = 380;
    private static final float PANEL_HEIGHT = 200;
    private static final float BUTTON_HEIGHT = 24;

    private final Screen parent;
    private final SeqPointsShop.Item item;
    private final SeqPointsShopManager manager = SeqPointsShopManager.getInstance();
    private String amount = "";
    private boolean saving;
    private boolean success;
    private String message;
    private float mouseX;
    private float mouseY;

    SeqPointsPayoutScreen(Screen parent, SeqPointsShop.Item item) {
        super(Component.literal("Seq Points Payout"));
        this.parent = parent;
        this.item = item;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.mouseX = MinecraftUiRenderer.mouseX(mouseX);
        this.mouseY = MinecraftUiRenderer.mouseY(mouseY);
        UiRenderer.renderScreen(this, this::renderPayout);
    }

    private void renderPayout(UiCanvas canvas) {
        float x = (canvas.metrics().width() - PANEL_WIDTH) / 2;
        float y = (canvas.metrics().height() - PANEL_HEIGHT) / 2;
        long available = manager.shop().balance().total();
        Long cost = payoutCost(amount, item.price());

        canvas.fillRect(0, 0, canvas.metrics().width(), canvas.metrics().height(), new Color(0, 0, 0, 150));
        canvas.fillRoundedRect(x, y, PANEL_WIDTH, PANEL_HEIGHT, 7, color(BACKGROUND_BODY_OPAQUE));
        text(canvas, "Payout · " + item.price() + " SP = 1 LE", x + 14, y + 20, 14, color(ACCENT_PRIMARY));
        text(canvas, "Amount (whole LE)", x + 14, y + 49, 9, color(TEXT_MUTED));
        input(canvas, x + 14, y + 57);
        String costText = cost == null ? "—" : cost.toString();
        text(canvas, "Cost: " + costText + " SP · Available: " + available + " SP", x + 14, y + 101, 10,
                color(cost != null && cost > available ? CONTROL_WARNING : ACCENT_SECONDARY));

        String status = saving
                ? "Submitting your payout request…"
                : message == null
                        ? "Requests are queued in Discord and fulfilled in person."
                        : message;
        text(canvas, status, x + 14, y + 130, 9,
                color(success ? CONTROL_SUCCESS : message == null || saving ? TEXT_MUTED : CONTROL_WARNING));
        button(canvas, x + PANEL_WIDTH - 174, y + PANEL_HEIGHT - 35, 76, "Cancel", false);
        button(canvas, x + PANEL_WIDTH - 90, y + PANEL_HEIGHT - 35, 76, saving ? "Sending…" : "Submit", saving);
    }

    private void input(UiCanvas canvas, float x, float y) {
        canvas.fillRoundedRect(x, y, PANEL_WIDTH - 28, 26, 4, color(CONTROL_INPUT_HOVER));
        text(canvas, amount + (!saving ? "│" : ""), x + 8, y + 13, 11, color(TEXT_PRIMARY));
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() != 0) return super.mouseClicked(click, outsideScreen);
        float x = (MinecraftUiRenderer.screenWidth() - PANEL_WIDTH) / 2;
        float y = (MinecraftUiRenderer.screenHeight() - PANEL_HEIGHT) / 2;
        if (hit(mouseX, mouseY, x + PANEL_WIDTH - 174, y + PANEL_HEIGHT - 35, 76, BUTTON_HEIGHT)) onClose();
        else if (hit(mouseX, mouseY, x + PANEL_WIDTH - 90, y + PANEL_HEIGHT - 35, 76, BUTTON_HEIGHT)) submit();
        return true;
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        if (!saving && event.key() == GLFW.GLFW_KEY_BACKSPACE && !amount.isEmpty()) {
            amount = amount.substring(0, amount.length() - 1);
            clearMessage();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NotNull CharacterEvent event) {
        if (saving) return true;
        String typed = TextInputHelper.getTypedText(event);
        if (typed != null && typed.length() == 1 && Character.isDigit(typed.charAt(0)) && amount.length() < 19) {
            amount += typed;
            clearMessage();
        }
        return true;
    }

    private void submit() {
        if (saving) return;
        String validationError = validationError(amount, item.price(), manager.shop().balance().total());
        if (validationError != null) {
            success = false;
            message = validationError;
            return;
        }

        long requestedLe = Long.parseLong(amount);
        saving = true;
        success = false;
        message = null;
        manager.purchase(item.key(), null, Long.toString(requestedLe))
                .whenComplete((result, error) -> SeqClient.mc.execute(() -> {
                    saving = false;
                    if (error != null || result == null) {
                        message = SeqPointsShopScreen.errorMessage(error);
                        return;
                    }
                    amount = "";
                    success = true;
                    message = result.message() == null || result.message().isBlank()
                            ? requestedLe + " LE request submitted; pending in-person fulfillment."
                            : result.message();
                }));
    }

    static Long payoutCost(String amount, long pointsPerLe) {
        if (amount == null || !amount.matches("[0-9]+") || pointsPerLe <= 0) return null;
        try {
            long requestedLe = Long.parseLong(amount);
            return requestedLe > 0 ? Math.multiplyExact(requestedLe, pointsPerLe) : null;
        } catch (ArithmeticException | NumberFormatException ignored) {
            return null;
        }
    }

    static String validationError(String amount, long pointsPerLe, long availablePoints) {
        if (pointsPerLe <= 0) return "Payout rate is unavailable.";
        if (amount == null || amount.isBlank() || !amount.matches("[0-9]+") || amount.matches("0+")) {
            return "Enter a positive whole LE amount.";
        }
        Long cost = payoutCost(amount, pointsPerLe);
        if (cost == null) return "Amount is too large.";
        return cost > availablePoints ? "Not enough Seq Points for that payout." : null;
    }

    private void clearMessage() {
        success = false;
        message = null;
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
