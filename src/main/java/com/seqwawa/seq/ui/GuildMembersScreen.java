package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.GuildPresenceManager;
import com.seqwawa.seq.managers.GuildRaidActivityTracker;
import com.seqwawa.seq.model.GuildMemberPresence;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Shows every guild member Wynncraft reports as online, grouped by the world they
 * are on, with the two actions that follow from seeing them there: switch to their
 * world, or pull them into a party.
 */
public class GuildMembersScreen extends Screen {

    // ── Layout ──
    private static final float HEADER_HEIGHT = 30;
    private static final float PADDING = 10;
    private static final float GROUP_HEADER_HEIGHT = 24;
    private static final float GROUP_SPACING = 6;
    private static final float ROW_HEIGHT = 26;
    private static final float ROW_SPACING = 2;
    private static final float CONTENT_MAX_WIDTH = 520;
    private static final float SCROLLBAR_WIDTH = 4;
    private static final float SCROLL_SPEED = 14;

    private static final float ACTION_BUTTON_W = 54;
    private static final float ACTION_BUTTON_H = 18;
    private static final float ACTION_BUTTON_GAP = 5;
    private static final float REFRESH_BUTTON_W = 64;
    private static final float REFRESH_BUTTON_H = 18;

    private static final float STATUS_DOT_RADIUS = 3.5f;
    private static final float BUSY_CHIP_W = 62;
    private static final float BUSY_CHIP_H = 15;

    // ── Font sizes ──
    private static final float TITLE_FONT_SIZE = 18;
    private static final float GROUP_FONT_SIZE = 13;
    private static final float ROW_FONT_SIZE = 12;
    private static final float SMALL_FONT_SIZE = 10;

    private static final long STATUS_BANNER_DURATION_MS = 3500L;
    private static final float STATUS_BANNER_H = 24;
    private static final float STATUS_BANNER_MIN_W = 260;

    private final Screen parent;

    private float uiMouseX;
    private float uiMouseY;
    private float scrollOffset;
    private float maxScroll;

    private String statusBannerMessage;
    private long statusBannerExpiresAtMs;

    /** Rebuilt every frame so clicks test against exactly what was drawn. */
    private final List<ActionHitbox> actionHitboxes = new ArrayList<>();
    private Rect refreshButtonBounds;
    /** The scissored list viewport, so a scrolled-away row cannot still be clicked. */
    private Rect listViewport;

    public GuildMembersScreen(Screen parent) {
        super(Component.literal("Guild Members"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        // Opening the panel is the moment the roster matters, so ask for a fresh one;
        // the throttle turns this into a no-op when it was refreshed moments ago.
        presence().refresh(false);
    }

    private static GuildPresenceManager presence() {
        return GuildPresenceManager.getInstance();
    }

    // ══════════════════════════════ RENDER ══════════════════════════════

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        uiMouseX = MinecraftUiRenderer.mouseX(mouseX);
        uiMouseY = MinecraftUiRenderer.mouseY(mouseY);
        actionHitboxes.clear();

        UiRenderer.renderScreen(this, canvas -> {
            float screenWidth = canvas.metrics().width();
            float screenHeight = canvas.metrics().height();
            String fontName = SeqClient.getFontManager().getSelectedFont();

            canvas.fillRect(0, 0, screenWidth, screenHeight, color(BACKGROUND_OVERLAY));

            float contentWidth = Math.min(CONTENT_MAX_WIDTH, screenWidth - PADDING * 2);
            float contentX = (screenWidth - contentWidth) / 2f;

            renderHeader(canvas, fontName, screenWidth, contentX, contentWidth);

            float contentY = HEADER_HEIGHT + PADDING;
            float contentHeight = screenHeight - contentY - PADDING;

            List<GuildPresenceManager.WorldGroup> groups = presence().groupedByWorld();

            listViewport = new Rect(contentX, contentY, contentWidth, contentHeight);
            canvas.save();
            canvas.scissor(contentX, contentY, contentWidth, contentHeight);
            float cursorY = contentY - scrollOffset;

            if (groups.isEmpty()) {
                renderEmptyState(canvas, fontName, contentX, contentY, contentWidth, contentHeight);
                maxScroll = 0;
            } else {
                String localWorld = presence().currentWorld();
                String localUsername = presence().localUsername();
                for (GuildPresenceManager.WorldGroup group : groups) {
                    cursorY = renderGroup(
                            canvas, fontName, contentX, cursorY, contentWidth, group, localWorld, localUsername);
                    cursorY += GROUP_SPACING;
                }
                maxScroll = Math.max(0, cursorY + scrollOffset - contentY - contentHeight);
                scrollOffset = Math.min(scrollOffset, maxScroll);
            }
            canvas.restore();

            if (maxScroll > 0) {
                float trackX = contentX + contentWidth - SCROLLBAR_WIDTH;
                canvas.fillRect(trackX, contentY, SCROLLBAR_WIDTH, contentHeight, color(CONTROL_TRACK));
                float thumbRatio = contentHeight / (contentHeight + maxScroll);
                float thumbHeight = Math.max(20, contentHeight * thumbRatio);
                float thumbY = contentY + (scrollOffset / maxScroll) * (contentHeight - thumbHeight);
                canvas.fillRect(trackX, thumbY, SCROLLBAR_WIDTH, thumbHeight, color(CONTROL_THUMB));
            }

            renderStatusBanner(canvas, fontName, screenWidth, screenHeight);
        });
    }

    private void renderHeader(
            UiCanvas canvas, String fontName, float screenWidth, float contentX, float contentWidth) {
        canvas.fillRect(0, 0, screenWidth, HEADER_HEIGHT, color(BACKGROUND_HEADER));

        drawText(
                canvas,
                fontName,
                TITLE_FONT_SIZE,
                color(ACCENT_PRIMARY),
                contentX,
                HEADER_HEIGHT / 2f,
                presence().guildDisplayName() + " members",
                UiCanvas.HorizontalAlign.LEFT);

        // Refresh button, pinned to the right of the content column.
        float refreshX = contentX + contentWidth - REFRESH_BUTTON_W;
        float refreshY = (HEADER_HEIGHT - REFRESH_BUTTON_H) / 2f;
        refreshButtonBounds = new Rect(refreshX, refreshY, REFRESH_BUTTON_W, REFRESH_BUTTON_H);

        boolean refreshing = presence().isRefreshing();
        boolean canRefresh = presence().canRefresh(System.currentTimeMillis());
        boolean hovered = refreshButtonBounds.contains(uiMouseX, uiMouseY);
        Color background = refreshing || !canRefresh
                ? color(ACCENT_DISABLED)
                : hovered ? color(ACCENT_PRIMARY_HOVER, 220) : color(ACCENT_PRIMARY, 200);
        canvas.fillRoundedRect(refreshX, refreshY, REFRESH_BUTTON_W, REFRESH_BUTTON_H, 3, background);
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                color(TEXT_PRIMARY),
                refreshX + REFRESH_BUTTON_W / 2f,
                refreshY + REFRESH_BUTTON_H / 2f,
                refreshing ? "Refreshing" : "Refresh",
                UiCanvas.HorizontalAlign.CENTER);

        // Summary sits between the title and the button, so the header reads as one line.
        String summary = headerSummary();
        float summaryRight = refreshX - 8;
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                color(TEXT_MUTED),
                summaryRight,
                HEADER_HEIGHT / 2f,
                summary,
                UiCanvas.HorizontalAlign.RIGHT);
    }

    private String headerSummary() {
        String error = presence().lastError();
        if (error != null && !error.isBlank()) {
            return error;
        }
        List<GuildMemberPresence> members = presence().onlineMembers();
        long busy = members.stream()
                .filter(member -> GuildRaidActivityTracker.isBusy(member.username()))
                .count();
        if (members.isEmpty()) {
            return presence().hasLoaded() ? "nobody online" : "loading";
        }
        String base = members.size() + (members.size() == 1 ? " online" : " online");
        return busy == 0 ? base : base + " · " + busy + " busy";
    }

    private void renderEmptyState(
            UiCanvas canvas, String fontName, float x, float y, float width, float height) {
        String message;
        if (presence().isRefreshing() || !presence().hasLoaded()) {
            message = "Loading the guild roster...";
        } else if (presence().lastError() != null && !presence().lastError().isBlank()) {
            message = presence().lastError();
        } else {
            message = "No guild member is online right now.";
        }
        drawText(
                canvas,
                fontName,
                ROW_FONT_SIZE,
                color(TEXT_MUTED),
                x + width / 2f,
                y + height / 3f,
                message,
                UiCanvas.HorizontalAlign.CENTER);
    }

    private float renderGroup(
            UiCanvas canvas,
            String fontName,
            float x,
            float y,
            float width,
            GuildPresenceManager.WorldGroup group,
            String localWorld,
            String localUsername) {

        boolean here = group.world().equalsIgnoreCase(localWorld == null ? "" : localWorld.trim());

        canvas.fillRect(x, y, width, GROUP_HEADER_HEIGHT, color(BACKGROUND_CONTENT));
        // A left rule keeps the world you are already on distinguishable without a
        // second colour running through the rows underneath it.
        canvas.fillRect(x, y, 2, GROUP_HEADER_HEIGHT, here ? color(CONTROL_SUCCESS) : color(ACCENT_PRIMARY, 160));

        drawText(
                canvas,
                fontName,
                GROUP_FONT_SIZE,
                here ? color(CONTROL_SUCCESS) : color(TEXT_PRIMARY),
                x + 10,
                y + GROUP_HEADER_HEIGHT / 2f,
                group.world(),
                UiCanvas.HorizontalAlign.LEFT);

        String count = group.members().size() + (group.members().size() == 1 ? " member" : " members");
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                color(TEXT_MUTED),
                x + width - 10,
                y + GROUP_HEADER_HEIGHT / 2f,
                here ? count + " · you are here" : count,
                UiCanvas.HorizontalAlign.RIGHT);

        float cursorY = y + GROUP_HEADER_HEIGHT + ROW_SPACING;
        for (GuildMemberPresence member : group.members()) {
            renderMemberRow(canvas, fontName, x, cursorY, width, member, group, localUsername, here);
            cursorY += ROW_HEIGHT + ROW_SPACING;
        }
        return cursorY;
    }

    private void renderMemberRow(
            UiCanvas canvas,
            String fontName,
            float x,
            float y,
            float width,
            GuildMemberPresence member,
            GuildPresenceManager.WorldGroup group,
            String localUsername,
            boolean onLocalWorld) {

        boolean isLocalPlayer = localUsername != null && localUsername.equalsIgnoreCase(member.username());
        boolean rowHovered = uiMouseX >= x && uiMouseX <= x + width && uiMouseY >= y && uiMouseY <= y + ROW_HEIGHT;
        canvas.fillRect(x, y, width, ROW_HEIGHT, rowHovered ? color(BACKGROUND_CONTENT_FOCUSED) : color(BACKGROUND_BODY));

        long busyRemainingMs = GuildRaidActivityTracker.busyRemainingMillis(member.username());
        boolean busy = busyRemainingMs > 0L;

        // Status dot: green when free, amber while the raid window is open.
        canvas.fillCircle(
                x + 12,
                y + ROW_HEIGHT / 2f,
                STATUS_DOT_RADIUS,
                busy ? color(CONTROL_WARNING) : color(CONTROL_SUCCESS));

        float nameX = x + 24;
        drawText(
                canvas,
                fontName,
                ROW_FONT_SIZE,
                isLocalPlayer ? color(ACCENT_PRIMARY) : color(TEXT_PRIMARY),
                nameX,
                y + ROW_HEIGHT / 2f,
                member.username(),
                UiCanvas.HorizontalAlign.LEFT);

        float nameWidth = textWidth(member.username(), fontName, ROW_FONT_SIZE);
        float metaX = nameX + nameWidth + 8;

        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                color(TEXT_MUTED),
                metaX,
                y + ROW_HEIGHT / 2f,
                member.rank().displayName(),
                UiCanvas.HorizontalAlign.LEFT);

        if (member.sequoiaConnected()) {
            float badgeX = metaX + textWidth(member.rank().displayName(), fontName, SMALL_FONT_SIZE) + 8;
            drawText(
                    canvas,
                    fontName,
                    SMALL_FONT_SIZE,
                    color(ACCENT_PRIMARY),
                    badgeX,
                    y + ROW_HEIGHT / 2f,
                    "SEQ",
                    UiCanvas.HorizontalAlign.LEFT);
        }

        // Actions are laid out right to left so they stay pinned to the row's edge.
        float buttonY = y + (ROW_HEIGHT - ACTION_BUTTON_H) / 2f;
        float cursorX = x + width - 10 - ACTION_BUTTON_W;

        boolean clickable = isRowVisible(y);

        if (!isLocalPlayer) {
            Rect inviteBounds = new Rect(cursorX, buttonY, ACTION_BUTTON_W, ACTION_BUTTON_H);
            renderActionButton(canvas, fontName, inviteBounds, "Invite", true);
            if (clickable) {
                actionHitboxes.add(new ActionHitbox(inviteBounds, ActionType.INVITE, member.username(), group.world()));
            }
            cursorX -= ACTION_BUTTON_W + ACTION_BUTTON_GAP;
        }

        boolean canSwitch = group.hasSwitchTarget() && !onLocalWorld;
        Rect joinBounds = new Rect(cursorX, buttonY, ACTION_BUTTON_W, ACTION_BUTTON_H);
        renderActionButton(canvas, fontName, joinBounds, onLocalWorld ? "Here" : "Join", canSwitch);
        if (canSwitch && clickable) {
            actionHitboxes.add(new ActionHitbox(joinBounds, ActionType.SWITCH, member.username(), group.world()));
        }
        cursorX -= BUSY_CHIP_W + ACTION_BUTTON_GAP;

        if (busy) {
            float chipY = y + (ROW_HEIGHT - BUSY_CHIP_H) / 2f;
            canvas.fillRoundedRect(
                    cursorX, chipY, BUSY_CHIP_W, BUSY_CHIP_H, 3, color(STATUS_WARNING_BACKGROUND));
            drawText(
                    canvas,
                    fontName,
                    SMALL_FONT_SIZE,
                    color(TEXT_PRIMARY),
                    cursorX + BUSY_CHIP_W / 2f,
                    chipY + BUSY_CHIP_H / 2f,
                    "Busy " + formatCountdown(busyRemainingMs),
                    UiCanvas.HorizontalAlign.CENTER);
        }
    }

    /** Whether a row sits inside the scissored viewport, and so is genuinely on screen. */
    private boolean isRowVisible(float rowY) {
        if (listViewport == null) {
            return false;
        }
        return rowY >= listViewport.y() && rowY + ROW_HEIGHT <= listViewport.y() + listViewport.height();
    }

    private void renderActionButton(UiCanvas canvas, String fontName, Rect bounds, String label, boolean enabled) {
        boolean hovered = enabled && bounds.contains(uiMouseX, uiMouseY);
        Color background = !enabled
                ? color(ACCENT_DISABLED, 140)
                : hovered ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT);
        canvas.fillRoundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 3, background);
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                enabled ? color(TEXT_PRIMARY) : color(TEXT_DISABLED),
                bounds.x() + bounds.width() / 2f,
                bounds.y() + bounds.height() / 2f,
                label,
                UiCanvas.HorizontalAlign.CENTER);
    }

    private void renderStatusBanner(UiCanvas canvas, String fontName, float screenWidth, float screenHeight) {
        if (statusBannerMessage == null || statusBannerMessage.isBlank()) {
            return;
        }
        if (System.currentTimeMillis() >= statusBannerExpiresAtMs) {
            statusBannerMessage = null;
            return;
        }

        float width = Math.max(STATUS_BANNER_MIN_W, textWidth(statusBannerMessage, fontName, ROW_FONT_SIZE) + 32);
        float x = (screenWidth - width) / 2f;
        float y = screenHeight - STATUS_BANNER_H - 16;
        canvas.fillRoundedRect(x, y, width, STATUS_BANNER_H, 4, color(BACKGROUND_POPUP));
        canvas.fillRect(x, y, 2, STATUS_BANNER_H, color(ACCENT_PRIMARY));
        drawText(
                canvas,
                fontName,
                ROW_FONT_SIZE,
                color(TEXT_SECONDARY),
                x + width / 2f,
                y + STATUS_BANNER_H / 2f,
                statusBannerMessage,
                UiCanvas.HorizontalAlign.CENTER);
    }

    private void showStatusBanner(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        statusBannerMessage = message;
        statusBannerExpiresAtMs = System.currentTimeMillis() + STATUS_BANNER_DURATION_MS;
    }

    /** Remaining busy time as {@code m:ss}, which is how long it reads as a wait. */
    static String formatCountdown(long remainingMs) {
        long totalSeconds = Math.max(0L, (remainingMs + 999L) / 1000L);
        return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60L, totalSeconds % 60L);
    }

    // ══════════════════════════════ INPUT ══════════════════════════════

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() != 0) {
            return super.mouseClicked(click, outsideScreen);
        }

        float mx = MinecraftUiRenderer.mouseX(click.x());
        float my = MinecraftUiRenderer.mouseY(click.y());

        if (refreshButtonBounds != null && refreshButtonBounds.contains(mx, my)) {
            if (presence().canRefresh(System.currentTimeMillis())) {
                presence().refresh(true);
                showStatusBanner("Refreshing the guild roster...");
            } else {
                showStatusBanner("Wynncraft only updates this every minute.");
            }
            return true;
        }

        for (ActionHitbox hitbox : actionHitboxes) {
            if (!hitbox.bounds().contains(mx, my)) {
                continue;
            }
            switch (hitbox.type()) {
                case SWITCH -> {
                    // Closing straight away puts the loading screen in front of the player;
                    // a banner behind it would never be read.
                    presence().switchToWorld(hitbox.world());
                    onClose();
                }
                case INVITE -> {
                    GuildPresenceManager.InviteOutcome outcome = presence().inviteToParty(hitbox.username());
                    showStatusBanner(outcome.message());
                }
            }
            return true;
        }

        return super.mouseClicked(click, outsideScreen);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (float) scrollY * SCROLL_SPEED));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        SeqClient.mc.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ══════════════════════════════ HELPERS ══════════════════════════════

    private static void drawText(
            UiCanvas canvas,
            String fontName,
            float fontSize,
            Color textColor,
            float x,
            float y,
            String text,
            UiCanvas.HorizontalAlign horizontalAlign) {
        canvas.drawText(
                text,
                x,
                y,
                new UiCanvas.TextStyle(
                        fontName, fontSize, textColor, horizontalAlign, UiCanvas.VerticalAlign.MIDDLE));
    }

    private static float textWidth(String text, String fontName, float fontSize) {
        return UiRenderer.measureText(text, fontName, fontSize).width();
    }

    private record Rect(float x, float y, float width, float height) {
        boolean contains(float pointX, float pointY) {
            return pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height;
        }
    }

    private enum ActionType {
        SWITCH,
        INVITE
    }

    private record ActionHitbox(Rect bounds, ActionType type, String username, String world) {}
}
