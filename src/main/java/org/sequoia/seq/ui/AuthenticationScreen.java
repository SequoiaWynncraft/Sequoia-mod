package org.sequoia.seq.ui;

import java.awt.Color;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.managers.ThemeManager;
import org.sequoia.seq.network.ConnectionManager;
import org.sequoia.seq.ui.values.Theme;
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

    private static Theme theme = ThemeManager.getCurrentTheme();

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

            NVGWrapper.drawRect(nvg, 0, 0, screenWidth, screenHeight, theme.background.OVERLAY);
            renderSidebar(nvg, fontName, screenHeight);

            NVGWrapper.drawRect(nvg, panelX, 0, panelWidth, screenHeight, theme.background.BODY);
            NVGWrapper.drawRect(nvg, panelX, 0, panelWidth, HEADER_HEIGHT, theme.background.HEADER);
            renderHeader(nvg, fontName, panelX, panelWidth);
            renderStatusPanel(nvg, fontName, panelX, panelWidth, screenHeight);
        });
    }

    private void renderSidebar(long nvg, String fontName, float screenHeight) {
        NVGWrapper.drawRect(nvg, 0, 0, SIDEBAR_WIDTH, screenHeight, theme.background.SIDEBAR);

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, SIDEBAR_TITLE_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var titleColor = NVGContext.nvgColor(theme.accent.MAIN_LIGHT);
        nvgFillColor(nvg, titleColor);
        nvgText(nvg, SIDEBAR_WIDTH / 2f, 22, "Sequoia");
        titleColor.free();

        NVGWrapper.drawRect(nvg, SIDEBAR_PADDING, 40, SIDEBAR_WIDTH - SIDEBAR_PADDING * 2, 1, theme.accent.MAIN_DARK);

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
        NVGWrapper.drawRect(nvg, searchX, searchY, SEARCH_BAR_WIDTH, SEARCH_BAR_HEIGHT, theme.element.INPUT_PRIMARY);

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, 12);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        var placeholder = NVGContext.nvgColor(theme.text.INACTIVE);
        nvgFillColor(nvg, placeholder);
        nvgText(nvg, searchX + 6, searchY + SEARCH_BAR_HEIGHT / 2f, "Search...");
        placeholder.free();

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, TITLE_FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_RIGHT | NVG_ALIGN_MIDDLE);
        var title = NVGContext.nvgColor(theme.accent.MAIN_LIGHT);
        nvgFillColor(nvg, title);
        nvgText(nvg, panelX + panelWidth - SEARCH_BAR_MARGIN, HEADER_HEIGHT / 2f, "Authentication");
        title.free();
    }

    private void renderStatusPanel(long nvg, String fontName, float panelX, float panelWidth, float screenHeight) {
        ConnectionManager connectionManager = ConnectionManager.getInstance();
        boolean connected = ConnectionManager.isConnected();
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
                nvg,
                fontName,
                baseX,
                connectionLineY,
                "Connection status:",
                connected ? "Connected" : "Disconnected",
                connected ? theme.element.GOOD_PRIMARY : theme.element.DANGER_PRIMARY);
        if (uptime != null && connected) {
            drawMetaLine(nvg, fontName, baseX, connectionMetaY, "Uptime: " + uptime);
        }

        ButtonBounds connectionButton = new ButtonBounds(baseX, connectionButtonY, BUTTON_WIDTH, BUTTON_HEIGHT);
        drawActionButton(
                nvg,
                fontName,
                connectionButton,
                connected ? "Disconnect" : "Connect",
                connected ? theme.element.DANGER_PRIMARY : theme.accent.MAIN_LIGHT,
                connected ? theme.element.DANGER_HOVER : theme.accent.MAIN_LIGHT_HOVER);

        drawStatusLine(
                nvg,
                fontName,
                baseX,
                authLineY,
                "Authentication status:",
                authenticated ? "Authorized" : "Signed out",
                authenticated ? theme.element.GOOD_PRIMARY : theme.element.DANGER_PRIMARY);
        drawMetaLine(
                nvg,
                fontName,
                baseX,
                authMetaY,
                "Minecraft profile: " + (minecraftUsername != null && !minecraftUsername.isBlank() ? minecraftUsername : "None"));

        ButtonBounds authButton = new ButtonBounds(baseX, authButtonY, BUTTON_WIDTH, BUTTON_HEIGHT);
        drawActionButton(
                nvg,
                fontName,
                authButton,
                authenticated ? "Logout" : "Authorize",
                authenticated ? theme.element.DANGER_PRIMARY : theme.accent.MAIN_LIGHT,
                authenticated ? theme.element.DANGER_HOVER : theme.accent.MAIN_LIGHT_HOVER);

        NVGWrapper.drawRect(nvg, baseX, dividerY, panelWidth - 68, 1, theme.accent.MAIN_DARK);
        drawMetaLine(
                nvg,
                fontName,
                baseX,
                dividerY + NOTE_OFFSET,
                "Logout clears only the local Sequoia session. Wynn authorization stays stored server-side.");
    }

    private void drawStatusLine(long nvg, String fontName, float x, float y, String label, String value, Color valueColor) {
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, LABEL_FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        var labelColor = NVGContext.nvgColor(theme.text.PRIMARY);
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
        var color = NVGContext.nvgColor(theme.text.FAINT);
        nvgFillColor(nvg, color);
        nvgText(nvg, x, y, text);
        color.free();
    }

    private void drawSidebarButton(long nvg, String fontName, float x, float y, float w, String label, boolean active) {
        boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y, w, SIDEBAR_BUTTON_HEIGHT);
        Color bgColor = active ? theme.accent.MAIN_DARK : (hovered ? theme.accent.ALT_LIGHT : theme.accent.ALT_DARK);
        NVGWrapper.drawRect(nvg, x, y, w, SIDEBAR_BUTTON_HEIGHT, bgColor);

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, SIDEBAR_BUTTON_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var textColor = NVGContext.nvgColor(theme.text.PRIMARY);
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
        var textColor = NVGContext.nvgColor(theme.text.PRIMARY);
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
            String token = SeqClient.getConfigManager().getToken();
            if (token != null && !token.isBlank()) {
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
