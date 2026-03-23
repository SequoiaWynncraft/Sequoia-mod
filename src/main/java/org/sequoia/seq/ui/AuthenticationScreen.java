package org.sequoia.seq.ui;

import java.awt.Color;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.network.ConnectionManager;
import org.sequoia.seq.utils.rendering.nvg.NVGContext;
import org.sequoia.seq.utils.rendering.nvg.NVGWrapper;

import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_RIGHT;
import static org.lwjgl.nanovg.NanoVG.nvgFillColor;
import static org.lwjgl.nanovg.NanoVG.nvgFontFace;
import static org.lwjgl.nanovg.NanoVG.nvgFontSize;
import static org.lwjgl.nanovg.NanoVG.nvgText;
import static org.lwjgl.nanovg.NanoVG.nvgTextAlign;
import static org.lwjgl.nanovg.NanoVG.nvgTextBounds;

public class AuthenticationScreen extends Screen {
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

    private static final Color BG_COLOR = new Color(10, 10, 16, 100);
    private static final Color SIDEBAR_COLOR = new Color(18, 18, 26, 200);
    private static final Color PANEL_COLOR = new Color(22, 22, 30, 100);
    private static final Color HEADER_COLOR = new Color(26, 26, 36, 110);
    private static final Color TITLE_COLOR = new Color(160, 130, 220, 255);
    private static final Color TEXT_COLOR = new Color(255, 255, 255, 255);
    private static final Color SUBTEXT_COLOR = new Color(180, 180, 200, 255);
    private static final Color DIVIDER_COLOR = new Color(40, 40, 55, 255);
    private static final Color SEARCH_BG = new Color(30, 30, 40, 255);
    private static final Color SEARCH_PLACEHOLDER = new Color(100, 100, 120, 200);
    private static final Color SIDEBAR_BUTTON_COLOR = new Color(30, 30, 42, 110);
    private static final Color SIDEBAR_BUTTON_HOVER = new Color(42, 42, 58, 120);
    private static final Color SIDEBAR_BUTTON_ACTIVE = new Color(80, 50, 140, 120);
    private static final Color PRIMARY_BUTTON = new Color(160, 130, 220, 255);
    private static final Color PRIMARY_BUTTON_HOVER = new Color(176, 148, 236, 255);
    private static final Color DANGER_BUTTON = new Color(220, 45, 60, 255);
    private static final Color DANGER_BUTTON_HOVER = new Color(236, 65, 80, 255);
    private static final Color CONNECTED_COLOR = new Color(0, 225, 90, 255);
    private static final Color DISCONNECTED_COLOR = new Color(235, 55, 55, 255);

    private static final String GITHUB_URL = "https://github.com/SequoiaWynncraft/sequoia-mod";

    private float nvgMouseX;
    private float nvgMouseY;

    public AuthenticationScreen(Screen parent) {
        super(Component.literal("Authentication"));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        double guiScale = SeqClient.mc.getWindow().getGuiScale();
        nvgMouseX = (float) (mouseX * guiScale / 2.0);
        nvgMouseY = (float) (mouseY * guiScale / 2.0);

        NVGContext.renderDeferred(nvg -> {
            float screenWidth = SeqClient.mc.getWindow().getWidth() / 2f;
            float screenHeight = SeqClient.mc.getWindow().getHeight() / 2f;
            float panelX = SIDEBAR_WIDTH;
            float panelWidth = screenWidth - SIDEBAR_WIDTH;
            String fontName = SeqClient.getFontManager().getSelectedFont();

            NVGWrapper.drawRect(nvg, 0, 0, screenWidth, screenHeight, BG_COLOR);
            renderSidebar(nvg, fontName, screenHeight);

            NVGWrapper.drawRect(nvg, panelX, 0, panelWidth, screenHeight, PANEL_COLOR);
            NVGWrapper.drawRect(nvg, panelX, 0, panelWidth, HEADER_HEIGHT, HEADER_COLOR);
            renderHeader(nvg, fontName, panelX, panelWidth);
            renderStatusPanel(nvg, fontName, panelX, panelWidth, screenHeight);
        });
    }

    private void renderSidebar(long nvg, String fontName, float screenHeight) {
        NVGWrapper.drawRect(nvg, 0, 0, SIDEBAR_WIDTH, screenHeight, SIDEBAR_COLOR);

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, SIDEBAR_TITLE_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var titleColor = NVGContext.nvgColor(TITLE_COLOR);
        nvgFillColor(nvg, titleColor);
        nvgText(nvg, SIDEBAR_WIDTH / 2f, 22, "Sequoia");
        titleColor.free();

        NVGWrapper.drawRect(nvg, SIDEBAR_PADDING, 40, SIDEBAR_WIDTH - SIDEBAR_PADDING * 2, 1, DIVIDER_COLOR);

        float btnX = SIDEBAR_PADDING;
        float btnW = SIDEBAR_WIDTH - SIDEBAR_PADDING * 2;
        float btnY = 50;
        float step = SIDEBAR_BUTTON_HEIGHT + SIDEBAR_BUTTON_SPACING;

        drawSidebarButton(nvg, fontName, btnX, btnY, btnW, "Partyfinder", false);
        drawSidebarButton(nvg, fontName, btnX, btnY + step, btnW, "Authentication", true);
        drawSidebarButton(nvg, fontName, btnX, btnY + step * 2, btnW, "Settings", false);
        drawSidebarButton(nvg, fontName, btnX, btnY + step * 3, btnW, "Github", false);
    }

    private void renderHeader(long nvg, String fontName, float panelX, float panelWidth) {
        float searchX = panelX + SEARCH_BAR_MARGIN;
        float searchY = (HEADER_HEIGHT - SEARCH_BAR_HEIGHT) / 2f;
        NVGWrapper.drawRect(nvg, searchX, searchY, SEARCH_BAR_WIDTH, SEARCH_BAR_HEIGHT, SEARCH_BG);

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, 12);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        var placeholder = NVGContext.nvgColor(SEARCH_PLACEHOLDER);
        nvgFillColor(nvg, placeholder);
        nvgText(nvg, searchX + 6, searchY + SEARCH_BAR_HEIGHT / 2f, "Search...");
        placeholder.free();

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, TITLE_FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_RIGHT | NVG_ALIGN_MIDDLE);
        var title = NVGContext.nvgColor(TITLE_COLOR);
        nvgFillColor(nvg, title);
        nvgText(nvg, panelX + panelWidth - SEARCH_BAR_MARGIN, HEADER_HEIGHT / 2f, "Authentication");
        title.free();
    }

    private void renderStatusPanel(long nvg, String fontName, float panelX, float panelWidth, float screenHeight) {
        ConnectionManager connectionManager = ConnectionManager.getInstance();
        boolean connected = ConnectionManager.isConnected();
        boolean linked = connectionManager.isDiscordLinked();
        String linkedAccount = connectionManager.getLinkedDiscordUsername();
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
                nvg,
                fontName,
                baseX,
                connectionLineY,
                "Connection status:",
                connected ? "Connected" : "Disconnected",
                connected ? CONNECTED_COLOR : DISCONNECTED_COLOR);
        if (uptime != null && connected) {
            drawMetaLine(nvg, fontName, baseX, connectionMetaY, "Uptime: " + uptime);
        }

        ButtonBounds connectionButton = new ButtonBounds(baseX, connectionButtonY, BUTTON_WIDTH, BUTTON_HEIGHT);
        drawActionButton(
                nvg,
                fontName,
                connectionButton,
                connected ? "Disconnect" : "Connect",
                connected ? DANGER_BUTTON : PRIMARY_BUTTON,
                connected ? DANGER_BUTTON_HOVER : PRIMARY_BUTTON_HOVER);

        drawStatusLine(
                nvg,
                fontName,
                baseX,
                authLineY,
                "Authentication status:",
                linked ? "Linked" : "Unlinked",
                linked ? CONNECTED_COLOR : DISCONNECTED_COLOR);
        drawMetaLine(
                nvg,
                fontName,
                baseX,
                authMetaY,
                "Linked account: " + (linkedAccount != null && !linkedAccount.isBlank() ? linkedAccount : "None"));

        ButtonBounds authButton = new ButtonBounds(baseX, authButtonY, BUTTON_WIDTH, BUTTON_HEIGHT);
        drawActionButton(
                nvg,
                fontName,
                authButton,
                linked ? "Unlink" : "Link",
                linked ? DANGER_BUTTON : PRIMARY_BUTTON,
                linked ? DANGER_BUTTON_HOVER : PRIMARY_BUTTON_HOVER);

        NVGWrapper.drawRect(nvg, baseX, dividerY, panelWidth - 68, 1, DIVIDER_COLOR);
        drawMetaLine(
                nvg,
                fontName,
                baseX,
                dividerY + NOTE_OFFSET,
                "Unlink clears local authentication so the next link can use a different Discord account.");
    }

    private void drawStatusLine(long nvg, String fontName, float x, float y, String label, String value, Color valueColor) {
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, LABEL_FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        var labelColor = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, labelColor);
        nvgText(nvg, x, y, label);
        labelColor.free();

        float valueX = x + measureTextWidth(nvg, fontName, LABEL_FONT_SIZE, label) + 10;
        var statusColor = NVGContext.nvgColor(valueColor);
        nvgFillColor(nvg, statusColor);
        nvgText(nvg, valueX, y, value);
        statusColor.free();
    }

    private void drawMetaLine(long nvg, String fontName, float x, float y, String text) {
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, META_FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        var color = NVGContext.nvgColor(SUBTEXT_COLOR);
        nvgFillColor(nvg, color);
        nvgText(nvg, x, y, text);
        color.free();
    }

    private void drawSidebarButton(long nvg, String fontName, float x, float y, float w, String label, boolean active) {
        boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y, w, SIDEBAR_BUTTON_HEIGHT);
        Color bgColor = active ? SIDEBAR_BUTTON_ACTIVE : (hovered ? SIDEBAR_BUTTON_HOVER : SIDEBAR_BUTTON_COLOR);
        NVGWrapper.drawRect(nvg, x, y, w, SIDEBAR_BUTTON_HEIGHT, bgColor);

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, SIDEBAR_BUTTON_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var textColor = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, textColor);
        nvgText(nvg, x + w / 2f, y + SIDEBAR_BUTTON_HEIGHT / 2f, label);
        textColor.free();
    }

    private void drawActionButton(long nvg, String fontName, ButtonBounds bounds, String label, Color base, Color hover) {
        boolean hovered = isHovered(nvgMouseX, nvgMouseY, bounds.x(), bounds.y(), bounds.w(), bounds.h());
        NVGWrapper.drawRect(nvg, bounds.x(), bounds.y(), bounds.w(), bounds.h(), hovered ? hover : base);

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, VALUE_FONT_SIZE - 2);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var textColor = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, textColor);
        nvgText(nvg, bounds.x() + bounds.w() / 2f, bounds.y() + bounds.h() / 2f, label);
        textColor.free();
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() != 0) {
            return super.mouseClicked(click, outsideScreen);
        }

        double guiScale = SeqClient.mc.getWindow().getGuiScale();
        float mx = (float) (click.x() * guiScale / 2.0);
        float my = (float) (click.y() * guiScale / 2.0);

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
            openGithub();
            return true;
        }

        ConnectionManager connectionManager = ConnectionManager.getInstance();
        ButtonBounds connectionButton = getConnectionButtonBounds();
        if (isHovered(mx, my, connectionButton.x(), connectionButton.y(), connectionButton.w(), connectionButton.h())) {
            if (ConnectionManager.isConnected()) {
                connectionManager.disconnectManually();
            } else {
                connectionManager.connectManually();
            }
            return true;
        }

        ButtonBounds authButton = getAuthButtonBounds();
        if (isHovered(mx, my, authButton.x(), authButton.y(), authButton.w(), authButton.h())) {
            if (connectionManager.isDiscordLinked()) {
                connectionManager.unlinkLocally();
            } else {
                connectionManager.linkManually();
            }
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
        return new ButtonBounds(baseX, authButtonY, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    private float measureTextWidth(long nvg, String fontName, float fontSize, String text) {
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, fontSize);
        return nvgTextBounds(nvg, 0, 0, text, new float[4]);
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
