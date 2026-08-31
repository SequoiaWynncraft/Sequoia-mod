package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.SeqPointsShopManager;
import com.seqwawa.seq.model.SeqPointsShop;
import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.util.List;
import java.util.concurrent.CompletionException;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/** Seq Points wallet, catalog, and purchase screen. */
public final class SeqPointsShopScreen extends Screen {
    private static final float MARGIN = 14;
    private static final float HEADER_HEIGHT = 42;
    private static final float PANEL_MAX_WIDTH = 620;
    private static final float ROW_HEIGHT = 66;
    private static final float ROW_GAP = 6;
    private static final float SCROLL_SPEED = ROW_HEIGHT + ROW_GAP;
    private static final float BUTTON_WIDTH = 82;
    private static final float BUTTON_HEIGHT = 24;

    private final Screen parent;
    private final SeqPointsShopManager manager = SeqPointsShopManager.getInstance();
    private float mouseX;
    private float mouseY;
    private String message;
    private String purchasingKey;
    private float scrollOffset;
    private float maxScroll;

    public SeqPointsShopScreen(Screen parent) {
        super(Component.literal("Seq Points Shop"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        manager.refreshShop();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.mouseX = MinecraftUiRenderer.mouseX(mouseX);
        this.mouseY = MinecraftUiRenderer.mouseY(mouseY);
        UiRenderer.renderScreen(this, this::renderShop);
    }

    private void renderShop(UiCanvas canvas) {
        float width = canvas.metrics().width();
        float height = canvas.metrics().height();
        float panelWidth = Math.max(0, Math.min(PANEL_MAX_WIDTH, width - MARGIN * 2));
        float panelX = (width - panelWidth) / 2;
        float panelY = HEADER_HEIGHT + MARGIN;
        float panelHeight = Math.max(0, height - panelY - MARGIN);

        canvas.fillRect(0, 0, width, height, color(BACKGROUND_MODAL_OVERLAY, 210));
        canvas.fillRect(0, 0, width, HEADER_HEIGHT, color(BACKGROUND_HEADER, 248));
        canvas.fillHorizontalGradient(
                0, HEADER_HEIGHT - 1, width, 1, color(ACCENT_PRIMARY, 190), color(ACCENT_PRIMARY, 0));
        text(canvas, "Seq Points Shop", MARGIN, HEADER_HEIGHT / 2, 19, color(ACCENT_PRIMARY), false);

        SeqPointsShop shop = manager.shop();
        String wallet = shop.balance().total()
                + " SP  ·  " + shop.balance().bonus() + " bonus  ·  " + shop.balance().war() + " war";
        text(canvas, wallet, width - MARGIN, HEADER_HEIGHT / 2, 10, color(TEXT_SECONDARY), true);

        canvas.fillRoundedRect(panelX, panelY, panelWidth, panelHeight, 7, color(BACKGROUND_BODY_OPAQUE, 245));
        if (manager.state() == SeqPointsShopManager.State.LOADING && shop.items().isEmpty()) {
            centered(canvas, "Loading your Seq Points…", panelX, panelY, panelWidth, panelHeight);
            return;
        }
        if (manager.state() == SeqPointsShopManager.State.UNAVAILABLE && shop.items().isEmpty()) {
            centered(canvas, "Shop unavailable right now", panelX, panelY, panelWidth, panelHeight);
            return;
        }

        float contentTop = panelY + 12;
        float contentBottom = panelY + panelHeight - (message == null ? 10 : 26);
        float viewportHeight = Math.max(0, contentBottom - contentTop);
        maxScroll = catalogMaxScroll(shop.items().size(), viewportHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);
        float y = contentTop - scrollOffset;
        canvas.scissor(panelX, contentTop, panelWidth, viewportHeight);
        for (SeqPointsShop.Item item : shop.items()) {
            renderItem(canvas, item, panelX + 10, y, panelWidth - 20);
            y += ROW_HEIGHT + ROW_GAP;
        }
        canvas.resetScissor();
        if (message != null) {
            text(canvas, message, panelX + 12, panelY + panelHeight - 14, 9,
                    color(message.startsWith("Purchased") ? CONTROL_SUCCESS : CONTROL_WARNING), false);
        }
    }

    private void renderItem(UiCanvas canvas, SeqPointsShop.Item item, float x, float y, float width) {
        canvas.fillRoundedRect(x, y, width, ROW_HEIGHT, 6, color(BACKGROUND_CONTENT, 225));
        text(canvas, item.name(), x + 12, y + 17, 13, color(TEXT_PRIMARY), false);
        text(canvas, item.description(), x + 12, y + 37, 9, color(TEXT_MUTED), false);
        text(canvas, purchaseDetails(item), x + 12, y + 53, 9, color(ACCENT_SECONDARY), false);

        boolean busy = item.key().equals(purchasingKey);
        boolean blocked = purchaseBlocked(item);
        String label = purchaseLabel(item, busy);
        button(canvas, x + width - BUTTON_WIDTH - 12, y + (ROW_HEIGHT - BUTTON_HEIGHT) / 2,
                BUTTON_WIDTH, label, blocked || busy);
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() != 0) return super.mouseClicked(click, outsideScreen);
        SeqPointsShop shop = manager.shop();
        float screenWidth = MinecraftUiRenderer.screenWidth();
        float panelWidth = Math.max(0, Math.min(PANEL_MAX_WIDTH, screenWidth - MARGIN * 2));
        float panelX = (screenWidth - panelWidth) / 2;
        float panelY = HEADER_HEIGHT + MARGIN;
        float panelHeight = Math.max(0, MinecraftUiRenderer.screenHeight() - panelY - MARGIN);
        float contentTop = panelY + 12;
        float contentBottom = panelY + panelHeight - (message == null ? 10 : 26);
        float y = contentTop - scrollOffset;
        for (SeqPointsShop.Item item : shop.items()) {
            float buttonX = panelX + 10 + panelWidth - 20 - BUTTON_WIDTH - 12;
            float buttonY = y + (ROW_HEIGHT - BUTTON_HEIGHT) / 2;
            if (!purchaseBlocked(item)
                    && purchasingKey == null
                    && mouseY >= contentTop
                    && mouseY <= contentBottom
                    && hit(mouseX, mouseY, buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                if (item.isRename()) {
                    SeqClient.mc.setScreen(new SeqPointsRenameScreen(this, item));
                } else if (item.isPayout()) {
                    SeqClient.mc.setScreen(new SeqPointsPayoutScreen(this, item));
                } else {
                    purchase(item);
                }
                return true;
            }
            y += ROW_HEIGHT + ROW_GAP;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (float) scrollY * SCROLL_SPEED));
        return true;
    }

    static float catalogMaxScroll(int itemCount, float viewportHeight) {
        float contentHeight = Math.max(0, itemCount * (ROW_HEIGHT + ROW_GAP) - ROW_GAP);
        return Math.max(0, contentHeight - Math.max(0, viewportHeight));
    }

    static boolean purchaseBlocked(SeqPointsShop.Item item) {
        return item.purchasedThisPeriod() && !item.isDraft() && !item.isPayout();
    }

    static String purchaseLabel(SeqPointsShop.Item item, boolean busy) {
        if (busy) return "Buying…";
        if (purchaseBlocked(item)) return "Entered";
        if (item.isPayout()) return "Payout";
        if (item.purchasedThisPeriod()) return "Buy again";
        return item.isRename() ? "Configure" : "Buy";
    }

    static String purchaseDetails(SeqPointsShop.Item item) {
        if (item.isPayout()) return item.price() + " SP = 1 LE · all points";
        String source = item.allowWarPoints() ? "bonus or war" : "bonus only";
        String details = item.price() + " SP · " + source;
        Long tickets = item.ticketCountThisPeriod();
        if (!item.isDraft() || tickets == null) return details;
        return details + " · " + tickets + (tickets == 1 ? " ticket" : " tickets");
    }

    private void purchase(SeqPointsShop.Item item) {
        purchasingKey = item.key();
        message = null;
        manager.purchase(item.key(), null, null).whenComplete((result, error) -> SeqClient.mc.execute(() -> {
            purchasingKey = null;
            if (error != null || result == null) {
                message = errorMessage(error);
                return;
            }
            message = result.message();
        }));
    }

    static String errorMessage(Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof ApiClient.ApiException apiException) {
            try {
                JsonObject json = JsonParser.parseString(apiException.getResponseBody()).getAsJsonObject();
                if (json.has("message")) return json.get("message").getAsString();
            } catch (RuntimeException ignored) {
            }
        }
        return cause == null || cause.getMessage() == null ? "Purchase failed." : cause.getMessage();
    }

    @Override
    public void onClose() {
        SeqClient.mc.setScreen(parent);
    }

    private void centered(UiCanvas canvas, String value, float x, float y, float width, float height) {
        canvas.drawText(value, x + width / 2, y + height / 2, new UiCanvas.TextStyle(
                SeqClient.getFontManager().getSelectedFont(), 11, color(TEXT_MUTED),
                UiCanvas.HorizontalAlign.CENTER, UiCanvas.VerticalAlign.MIDDLE));
    }

    private void button(UiCanvas canvas, float x, float y, float width, String label, boolean disabled) {
        boolean hovered = !disabled && hit(mouseX, mouseY, x, y, width, BUTTON_HEIGHT);
        canvas.fillRoundedRect(x, y, width, BUTTON_HEIGHT, 4,
                color(disabled ? ACCENT_PRIMARY_DARK : hovered ? CONTROL_INPUT_HOVER : CONTROL_INPUT));
        canvas.drawText(label, x + width / 2, y + BUTTON_HEIGHT / 2, new UiCanvas.TextStyle(
                SeqClient.getFontManager().getSelectedFont(), 10, color(disabled ? TEXT_MUTED : TEXT_PRIMARY),
                UiCanvas.HorizontalAlign.CENTER, UiCanvas.VerticalAlign.MIDDLE));
    }

    private static void text(
            UiCanvas canvas, String value, float x, float y, float size, Color textColor, boolean rightAligned) {
        canvas.drawText(value, x, y, new UiCanvas.TextStyle(
                SeqClient.getFontManager().getSelectedFont(), size, textColor,
                rightAligned ? UiCanvas.HorizontalAlign.RIGHT : UiCanvas.HorizontalAlign.LEFT,
                UiCanvas.VerticalAlign.MIDDLE));
    }

    private static boolean hit(float mx, float my, float x, float y, float width, float height) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }
}
