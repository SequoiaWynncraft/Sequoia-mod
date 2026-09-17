package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.GuildPresenceManager;
import com.seqwawa.seq.managers.GuildRaidActivityTracker;
import com.seqwawa.seq.managers.PlayerHeadCache;
import com.seqwawa.seq.managers.RaidProfileStore;
import com.seqwawa.seq.model.GuildMemberPresence;
import com.seqwawa.seq.model.GuildMemberStats;
import com.seqwawa.seq.model.KnownGuildMember;
import com.seqwawa.seq.model.MemberFilter;
import com.seqwawa.seq.model.MemberSort;
import com.seqwawa.seq.model.PartyFinderSpot;
import com.seqwawa.seq.model.PremadeAvailability;
import com.seqwawa.seq.model.PremadeParty;
import com.seqwawa.seq.model.RaidCatalog;
import com.seqwawa.seq.model.RaidPerformance;
import com.seqwawa.seq.model.RaidTeamProfile;
import com.seqwawa.seq.model.RaidType;
import com.seqwawa.seq.utils.TextInputHelper;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiImage;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.time.Instant;
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
 * The guild members panel: who is online, what they bring to a raid, and the two
 * things you would do about it, join their world or invite them.
 * <p>
 * A member with a raid profile shows the builds they declared; one without shows
 * how many times they have run that raid with the guild, which is weaker evidence
 * but needs nobody to opt in.
 */
public class GuildMembersScreen extends Screen {

    // ── Layout ──
    private static final float HEADER_HEIGHT = 30;
    private static final float TAB_STRIP_HEIGHT = 24;
    private static final float FILTER_BAR_HEIGHT = 30;
    private static final float COLUMN_HEADER_HEIGHT = 16;
    /** Kept between a column heading and the next column. */
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
    private static final float COL_WORLD = 172;
    private static final float COL_ONLINE = 212;
    private static final float COL_RAIDS = 252;
    private static final float COL_WARS = 298;
    private static final float COL_EVIDENCE = 344;

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
    /** A party finder chip may outgrow the busy one, but not enough to reach the builds. */
    private static final float PF_CHIP_MAX_W = 80;
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

    private static final float MODAL_WIDTH = 420;
    private static final float PREMADE_MODAL_WIDTH = 470;
    private static final float PREMADE_SEAT_H = 22;
    private static final float MODAL_PADDING = 18;
    private static final float MODAL_ROW_H = 18;
    /** Fact rows above the per-raid block: seven shared lines and the builds line. */
    private static final int MODAL_FACT_ROWS = 8;
    private static final float MODAL_PAIR_GAP = 14;
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

    /**
     * Set when the panel opened before profiles had loaded, since "no profile" may
     * then only mean "not fetched yet". The first-run decision waits for the fetch.
     */
    private boolean firstRunCheckPending;

    /** init() runs again on every resize, and the fetches are wanted only on open. */
    private boolean dataRequested;

    private float uiMouseX;
    private float uiMouseY;
    private float scrollOffset;
    private float maxScroll;

    private Tab tab = Tab.MEMBERS;
    private MemberFilter filter = MemberFilter.none();
    /** Which column the list is ordered by, and which way round. */
    private MemberSort sort = MemberSort.NAME;
    private boolean sortDescending;
    private String searchInput = "";
    private boolean searchFocused;

    private GuildMemberPresence selectedMember;
    private String noteInput = "";
    private boolean noteFocused;

    /** The premade whose seats are open in the modal, or null. */
    private PremadeParty selectedPremade;

    private volatile String statusBannerMessage;
    private volatile long statusBannerExpiresAtMs;

    /** Rebuilt every frame so clicks test against exactly what was drawn. */
    private final List<ActionHitbox> actionHitboxes = new ArrayList<>();
    private final List<RaidFilterHitbox> raidFilterHitboxes = new ArrayList<>();
    private final List<TabHitbox> tabHitboxes = new ArrayList<>();
    private final List<FriendHitbox> friendHitboxes = new ArrayList<>();
    private final List<PremadeHitbox> premadeHitboxes = new ArrayList<>();
    private final List<PartyFinderHitbox> partyFinderHitboxes = new ArrayList<>();
    private final List<SortHitbox> sortHitboxes = new ArrayList<>();
    private Rect premadeInviteBounds;
    private boolean premadeInviteEnabled;
    private Rect premadeEditBounds;
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
        if (dataRequested) {
            return;
        }
        dataRequested = true;
        // The throttle turns this into a no-op when the roster was just fetched.
        presence().refresh(false);
        profiles().refresh();
        // Listings only load when the party finder screen opens, so ask for them here.
        presence().refreshPartyFinder();
        firstRunCheckPending = !profiles().hasLoadedProfiles();
    }

    @Override
    public void tick() {
        super.tick();
        if (!firstRunCheckPending || !profiles().hasLoadedProfiles()) {
            return;
        }
        firstRunCheckPending = false;
        // Only now can a missing profile be trusted, and only if no card is open.
        if (profiles().needsSetup() && selectedMember == null && selectedPremade == null && SeqClient.mc.screen == this) {
            SeqClient.mc.setScreen(new RaidProfileSetupScreen(parent, true));
        }
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
        partyFinderHitboxes.clear();
        sortHitboxes.clear();

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

            premadeInviteBounds = null;
            premadeEditBounds = null;
            premadeInviteEnabled = false;
            if (selectedMember != null) {
                renderDetailModal(canvas, fontName, screenWidth, screenHeight);
            } else if (selectedPremade != null) {
                noteBounds = null;
                renderPremadeModal(canvas, fontName, screenWidth, screenHeight);
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
            case FRIENDS -> "Friends";
            case PREMADES -> {
                int count = profiles().premades().size();
                yield count == 0 ? "Premades" : "Premades " + count;
            }
        };
    }

    private void renderMembersTab(
            UiCanvas canvas, String fontName, float contentX, float contentY, float contentWidth, float contentHeight) {
        List<GuildMemberPresence> members =
                presence().membersForDisplay(filter, sort, sortDescending, filteredRaid());
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

        // Resolved once for the tab: per row would rebuild the roster view every frame.
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
        // The roster lists offline members too, so an offline friend still has a head.
        KnownGuildMember known = presence().knownMember(friend);
        String headUuid = online != null ? online.uuid() : known == null ? null : known.uuid();
        renderHead(canvas, x + COL_HEAD, y + (ROW_HEIGHT - HEAD_SIZE) / 2f, headUuid, busy, isOnline, isOnline ? 1f : 0.45f);

        drawText(
                canvas,
                fontName,
                ROW_FONT_SIZE,
                isOnline ? color(TEXT_PRIMARY) : color(TEXT_DISABLED),
                x + COL_NAME,
                centerY,
                friend,
                UiCanvas.HorizontalAlign.LEFT);

        // lastJoin is the start of the session, so it reads as time online for someone
        // on, and as time since they were last on for someone off.
        String lastLogin = known == null ? null : known.lastLoginLabel(Instant.now());
        String where = isOnline ? (online.hasWorld() ? online.world() : "?") : "offline";
        if (lastLogin != null) {
            where += ", " + lastLogin;
        }
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                isOnline ? color(TEXT_SECONDARY) : color(TEXT_DISABLED),
                x + COL_WORLD,
                centerY,
                fitToWidth(where, fontName, SMALL_FONT_SIZE, width - COL_WORLD - 10 - (FRIEND_BUTTON_W + ACTION_BUTTON_GAP) * 3),
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
                "Groups you run with often. Click one to see who can come.",
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

        List<GuildMemberPresence> online = presence().onlineMembers();
        String localUsername = presence().localUsername();
        float cursorY = contentY - scrollOffset;
        for (PremadeParty party : parties) {
            PremadeAvailability availability =
                    PremadeAvailability.of(party, online, GuildRaidActivityTracker::isBusy, localUsername);
            renderPremadeRow(canvas, fontName, contentX, cursorY, contentWidth, party, availability);
            cursorY += ROW_HEIGHT + ROW_SPACING;
        }
        maxScroll = Math.max(0, cursorY + scrollOffset - contentY - contentHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    private void renderPremadeRow(
            UiCanvas canvas,
            String fontName,
            float x,
            float y,
            float width,
            PremadeParty party,
            PremadeAvailability availability) {
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
                fitToWidth(party.name(), fontName, ROW_FONT_SIZE, COL_WORLD - 16),
                UiCanvas.HorizontalAlign.LEFT);

        // Green only when the whole group can go.
        Color availabilityColor = availability.everyoneFree()
                ? color(CONTROL_SUCCESS)
                : availability.onlineCount() == 0 ? color(TEXT_DISABLED) : color(TEXT_SECONDARY);
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                availabilityColor,
                x + COL_WORLD,
                centerY,
                fitToWidth(availability.summary(), fontName, SMALL_FONT_SIZE, COL_EVIDENCE - COL_WORLD - 8),
                UiCanvas.HorizontalAlign.LEFT);

        float buttonsWidth = PREMADE_BUTTON_W * 2 + ACTION_BUTTON_GAP + 20;
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                color(TEXT_MUTED),
                x + COL_EVIDENCE,
                centerY,
                fitToWidth(party.summary(), fontName, SMALL_FONT_SIZE, width - COL_EVIDENCE - buttonsWidth),
                UiCanvas.HorizontalAlign.LEFT);

        float buttonY = y + (ROW_HEIGHT - ACTION_BUTTON_H) / 2f;
        float cursorX = x + width - 10 - PREMADE_BUTTON_W;

        Rect editBounds = new Rect(cursorX, buttonY, PREMADE_BUTTON_W, ACTION_BUTTON_H);
        renderButton(canvas, fontName, editBounds, "Edit", true, false);
        if (clickable) {
            premadeHitboxes.add(new PremadeHitbox(editBounds, PremadeAction.EDIT, party));
        }
        cursorX -= PREMADE_BUTTON_W + ACTION_BUTTON_GAP;

        int free = availability.freeToInvite().size();
        Rect inviteBounds = new Rect(cursorX, buttonY, PREMADE_BUTTON_W, ACTION_BUTTON_H);
        renderButton(canvas, fontName, inviteBounds, premadeInviteLabel(availability), free > 0, true);
        if (clickable && free > 0) {
            premadeHitboxes.add(new PremadeHitbox(inviteBounds, PremadeAction.INVITE, party));
        }

        if (clickable) {
            Rect rowBounds = new Rect(x, y, width - buttonsWidth, ROW_HEIGHT);
            premadeHitboxes.add(new PremadeHitbox(rowBounds, PremadeAction.OPEN, party));
        }
    }

    /** "Invite all" when everyone can come, "Invite 2" when only some can. */
    private static String premadeInviteLabel(PremadeAvailability availability) {
        int free = availability.freeToInvite().size();
        if (free == 0) {
            return "Invite";
        }
        return availability.everyoneFree() ? "Invite all" : "Invite " + free;
    }

    /** Invites the free seats only, and says who was left out. */
    private String invitePremade(PremadeParty party) {
        PremadeAvailability availability = PremadeAvailability.of(
                party, presence().onlineMembers(), GuildRaidActivityTracker::isBusy, presence().localUsername());
        List<String> free = availability.freeToInvite();
        if (free.isEmpty()) {
            return "Nobody in " + party.name() + " is free right now.";
        }
        GuildPresenceManager.InviteOutcome outcome = presence().inviteAllToParty(free);
        String leftOut = availability.leftOutSummary();
        return leftOut == null ? outcome.message() : outcome.message() + " " + capitalize(leftOut) + ".";
    }

    private static String capitalize(String text) {
        return text == null || text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    // ── Premade modal ──

    private void renderPremadeModal(UiCanvas canvas, String fontName, float screenWidth, float screenHeight) {
        PremadeParty party = profiles().premade(selectedPremade.name());
        if (party == null) {
            // Deleted from the editor while the modal was open.
            selectedPremade = null;
            modalBounds = null;
            modalCloseBounds = null;
            return;
        }
        selectedPremade = party;
        PremadeAvailability availability = PremadeAvailability.of(
                party, presence().onlineMembers(), GuildRaidActivityTracker::isBusy, presence().localUsername());

        float height = MODAL_PADDING * 2
                + MODAL_HEAD_SIZE
                + 10
                + PREMADE_SEAT_H * availability.size()
                + 12
                + ACTION_BUTTON_H;
        float width = Math.min(PREMADE_MODAL_WIDTH, screenWidth - 24);
        float x = (screenWidth - width) / 2f;
        float y = Math.max(12, (screenHeight - height) / 2f);
        modalBounds = new Rect(x, y, width, height);

        canvas.fillRect(0, 0, screenWidth, screenHeight, color(BACKGROUND_MODAL_OVERLAY, 170));
        canvas.fillRoundedRect(x, y, width, height, 6, color(BACKGROUND_POPUP));
        canvas.fillRect(x, y, 3, height, color(ACCENT_PRIMARY));

        float contentX = x + MODAL_PADDING;
        float contentWidth = width - MODAL_PADDING * 2;
        float cursorY = y + MODAL_PADDING;

        drawText(
                canvas,
                fontName,
                TITLE_FONT_SIZE,
                color(TEXT_PRIMARY),
                contentX,
                cursorY + MODAL_HEAD_SIZE / 2f - 5,
                party.name(),
                UiCanvas.HorizontalAlign.LEFT);
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                availability.everyoneFree() ? color(CONTROL_SUCCESS) : color(TEXT_MUTED),
                contentX,
                cursorY + MODAL_HEAD_SIZE / 2f + 11,
                availability.summary(),
                UiCanvas.HorizontalAlign.LEFT);

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

        Instant now = Instant.now();
        for (PremadeAvailability.Seat seat : availability.seats()) {
            cursorY = renderPremadeSeat(canvas, fontName, contentX, cursorY, contentWidth, seat, now);
        }

        cursorY += 12;
        int free = availability.freeToInvite().size();
        premadeInviteEnabled = free > 0;
        premadeInviteBounds = new Rect(contentX, cursorY, 110, ACTION_BUTTON_H);
        renderButton(
                canvas,
                fontName,
                premadeInviteBounds,
                free == 0 ? "Nobody free" : availability.everyoneFree() ? "Invite all" : "Invite the " + free + " free",
                free > 0,
                true);
        premadeEditBounds = new Rect(contentX + 110 + ACTION_BUTTON_GAP, cursorY, PREMADE_BUTTON_W, ACTION_BUTTON_H);
        renderButton(canvas, fontName, premadeEditBounds, "Edit", true, false);
    }

    private float renderPremadeSeat(
            UiCanvas canvas,
            String fontName,
            float x,
            float y,
            float width,
            PremadeAvailability.Seat seat,
            Instant now) {
        float centerY = y + PREMADE_SEAT_H / 2f;
        boolean offline = seat.state() == PremadeAvailability.State.OFFLINE;
        boolean busy = seat.state() == PremadeAvailability.State.BUSY;
        KnownGuildMember known = presence().knownMember(seat.username());
        String uuid = seat.presence() != null ? seat.presence().uuid() : known == null ? null : known.uuid();
        renderHead(canvas, x, y + (PREMADE_SEAT_H - HEAD_SIZE) / 2f, uuid, busy, !offline, offline ? 0.45f : 1f);

        drawText(
                canvas,
                fontName,
                ROW_FONT_SIZE,
                seat.self() ? color(ACCENT_PRIMARY) : offline ? color(TEXT_DISABLED) : color(TEXT_PRIMARY),
                x + HEAD_SIZE + 8,
                centerY,
                seat.self() ? seat.username() + " (you)" : seat.username(),
                UiCanvas.HorizontalAlign.LEFT);

        String state;
        Color stateColor;
        if (busy) {
            state = "busy " + formatCountdown(GuildRaidActivityTracker.busyRemainingMillis(seat.username()));
            stateColor = color(CONTROL_DANGER);
        } else if (offline) {
            String lastLogin = known == null ? null : known.lastLoginLabel(now);
            state = lastLogin == null ? "offline" : "offline, " + lastLogin;
            stateColor = color(TEXT_DISABLED);
        } else {
            GuildMemberPresence presence = seat.presence();
            state = presence != null && presence.hasWorld() ? "free on " + presence.world() : "free";
            stateColor = color(CONTROL_SUCCESS);
        }
        drawText(canvas, fontName, SMALL_FONT_SIZE, stateColor, x + width, centerY, state, UiCanvas.HorizontalAlign.RIGHT);
        return y + PREMADE_SEAT_H;
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

        // Labels are clipped to the gap before the next column so they cannot collide.
        // The five comparable ones are buttons: clicking one orders the list by it.
        sortLabel(canvas, fontName, contentX + COL_NAME, y, "MEMBER", COL_WORLD - COL_NAME, MemberSort.NAME);
        sortLabel(canvas, fontName, contentX + COL_WORLD, y, "WORLD", COL_ONLINE - COL_WORLD, MemberSort.WORLD);
        sortLabel(
                canvas,
                fontName,
                contentX + COL_ONLINE,
                y,
                "ONLINE",
                COL_RAIDS - COL_ONLINE,
                MemberSort.ONLINE_SINCE);
        sortLabel(
                canvas,
                fontName,
                contentX + COL_RAIDS,
                y,
                // "GRAIDS" is what the guild says, and it fits where the full words do not.
                filter.hasRaid() ? shortRaidName() + " GR" : "GRAIDS",
                COL_WARS - COL_RAIDS,
                MemberSort.GUILD_RAIDS);
        sortLabel(canvas, fontName, contentX + COL_WARS, y, "WARS", COL_EVIDENCE - COL_WARS, MemberSort.WARS);
        drawLabel(
                canvas,
                fontName,
                contentX + COL_EVIDENCE,
                centerY,
                filter.hasRaid() ? "READY FOR " + shortRaidName() : "BUILDS",
                contentWidth - ACTIONS_ZONE_W - COL_EVIDENCE);

        // How many profiles are in play, so an empty builds column reads as "none shared".
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

    /**
     * A column heading you can click to sort by, arrowed when it is the one in force.
     * The hitbox covers the whole column, not just the label.
     */
    private void sortLabel(
            UiCanvas canvas, String fontName, float x, float y, String label, float columnWidth, MemberSort key) {
        boolean active = sort == key;
        Rect bounds = new Rect(x - 4, y, Math.max(20, columnWidth), COLUMN_HEADER_HEIGHT);
        boolean hovered = bounds.contains(uiMouseX, uiMouseY);
        if (hovered) {
            canvas.fillRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), color(BACKGROUND_CONTENT));
        }
        String text = active ? label + (sortDescending ? " v" : " ^") : label;
        drawText(
                canvas,
                fontName,
                TINY_FONT_SIZE,
                active ? color(ACCENT_PRIMARY) : hovered ? color(TEXT_SECONDARY) : color(TEXT_MUTED),
                x,
                y + COLUMN_HEADER_HEIGHT / 2f,
                fitToWidth(text, fontName, TINY_FONT_SIZE, columnWidth - COLUMN_LABEL_GAP),
                UiCanvas.HorizontalAlign.LEFT);
        sortHitboxes.add(new SortHitbox(bounds, key));
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

        // Tags ride behind the name; a name that fills the column keeps them off.
        float afterName = nameX + textWidth(member.username(), fontName, ROW_FONT_SIZE) + 6;
        float tagLimit = x + COL_WORLD - COLUMN_LABEL_GAP;
        if (member.sequoiaConnected() && afterName + textWidth("SEQ", fontName, TINY_FONT_SIZE) <= tagLimit) {
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
        if (profile.canBringAuras() && afterName + textWidth("AURAS", fontName, TINY_FONT_SIZE) <= tagLimit) {
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

        // Time on this session. A member who hides their status has none.
        String onlineFor = KnownGuildMember.formatElapsedShort(presence().lastLogin(member), Instant.now());
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                onlineFor == null ? color(TEXT_DISABLED) : color(TEXT_SECONDARY),
                x + COL_ONLINE,
                centerY,
                onlineFor == null ? "?" : onlineFor,
                UiCanvas.HorizontalAlign.LEFT);

        // The count tracks the filtered raid, which is the question the column answers.
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

        int wars = member.stats().wars();
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                wars > 0 ? color(TEXT_SECONDARY) : color(TEXT_DISABLED),
                x + COL_WARS,
                centerY,
                wars > 0 ? GuildMemberStats.formatCount(wars) : "?",
                UiCanvas.HorizontalAlign.LEFT);

        renderEvidence(
                canvas, fontName, x + COL_EVIDENCE, y, member, profile, width - ACTIONS_ZONE_W - COL_EVIDENCE);

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

        // A listing says more than the busy timer, and the red dot still carries busy.
        PartyFinderSpot spot = presence().partyFinderSpotFor(member);
        if (spot != null) {
            renderPartyFinderChip(canvas, fontName, cursorX + BUSY_CHIP_W, y, spot, isLocalPlayer, clickable);
        } else if (busy) {
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
     * "PF TNA 2/4", right-aligned to {@code rightEdge}. It is a join button only when
     * joining could work: open listing with room, not you, and you are in none yourself.
     */
    private void renderPartyFinderChip(
            UiCanvas canvas,
            String fontName,
            float rightEdge,
            float rowY,
            PartyFinderSpot spot,
            boolean isLocalPlayer,
            boolean clickable) {
        String label = spot.label();
        float chipW = Math.min(PF_CHIP_MAX_W, Math.max(BUSY_CHIP_W, textWidth(label, fontName, SMALL_FONT_SIZE) + 12));
        float chipX = rightEdge - chipW;
        float chipY = rowY + (ROW_HEIGHT - BUSY_CHIP_H) / 2f;
        Rect bounds = new Rect(chipX, chipY, chipW, BUSY_CHIP_H);

        boolean joinable = spot.isJoinable() && !isLocalPlayer && !presence().isInPartyFinderListing();
        boolean hovered = joinable && bounds.contains(uiMouseX, uiMouseY);
        Color background = !joinable
                ? color(CONTROL_INPUT)
                : hovered ? color(ACCENT_PRIMARY_HOVER, 220) : color(ACCENT_PRIMARY_DARK);
        canvas.fillRoundedRect(chipX, chipY, chipW, BUSY_CHIP_H, 3, background);
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                joinable ? color(TEXT_PRIMARY) : color(TEXT_MUTED),
                chipX + chipW / 2f,
                chipY + BUSY_CHIP_H / 2f,
                fitToWidth(hovered ? "Join " + spot.occupiedSlots() + "/" + spot.maxSize() : label, fontName, SMALL_FONT_SIZE, chipW - 4),
                UiCanvas.HorizontalAlign.CENTER);
        if (joinable && clickable) {
            partyFinderHitboxes.add(new PartyFinderHitbox(bounds, spot));
        }
    }

    /** The player's head, with availability as a dot on its corner. */
    private void renderHead(UiCanvas canvas, float x, float y, String uuid, boolean busy, boolean online) {
        renderHead(canvas, x, y, uuid, busy, online, 1f);
    }

    private void renderHead(
            UiCanvas canvas, float x, float y, String uuid, boolean busy, boolean online, float alpha) {
        UiImage head = PlayerHeadCache.headFor(uuid);
        if (head != null) {
            canvas.drawImage(head, x, y, HEAD_SIZE, HEAD_SIZE, alpha);
        } else {
            // Still loading, or the player has no UUID on the roster.
            canvas.fillRoundedRect(x, y, HEAD_SIZE, HEAD_SIZE, 2, color(CONTROL_INPUT));
        }

        float dotX = x + HEAD_SIZE - 1f;
        float dotY = y + HEAD_SIZE - 1f;
        // A ring in the row colour separates the dot from the skin.
        canvas.fillCircle(dotX, dotY, STATUS_DOT_RADIUS + 1.5f, color(BACKGROUND_BODY_OPAQUE));
        Color dot = !online ? color(ACCENT_DISABLED) : busy ? color(CONTROL_DANGER) : color(CONTROL_SUCCESS);
        canvas.fillCircle(dotX, dotY, STATUS_DOT_RADIUS, dot);
    }

    /** The builds this member declared. A member with no profile reads as unanswered. */
    private void renderEvidence(
            UiCanvas canvas,
            String fontName,
            float x,
            float rowY,
            GuildMemberPresence member,
            RaidTeamProfile profile,
            float maxWidth) {

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
        renderBuildChips(canvas, fontName, x, rowY, shown, maxWidth);
    }

    private void renderBuildChips(
            UiCanvas canvas, String fontName, float x, float rowY, Set<String> builds, float maxWidth) {
        float chipY = rowY + (ROW_HEIGHT - CHIP_H) / 2f;
        float cursorX = x;
        int drawn = 0;
        RaidCatalog catalog = catalog();

        for (String buildKey : catalog.orderKeys(builds)) {
            String nextLabel = catalog.labelFor(buildKey);
            boolean outOfRoom = cursorX + textWidth(nextLabel, fontName, TINY_FONT_SIZE) + 12 > x + maxWidth;
            if (drawn == MAX_VISIBLE_CHIPS || (drawn > 0 && outOfRoom)) {
                drawText(
                        canvas,
                        fontName,
                        SMALL_FONT_SIZE,
                        color(TEXT_MUTED),
                        cursorX,
                        rowY + ROW_HEIGHT / 2f,
                        "+" + (builds.size() - drawn),
                        UiCanvas.HorizontalAlign.LEFT);
                return;
            }
            String label = nextLabel;
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
                + MODAL_ROW_H * (MODAL_FACT_ROWS + Math.max(1, catalog().raids().size()))
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

        // Wynncraft's numbers, already in the roster payload. Two to a line, so the
        // note still fits on screen.
        GuildMemberStats stats = member.stats();
        RaidPerformance raids = stats.raidPerformance();
        String lastLogin = KnownGuildMember.formatElapsedShort(presence().lastLogin(member), Instant.now());

        cursorY = modalPairRow(
                canvas,
                fontName,
                contentX,
                cursorY,
                contentWidth,
                "Rank",
                member.rank().displayName(),
                "In guild since",
                stats.joinedGuildLabel());
        cursorY = modalPairRow(
                canvas,
                fontName,
                contentX,
                cursorY,
                contentWidth,
                "World",
                member.hasWorld() ? member.world() : "not reported",
                "Online for",
                lastLogin == null ? "?" : lastLogin);
        cursorY = modalPairRow(
                canvas,
                fontName,
                contentX,
                cursorY,
                contentWidth,
                "Playtime",
                stats.playtimeLabel(),
                "Total level",
                stats.totalLevelLabel());
        cursorY = modalPairRow(
                canvas,
                fontName,
                contentX,
                cursorY,
                contentWidth,
                "Wars",
                stats.warsLabel(),
                "Guild XP",
                stats.contributedXp() > 0L
                        ? stats.contributedXpLabel() + ", " + stats.contributionRankLabel()
                        : "?");
        cursorY = modalPairRow(
                canvas,
                fontName,
                contentX,
                cursorY,
                contentWidth,
                "Raid damage",
                raids.isKnown() ? GuildMemberStats.formatCompact(raids.damageDealt()) : "?",
                "Raid healing",
                raids.isKnown() ? GuildMemberStats.formatCompact(raids.healthHealed()) : "?");
        cursorY = modalPairRow(
                canvas,
                fontName,
                contentX,
                cursorY,
                contentWidth,
                "Raid deaths",
                raids.isKnown() ? GuildMemberStats.formatCount(raids.deaths()) : "?",
                "Gambits used",
                raids.isKnown() ? GuildMemberStats.formatCount(raids.gambitsUsed()) : "?");
        // Declared, not measured. Auras is something you bring, not a build.
        cursorY = modalPairRow(
                canvas,
                fontName,
                contentX,
                cursorY,
                contentWidth,
                "Region",
                profile.region() == null ? "unknown" : profile.region().name(),
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

    /** Two label and value pairs side by side. */
    private float modalPairRow(
            UiCanvas canvas,
            String fontName,
            float x,
            float y,
            float width,
            String leftLabel,
            String leftValue,
            String rightLabel,
            String rightValue) {
        float half = (width - MODAL_PAIR_GAP) / 2f;
        modalRow(canvas, fontName, x, y, half, leftLabel, leftValue);
        modalRow(canvas, fontName, x + half + MODAL_PAIR_GAP, y, half, rightLabel, rightValue);
        return y + MODAL_ROW_H;
    }

    private float modalRow(
            UiCanvas canvas, String fontName, float x, float y, float width, String label, String value) {
        float centerY = y + MODAL_ROW_H / 2f;
        drawText(canvas, fontName, SMALL_FONT_SIZE, color(TEXT_MUTED), x, centerY, label, UiCanvas.HorizontalAlign.LEFT);
        float room = width - textWidth(label, fontName, SMALL_FONT_SIZE) - 8;
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                color(TEXT_SECONDARY),
                x + width,
                centerY,
                fitToWidth(value, fontName, SMALL_FONT_SIZE, room),
                UiCanvas.HorizontalAlign.RIGHT);
        return y + MODAL_ROW_H;
    }

    /** Whether a row sits inside the scissored viewport, and so is really on screen. */
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

    /** Remaining busy time as {@code m:ss}. */
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
        if (selectedPremade != null) {
            return handlePremadeModalClick(mx, my);
        }

        for (TabHitbox hitbox : tabHitboxes) {
            if (hitbox.bounds().contains(mx, my)) {
                tab = hitbox.tab();
                scrollOffset = 0;
                searchFocused = false;
                return true;
            }
        }
        // Party finder chips come first: a wide one reaches past the actions zone.
        if (handleFriendClick(mx, my)
                || handlePremadeClick(mx, my)
                || handlePartyFinderClick(mx, my)) {
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
            // A skip decision is left as it was, or Cancel would bring setup back.
            SeqClient.mc.setScreen(new RaidProfileSetupScreen(parent, false));
            return true;
        }
        if (tab != Tab.MEMBERS) {
            // The filter bar only exists here, and its last bounds must not stay live.
            return handleActionClick(click, outsideScreen, mx, my);
        }
        for (SortHitbox hitbox : sortHitboxes) {
            if (!hitbox.bounds().contains(mx, my)) {
                continue;
            }
            // The column in force flips; a new one starts on its "best first" direction.
            if (sort == hitbox.key()) {
                sortDescending = !sortDescending;
            } else {
                sort = hitbox.key();
                sortDescending = hitbox.key().descendingByDefault();
            }
            scrollOffset = 0;
            searchFocused = false;
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
        return handleActionClick(click, outsideScreen, mx, my);
    }

    private boolean handleActionClick(MouseButtonEvent click, boolean outsideScreen, float mx, float my) {
        searchFocused = false;

        for (ActionHitbox hitbox : actionHitboxes) {
            if (!hitbox.bounds().contains(mx, my)) {
                continue;
            }
            switch (hitbox.type()) {
                case SWITCH -> {
                    // Closing puts the loading screen up; a banner behind it is never read.
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
            switch (hitbox.action()) {
                case INVITE -> showStatusBanner(invitePremade(hitbox.party()));
                case EDIT -> SeqClient.mc.setScreen(new PremadePartyEditorScreen(this, hitbox.party()));
                case OPEN -> selectedPremade = hitbox.party();
            }
            return true;
        }
        return false;
    }

    private boolean handlePremadeModalClick(float mx, float my) {
        if (modalCloseBounds != null && modalCloseBounds.contains(mx, my)) {
            selectedPremade = null;
            return true;
        }
        if (premadeInviteBounds != null && premadeInviteBounds.contains(mx, my)) {
            if (premadeInviteEnabled) {
                showStatusBanner(invitePremade(selectedPremade));
            }
            return true;
        }
        if (premadeEditBounds != null && premadeEditBounds.contains(mx, my)) {
            PremadeParty party = selectedPremade;
            // Closed first: the editor may rename or delete the party we would reopen.
            selectedPremade = null;
            SeqClient.mc.setScreen(new PremadePartyEditorScreen(this, party));
            return true;
        }
        if (modalBounds != null && !modalBounds.contains(mx, my)) {
            selectedPremade = null;
        }
        return true;
    }

    private boolean handlePartyFinderClick(float mx, float my) {
        for (PartyFinderHitbox hitbox : partyFinderHitboxes) {
            if (!hitbox.bounds().contains(mx, my)) {
                continue;
            }
            List<String> raids = hitbox.spot().raidShortNames();
            showStatusBanner(raids.isEmpty()
                    ? "Joining their party as DPS"
                    : "Joining their " + String.join("/", raids) + " party as DPS");
            presence().joinPartyFinder(hitbox.spot())
                    .thenAccept(message -> SeqClient.mc.execute(() -> showStatusBanner(message)));
            return true;
        }
        return false;
    }

    /** Invites the member, naming the filtered raid so the confirmation says what for. */
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

    /** The note is saved on close, which is what keeps it a one-line action. */
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
        if (selectedMember == null && selectedPremade == null && maxScroll > 0) {
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
        if (selectedPremade != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                selectedPremade = null;
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

    private enum PremadeAction {
        OPEN,
        INVITE,
        EDIT
    }

    private record PremadeHitbox(Rect bounds, PremadeAction action, PremadeParty party) {}

    private record PartyFinderHitbox(Rect bounds, PartyFinderSpot spot) {}

    private record SortHitbox(Rect bounds, MemberSort key) {}

}
