package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import java.awt.Color;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.network.ConnectionManager;
import com.seqwawa.seq.managers.TreasuryOutManager;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;

public class ConnectionScreen extends SequoiaPanelScreen {
    private static final float SIDEBAR_WIDTH = SequoiaSidebarNavigation.WIDTH;
    private static final float HEADER_HEIGHT = 30;
    private static final float SEARCH_BAR_HEIGHT = 18;
    private static final float SEARCH_BAR_MARGIN = 8;
    private static final float TITLE_FONT_SIZE = 18;
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

    /** Matches the other panels' headers; Connection has nothing to search, so it never takes focus. */
    private final TextField search = new TextField();

    public ConnectionScreen(Screen parent) {
        super(Component.literal("Connection"), parent, SequoiaSidebarNavigation.Destination.CONNECTION);
    }

    /** Like Settings, a sidebar button opens its screen in front of this one. */
    @Override
    protected Screen sidebarParent() {
        return this;
    }

    @Override
    protected void draw(UiCanvas canvas) {
        float screenWidth = canvas.metrics().width();
        float screenHeight = canvas.metrics().height();
        float panelX = SIDEBAR_WIDTH;
        float panelWidth = screenWidth - SIDEBAR_WIDTH;
        String fontName = SeqClient.getFontManager().getSelectedFont();

        canvas.fillRect(0, 0, screenWidth, screenHeight, color(BACKGROUND_OVERLAY));
        drawSidebar(canvas);

        canvas.fillRect(panelX, 0, panelWidth, screenHeight, color(BACKGROUND_BODY));
        canvas.fillRect(panelX, 0, panelWidth, HEADER_HEIGHT, color(BACKGROUND_HEADER));
        renderHeader(canvas, fontName, panelX, panelWidth);
        renderStatusPanel(canvas, fontName, panelX, panelWidth);
    }

    private void renderHeader(UiCanvas canvas, String fontName, float panelX, float panelWidth) {
        float searchWidth = SequoiaUiStyle.searchWidth(panelWidth - SEARCH_BAR_MARGIN * 2);
        UiBounds searchBounds = new UiBounds(panelX + SEARCH_BAR_MARGIN, (HEADER_HEIGHT - SEARCH_BAR_HEIGHT) / 2f,
                searchWidth, SEARCH_BAR_HEIGHT);
        search.draw(canvas, searchBounds, fontName, 12, "Search...");
        drawText(canvas, fontName, TITLE_FONT_SIZE, color(ACCENT_PRIMARY_HOVER), UiCanvas.HorizontalAlign.RIGHT,
                panelX + panelWidth - SEARCH_BAR_MARGIN, HEADER_HEIGHT / 2f, "Connection");
    }

    private void renderStatusPanel(UiCanvas canvas, String fontName, float panelX, float panelWidth) {
        ConnectionManager connectionManager = ConnectionManager.getInstance();
        boolean treasuryAccount = isTreasuryAccount();
        boolean connected = showsConnected(
                treasuryAccount, ConnectionManager.isTreasuryOutConnected(), ConnectionManager.isConnected());
        boolean authenticated = SeqClient.getConfigManager().getToken() != null
                && !SeqClient.getConfigManager().getToken().isBlank();
        String minecraftUsername = SeqClient.getConfigManager().getMinecraftUsername();
        String uptime = connectionManager.getUptimeString();

        float baseX = panelX + 34;
        float connectionLineY = HEADER_HEIGHT + SECTION_TOP;
        float connectionMetaY = connectionLineY + META_OFFSET;
        float authLineY = connectionButton().bottom() + SECTION_SPACING;
        float authMetaY = authLineY + META_OFFSET;
        float dividerY = authButton().bottom() + 18;

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

        drawActionButton(
                canvas,
                fontName,
                connectionButton(),
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
        if (showsClearSession(authenticated, treasuryAccount)) {
            drawActionButton(
                    canvas,
                    fontName,
                    authButton(),
                    "Clear session",
                    color(CONTROL_DANGER),
                    color(CONTROL_DANGER_HOVER));
        }

        canvas.fillRect(baseX, dividerY, panelWidth - 68, 1, color(ACCENT_PRIMARY_DARK));
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

    private void drawActionButton(
            UiCanvas canvas, String fontName, UiBounds bounds, String label, Color base, Color hover) {
        bounds.fill(canvas, bounds.contains(pointer) ? hover : base);
        drawText(canvas, fontName, VALUE_FONT_SIZE - 2, color(TEXT_PRIMARY), UiCanvas.HorizontalAlign.CENTER,
                bounds.centerX(), bounds.centerY(), label);
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() != 0) {
            return super.mouseClicked(click, outsideScreen);
        }

        UiPointer at = UiPointer.at(click);
        if (clickSidebar(at)) return true;

        ConnectionManager connectionManager = ConnectionManager.getInstance();
        if (connectionButton().contains(at)) {
            // The same test as the label, so a button reading "Disconnect" disconnects.
            if (showsConnected(
                    isTreasuryAccount(), ConnectionManager.isTreasuryOutConnected(), ConnectionManager.isConnected())) {
                connectionManager.disconnectManually();
            } else {
                connectionManager.connectManually();
            }
            return true;
        }

        String token = SeqClient.getConfigManager().getToken();
        if (showsClearSession(token != null && !token.isBlank(), isTreasuryAccount()) && authButton().contains(at)) {
            connectionManager.unlinkLocally();
            return true;
        }

        return super.mouseClicked(click, outsideScreen);
    }

    /**
     * Whether the screen shows this account as connected, which also makes the connection
     * button read and act as Disconnect. The Treasury account has only its own connection.
     */
    static boolean showsConnected(boolean treasuryAccount, boolean treasuryConnected, boolean connected) {
        return treasuryAccount ? treasuryConnected : connected;
    }

    /** Whether Clear session shows, and so whether a click where it sits clears the session. */
    static boolean showsClearSession(boolean authenticated, boolean treasuryAccount) {
        return authenticated && !treasuryAccount;
    }

    private static boolean isTreasuryAccount() {
        return SeqClient.mc.getUser() != null
                && TreasuryOutManager.isTreasuryMinecraftAccount(SeqClient.mc.getUser().getName());
    }

    private static UiBounds connectionButton() {
        return new UiBounds(SIDEBAR_WIDTH + 34, HEADER_HEIGHT + SECTION_TOP + BUTTON_OFFSET,
                BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    private static UiBounds authButton() {
        float authLineY = connectionButton().bottom() + SECTION_SPACING;
        return new UiBounds(SIDEBAR_WIDTH + 34, authLineY + BUTTON_OFFSET, AUTH_BUTTON_WIDTH, BUTTON_HEIGHT);
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
}
