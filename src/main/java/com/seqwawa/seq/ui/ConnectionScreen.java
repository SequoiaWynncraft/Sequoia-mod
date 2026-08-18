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
import com.seqwawa.seq.network.ConnectionManager;
import com.seqwawa.seq.managers.TreasuryOutManager;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;

public class ConnectionScreen extends Screen {
    private static final float SIDEBAR_WIDTH = 140;
    private static final float SIDEBAR_PADDING = 10;
    private static final float SIDEBAR_BUTTON_HEIGHT = 22;
    private static final float SIDEBAR_BUTTON_SPACING = 6;
    private static final float HEADER_HEIGHT = 30;
    private static final float SEARCH_BAR_HEIGHT = 18;
    private static final float SEARCH_BAR_WIDTH = 180;
    private static final float SEARCH_BAR_MARGIN = 8;
    private static final float TITLE_FONT_SIZE = 18;
    private static final float SIDEBAR_TITLE_SIZE = 16;
    private static final float SIDEBAR_BUTTON_SIZE = 12;
    private static final float LABEL_FONT_SIZE = 16;
    private static final float VALUE_FONT_SIZE = 16;
    private static final float META_FONT_SIZE = 12;
    private static final float BUTTON_WIDTH = 92;
    private static final float BUTTON_HEIGHT = 24;
    private static final float SECTION_TOP = 18;
    private static final float META_OFFSET = 28;
    private static final float BUTTON_OFFSET = 44;
    private static final float SECTION_SPACING = 44;
    private static final float NOTE_OFFSET = 22;
    private static final float AUTH_BUTTON_WIDTH = 118;

    private static final String GITHUB_URL = "https://github.com/SequoiaWynncraft/sequoia-mod";

    private float nvgMouseX;
    private float nvgMouseY;

    public ConnectionScreen(Screen parent) {
        super(Component.literal("Connection"));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        nvgMouseX = MinecraftUiRenderer.mouseX(mouseX);
        nvgMouseY = MinecraftUiRenderer.mouseY(mouseY);

        UiRenderer.renderScreen(this, canvas -> {
            float screenWidth = canvas.metrics().width();
            float screenHeight = canvas.metrics().height();
            float panelX = SIDEBAR_WIDTH;
            float panelWidth = screenWidth - SIDEBAR_WIDTH;
            String fontName = SeqClient.getFontManager().getSelectedFont();

            canvas.fillRect(0, 0, screenWidth, screenHeight, color(BACKGROUND_OVERLAY));
            renderSidebar(canvas, fontName, screenHeight);

            canvas.fillRect(panelX, 0, panelWidth, screenHeight, color(BACKGROUND_BODY));
            canvas.fillRect(panelX, 0, panelWidth, HEADER_HEIGHT, color(BACKGROUND_HEADER));
            renderHeader(canvas, fontName, panelX, panelWidth);
            renderStatusPanel(canvas, fontName, panelX, panelWidth, screenHeight);
        });
    }

    private void renderSidebar(UiCanvas canvas, String fontName, float screenHeight) {
        canvas.fillRect(0, 0, SIDEBAR_WIDTH, screenHeight, color(BACKGROUND_SIDEBAR));
        drawText(canvas, fontName, SIDEBAR_TITLE_SIZE, color(ACCENT_PRIMARY),
                UiCanvas.HorizontalAlign.CENTER, SIDEBAR_WIDTH / 2f, 22, "Sequoia");

        canvas.fillRect(SIDEBAR_PADDING, 40, SIDEBAR_WIDTH - SIDEBAR_PADDING * 2, 1, color(ACCENT_DIVIDER));

        float btnX = SIDEBAR_PADDING;
        float btnW = SIDEBAR_WIDTH - SIDEBAR_PADDING * 2;
        float btnY = 50;
        float step = SIDEBAR_BUTTON_HEIGHT + SIDEBAR_BUTTON_SPACING;

        drawSidebarButton(canvas, fontName, btnX, btnY, btnW, "Partyfinder", false);
        drawSidebarButton(canvas, fontName, btnX, btnY + step, btnW, "Connection", true);
        drawSidebarButton(canvas, fontName, btnX, btnY + step * 2, btnW, "Settings", false);
        drawSidebarButton(canvas, fontName, btnX, btnY + step * 3, btnW, "Map", false);
        drawSidebarButton(canvas, fontName, btnX, btnY + step * 4, btnW, "Ingredients", false);
        drawSidebarButton(canvas, fontName, btnX, btnY + step * 5, btnW, "Achievements", false);
        drawSidebarButton(canvas, fontName, btnX, btnY + step * 6, btnW, "Github", false);
    }

    private void renderHeader(UiCanvas canvas, String fontName, float panelX, float panelWidth) {
        float searchX = panelX + SEARCH_BAR_MARGIN;
        float searchY = (HEADER_HEIGHT - SEARCH_BAR_HEIGHT) / 2f;
        canvas.fillRect(searchX, searchY, SEARCH_BAR_WIDTH, SEARCH_BAR_HEIGHT, color(CONTROL_INPUT));
        drawText(canvas, fontName, 12, color(TEXT_DISABLED), UiCanvas.HorizontalAlign.LEFT,
                searchX + 6, searchY + SEARCH_BAR_HEIGHT / 2f, "Search...");
        drawText(canvas, fontName, TITLE_FONT_SIZE, color(ACCENT_PRIMARY), UiCanvas.HorizontalAlign.RIGHT,
                panelX + panelWidth - SEARCH_BAR_MARGIN, HEADER_HEIGHT / 2f, "Connection");
    }

    private void renderStatusPanel(UiCanvas canvas, String fontName, float panelX, float panelWidth, float screenHeight) {
        ConnectionManager connectionManager = ConnectionManager.getInstance();
        boolean treasuryAccount = SeqClient.mc.getUser() != null
                && TreasuryOutManager.isTreasuryMinecraftAccount(SeqClient.mc.getUser().getName());
        boolean connected = treasuryAccount
                ? ConnectionManager.isTreasuryOutConnected()
                : ConnectionManager.isConnected();
        boolean authenticated = SeqClient.getConfigManager().getToken() != null
                && !SeqClient.getConfigManager().getToken().isBlank();
        String minecraftUsername = SeqClient.getConfigManager().getMinecraftUsername();
        String uptime = connectionManager.getUptimeString();

        float baseX = panelX + 34;
        float connectionLineY = HEADER_HEIGHT + SECTION_TOP;
        float connectionMetaY = connectionLineY + META_OFFSET;
        float connectionButtonY = connectionLineY + BUTTON_OFFSET;
        float authLineY = connectionButtonY + BUTTON_HEIGHT + SECTION_SPACING;
        float authMetaY = authLineY + META_OFFSET;
        float authButtonY = authLineY + BUTTON_OFFSET;
        float dividerY = authButtonY + BUTTON_HEIGHT + 18;

        drawStatusLine(
                canvas,
                fontName,
                baseX,
                connectionLineY,
                "Connection status:",
                connected ? "Connected" : "Disconnected",
                connected ? color(CONTROL_SUCCESS) : color(CONTROL_DANGER));
        if (uptime != null && connected) {
            drawMetaLine(canvas, fontName, baseX, connectionMetaY, "Uptime: " + uptime);
        }

        ButtonBounds connectionButton = new ButtonBounds(baseX, connectionButtonY, BUTTON_WIDTH, BUTTON_HEIGHT);
        drawActionButton(
                canvas,
                fontName,
                connectionButton,
                connected ? "Disconnect" : "Connect",
                connected ? color(CONTROL_DANGER) : color(ACCENT_PRIMARY),
                connected ? color(CONTROL_DANGER_HOVER) : color(ACCENT_PRIMARY_HOVER));

        drawStatusLine(
                canvas,
                fontName,
                baseX,
                authLineY,
                "Backend session:",
                treasuryAccount ? "Not required" : authenticated ? "Ready" : "Not ready",
                treasuryAccount || authenticated ? color(CONTROL_SUCCESS) : color(CONTROL_DANGER));
        drawMetaLine(
                canvas,
                fontName,
                baseX,
                authMetaY,
                treasuryAccount
                        ? "Treasury mode uses the active cinfrascitizen Minecraft account."
                        : authenticated
                        ? "Minecraft account: " + (minecraftUsername != null && !minecraftUsername.isBlank()
                                ? minecraftUsername
                                : "Unknown")
                        : "Use Connect to start a backend session when needed.");
        if (authenticated && !treasuryAccount) {
            ButtonBounds authButton = new ButtonBounds(baseX, authButtonY, AUTH_BUTTON_WIDTH, BUTTON_HEIGHT);
            drawActionButton(
                    canvas,
                    fontName,
                    authButton,
                    "Clear session",
                    color(CONTROL_DANGER),
                    color(CONTROL_DANGER_HOVER));
        }

        canvas.fillRect(baseX, dividerY, panelWidth - 68, 1, color(ACCENT_DIVIDER));
        drawMetaLine(
                canvas,
                fontName,
                baseX,
                dividerY + NOTE_OFFSET,
                treasuryAccount
                        ? "Only Treasury OUT is available in this tokenless connection mode."
                        : "Connect verifies your Minecraft session and checks your Discord link automatically.");
    }

    private void drawStatusLine(
            UiCanvas canvas, String fontName, float x, float y, String label, String value, Color valueColor) {
        drawText(canvas, fontName, LABEL_FONT_SIZE, color(TEXT_PRIMARY), UiCanvas.HorizontalAlign.LEFT, x, y, label);
        float valueX = x + measureTextWidth(fontName, LABEL_FONT_SIZE, label) + 10;
        drawText(canvas, fontName, LABEL_FONT_SIZE, valueColor, UiCanvas.HorizontalAlign.LEFT, valueX, y, value);
    }

    private void drawMetaLine(UiCanvas canvas, String fontName, float x, float y, String text) {
        drawText(canvas, fontName, META_FONT_SIZE, color(TEXT_MUTED), UiCanvas.HorizontalAlign.LEFT, x, y, text);
    }

    private void drawSidebarButton(
            UiCanvas canvas, String fontName, float x, float y, float w, String label, boolean active) {
        boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y, w, SIDEBAR_BUTTON_HEIGHT);
        Color bgColor = active ? color(ACCENT_PRIMARY_DARK_HOVER, 120) : (hovered ? color(BACKGROUND_CONTENT_FOCUSED) : color(BACKGROUND_CONTENT));
        canvas.fillRect(x, y, w, SIDEBAR_BUTTON_HEIGHT, bgColor);
        drawText(canvas, fontName, SIDEBAR_BUTTON_SIZE, color(TEXT_PRIMARY), UiCanvas.HorizontalAlign.CENTER,
                x + w / 2f, y + SIDEBAR_BUTTON_HEIGHT / 2f, label);
    }

    private void drawActionButton(
            UiCanvas canvas, String fontName, ButtonBounds bounds, String label, Color base, Color hover) {
        boolean hovered = isHovered(nvgMouseX, nvgMouseY, bounds.x(), bounds.y(), bounds.w(), bounds.h());
        canvas.fillRect(bounds.x(), bounds.y(), bounds.w(), bounds.h(), hovered ? hover : base);
        drawText(canvas, fontName, VALUE_FONT_SIZE - 2, color(TEXT_PRIMARY), UiCanvas.HorizontalAlign.CENTER,
                bounds.x() + bounds.w() / 2f, bounds.y() + bounds.h() / 2f, label);
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() != 0) {
            return super.mouseClicked(click, outsideScreen);
        }

        float mx = MinecraftUiRenderer.mouseX(click.x());
        float my = MinecraftUiRenderer.mouseY(click.y());

        float btnX = SIDEBAR_PADDING;
        float btnW = SIDEBAR_WIDTH - SIDEBAR_PADDING * 2;
        float btnY = 50;
        float step = SIDEBAR_BUTTON_HEIGHT + SIDEBAR_BUTTON_SPACING;

        if (isHovered(mx, my, btnX, btnY, btnW, SIDEBAR_BUTTON_HEIGHT)) {
            SeqClient.mc.setScreen(new PartyFinderScreen(this));
            return true;
        }
        if (isHovered(mx, my, btnX, btnY + step, btnW, SIDEBAR_BUTTON_HEIGHT)) {
            return true;
        }
        if (isHovered(mx, my, btnX, btnY + step * 2, btnW, SIDEBAR_BUTTON_HEIGHT)) {
            SeqClient.mc.setScreen(new SettingsScreen(this));
            return true;
        }
        if (isHovered(mx, my, btnX, btnY + step * 3, btnW, SIDEBAR_BUTTON_HEIGHT)) {
            SeqClient.mc.setScreen(new WorldMapScreen(this));
            return true;
        }
        if (isHovered(mx, my, btnX, btnY + step * 4, btnW, SIDEBAR_BUTTON_HEIGHT)) {
            SeqClient.mc.setScreen(new IngredientGuideScreen(this));
            return true;
        }
        if (isHovered(mx, my, btnX, btnY + step * 5, btnW, SIDEBAR_BUTTON_HEIGHT)) {
            SeqClient.mc.setScreen(new AchievementsScreen(this));
            return true;
        }
        if (isHovered(mx, my, btnX, btnY + step * 6, btnW, SIDEBAR_BUTTON_HEIGHT)) {
            openGithub();
            return true;
        }

        ConnectionManager connectionManager = ConnectionManager.getInstance();
        ButtonBounds connectionButton = getConnectionButtonBounds();
        if (isHovered(mx, my, connectionButton.x(), connectionButton.y(), connectionButton.w(), connectionButton.h())) {
            if (ConnectionManager.isTreasuryOutConnected()) {
                connectionManager.disconnectManually();
            } else {
                connectionManager.connectManually();
            }
            return true;
        }

        ButtonBounds authButton = getAuthButtonBounds();
        String token = SeqClient.getConfigManager().getToken();
        if (token != null
                && !token.isBlank()
                && isHovered(mx, my, authButton.x(), authButton.y(), authButton.w(), authButton.h())) {
            connectionManager.unlinkLocally();
            return true;
        }

        return super.mouseClicked(click, outsideScreen);
    }

    private ButtonBounds getConnectionButtonBounds() {
        float panelX = SIDEBAR_WIDTH;
        float baseX = panelX + 34;
        float connectionButtonY = HEADER_HEIGHT + SECTION_TOP + BUTTON_OFFSET;
        return new ButtonBounds(baseX, connectionButtonY, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    private ButtonBounds getAuthButtonBounds() {
        float panelX = SIDEBAR_WIDTH;
        float baseX = panelX + 34;
        float connectionButtonY = HEADER_HEIGHT + SECTION_TOP + BUTTON_OFFSET;
        float authLineY = connectionButtonY + BUTTON_HEIGHT + SECTION_SPACING;
        float authButtonY = authLineY + BUTTON_OFFSET;
        return new ButtonBounds(baseX, authButtonY, AUTH_BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    private float measureTextWidth(String fontName, float fontSize, String text) {
        return UiRenderer.measureText(text, fontName, fontSize).width();
    }

    private static void drawText(
            UiCanvas canvas,
            String font,
            float size,
            Color color,
            UiCanvas.HorizontalAlign horizontalAlign,
            float x,
            float y,
            String text) {
        canvas.drawText(text, x, y, new UiCanvas.TextStyle(
                font, size, color, horizontalAlign, UiCanvas.VerticalAlign.MIDDLE));
    }

    private void openGithub() {
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(GITHUB_URL));
        } catch (Exception ignored) {
        }
    }

    private boolean isHovered(float mx, float my, float bx, float by, float bw, float bh) {
        return mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record ButtonBounds(float x, float y, float w, float h) {}
}
