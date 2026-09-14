package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.GuildPresenceManager;
import com.seqwawa.seq.managers.GuildRaidActivityTracker;
import com.seqwawa.seq.managers.PlayerHeadCache;
import com.seqwawa.seq.managers.RaidProfileStore;
import com.seqwawa.seq.model.GuildMemberPresence;
import com.seqwawa.seq.model.MemberFilter;
import com.seqwawa.seq.model.PremadeParty;
import com.seqwawa.seq.model.RaidBuild;
import com.seqwawa.seq.model.RaidCatalog;
import com.seqwawa.seq.model.RaidTeamProfile;
import com.seqwawa.seq.model.RaidType;
import com.seqwawa.seq.utils.TextInputHelper;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiImage;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/**
 * Every guild member Wynncraft says is online, sorted by name, with what each of
 * them can bring to a raid group and the two things you would do about it: go to
 * their world, or pull them into a party.
 * <p>
 * Two kinds of evidence sit side by side and are drawn differently on purpose.
 * A member who filled in a raid profile shows the builds they said they own. A
 * member who has not shows how many times they have run that raid with the guild,
 * which answers a weaker question but answers it without anyone opting in.
 */
public class GuildMembersScreen extends Screen {

    // ── Layout ──
    private static final float HEADER_HEIGHT = 30;
    private static final float TAB_STRIP_HEIGHT = 24;
    private static final float FILTER_BAR_HEIGHT = 30;
    private static final float COLUMN_HEADER_HEIGHT = 16;
    /** Breathing room kept between a column heading and the next column's. */
    private static final float COLUMN_LABEL_GAP = 6;
    private static final float PADDING = 10;
    private static final float ROW_HEIGHT = 26;
    private static final float ROW_SPACING = 2;
    private static final float CONTENT_MAX_WIDTH = 720;
    private static final float SCROLLBAR_WIDTH = 4;
    private static final float SCROLL_SPEED = 14;

    // Column offsets from the content's left edge, so every row lines up.
    private static final float COL_HEAD = 8;
    private static final float COL_NAME = 30;
    private static final float COL_WORLD = 186;
    private static final float COL_REGION = 230;
    private static final float COL_RAIDS = 268;
    private static final float COL_EVIDENCE = 320;

    private static final float HEAD_SIZE = 16;
    private static final float STATUS_DOT_RADIUS = 3.5f;

    private static final float ACTION_BUTTON_W = 54;
    private static final float ACTION_BUTTON_H = 18;
    private static final float ACTION_BUTTON_GAP = 5;
    private static final float ACTIONS_ZONE_W = 190;
    private static final float REFRESH_BUTTON_W = 64;
    private static final float PROFILE_BUTTON_W = 76;
    private static final float HEADER_BUTTON_H = 18;

    private static final float BUSY_CHIP_W = 62;
    private static final float BUSY_CHIP_H = 15;
    private static final float CHIP_H = 14;
    private static final float CHIP_GAP = 4;
    private static final int MAX_VISIBLE_CHIPS = 3;

    private static final float FILTER_CHIP_H = 18;
    private static final float FILTER_CHIP_GAP = 4;
    private static final float SEARCH_W = 130;

    // Detail modal
    private static final float TAB_WIDTH = 92;
    private static final float FRIEND_BUTTON_W = 62;
    private static final float PREMADE_BUTTON_W = 72;
    private static final float NEW_PARTY_BUTTON_W = 88;

    private static final float MODAL_WIDTH = 380;
    private static final float MODAL_PADDING = 18;
    private static final float MODAL_ROW_H = 18;
    private static final float MODAL_HEAD_SIZE = 28;
    private static final float NOTE_INPUT_H = 22;

    // ── Font sizes ──
    private static final float TITLE_FONT_SIZE = 18;
    private static final float ROW_FONT_SIZE = 12;
    private static final float SMALL_FONT_SIZE = 10;
    private static final float TINY_FONT_SIZE = 9;

    private static final long STATUS_BANNER_DURATION_MS = 3500L;
    private static final float STATUS_BANNER_H = 24;
    private static final float STATUS_BANNER_MIN_W = 260;

    private final Screen parent;

    private float uiMouseX;
    private float uiMouseY;
    private float scrollOffset;
    private float maxScroll;

    private Tab tab = Tab.MEMBERS;
    private MemberFilter filter = MemberFilter.none();
    private String searchInput = "";
    private boolean searchFocused;

    private GuildMemberPresence selectedMember;
    private String noteInput = "";
    private boolean noteFocused;

    private String statusBannerMessage;
    private long statusBannerExpiresAtMs;

    /** Rebuilt every frame so clicks test against exactly what was drawn. */
    private final List<ActionHitbox> actionHitboxes = new ArrayList<>();
    private final List<RaidFilterHitbox> raidFilterHitboxes = new ArrayList<>();
    private final List<TabHitbox> tabHitboxes = new ArrayList<>();
    private final List<FriendHitbox> friendHitboxes = new ArrayList<>();
    private final List<PremadeHitbox> premadeHitboxes = new ArrayList<>();
    private Rect newPartyBounds;
    private Rect friendToggleBounds;
    private Rect refreshButtonBounds;
    private Rect profileButtonBounds;
    private Rect aurasFilterBounds;
    private Rect freeFilterBounds;
    private Rect searchBounds;
    private Rect noteBounds;
    private Rect modalCloseBounds;
    private Rect modalBounds;
    /** The scissored list viewport, so a scrolled-away row cannot still be clicked. */
    private Rect listViewport;

    public GuildMembersScreen(Screen parent) {
        super(Component.literal("Guild Members"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        // Opening the panel is the moment the roster matters, so ask for a fresh one.
        // The throttle turns this into a no-op when it was refreshed moments ago.
        presence().refresh(false);
        profiles().refresh();
    }

    private static GuildPresenceManager presence() {
        return GuildPresenceManager.getInstance();
    }

    private static RaidProfileStore profiles() {
        return RaidProfileStore.getInstance();
    }

    private static RaidCatalog catalog() {
        return profiles().catalog();
    }

    /** The raid the filter names, resolved against the catalog that is loaded now. */
    private RaidType filteredRaid() {
        return filter.raid(catalog());
    }

    // ══════════════════════════════ RENDER ══════════════════════════════

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        uiMouseX = MinecraftUiRenderer.mouseX(mouseX);
        uiMouseY = MinecraftUiRenderer.mouseY(mouseY);
        actionHitboxes.clear();
        raidFilterHitboxes.clear();
        tabHitboxes.clear();
        friendHitboxes.clear();
        premadeHitboxes.clear();

        UiRenderer.renderScreen(this, canvas -> {
            float screenWidth = canvas.metrics().width();
            float screenHeight = canvas.metrics().height();
            String fontName = SeqClient.getFontManager().getSelectedFont();

            canvas.fillRect(0, 0, screenWidth, screenHeight, color(BACKGROUND_OVERLAY));

            float contentWidth = Math.min(CONTENT_MAX_WIDTH, screenWidth - PADDING * 2);
            float contentX = (screenWidth - contentWidth) / 2f;

            renderHeader(canvas, fontName, screenWidth, contentX, contentWidth);
            renderTabStrip(canvas, fontName, contentX, contentWidth);

            float contentY = HEADER_HEIGHT + TAB_STRIP_HEIGHT;
            if (tab == Tab.MEMBERS) {
                renderFilterBar(canvas, fontName, contentX, contentWidth, contentY);
                renderColumnHeader(canvas, fontName, contentX, contentWidth, contentY + FILTER_BAR_HEIGHT);
                contentY += FILTER_BAR_HEIGHT + COLUMN_HEADER_HEIGHT;
            } else if (tab == Tab.PREMADES) {
                renderPremadeBar(canvas, fontName, contentX, contentWidth, contentY);
                contentY += FILTER_BAR_HEIGHT;
            }
            float contentHeight = screenHeight - contentY - PADDING;

            listViewport = new Rect(contentX, contentY, contentWidth, contentHeight);
            canvas.save();
            canvas.scissor(contentX, contentY, contentWidth, contentHeight);
            switch (tab) {
                case MEMBERS -> renderMembersTab(canvas, fontName, contentX, contentY, contentWidth, contentHeight);
                case FRIENDS -> renderFriendsTab(canvas, fontName, contentX, contentY, contentWidth, contentHeight);
                case PREMADES -> renderPremadesTab(canvas, fontName, contentX, contentY, contentWidth, contentHeight);
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

            if (selectedMember != null) {
                renderDetailModal(canvas, fontName, screenWidth, screenHeight);
            } else {
                modalBounds = null;
                modalCloseBounds = null;
                noteBounds = null;
            }

            renderStatusBanner(canvas, fontName, screenWidth, screenHeight);
        });
    }

    private void renderTabStrip(UiCanvas canvas, String fontName, float contentX, float contentWidth) {
        float y = HEADER_HEIGHT;
        canvas.fillRect(contentX, y, contentWidth, TAB_STRIP_HEIGHT, color(BACKGROUND_CONTENT));

        float cursorX = contentX;
        for (Tab candidate : Tab.values()) {
            Rect bounds = new Rect(cursorX, y, TAB_WIDTH, TAB_STRIP_HEIGHT);
            boolean selected = tab == candidate;
            boolean hovered = bounds.contains(uiMouseX, uiMouseY);
            if (selected) {
                canvas.fillRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), color(BACKGROUND_BODY));
                // The underline is what marks the tab, so the fill can stay quiet.
                canvas.fillRect(bounds.x(), bounds.y() + bounds.height() - 2, bounds.width(), 2, color(ACCENT_PRIMARY));
            } else if (hovered) {
                canvas.fillRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), color(CONTROL_INPUT));
            }
            drawText(
                    canvas,
                    fontName,
                    SMALL_FONT_SIZE,
                    selected ? color(TEXT_PRIMARY) : color(TEXT_MUTED),
                    bounds.x() + bounds.width() / 2f,
                    bounds.y() + bounds.height() / 2f,
                    tabLabel(candidate),
                    UiCanvas.HorizontalAlign.CENTER);
            tabHitboxes.add(new TabHitbox(bounds, candidate));
            cursorX += TAB_WIDTH;
        }
    }

    private String tabLabel(Tab candidate) {
        return switch (candidate) {
            case MEMBERS -> "Members";
            case FRIENDS -> {
                int count = profiles().friends().size();
                yield count == 0 ? "Friends" : "Friends " + count;
            }
            case PREMADES -> {
                int count = profiles().premades().size();
                yield count == 0 ? "Premades" : "Premades " + count;
            }
        };
    }

    private void renderMembersTab(
            UiCanvas canvas, String fontName, float contentX, float contentY, float contentWidth, float contentHeight) {
        List<GuildMemberPresence> members = presence().membersForDisplay(filter);
        if (members.isEmpty()) {
            renderEmptyState(canvas, fontName, contentX, contentY, contentWidth, contentHeight);
            maxScroll = 0;
            return;
        }

        String localWorld = presence().currentWorld();
        String localUsername = presence().localUsername();
        float cursorY = contentY - scrollOffset;
        for (GuildMemberPresence member : members) {
            renderMemberRow(canvas, fontName, contentX, cursorY, contentWidth, member, localWorld, localUsername);
            cursorY += ROW_HEIGHT + ROW_SPACING;
        }
        maxScroll = Math.max(0, cursorY + scrollOffset - contentY - contentHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    // ── Friends ──

    private void renderFriendsTab(
            UiCanvas canvas, String fontName, float contentX, float contentY, float contentWidth, float contentHeight) {
        List<String> friends = profiles().friends();
        if (friends.isEmpty()) {
            drawText(
                    canvas,
                    fontName,
                    ROW_FONT_SIZE,
                    color(TEXT_MUTED),
                    contentX + contentWidth / 2f,
                    contentY + contentHeight / 3f,
                    "Open someone from Members and press Add friend.",
                    UiCanvas.HorizontalAlign.CENTER);
            maxScroll = 0;
            return;
        }

        // Resolved once for the whole tab rather than per row, which would rebuild the
        // roster view ten times a frame.
        List<GuildMemberPresence> online = presence().onlineMembers();
        float cursorY = contentY - scrollOffset;
        for (String friend : friends) {
            renderFriendRow(canvas, fontName, contentX, cursorY, contentWidth, friend, online);
            cursorY += ROW_HEIGHT + ROW_SPACING;
        }
        maxScroll = Math.max(0, cursorY + scrollOffset - contentY - contentHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    private void renderFriendRow(
            UiCanvas canvas,
            String fontName,
            float x,
            float y,
            float width,
            String friend,
            List<GuildMemberPresence> onlineMembers) {
        GuildMemberPresence online = onlineMembers.stream()
                .filter(member -> member.username().equalsIgnoreCase(friend))
                .findFirst()
                .orElse(null);
        boolean isOnline = online != null;
        boolean busy = GuildRaidActivityTracker.isBusy(friend);
        boolean clickable = isRowVisible(y);
        boolean rowHovered = uiMouseX >= x && uiMouseX <= x + width && uiMouseY >= y && uiMouseY <= y + ROW_HEIGHT;
        canvas.fillRect(x, y, width, ROW_HEIGHT, rowHovered ? color(BACKGROUND_CONTENT_FOCUSED) : color(BACKGROUND_BODY));

        float centerY = y + ROW_HEIGHT / 2f;
        renderHead(canvas, x + COL_HEAD, y + (ROW_HEIGHT - HEAD_SIZE) / 2f, online == null ? null : online.uuid(), busy, isOnline);

        drawText(
                canvas,
                fontName,
                ROW_FONT_SIZE,
                isOnline ? color(TEXT_PRIMARY) : color(TEXT_DISABLED),
                x + COL_NAME,
                centerY,
                friend,
                UiCanvas.HorizontalAlign.LEFT);

        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                isOnline ? color(TEXT_SECONDARY) : color(TEXT_DISABLED),
                x + COL_WORLD,
                centerY,
                isOnline ? (online.hasWorld() ? online.world() : "?") : "offline",
                UiCanvas.HorizontalAlign.LEFT);

        float buttonY = y + (ROW_HEIGHT - ACTION_BUTTON_H) / 2f;
        float cursorX = x + width - 10 - FRIEND_BUTTON_W;

        Rect removeBounds = new Rect(cursorX, buttonY, FRIEND_BUTTON_W, ACTION_BUTTON_H);
        renderButton(canvas, fontName, removeBounds, "Remove", true, false);
        if (clickable) {
            friendHitboxes.add(new FriendHitbox(removeBounds, FriendAction.REMOVE, friend));
        }
        cursorX -= FRIEND_BUTTON_W + ACTION_BUTTON_GAP;

        Rect inviteBounds = new Rect(cursorX, buttonY, FRIEND_BUTTON_W, ACTION_BUTTON_H);
        renderButton(canvas, fontName, inviteBounds, "Invite", isOnline, false);
        if (clickable && isOnline) {
            friendHitboxes.add(new FriendHitbox(inviteBounds, FriendAction.INVITE, friend));
        }
        cursorX -= FRIEND_BUTTON_W + ACTION_BUTTON_GAP;

        Rect askBounds = new Rect(cursorX, buttonY, FRIEND_BUTTON_W, ACTION_BUTTON_H);
        renderButton(canvas, fontName, askBounds, "Raid ?", isOnline, true);
        if (clickable && isOnline) {
            friendHitboxes.add(new FriendHitbox(askBounds, FriendAction.ASK, friend));
        }
    }

    // ── Premade parties ──

    private void renderPremadeBar(
            UiCanvas canvas, String fontName, float contentX, float contentWidth, float barY) {
        canvas.fillRect(contentX, barY, contentWidth, FILTER_BAR_HEIGHT, color(BACKGROUND_CONTENT));
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                color(TEXT_MUTED),
                contentX + 8,
                barY + FILTER_BAR_HEIGHT / 2f,
                "Groups you run with often. Invite all creates the party if you are not in one.",
                UiCanvas.HorizontalAlign.LEFT);

        newPartyBounds = new Rect(
                contentX + contentWidth - NEW_PARTY_BUTTON_W - 8,
                barY + (FILTER_BAR_HEIGHT - ACTION_BUTTON_H) / 2f,
                NEW_PARTY_BUTTON_W,
                ACTION_BUTTON_H);
        renderButton(canvas, fontName, newPartyBounds, "New party", true, true);
    }

    private void renderPremadesTab(
            UiCanvas canvas, String fontName, float contentX, float contentY, float contentWidth, float contentHeight) {
        List<PremadeParty> parties = profiles().premades();
        if (parties.isEmpty()) {
            drawText(
                    canvas,
                    fontName,
                    ROW_FONT_SIZE,
                    color(TEXT_MUTED),
                    contentX + contentWidth / 2f,
                    contentY + contentHeight / 3f,
                    "No saved parties yet. Press New party to build one.",
                    UiCanvas.HorizontalAlign.CENTER);
            maxScroll = 0;
            return;
        }

        float cursorY = contentY - scrollOffset;
        for (PremadeParty party : parties) {
            renderPremadeRow(canvas, fontName, contentX, cursorY, contentWidth, party);
            cursorY += ROW_HEIGHT + ROW_SPACING;
        }
        maxScroll = Math.max(0, cursorY + scrollOffset - contentY - contentHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    private void renderPremadeRow(
            UiCanvas canvas, String fontName, float x, float y, float width, PremadeParty party) {
        boolean clickable = isRowVisible(y);
        boolean rowHovered = uiMouseX >= x && uiMouseX <= x + width && uiMouseY >= y && uiMouseY <= y + ROW_HEIGHT;
        canvas.fillRect(x, y, width, ROW_HEIGHT, rowHovered ? color(BACKGROUND_CONTENT_FOCUSED) : color(BACKGROUND_BODY));

        float centerY = y + ROW_HEIGHT / 2f;
        drawText(
                canvas,
                fontName,
                ROW_FONT_SIZE,
                color(TEXT_PRIMARY),
                x + 10,
                centerY,
                party.name(),
                UiCanvas.HorizontalAlign.LEFT);

        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                color(TEXT_MUTED),
                x + COL_WORLD,
                centerY,
                party.members().size() + "/" + PremadeParty.MAX_MEMBERS,
                UiCanvas.HorizontalAlign.LEFT);

        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                color(TEXT_SECONDARY),
                x + COL_RAIDS,
                centerY,
                fitToWidth(party.summary(), fontName, SMALL_FONT_SIZE, width - COL_RAIDS - 175),
                UiCanvas.HorizontalAlign.LEFT);

        float buttonY = y + (ROW_HEIGHT - ACTION_BUTTON_H) / 2f;
        float cursorX = x + width - 10 - PREMADE_BUTTON_W;

        Rect editBounds = new Rect(cursorX, buttonY, PREMADE_BUTTON_W, ACTION_BUTTON_H);
        renderButton(canvas, fontName, editBounds, "Edit", true, false);
        if (clickable) {
            premadeHitboxes.add(new PremadeHitbox(editBounds, false, party));
        }
        cursorX -= PREMADE_BUTTON_W + ACTION_BUTTON_GAP;

        Rect inviteBounds = new Rect(cursorX, buttonY, PREMADE_BUTTON_W, ACTION_BUTTON_H);
        renderButton(canvas, fontName, inviteBounds, "Invite all", true, true);
        if (clickable) {
            premadeHitboxes.add(new PremadeHitbox(inviteBounds, true, party));
        }
    }

    /** Trims a summary so a long member list cannot run under the buttons. */
    static String fitToWidth(String text, String fontName, float fontSize, float maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (textWidth(text, fontName, fontSize) <= maxWidth) {
            return text;
        }
        String trimmed = text;
        while (!trimmed.isEmpty() && textWidth(trimmed + "...", fontName, fontSize) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? "" : trimmed + "...";
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

        float buttonY = (HEADER_HEIGHT - HEADER_BUTTON_H) / 2f;
        float refreshX = contentX + contentWidth - REFRESH_BUTTON_W;
        refreshButtonBounds = new Rect(refreshX, buttonY, REFRESH_BUTTON_W, HEADER_BUTTON_H);

        boolean refreshing = presence().isRefreshing();
        boolean canRefresh = presence().canRefresh(System.currentTimeMillis());
        renderButton(
                canvas,
                fontName,
                refreshButtonBounds,
                refreshing ? "Refreshing" : "Refresh",
                !refreshing && canRefresh,
                true);

        float profileX = refreshX - PROFILE_BUTTON_W - 6;
        profileButtonBounds = new Rect(profileX, buttonY, PROFILE_BUTTON_W, HEADER_BUTTON_H);
        renderButton(canvas, fontName, profileButtonBounds, "My profile", true, false);

        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                color(TEXT_MUTED),
                profileX - 8,
                HEADER_HEIGHT / 2f,
                headerSummary(),
                UiCanvas.HorizontalAlign.RIGHT);
    }

    private String headerSummary() {
        String error = presence().lastError();
        if (error != null && !error.isBlank()) {
            return error;
        }
        List<GuildMemberPresence> all = presence().onlineMembers();
        if (all.isEmpty()) {
            return presence().hasLoaded() ? "nobody online" : "loading";
        }
        int shown = presence().filteredMembers(filter).size();
        long busy = all.stream()
                .filter(member -> GuildRaidActivityTracker.isBusy(member.username()))
                .count();
        String base = filter.isActive() ? shown + " of " + all.size() + " online" : all.size() + " online";
        return busy == 0 ? base : base + ", " + busy + " busy";
    }

    private void renderFilterBar(
            UiCanvas canvas, String fontName, float contentX, float contentWidth, float barY) {
        canvas.fillRect(contentX, barY, contentWidth, FILTER_BAR_HEIGHT, color(BACKGROUND_CONTENT));

        float chipY = barY + (FILTER_BAR_HEIGHT - FILTER_CHIP_H) / 2f;
        float cursorX = contentX + 8;

        // "All" clears the raid filter, each raid chip selects it.
        cursorX = renderRaidChip(canvas, fontName, cursorX, chipY, "All", null);
        for (RaidType raid : catalog().raids()) {
            cursorX = renderRaidChip(canvas, fontName, cursorX, chipY, raid.shortName(), raid);
        }

        cursorX += 8;
        aurasFilterBounds = new Rect(cursorX, chipY, 52, FILTER_CHIP_H);
        renderToggleChip(canvas, fontName, aurasFilterBounds, "Auras", filter.aurasOnly());
        cursorX += 52 + FILTER_CHIP_GAP;

        freeFilterBounds = new Rect(cursorX, chipY, 46, FILTER_CHIP_H);
        renderToggleChip(canvas, fontName, freeFilterBounds, "Free", filter.availableOnly());

        // Search sits at the far right of the bar.
        float searchX = contentX + contentWidth - SEARCH_W - 8;
        searchBounds = new Rect(searchX, chipY, SEARCH_W, FILTER_CHIP_H);
        canvas.fillRoundedRect(
                searchX,
                chipY,
                SEARCH_W,
                FILTER_CHIP_H,
                3,
                searchFocused ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
        if (searchFocused) {
            canvas.strokeRect(searchX, chipY, SEARCH_W, FILTER_CHIP_H, 1, color(ACCENT_PRIMARY));
        }
        boolean searchEmpty = searchInput.isEmpty();
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                searchEmpty ? color(TEXT_DISABLED) : color(TEXT_PRIMARY),
                searchX + 7,
                chipY + FILTER_CHIP_H / 2f,
                searchEmpty ? "Search name" : (searchFocused ? searchInput + "_" : searchInput),
                UiCanvas.HorizontalAlign.LEFT);
    }

    private float renderRaidChip(
            UiCanvas canvas, String fontName, float x, float y, String label, RaidType raid) {
        float width = Math.max(30, textWidth(label, fontName, SMALL_FONT_SIZE) + 14);
        Rect bounds = new Rect(x, y, width, FILTER_CHIP_H);
        boolean selected = raid == null ? !filter.hasRaid() : raid.key().equals(filter.raidKey());
        boolean hovered = bounds.contains(uiMouseX, uiMouseY);
        canvas.fillRoundedRect(
                x,
                y,
                width,
                FILTER_CHIP_H,
                3,
                selected ? color(ACCENT_PRIMARY, 210) : hovered ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                selected ? color(TEXT_PRIMARY) : color(TEXT_SECONDARY),
                x + width / 2f,
                y + FILTER_CHIP_H / 2f,
                label,
                UiCanvas.HorizontalAlign.CENTER);
        raidFilterHitboxes.add(new RaidFilterHitbox(bounds, raid));
        return x + width + FILTER_CHIP_GAP;
    }

    private void renderToggleChip(UiCanvas canvas, String fontName, Rect bounds, String label, boolean active) {
        boolean hovered = bounds.contains(uiMouseX, uiMouseY);
        canvas.fillRoundedRect(
                bounds.x(),
                bounds.y(),
                bounds.width(),
                bounds.height(),
                3,
                active ? color(CONTROL_SUCCESS, 200) : hovered ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                active ? color(TEXT_PRIMARY) : color(TEXT_SECONDARY),
                bounds.x() + bounds.width() / 2f,
                bounds.y() + bounds.height() / 2f,
                label,
                UiCanvas.HorizontalAlign.CENTER);
    }

    private void renderColumnHeader(
            UiCanvas canvas, String fontName, float contentX, float contentWidth, float y) {
        canvas.fillRect(contentX, y, contentWidth, COLUMN_HEADER_HEIGHT, color(BACKGROUND_BODY));
        float centerY = y + COLUMN_HEADER_HEIGHT / 2f;

        // Every label is clipped to the gap before the next column, so a longer word
        // or a wider font can never run one header into its neighbour.
        drawLabel(canvas, fontName, contentX + COL_NAME, centerY, "MEMBER", COL_WORLD - COL_NAME);
        drawLabel(canvas, fontName, contentX + COL_WORLD, centerY, "WORLD", COL_REGION - COL_WORLD);
        drawLabel(canvas, fontName, contentX + COL_REGION, centerY, "REGION", COL_RAIDS - COL_REGION);
        drawLabel(
                canvas,
                fontName,
                contentX + COL_RAIDS,
                centerY,
                // "GRAIDS" is what the guild says out loud, and the full words do not fit
                // in the gap before the builds column.
                filter.hasRaid() ? shortRaidName() + " GR" : "GRAIDS",
                COL_EVIDENCE - COL_RAIDS);
        drawLabel(
                canvas,
                fontName,
                contentX + COL_EVIDENCE,
                centerY,
                filter.hasRaid() ? "READY FOR " + shortRaidName() : "BUILDS",
                contentWidth - ACTIONS_ZONE_W - COL_EVIDENCE);

        // Says plainly how many profiles are in play, so an empty builds column reads
        // as "nobody has shared one" rather than "nobody owns anything".
        int shared = profiles().sharedProfileCount();
        drawText(
                canvas,
                fontName,
                TINY_FONT_SIZE,
                color(TEXT_MUTED),
                contentX + contentWidth - 8,
                centerY,
                shared == 0 ? "ONLY YOUR PROFILE" : shared + " SHARED",
                UiCanvas.HorizontalAlign.RIGHT);
    }

    /** Draws a column heading, clipped to the room it has before the next column. */
    private void drawLabel(
            UiCanvas canvas, String fontName, float x, float y, String label, float maxWidth) {
        drawText(
                canvas,
                fontName,
                TINY_FONT_SIZE,
                color(TEXT_MUTED),
                x,
                y,
                fitToWidth(label, fontName, TINY_FONT_SIZE, maxWidth - COLUMN_LABEL_GAP),
                UiCanvas.HorizontalAlign.LEFT);
    }

    private void renderEmptyState(
            UiCanvas canvas, String fontName, float x, float y, float width, float height) {
        String message;
        if (presence().isRefreshing() || !presence().hasLoaded()) {
            message = "Loading the guild roster";
        } else if (presence().lastError() != null && !presence().lastError().isBlank()) {
            message = presence().lastError();
        } else if (filter.isActive()) {
            message = "Nobody online matches this filter.";
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

    private void renderMemberRow(
            UiCanvas canvas,
            String fontName,
            float x,
            float y,
            float width,
            GuildMemberPresence member,
            String localWorld,
            String localUsername) {

        boolean isLocalPlayer = localUsername != null && localUsername.equalsIgnoreCase(member.username());
        boolean onLocalWorld = member.hasWorld()
                && localWorld != null
                && member.world().equalsIgnoreCase(localWorld.trim());
        boolean rowHovered = uiMouseX >= x && uiMouseX <= x + width && uiMouseY >= y && uiMouseY <= y + ROW_HEIGHT;
        canvas.fillRect(x, y, width, ROW_HEIGHT, rowHovered ? color(BACKGROUND_CONTENT_FOCUSED) : color(BACKGROUND_BODY));

        RaidTeamProfile profile = profiles().profileFor(member);
        long busyRemainingMs = GuildRaidActivityTracker.busyRemainingMillis(member.username());
        boolean busy = busyRemainingMs > 0L;
        boolean clickable = isRowVisible(y);
        float centerY = y + ROW_HEIGHT / 2f;

        renderHead(canvas, x + COL_HEAD, y + (ROW_HEIGHT - HEAD_SIZE) / 2f, member.uuid(), busy, true);

        float nameX = x + COL_NAME;
        drawText(
                canvas,
                fontName,
                ROW_FONT_SIZE,
                isLocalPlayer ? color(ACCENT_PRIMARY) : color(TEXT_PRIMARY),
                nameX,
                centerY,
                member.username(),
                UiCanvas.HorizontalAlign.LEFT);

        float afterName = nameX + textWidth(member.username(), fontName, ROW_FONT_SIZE) + 6;
        if (member.sequoiaConnected()) {
            drawText(
                    canvas,
                    fontName,
                    TINY_FONT_SIZE,
                    color(ACCENT_PRIMARY),
                    afterName,
                    centerY,
                    "SEQ",
                    UiCanvas.HorizontalAlign.LEFT);
            afterName += 22;
        }
        if (profile.canBringAuras()) {
            drawText(
                    canvas,
                    fontName,
                    TINY_FONT_SIZE,
                    color(CONTROL_SUCCESS),
                    afterName,
                    centerY,
                    "AURAS",
                    UiCanvas.HorizontalAlign.LEFT);
        }

        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                onLocalWorld ? color(CONTROL_SUCCESS) : color(TEXT_SECONDARY),
                x + COL_WORLD,
                centerY,
                member.hasWorld() ? member.world() : "?",
                UiCanvas.HorizontalAlign.LEFT);

        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                color(TEXT_SECONDARY),
                x + COL_REGION,
                centerY,
                profile.region() == null ? "?" : profile.region().name(),
                UiCanvas.HorizontalAlign.LEFT);

        // The number tracks the raid being filtered on, because "has this person run
        // TNA with us" is the question the column is there to answer.
        RaidType filtered = filteredRaid();
        int raidCount = filtered == null
                ? member.stats().totalRaidCompletions()
                : member.stats().completions(filtered);
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                raidCount > 0 ? color(TEXT_SECONDARY) : color(TEXT_DISABLED),
                x + COL_RAIDS,
                centerY,
                String.valueOf(raidCount),
                UiCanvas.HorizontalAlign.LEFT);

        renderEvidence(canvas, fontName, x + COL_EVIDENCE, y, member, profile);

        // Actions are laid out right to left so they stay pinned to the row's edge.
        float buttonY = y + (ROW_HEIGHT - ACTION_BUTTON_H) / 2f;
        float cursorX = x + width - 10 - ACTION_BUTTON_W;

        if (!isLocalPlayer) {
            Rect inviteBounds = new Rect(cursorX, buttonY, ACTION_BUTTON_W, ACTION_BUTTON_H);
            renderButton(canvas, fontName, inviteBounds, "Invite", true, false);
            if (clickable) {
                actionHitboxes.add(new ActionHitbox(inviteBounds, ActionType.INVITE, member));
            }
            cursorX -= ACTION_BUTTON_W + ACTION_BUTTON_GAP;
        }

        boolean canSwitch = member.hasWorld() && !onLocalWorld;
        Rect joinBounds = new Rect(cursorX, buttonY, ACTION_BUTTON_W, ACTION_BUTTON_H);
        renderButton(canvas, fontName, joinBounds, onLocalWorld ? "Here" : "Join", canSwitch, false);
        if (canSwitch && clickable) {
            actionHitboxes.add(new ActionHitbox(joinBounds, ActionType.SWITCH, member));
        }
        cursorX -= BUSY_CHIP_W + ACTION_BUTTON_GAP;

        if (busy) {
            float chipY = y + (ROW_HEIGHT - BUSY_CHIP_H) / 2f;
            canvas.fillRoundedRect(cursorX, chipY, BUSY_CHIP_W, BUSY_CHIP_H, 3, color(STATUS_DANGER_BACKGROUND));
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

        // The whole row opens the member's profile, minus the zone the buttons own.
        if (clickable) {
            Rect rowBounds = new Rect(x, y, width - ACTIONS_ZONE_W, ROW_HEIGHT);
            actionHitboxes.add(new ActionHitbox(rowBounds, ActionType.OPEN_PROFILE, member));
        }
    }

    /**
     * Draws the player's head with their availability as a dot on its corner, the
     * way a Discord avatar carries a presence badge. The head is what you actually
     * recognise someone by, and the dot rides along instead of taking its own column.
     */
    private void renderHead(UiCanvas canvas, float x, float y, String uuid, boolean busy, boolean online) {
        UiImage head = PlayerHeadCache.headFor(uuid);
        if (head != null) {
            canvas.drawImage(head, x, y, HEAD_SIZE, HEAD_SIZE, 1f);
        } else {
            // Still loading, or the player has no UUID on the roster.
            canvas.fillRoundedRect(x, y, HEAD_SIZE, HEAD_SIZE, 2, color(CONTROL_INPUT));
        }

        float dotX = x + HEAD_SIZE - 1f;
        float dotY = y + HEAD_SIZE - 1f;
        // A ring in the row colour separates the dot from the skin behind it.
        canvas.fillCircle(dotX, dotY, STATUS_DOT_RADIUS + 1.5f, color(BACKGROUND_BODY_OPAQUE));
        Color dot = !online ? color(ACCENT_DISABLED) : busy ? color(CONTROL_DANGER) : color(CONTROL_SUCCESS);
        canvas.fillCircle(dotX, dotY, STATUS_DOT_RADIUS, dot);
    }

    /**
     * Draws the builds this member said they own. The raid count lives in its own
     * column now, so this one only ever carries declared builds, and a member with
     * no profile reads as unanswered rather than as owning nothing.
     */
    private void renderEvidence(
            UiCanvas canvas,
            String fontName,
            float x,
            float rowY,
            GuildMemberPresence member,
            RaidTeamProfile profile) {

        float centerY = rowY + ROW_HEIGHT / 2f;

        if (!profile.isComplete()) {
            drawText(
                    canvas,
                    fontName,
                    SMALL_FONT_SIZE,
                    color(TEXT_DISABLED),
                    x,
                    centerY,
                    "no profile yet",
                    UiCanvas.HorizontalAlign.LEFT);
            return;
        }

        RaidType filtered = filteredRaid();
        Set<String> shown =
                filtered == null ? profile.buildKeys() : filtered.matchingBuildKeys(profile.buildKeys());
        if (shown.isEmpty()) {
            drawText(
                    canvas,
                    fontName,
                    SMALL_FONT_SIZE,
                    color(TEXT_DISABLED),
                    x,
                    centerY,
                    filtered == null ? "no builds yet" : "nothing for " + filtered.shortName(),
                    UiCanvas.HorizontalAlign.LEFT);
            return;
        }
        renderBuildChips(canvas, fontName, x, rowY, shown);
    }

    private void renderBuildChips(UiCanvas canvas, String fontName, float x, float rowY, Set<String> builds) {
        float chipY = rowY + (ROW_HEIGHT - CHIP_H) / 2f;
        float cursorX = x;
        int drawn = 0;
        RaidCatalog catalog = catalog();

        for (String buildKey : catalog.orderKeys(builds)) {
            if (drawn == MAX_VISIBLE_CHIPS) {
                drawText(
                        canvas,
                        fontName,
                        SMALL_FONT_SIZE,
                        color(TEXT_MUTED),
                        cursorX,
                        rowY + ROW_HEIGHT / 2f,
                        "+" + (builds.size() - MAX_VISIBLE_CHIPS),
                        UiCanvas.HorizontalAlign.LEFT);
                return;
            }
            String label = catalog.labelFor(buildKey);
            float width = textWidth(label, fontName, TINY_FONT_SIZE) + 12;
            canvas.fillRoundedRect(cursorX, chipY, width, CHIP_H, 3, color(ACCENT_PRIMARY_DARK));
            drawText(
                    canvas,
                    fontName,
                    TINY_FONT_SIZE,
                    color(TEXT_PRIMARY),
                    cursorX + width / 2f,
                    chipY + CHIP_H / 2f,
                    label,
                    UiCanvas.HorizontalAlign.CENTER);
            cursorX += width + CHIP_GAP;
            drawn++;
        }
    }

    // ── Detail modal ──

    private void renderDetailModal(UiCanvas canvas, String fontName, float screenWidth, float screenHeight) {
        GuildMemberPresence member = selectedMember;
        RaidTeamProfile profile = profiles().profileFor(member);

        float height = MODAL_PADDING * 2
                + MODAL_HEAD_SIZE
                + 10
                + MODAL_ROW_H * (5 + Math.max(1, catalog().raids().size()))
                + 10
                + ACTION_BUTTON_H
                + 22
                + NOTE_INPUT_H
                + 14;
        float width = Math.min(MODAL_WIDTH, screenWidth - 24);
        float x = (screenWidth - width) / 2f;
        float y = Math.max(12, (screenHeight - height) / 2f);
        modalBounds = new Rect(x, y, width, height);

        canvas.fillRect(0, 0, screenWidth, screenHeight, color(BACKGROUND_MODAL_OVERLAY, 170));
        canvas.fillRoundedRect(x, y, width, height, 6, color(BACKGROUND_POPUP));
        canvas.fillRect(x, y, 3, height, color(ACCENT_PRIMARY));

        float contentX = x + MODAL_PADDING;
        float contentWidth = width - MODAL_PADDING * 2;
        float cursorY = y + MODAL_PADDING;

        UiImage head = PlayerHeadCache.headFor(member.uuid());
        if (head != null) {
            canvas.drawImage(head, contentX, cursorY, MODAL_HEAD_SIZE, MODAL_HEAD_SIZE, 1f);
        } else {
            canvas.fillRoundedRect(contentX, cursorY, MODAL_HEAD_SIZE, MODAL_HEAD_SIZE, 3, color(CONTROL_INPUT));
        }

        drawText(
                canvas,
                fontName,
                TITLE_FONT_SIZE,
                color(TEXT_PRIMARY),
                contentX + MODAL_HEAD_SIZE + 10,
                cursorY + MODAL_HEAD_SIZE / 2f - 5,
                member.username(),
                UiCanvas.HorizontalAlign.LEFT);
        if (profile.hasStatus()) {
            drawText(
                    canvas,
                    fontName,
                    SMALL_FONT_SIZE,
                    color(TEXT_MUTED),
                    contentX + MODAL_HEAD_SIZE + 10,
                    cursorY + MODAL_HEAD_SIZE / 2f + 9,
                    profile.status(),
                    UiCanvas.HorizontalAlign.LEFT);
        }

        modalCloseBounds = new Rect(x + width - 26, y + 10, 16, 16);
        drawText(
                canvas,
                fontName,
                ROW_FONT_SIZE,
                modalCloseBounds.contains(uiMouseX, uiMouseY) ? color(TEXT_PRIMARY) : color(TEXT_MUTED),
                modalCloseBounds.x() + 8,
                modalCloseBounds.y() + 8,
                "x",
                UiCanvas.HorizontalAlign.CENTER);
        cursorY += MODAL_HEAD_SIZE + 10;

        cursorY = modalRow(canvas, fontName, contentX, cursorY, contentWidth, "Rank", member.rank().displayName());
        cursorY = modalRow(
                canvas,
                fontName,
                contentX,
                cursorY,
                contentWidth,
                "World",
                member.hasWorld() ? member.world() : "not reported");
        cursorY = modalRow(
                canvas, fontName, contentX, cursorY, contentWidth, "Playtime", member.stats().playtimeLabel());
        // Auras is a thing you can bring, not a build, so it gets its own line.
        cursorY = modalRow(
                canvas,
                fontName,
                contentX,
                cursorY,
                contentWidth,
                "Auras",
                !profile.isComplete() ? "unknown" : profile.canBringAuras() ? "yes" : "no");
        cursorY = modalRow(canvas, fontName, contentX, cursorY, contentWidth, "Builds", profileSummary(profile));

        RaidCatalog catalog = catalog();
        if (catalog.raids().isEmpty()) {
            cursorY = modalRow(
                    canvas, fontName, contentX, cursorY, contentWidth, "Raids", "meta not loaded");
        }
        for (RaidType raid : catalog.raids()) {
            Set<String> matching =
                    profile.isComplete() ? raid.matchingBuildKeys(profile.buildKeys()) : Set.of();
            String value = member.stats().raidCountLabel(raid);
            if (!matching.isEmpty()) {
                value += "   " + catalog.joinLabels(matching);
            }
            cursorY = modalRow(canvas, fontName, contentX, cursorY, contentWidth, raid.shortName(), value);
        }

        cursorY += 10;
        boolean isFriend = profiles().isFriend(member.username());
        friendToggleBounds = new Rect(contentX, cursorY, 108, ACTION_BUTTON_H);
        renderButton(
                canvas,
                fontName,
                friendToggleBounds,
                isFriend ? "Remove friend" : "Add friend",
                true,
                !isFriend);
        cursorY += ACTION_BUTTON_H + 8;

        drawText(
                canvas,
                fontName,
                TINY_FONT_SIZE,
                color(ACCENT_PRIMARY),
                contentX,
                cursorY + 7,
                "YOUR NOTE, ONLY YOU SEE IT",
                UiCanvas.HorizontalAlign.LEFT);
        cursorY += 18;

        noteBounds = new Rect(contentX, cursorY, contentWidth, NOTE_INPUT_H);
        canvas.fillRoundedRect(
                contentX,
                cursorY,
                contentWidth,
                NOTE_INPUT_H,
                3,
                noteFocused ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
        if (noteFocused) {
            canvas.strokeRect(contentX, cursorY, contentWidth, NOTE_INPUT_H, 1, color(ACCENT_PRIMARY));
        }
        boolean noteEmpty = noteInput.isEmpty();
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                noteEmpty ? color(TEXT_DISABLED) : color(TEXT_PRIMARY),
                contentX + 7,
                cursorY + NOTE_INPUT_H / 2f,
                noteEmpty ? "solid tna aco, knows the lineup" : (noteFocused ? noteInput + "_" : noteInput),
                UiCanvas.HorizontalAlign.LEFT);
    }

    private String profileSummary(RaidTeamProfile profile) {
        if (!profile.isComplete()) {
            return "hasn't shared a profile";
        }
        return profile.buildKeys().isEmpty() ? "none ticked" : catalog().joinLabels(profile.buildKeys());
    }

    /** The filtered raid's abbreviation, or a placeholder while the meta loads. */
    private String shortRaidName() {
        RaidType raid = filteredRaid();
        return raid == null ? String.valueOf(filter.raidKey()) : raid.shortName();
    }

    private float modalRow(
            UiCanvas canvas, String fontName, float x, float y, float width, String label, String value) {
        float centerY = y + MODAL_ROW_H / 2f;
        drawText(canvas, fontName, SMALL_FONT_SIZE, color(TEXT_MUTED), x, centerY, label, UiCanvas.HorizontalAlign.LEFT);
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                color(TEXT_SECONDARY),
                x + width,
                centerY,
                value,
                UiCanvas.HorizontalAlign.RIGHT);
        return y + MODAL_ROW_H;
    }

    /** Whether a row sits inside the scissored viewport, and so is genuinely on screen. */
    private boolean isRowVisible(float rowY) {
        if (listViewport == null) {
            return false;
        }
        return rowY >= listViewport.y() && rowY + ROW_HEIGHT <= listViewport.y() + listViewport.height();
    }

    private void renderButton(
            UiCanvas canvas, String fontName, Rect bounds, String label, boolean enabled, boolean accent) {
        boolean hovered = enabled && bounds.contains(uiMouseX, uiMouseY);
        Color background;
        if (!enabled) {
            background = color(ACCENT_DISABLED, 140);
        } else if (accent) {
            background = hovered ? color(ACCENT_PRIMARY_HOVER, 220) : color(ACCENT_PRIMARY, 200);
        } else {
            background = hovered ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT);
        }
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

        if (selectedMember != null) {
            return handleModalClick(mx, my);
        }

        for (TabHitbox hitbox : tabHitboxes) {
            if (hitbox.bounds().contains(mx, my)) {
                tab = hitbox.tab();
                scrollOffset = 0;
                searchFocused = false;
                return true;
            }
        }
        if (handleFriendClick(mx, my) || handlePremadeClick(mx, my)) {
            return true;
        }

        if (refreshButtonBounds != null && refreshButtonBounds.contains(mx, my)) {
            if (presence().canRefresh(System.currentTimeMillis())) {
                presence().refresh(true);
                profiles().refresh();
                PlayerHeadCache.retryFailed();
                showStatusBanner("Refreshing the guild roster");
            } else {
                showStatusBanner("Wynncraft only refreshes this once a minute.");
            }
            return true;
        }
        if (profileButtonBounds != null && profileButtonBounds.contains(mx, my)) {
            profiles().requestSetup();
            SeqClient.mc.setScreen(new RaidProfileSetupScreen(parent, false));
            return true;
        }
        for (RaidFilterHitbox hitbox : raidFilterHitboxes) {
            if (hitbox.bounds().contains(mx, my)) {
                filter = filter.withRaid(hitbox.raid());
                scrollOffset = 0;
                searchFocused = false;
                return true;
            }
        }
        if (aurasFilterBounds != null && aurasFilterBounds.contains(mx, my)) {
            filter = filter.withAurasOnly(!filter.aurasOnly());
            scrollOffset = 0;
            return true;
        }
        if (freeFilterBounds != null && freeFilterBounds.contains(mx, my)) {
            filter = filter.withAvailableOnly(!filter.availableOnly());
            scrollOffset = 0;
            return true;
        }
        if (searchBounds != null && searchBounds.contains(mx, my)) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;

        for (ActionHitbox hitbox : actionHitboxes) {
            if (!hitbox.bounds().contains(mx, my)) {
                continue;
            }
            switch (hitbox.type()) {
                case SWITCH -> {
                    // Closing straight away puts the loading screen in front of the player,
                    // and a banner behind it would never be read.
                    presence().switchToWorld(hitbox.member().world());
                    onClose();
                }
                case INVITE -> showStatusBanner(inviteMessage(hitbox.member()));
                case OPEN_PROFILE -> openProfile(hitbox.member());
            }
            return true;
        }

        return super.mouseClicked(click, outsideScreen);
    }

    private boolean handleFriendClick(float mx, float my) {
        for (FriendHitbox hitbox : friendHitboxes) {
            if (!hitbox.bounds().contains(mx, my)) {
                continue;
            }
            switch (hitbox.action()) {
                case ASK -> showStatusBanner(
                        presence().whisper(hitbox.friend(), "raid ?")
                                ? "Asked " + hitbox.friend() + " for a raid."
                                : "Could not message " + hitbox.friend() + ".");
                case INVITE -> showStatusBanner(presence().inviteToParty(hitbox.friend()).message());
                case REMOVE -> {
                    profiles().toggleFriend(hitbox.friend());
                    showStatusBanner(hitbox.friend() + " removed from your friends.");
                }
            }
            return true;
        }
        return false;
    }

    private boolean handlePremadeClick(float mx, float my) {
        if (newPartyBounds != null && tab == Tab.PREMADES && newPartyBounds.contains(mx, my)) {
            SeqClient.mc.setScreen(new PremadePartyEditorScreen(this, null));
            return true;
        }
        for (PremadeHitbox hitbox : premadeHitboxes) {
            if (!hitbox.bounds().contains(mx, my)) {
                continue;
            }
            if (hitbox.inviteAll()) {
                showStatusBanner(presence().inviteAllToParty(hitbox.party().members()).message());
            } else {
                SeqClient.mc.setScreen(new PremadePartyEditorScreen(this, hitbox.party()));
            }
            return true;
        }
        return false;
    }

    /**
     * Invites the member and reports it, naming the raid when one is being filtered
     * on so the confirmation says what the invite was for.
     */
    private String inviteMessage(GuildMemberPresence member) {
        GuildPresenceManager.InviteOutcome outcome = presence().inviteToParty(member.username());
        RaidType filtered = filteredRaid();
        if (!outcome.sent() || filtered == null) {
            return outcome.message();
        }
        RaidTeamProfile profile = profiles().profileFor(member);
        Set<String> matching = filtered.matchingBuildKeys(profile.buildKeys());
        if (matching.isEmpty()) {
            return outcome.message();
        }
        return outcome.message()
                + " ("
                + filtered.shortName()
                + ": "
                + catalog().joinLabels(matching)
                + ")";
    }

    private void openProfile(GuildMemberPresence member) {
        selectedMember = member;
        String existing = profiles().noteFor(member.username());
        noteInput = existing == null ? "" : existing;
        noteFocused = false;
    }

    private boolean handleModalClick(float mx, float my) {
        if (modalCloseBounds != null && modalCloseBounds.contains(mx, my)) {
            closeProfile();
            return true;
        }
        if (friendToggleBounds != null && friendToggleBounds.contains(mx, my)) {
            profiles().toggleFriend(selectedMember.username());
            showStatusBanner(
                    profiles().isFriend(selectedMember.username())
                            ? selectedMember.username() + " added to your friends."
                            : selectedMember.username() + " removed from your friends.");
            return true;
        }
        if (noteBounds != null && noteBounds.contains(mx, my)) {
            noteFocused = true;
            return true;
        }
        if (modalBounds != null && !modalBounds.contains(mx, my)) {
            closeProfile();
            return true;
        }
        noteFocused = false;
        return true;
    }

    /** Saving on close is what makes the note a one-line action rather than a form. */
    private void closeProfile() {
        if (selectedMember != null) {
            profiles().setNote(selectedMember.username(), noteInput);
        }
        selectedMember = null;
        noteFocused = false;
        noteInput = "";
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (selectedMember == null && maxScroll > 0) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (float) scrollY * SCROLL_SPEED));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent keyEvent) {
        int key = keyEvent.key();

        if (selectedMember != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                closeProfile();
                return true;
            }
            if (noteFocused && key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!noteInput.isEmpty()) {
                    noteInput = noteInput.substring(0, noteInput.length() - 1);
                }
                return true;
            }
            return true;
        }

        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchInput.isEmpty()) {
                    searchInput = searchInput.substring(0, searchInput.length() - 1);
                    filter = filter.withSearch(searchInput);
                    scrollOffset = 0;
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                searchFocused = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                searchInput = "";
                filter = filter.withSearch(null);
                return true;
            }
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean charTyped(@NotNull CharacterEvent characterEvent) {
        String typed = TextInputHelper.getTypedText(characterEvent);
        if (typed == null || typed.length() != 1 || typed.charAt(0) < ' ') {
            return super.charTyped(characterEvent);
        }

        if (selectedMember != null) {
            if (noteFocused && noteInput.length() < RaidProfileStore.MAX_NOTE_LENGTH) {
                noteInput += typed;
            }
            return true;
        }
        if (searchFocused && searchInput.length() < 16) {
            searchInput += typed;
            filter = filter.withSearch(searchInput);
            scrollOffset = 0;
            return true;
        }
        return super.charTyped(characterEvent);
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
        INVITE,
        OPEN_PROFILE
    }

    private record ActionHitbox(Rect bounds, ActionType type, GuildMemberPresence member) {}

    private record RaidFilterHitbox(Rect bounds, RaidType raid) {}

    private enum Tab {
        MEMBERS,
        FRIENDS,
        PREMADES
    }

    private enum FriendAction {
        ASK,
        INVITE,
        REMOVE
    }

    private record TabHitbox(Rect bounds, Tab tab) {}

    private record FriendHitbox(Rect bounds, FriendAction action, String friend) {}

    private record PremadeHitbox(Rect bounds, boolean inviteAll, PremadeParty party) {}
}
