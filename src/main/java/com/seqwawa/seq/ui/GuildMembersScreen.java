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
 * The guild members panel: who is online, whether they are free, what they bring to
 * a raid, and an invite that opens a party finder listing to go with it.
 * <p>
 * A member with a raid profile shows the builds they declared; one without shows
 * how many times they have run that raid with the guild, which is weaker evidence
 * but needs nobody to opt in.
 */
public class GuildMembersScreen extends Screen {

    // ── Layout ──
    private static final float SIDEBAR_WIDTH = SequoiaSidebarNavigation.WIDTH;
    private static final float HEADER_HEIGHT = SequoiaUiStyle.HEADER_HEIGHT;
    private static final float HEADER_CONTROL_H = SequoiaUiStyle.HEADER_CONTROL_HEIGHT;
    private static final float HEADER_CONTROL_GAP = 6;
    private static final float TAB_STRIP_HEIGHT = 24;
    private static final float FILTER_BAR_HEIGHT = 30;
    private static final float COLUMN_HEADER_HEIGHT = 16;
    /** Kept between a column heading and the next column. */
    private static final float COLUMN_LABEL_GAP = 6;
    private static final float PADDING = SequoiaUiStyle.CONTENT_PADDING;
    private static final float ROW_HEIGHT = 26;
    private static final float ROW_SPACING = 2;
    private static final float SCROLLBAR_WIDTH = 4;
    private static final float SCROLL_SPEED = 14;

    // Column offsets from the content's left edge, so every row lines up.
    private static final float COL_HEAD = 8;
    private static final float COL_NAME = 30;
    private static final float COL_WORLD = 172;
    private static final float COL_ONLINE = 212;
    private static final float COL_STATUS = 252;
    private static final float COL_EVIDENCE = 340;

    private static final float HEAD_SIZE = 16;

    private static final float ACTION_BUTTON_W = 54;
    private static final float ACTION_BUTTON_H = 18;
    private static final float ACTION_BUTTON_GAP = 5;
    private static final float ACTIONS_ZONE_W = 70;
    private static final float REFRESH_BUTTON_W = 64;
    private static final float PROFILE_BUTTON_W = 76;

    private static final float BUSY_CHIP_W = 62;
    private static final float BUSY_CHIP_H = 15;
    /** A party finder chip may outgrow the busy one, but not enough to reach the builds. */
    private static final float PF_CHIP_MAX_W = 80;
    private static final float CHIP_H = 14;
    private static final float CHIP_GAP = 4;
    private static final int MAX_VISIBLE_CHIPS = 3;

    private static final float FILTER_CHIP_H = 18;
    private static final float RAID_DROPDOWN_W = 96;
    /** Marks a member whose client is running the mod. */
    private static final String MOD_TAG = "MOD";
    private static final String ALL_RAIDS = "All raids";
    private static final float FILTER_CHIP_GAP = 4;

    // Detail modal
    private static final float TAB_WIDTH = 92;
    private static final float FRIEND_BUTTON_W = 62;
    private static final float PREMADE_BUTTON_W = 72;
    private static final float NEW_PARTY_BUTTON_W = 88;

    private static final float MODAL_WIDTH = 580;
    /** The stat tiles need less room than the raid table and its build chips. */
    private static final float MODAL_LEFT_SHARE = 0.45f;
    /** Below this inner width the card's two columns stack. */
    private static final float MODAL_TWO_COLUMN_MIN = 480;
    private static final float MODAL_COLUMN_GAP = 18;
    private static final float MODAL_SECTION_GAP = 12;
    private static final float MODAL_HEADER_H = 46;
    private static final float MEMBER_HEAD_SIZE = 32;
    private static final float SECTION_TITLE_H = 18;
    private static final float TILE_H = 32;
    private static final float TILE_GAP = 4;
    private static final float INFO_LINE_H = 16;
    private static final float RAID_LINE_H = 17;
    private static final float RAID_NAME_W = 40;
    private static final float RAID_COUNT_W = 44;
    private static final float FRIEND_TOGGLE_W = 104;
    private static final float PREMADE_MODAL_WIDTH = 470;
    private static final float PREMADE_SEAT_H = 22;
    private static final float MODAL_PADDING = 18;
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
    /** The canvas height of the last frame, which the sidebar needs to hit test. */
    private float uiHeight;
    private int searchCursorBlink;
    private float scrollOffset;
    private float maxScroll;

    private Tab tab = Tab.MEMBERS;
    private MemberFilter filter = MemberFilter.none();
    /** Which column the list is ordered by, and which way round. */
    private MemberSort sort = MemberSort.NAME;
    private boolean sortDescending;
    private boolean raidDropdownOpen;
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
    private Rect raidDropdownBounds;
    private Rect aurasFilterBounds;
    private Rect freeFilterBounds;
    private Rect searchBounds;
    private Rect noteBounds;
    private Rect modalCloseBounds;
    private Rect modalBounds;
    /** The scissored list viewport, so a scrolled-away row cannot still be clicked. */
    private Rect listViewport;

    /** The roster as this frame sees it, read once and shared by header, counts and rows. */
    private List<GuildMemberPresence> onlineThisFrame = List.of();

    private List<GuildMemberPresence> visibleThisFrame = List.of();

    /** The party finder listing you are in, so your own party can be told from the others. */
    private PartyFinderSpot myPartyThisFrame;

    public GuildMembersScreen(Screen parent) {
        super(Component.literal(SequoiaSidebarNavigation.Destination.MEMBERS.label()));
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
        profiles().refreshIfStale();
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
        tabHitboxes.clear();
        friendHitboxes.clear();
        premadeHitboxes.clear();
        partyFinderHitboxes.clear();
        sortHitboxes.clear();

        UiRenderer.renderScreen(this, canvas -> {
            float screenWidth = canvas.metrics().width();
            float screenHeight = canvas.metrics().height();
            String fontName = SeqClient.getFontManager().getSelectedFont();

            uiHeight = screenHeight;
            SequoiaUiStyle.drawPanelFrame(canvas, HEADER_HEIGHT);
            SequoiaSidebarNavigation.render(
                    canvas, SequoiaSidebarNavigation.Destination.MEMBERS, uiMouseX, uiMouseY);

            float contentX = SIDEBAR_WIDTH + PADDING;
            float contentWidth = Math.max(0, screenWidth - contentX - PADDING);

            onlineThisFrame = presence().onlineMembers();
            visibleThisFrame = presence().membersForDisplay(onlineThisFrame, filter, sort, sortDescending);
            myPartyThisFrame = presence().myPartyFinderSpot();

            renderHeader(canvas, fontName, contentX, contentWidth);
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

            if (tab == Tab.MEMBERS) {
                renderRaidDropdown(canvas);
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

        float segmentY = y + (TAB_STRIP_HEIGHT - HEADER_CONTROL_H) / 2f;
        float cursorX = contentX;
        for (Tab candidate : Tab.values()) {
            Rect bounds = new Rect(cursorX, segmentY, TAB_WIDTH, HEADER_CONTROL_H);
            boolean selected = tab == candidate;
            boolean hovered = bounds.contains(uiMouseX, uiMouseY);
            canvas.fillRect(
                    bounds.x(),
                    bounds.y(),
                    bounds.width(),
                    bounds.height(),
                    selected
                            ? color(ACCENT_PRIMARY_DARK)
                            : hovered ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
            canvas.strokeRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 1, color(ACCENT_DIVIDER));
            drawText(
                    canvas,
                    fontName,
                    ROW_FONT_SIZE,
                    selected ? color(TEXT_PRIMARY) : color(TEXT_SECONDARY),
                    bounds.x() + bounds.width() / 2f,
                    bounds.y() + bounds.height() / 2f,
                    tabLabel(candidate),
                    UiCanvas.HorizontalAlign.CENTER);
            tabHitboxes.add(new TabHitbox(bounds, candidate));
            cursorX += TAB_WIDTH;
        }

        // Right-aligned under the title, in whatever room the switch leaves.
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                color(TEXT_MUTED),
                contentX + contentWidth - 8,
                y + TAB_STRIP_HEIGHT / 2f,
                fitToWidth(headerSummary(), fontName, SMALL_FONT_SIZE, contentX + contentWidth - 8 - cursorX - 12),
                UiCanvas.HorizontalAlign.RIGHT);
    }

    private String tabLabel(Tab candidate) {
        return switch (candidate) {
            case MEMBERS -> "All";
            case FRIENDS -> "Friends";
            case PREMADES -> {
                int count = profiles().premades().size();
                yield count == 0 ? "Premades" : "Premades " + count;
            }
        };
    }

    private void renderMembersTab(
            UiCanvas canvas, String fontName, float contentX, float contentY, float contentWidth, float contentHeight) {
        List<GuildMemberPresence> members = visibleThisFrame;
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
                    "Open someone from the All tab and press Add friend.",
                    UiCanvas.HorizontalAlign.CENTER);
            maxScroll = 0;
            return;
        }

        List<GuildMemberPresence> online = onlineThisFrame;
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
        renderHead(canvas, x + COL_HEAD, y + (ROW_HEIGHT - HEAD_SIZE) / 2f, headUuid, isOnline ? 1f : 0.45f);

        drawText(
                canvas,
                fontName,
                ROW_FONT_SIZE,
                isOnline ? color(TEXT_PRIMARY) : color(TEXT_DISABLED),
                x + COL_NAME,
                centerY,
                friend,
                UiCanvas.HorizontalAlign.LEFT);
        if (isOnline && busy) {
            drawText(
                    canvas,
                    fontName,
                    TINY_FONT_SIZE,
                    color(CONTROL_DANGER),
                    x + COL_NAME + textWidth(friend, fontName, ROW_FONT_SIZE) + 6,
                    centerY,
                    "BUSY",
                    UiCanvas.HorizontalAlign.LEFT);
        }

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
        renderButton(canvas, fontName, askBounds, "Raid ?", isOnline, false);
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

        List<GuildMemberPresence> online = onlineThisFrame;
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
        renderButton(canvas, fontName, inviteBounds, premadeInviteLabel(availability), free > 0, false);
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
    private void invitePremade(PremadeParty party) {
        PremadeAvailability availability = PremadeAvailability.of(
                party, presence().onlineMembers(), GuildRaidActivityTracker::isBusy, presence().localUsername());
        List<String> free = availability.freeToInvite();
        if (free.isEmpty()) {
            showStatusBanner("Nobody in " + party.name() + " is free right now.");
            return;
        }
        GuildPresenceManager.InviteOutcome outcome = presence().inviteAllToParty(free);
        String leftOut = availability.leftOutSummary();
        String line = leftOut == null ? outcome.message() : outcome.message() + " " + capitalize(leftOut) + ".";
        showStatusBanner(line);
        if (outcome.sent()) {
            linkPartyFinder(line, null);
        }
    }

    /**
     * Opens a party finder listing for the invite that just went out, for the filtered
     * raid when there is one, and adds what the party finder said to the banner.
     */
    private void linkPartyFinder(String inviteLine, RaidType raid) {
        presence().openPartyFinderListing(raid).thenAccept(result -> {
            if (result != null && !result.isBlank()) {
                SeqClient.mc.execute(() -> showStatusBanner(inviteLine + " " + result));
            }
        });
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
        PremadeAvailability availability =
                PremadeAvailability.of(party, onlineThisFrame, GuildRaidActivityTracker::isBusy, presence().localUsername());

        float height = MODAL_PADDING * 2
                + MODAL_HEAD_SIZE
                + 10
                + PREMADE_SEAT_H * availability.size()
                + 12
                + ACTION_BUTTON_H;
        float width = Math.min(PREMADE_MODAL_WIDTH, screenWidth - SIDEBAR_WIDTH - 24);
        float x = SIDEBAR_WIDTH + (screenWidth - SIDEBAR_WIDTH - width) / 2f;
        float y = Math.max(12, (screenHeight - height) / 2f);
        modalBounds = new Rect(x, y, width, height);

        canvas.fillRect(0, 0, screenWidth, screenHeight, color(BACKGROUND_MODAL_OVERLAY));
        canvas.fillRect(x, y, width, height, color(BACKGROUND_POPUP));
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
                false);
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
        renderHead(canvas, x, y + (PREMADE_SEAT_H - HEAD_SIZE) / 2f, uuid, offline ? 0.45f : 1f);

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

    /** Like {@link #fitToWidth}, but keeps the end of the text, for an input being typed into. */
    static String fitTail(String text, String fontName, float fontSize, float maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (textWidth(text, fontName, fontSize) <= maxWidth) {
            return text;
        }
        String trimmed = text;
        while (!trimmed.isEmpty() && textWidth("..." + trimmed, fontName, fontSize) > maxWidth) {
            trimmed = trimmed.substring(1);
        }
        return trimmed.isEmpty() ? "" : "..." + trimmed;
    }

    private void renderHeader(UiCanvas canvas, String fontName, float contentX, float contentWidth) {
        String title = SequoiaSidebarNavigation.Destination.MEMBERS.label();
        float rightEdge = contentX + contentWidth;
        float titleLeft = rightEdge - textWidth(title, fontName, TITLE_FONT_SIZE);
        drawText(
                canvas,
                fontName,
                TITLE_FONT_SIZE,
                color(ACCENT_PRIMARY_HOVER),
                rightEdge,
                HEADER_HEIGHT / 2f,
                title,
                UiCanvas.HorizontalAlign.RIGHT);

        float controlY = (HEADER_HEIGHT - HEADER_CONTROL_H) / 2f;
        float cursorX = contentX;
        float searchWidth = SequoiaUiStyle.searchWidth(titleLeft - cursorX - HEADER_CONTROL_GAP);
        renderSearchBox(canvas, fontName, cursorX, controlY, searchWidth);
        cursorX += searchWidth + HEADER_CONTROL_GAP;

        profileButtonBounds = new Rect(cursorX, controlY, PROFILE_BUTTON_W, HEADER_CONTROL_H);
        renderButton(canvas, fontName, profileButtonBounds, "My profile", true, false);
        cursorX += PROFILE_BUTTON_W + HEADER_CONTROL_GAP;

        boolean refreshing = presence().isRefreshing();
        boolean canRefresh = presence().canRefresh(System.currentTimeMillis());
        refreshButtonBounds = new Rect(cursorX, controlY, REFRESH_BUTTON_W, HEADER_CONTROL_H);
        renderButton(
                canvas,
                fontName,
                refreshButtonBounds,
                refreshing ? "Refreshing" : "Refresh",
                !refreshing && canRefresh,
                true);
    }

    /** The search field, drawn and behaving the way Party Finder's does. */
    private void renderSearchBox(UiCanvas canvas, String fontName, float x, float y, float width) {
        if (width <= 0) {
            searchBounds = null;
            return;
        }
        searchBounds = new Rect(x, y, width, HEADER_CONTROL_H);
        canvas.fillRect(x, y, width, HEADER_CONTROL_H, searchFocused ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
        if (searchFocused) {
            canvas.strokeRect(x, y, width, HEADER_CONTROL_H, 1, color(CONTROL_BORDER));
        }

        canvas.save();
        canvas.scissor(x, y, width, HEADER_CONTROL_H);
        boolean empty = searchInput.isEmpty();
        // The end of what is being typed is the part to keep in view.
        String shown = empty ? "Search..." : fitTail(searchInput, fontName, SMALL_FONT_SIZE, width - 14);
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                empty ? color(TEXT_DISABLED) : color(TEXT_PRIMARY),
                x + 6,
                y + HEADER_CONTROL_H / 2f,
                shown,
                UiCanvas.HorizontalAlign.LEFT);
        canvas.restore();

        searchCursorBlink++;
        if (searchFocused && (searchCursorBlink / 20) % 2 == 0) {
            float typed = empty ? 0 : textWidth(shown, fontName, SMALL_FONT_SIZE);
            canvas.fillRect(
                    Math.min(x + 6 + typed + 1, x + width - 1), y + 3, 1, HEADER_CONTROL_H - 6, color(TEXT_PRIMARY));
        }
    }

    private String headerSummary() {
        String error = presence().lastError();
        if (error != null && !error.isBlank()) {
            return error;
        }
        List<GuildMemberPresence> all = onlineThisFrame;
        if (all.isEmpty()) {
            return presence().hasLoaded() ? "nobody online" : "loading";
        }
        int shown = visibleThisFrame.size();
        long busy = all.stream()
                .filter(member -> GuildRaidActivityTracker.isBusy(member.username()))
                .count();
        String base = filter.isActive() ? shown + " of " + all.size() + " online" : all.size() + " online";
        if (busy > 0) {
            base += ", " + busy + " busy";
        }
        // Your own listing is named here too, since its members may not all be on the roster.
        return myPartyThisFrame == null ? base : base + ", in " + myPartyThisFrame.label();
    }

    private void renderFilterBar(
            UiCanvas canvas, String fontName, float contentX, float contentWidth, float barY) {
        canvas.fillRect(contentX, barY, contentWidth, FILTER_BAR_HEIGHT, color(BACKGROUND_CONTENT));

        float chipY = barY + (FILTER_BAR_HEIGHT - FILTER_CHIP_H) / 2f;
        float cursorX = contentX + 8;

        RaidType filtered = filteredRaid();
        raidDropdownBounds = new Rect(cursorX, chipY, RAID_DROPDOWN_W, FILTER_CHIP_H);
        DropdownMenu.trigger(
                canvas,
                raidDropdownBounds.x(),
                raidDropdownBounds.y(),
                raidDropdownBounds.width(),
                raidDropdownBounds.height(),
                filtered == null ? ALL_RAIDS : filtered.shortName(),
                raidDropdownOpen,
                true,
                uiMouseX,
                uiMouseY);
        cursorX += RAID_DROPDOWN_W + 8;
        aurasFilterBounds = new Rect(cursorX, chipY, 52, FILTER_CHIP_H);
        renderToggleChip(canvas, fontName, aurasFilterBounds, "Auras", filter.aurasOnly());
        cursorX += 52 + FILTER_CHIP_GAP;

        freeFilterBounds = new Rect(cursorX, chipY, 46, FILTER_CHIP_H);
        renderToggleChip(canvas, fontName, freeFilterBounds, "Free", filter.availableOnly());
    }

    /** Every raid the dropdown offers, with "All raids" first to clear the filter. */
    private List<String> raidDropdownLabels() {
        List<String> labels = new ArrayList<>();
        labels.add(ALL_RAIDS);
        catalog().raids().forEach(raid -> labels.add(raid.shortName()));
        return labels;
    }

    /** Drawn last in the frame, so the open list covers the rows under it. */
    private void renderRaidDropdown(UiCanvas canvas) {
        if (!raidDropdownOpen || raidDropdownBounds == null) {
            return;
        }
        List<String> labels = raidDropdownLabels();
        RaidType filtered = filteredRaid();
        String selected = filtered == null ? ALL_RAIDS : filtered.shortName();
        DropdownMenu.list(
                canvas,
                raidDropdownBounds.x(),
                raidDropdownBounds.y() + raidDropdownBounds.height(),
                raidDropdownBounds.width(),
                DropdownMenu.ROW_HEIGHT,
                labels,
                index -> labels.get(index).equals(selected),
                0,
                labels.size(),
                uiMouseX,
                uiMouseY);
    }

    /** The raid a click in the open list landed on, or null when it missed. */
    private Integer raidDropdownRowAt(float mx, float my) {
        if (!raidDropdownOpen || raidDropdownBounds == null) {
            return null;
        }
        float listY = raidDropdownBounds.y() + raidDropdownBounds.height();
        int rows = raidDropdownLabels().size();
        if (mx < raidDropdownBounds.x() || mx > raidDropdownBounds.x() + raidDropdownBounds.width()) {
            return null;
        }
        if (my < listY || my >= listY + rows * DropdownMenu.ROW_HEIGHT) {
            return null;
        }
        return (int) ((my - listY) / DropdownMenu.ROW_HEIGHT);
    }

    private void renderToggleChip(UiCanvas canvas, String fontName, Rect bounds, String label, boolean active) {
        boolean hovered = bounds.contains(uiMouseX, uiMouseY);
        canvas.fillRect(
                bounds.x(),
                bounds.y(),
                bounds.width(),
                bounds.height(),
                active ? color(CONTROL_SUCCESS) : hovered ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
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
        // The three comparable ones are buttons: clicking one orders the list by it.
        sortLabel(canvas, fontName, contentX + COL_NAME, y, "MEMBER", COL_WORLD - COL_NAME, MemberSort.NAME);
        sortLabel(canvas, fontName, contentX + COL_WORLD, y, "WORLD", COL_ONLINE - COL_WORLD, MemberSort.WORLD);
        sortLabel(
                canvas,
                fontName,
                contentX + COL_ONLINE,
                y,
                "ONLINE",
                COL_STATUS - COL_ONLINE,
                MemberSort.ONLINE_SINCE);
        drawLabel(canvas, fontName, contentX + COL_STATUS, centerY, "STATUS", COL_EVIDENCE - COL_STATUS);
        drawLabel(
                canvas,
                fontName,
                contentX + COL_EVIDENCE,
                centerY,
                filter.hasRaid() ? "READY FOR " + shortRaidName() : "BUILDS",
                contentWidth - ACTIONS_ZONE_W - COL_EVIDENCE);
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

        renderHead(canvas, x + COL_HEAD, y + (ROW_HEIGHT - HEAD_SIZE) / 2f, member.uuid(), 1f);

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
        if (member.sequoiaConnected() && afterName + textWidth(MOD_TAG, fontName, TINY_FONT_SIZE) <= tagLimit) {
            drawText(
                    canvas,
                    fontName,
                    TINY_FONT_SIZE,
                    color(ACCENT_PRIMARY),
                    afterName,
                    centerY,
                    MOD_TAG,
                    UiCanvas.HorizontalAlign.LEFT);
            afterName += textWidth(MOD_TAG, fontName, TINY_FONT_SIZE) + 6;
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

        renderEvidence(
                canvas, fontName, x + COL_EVIDENCE, y, member, profile, width - ACTIONS_ZONE_W - COL_EVIDENCE);

        // A listing says more than the busy timer, so it takes the column when there is one.
        float statusX = x + COL_STATUS;
        PartyFinderSpot spot = presence().partyFinderSpotFor(member);
        if (spot != null) {
            renderPartyFinderChip(canvas, fontName, statusX, y, spot, isLocalPlayer, clickable);
        } else if (busy) {
            renderStatusBadge(
                    canvas,
                    fontName,
                    statusX,
                    y,
                    BUSY_CHIP_W,
                    "Busy " + formatCountdown(busyRemainingMs),
                    color(STATUS_DANGER_BACKGROUND),
                    color(STATUS_DANGER_BORDER));
        } else {
            renderStatusBadge(
                    canvas,
                    fontName,
                    statusX,
                    y,
                    BUSY_CHIP_W,
                    "Free",
                    color(STATUS_SUCCESS_BACKGROUND),
                    color(STATUS_SUCCESS_BORDER));
        }

        // Pinned to the row's right edge. Greyed out on your own row rather than dropped,
        // so the column stays even.
        float buttonY = y + (ROW_HEIGHT - ACTION_BUTTON_H) / 2f;
        Rect inviteBounds = new Rect(x + width - 10 - ACTION_BUTTON_W, buttonY, ACTION_BUTTON_W, ACTION_BUTTON_H);
        renderButton(canvas, fontName, inviteBounds, "Invite", !isLocalPlayer, false);
        if (clickable && !isLocalPlayer) {
            actionHitboxes.add(new ActionHitbox(inviteBounds, ActionType.INVITE, member));
        }

        // The whole row opens the member's profile, minus the zone the buttons own.
        if (clickable) {
            Rect rowBounds = new Rect(x, y, width - ACTIONS_ZONE_W, ROW_HEIGHT);
            actionHitboxes.add(new ActionHitbox(rowBounds, ActionType.OPEN_PROFILE, member));
        }
    }

    /**
     * "PF TNA 2/4", starting at {@code chipX}, in the accent when it is the listing you
     * are in. It is a join button only when joining could work: open listing with room,
     * not you, and you are in none yourself.
     */
    private void renderPartyFinderChip(
            UiCanvas canvas,
            String fontName,
            float chipX,
            float rowY,
            PartyFinderSpot spot,
            boolean isLocalPlayer,
            boolean clickable) {
        String label = spot.label();
        float chipW = Math.min(PF_CHIP_MAX_W, Math.max(BUSY_CHIP_W, textWidth(label, fontName, SMALL_FONT_SIZE) + 12));
        float chipY = rowY + (ROW_HEIGHT - BUSY_CHIP_H) / 2f;
        Rect bounds = new Rect(chipX, chipY, chipW, BUSY_CHIP_H);

        boolean mine = myPartyThisFrame != null && myPartyThisFrame.listingId() == spot.listingId();
        boolean joinable = spot.isJoinable() && !isLocalPlayer && !presence().isInPartyFinderListing();
        boolean hovered = bounds.contains(uiMouseX, uiMouseY) && (joinable || mine);
        // Your own party wears the accent, so it stands out from the ones you could join.
        // Elsewhere a full listing reads like Busy, since nobody else is getting in, and one
        // with room stays orange. Hovering one you can join says what a click does.
        boolean full = spot.isFull();
        Color fill = mine
                ? color(ACCENT_PRIMARY)
                : full
                        ? color(STATUS_DANGER_BACKGROUND)
                        : hovered ? color(CONTROL_WARNING) : color(STATUS_WARNING_BACKGROUND);
        Color border = mine
                ? color(ACCENT_PRIMARY_HOVER)
                : full ? color(STATUS_DANGER_BORDER) : color(STATUS_WARNING_BORDER);
        String shown = label;
        if (mine && hovered) {
            shown = "Your party";
        } else if (hovered) {
            shown = "Join " + spot.occupiedSlots() + "/" + spot.maxSize();
        }
        renderStatusBadge(canvas, fontName, chipX, rowY, chipW, shown, fill, border);
        if (joinable && clickable) {
            partyFinderHitboxes.add(new PartyFinderHitbox(bounds, spot));
        }
    }

    /**
     * A status, drawn the way Party Finder draws its Open and Full badges: a tinted fill
     * inside a one pixel border. Buttons have no border, which is what keeps the two apart.
     */
    private void renderStatusBadge(
            UiCanvas canvas, String fontName, float x, float rowY, float width, String label, Color fill, Color border) {
        float badgeY = rowY + (ROW_HEIGHT - BUSY_CHIP_H) / 2f;
        canvas.fillRect(x, badgeY, width, BUSY_CHIP_H, fill);
        canvas.strokeRect(x, badgeY, width, BUSY_CHIP_H, 1, border);
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                color(TEXT_PRIMARY),
                x + width / 2f,
                badgeY + BUSY_CHIP_H / 2f,
                fitToWidth(label, fontName, SMALL_FONT_SIZE, width - 4),
                UiCanvas.HorizontalAlign.CENTER);
    }

    /** The player's head, faded with {@code alpha} for someone who is offline. */
    private void renderHead(UiCanvas canvas, float x, float y, String uuid, float alpha) {
        UiImage head = PlayerHeadCache.headFor(uuid);
        if (head != null) {
            canvas.drawImage(head, x, y, HEAD_SIZE, HEAD_SIZE, alpha);
        } else {
            // Still loading, or the player has no UUID on the roster.
            canvas.fillRect(x, y, HEAD_SIZE, HEAD_SIZE, color(CONTROL_INPUT));
        }
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
        renderChips(canvas, fontName, x, rowY + ROW_HEIGHT / 2f, shown, maxWidth, MAX_VISIBLE_CHIPS);
    }

    /** Build chips in catalog order, cut to {@code maxChips} or to the room, whichever comes first. */
    private void renderChips(
            UiCanvas canvas,
            String fontName,
            float x,
            float centerY,
            Set<String> builds,
            float maxWidth,
            int maxChips) {
        float chipY = centerY - CHIP_H / 2f;
        float cursorX = x;
        int drawn = 0;
        RaidCatalog catalog = catalog();

        for (String buildKey : catalog.orderKeys(builds)) {
            String nextLabel = catalog.labelFor(buildKey);
            boolean outOfRoom = cursorX + textWidth(nextLabel, fontName, TINY_FONT_SIZE) + 12 > x + maxWidth;
            if (drawn == maxChips || (drawn > 0 && outOfRoom)) {
                drawText(
                        canvas,
                        fontName,
                        SMALL_FONT_SIZE,
                        color(TEXT_MUTED),
                        cursorX,
                        centerY,
                        "+" + (builds.size() - drawn),
                        UiCanvas.HorizontalAlign.LEFT);
                return;
            }
            String label = nextLabel;
            float width = textWidth(label, fontName, TINY_FONT_SIZE) + 12;
            canvas.fillRect(cursorX, chipY, width, CHIP_H, color(ACCENT_PRIMARY_DARK));
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

    private record StatTile(String value, String label) {}

    private void renderDetailModal(UiCanvas canvas, String fontName, float screenWidth, float screenHeight) {
        GuildMemberPresence member = selectedMember;
        RaidTeamProfile profile = profiles().profileFor(member);
        List<RaidType> raids = catalog().raids();
        String localUsername = presence().localUsername();
        boolean self = localUsername != null && localUsername.equalsIgnoreCase(member.username());

        float panelWidth = screenWidth - SIDEBAR_WIDTH;
        float width = Math.min(MODAL_WIDTH, panelWidth - 24);
        float innerWidth = width - MODAL_PADDING * 2;
        // Two columns side by side when there is room, stacked on a narrow window.
        boolean twoColumns = innerWidth >= MODAL_TWO_COLUMN_MIN;
        float leftWidth = twoColumns ? (innerWidth - MODAL_COLUMN_GAP) * MODAL_LEFT_SHARE : innerWidth;
        float rightWidth = twoColumns ? innerWidth - MODAL_COLUMN_GAP - leftWidth : innerWidth;

        float statsHeight = (SECTION_TITLE_H + TILE_H) * 2 + MODAL_SECTION_GAP;
        float raidHeight = SECTION_TITLE_H
                + INFO_LINE_H * 2
                + MODAL_SECTION_GAP
                + SECTION_TITLE_H
                + RAID_LINE_H * Math.max(1, raids.size());
        float bodyHeight =
                twoColumns ? Math.max(statsHeight, raidHeight) : statsHeight + MODAL_SECTION_GAP + raidHeight;
        float height = MODAL_PADDING
                + MODAL_HEADER_H
                + MODAL_SECTION_GAP
                + bodyHeight
                + MODAL_SECTION_GAP
                + SECTION_TITLE_H
                + NOTE_INPUT_H
                + MODAL_PADDING;

        float x = SIDEBAR_WIDTH + (panelWidth - width) / 2f;
        float y = Math.max(12, (screenHeight - height) / 2f);
        modalBounds = new Rect(x, y, width, height);

        canvas.fillRect(0, 0, screenWidth, screenHeight, color(BACKGROUND_MODAL_OVERLAY));
        canvas.fillRect(x, y, width, height, color(BACKGROUND_POPUP));
        canvas.fillRect(x, y, 3, height, color(ACCENT_PRIMARY));

        float contentX = x + MODAL_PADDING;
        float cursorY = y + MODAL_PADDING;
        renderModalHeader(canvas, fontName, contentX, cursorY, innerWidth, member, profile);

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
        cursorY += MODAL_HEADER_H + MODAL_SECTION_GAP;

        float rightX = twoColumns ? contentX + leftWidth + MODAL_COLUMN_GAP : contentX;
        float rightY = twoColumns ? cursorY : cursorY + statsHeight + MODAL_SECTION_GAP;
        renderStatsColumn(canvas, fontName, contentX, cursorY, leftWidth, member.stats());
        renderRaidColumn(canvas, fontName, rightX, rightY, rightWidth, member, profile, raids);

        cursorY += bodyHeight + MODAL_SECTION_GAP;
        renderNoteRow(canvas, fontName, contentX, cursorY, innerWidth, member, self);
    }

    /** Head, name, and one quiet line saying who they are in the guild and where. */
    private void renderModalHeader(
            UiCanvas canvas,
            String fontName,
            float x,
            float y,
            float width,
            GuildMemberPresence member,
            RaidTeamProfile profile) {
        UiImage head = PlayerHeadCache.headFor(member.uuid());
        if (head != null) {
            canvas.drawImage(head, x, y, MEMBER_HEAD_SIZE, MEMBER_HEAD_SIZE, 1f);
        } else {
            canvas.fillRect(x, y, MEMBER_HEAD_SIZE, MEMBER_HEAD_SIZE, color(CONTROL_INPUT));
        }

        float textX = x + MEMBER_HEAD_SIZE + 12;
        // Room is kept on the right for the close cross.
        float textRoom = width - MEMBER_HEAD_SIZE - 12 - 24;
        drawText(
                canvas,
                fontName,
                TITLE_FONT_SIZE,
                color(TEXT_PRIMARY),
                textX,
                y + 8,
                member.username(),
                UiCanvas.HorizontalAlign.LEFT);

        List<String> facts = new ArrayList<>();
        facts.add(member.rank().displayName());
        facts.add(member.hasWorld() ? member.world() : "world hidden");
        String onlineFor = KnownGuildMember.formatElapsedShort(presence().lastLogin(member), Instant.now());
        if (onlineFor != null) {
            facts.add("now".equals(onlineFor) ? "just logged in" : "online for " + onlineFor);
        }
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                color(TEXT_SECONDARY),
                textX,
                y + 26,
                fitToWidth(String.join("  ·  ", facts), fontName, SMALL_FONT_SIZE, textRoom),
                UiCanvas.HorizontalAlign.LEFT);

        if (profile.hasStatus()) {
            drawText(
                    canvas,
                    fontName,
                    SMALL_FONT_SIZE,
                    color(TEXT_MUTED),
                    textX,
                    y + 40,
                    fitToWidth("\"" + profile.status() + "\"", fontName, SMALL_FONT_SIZE, textRoom),
                    UiCanvas.HorizontalAlign.LEFT);
        }
    }

    /** What Wynncraft measures: the guild side, then how their raids have gone. */
    private void renderStatsColumn(
            UiCanvas canvas, String fontName, float x, float y, float width, GuildMemberStats stats) {
        renderSectionTitle(
                canvas,
                fontName,
                x,
                y,
                width,
                "GUILD",
                stats.joinedGuildAt() == null ? null : "member since " + stats.joinedGuildLabel());
        y += SECTION_TITLE_H;
        renderStatTiles(
                canvas,
                fontName,
                x,
                y,
                width,
                List.of(
                        new StatTile(stats.playtimeLabel(), "PLAYTIME"),
                        new StatTile(stats.totalLevelLabel(), "LEVEL"),
                        new StatTile(stats.warsLabel(), "WARS"),
                        new StatTile(
                                stats.contributedXpLabel(),
                                stats.contributionRank() > 0 ? "XP " + stats.contributionRankLabel() : "GUILD XP")));
        y += TILE_H + MODAL_SECTION_GAP;

        RaidPerformance record = stats.raidPerformance();
        boolean known = record.isKnown();
        renderSectionTitle(canvas, fontName, x, y, width, "RAID RECORD", "all raids, lifetime");
        y += SECTION_TITLE_H;
        renderStatTiles(
                canvas,
                fontName,
                x,
                y,
                width,
                List.of(
                        new StatTile(known ? GuildMemberStats.formatCompact(record.damageDealt()) : "?", "DAMAGE"),
                        new StatTile(known ? GuildMemberStats.formatCompact(record.healthHealed()) : "?", "HEALING"),
                        new StatTile(known ? GuildMemberStats.formatCount(record.deaths()) : "?", "DEATHS"),
                        new StatTile(known ? GuildMemberStats.formatCount(record.gambitsUsed()) : "?", "GAMBITS")));
    }

    /** What they declared, then their guild clears raid by raid with the builds they bring to each. */
    private void renderRaidColumn(
            UiCanvas canvas,
            String fontName,
            float x,
            float y,
            float width,
            GuildMemberPresence member,
            RaidTeamProfile profile,
            List<RaidType> raids) {
        renderSectionTitle(canvas, fontName, x, y, width, "RAID PROFILE", profile.isComplete() ? null : "not shared");
        y += SECTION_TITLE_H;
        renderInfoPair(canvas, fontName, x, y, "Region", profile.region() == null ? "?" : profile.region().name());
        renderInfoPair(
                canvas,
                fontName,
                x + width / 2f,
                y,
                "Auras",
                !profile.isComplete() ? "?" : profile.canBringAuras() ? "yes" : "no");
        y += INFO_LINE_H;
        if (profile.isComplete() && !profile.buildKeys().isEmpty()) {
            renderChips(canvas, fontName, x, y + INFO_LINE_H / 2f, profile.buildKeys(), width, Integer.MAX_VALUE);
        } else {
            drawText(
                    canvas,
                    fontName,
                    SMALL_FONT_SIZE,
                    color(TEXT_DISABLED),
                    x,
                    y + INFO_LINE_H / 2f,
                    profile.isComplete() ? "no meta build ticked" : "hasn't filled in a profile",
                    UiCanvas.HorizontalAlign.LEFT);
        }
        y += INFO_LINE_H + MODAL_SECTION_GAP;

        renderSectionTitle(canvas, fontName, x, y, width, "GUILD RAIDS", "clears in the guild");
        y += SECTION_TITLE_H;
        if (raids.isEmpty()) {
            drawText(
                    canvas,
                    fontName,
                    SMALL_FONT_SIZE,
                    color(TEXT_DISABLED),
                    x,
                    y + RAID_LINE_H / 2f,
                    "raid meta not loaded",
                    UiCanvas.HorizontalAlign.LEFT);
            return;
        }

        float chipsX = x + RAID_NAME_W + RAID_COUNT_W + 10;
        for (int index = 0; index < raids.size(); index++) {
            RaidType raid = raids.get(index);
            if (index % 2 == 0) {
                // Light striping keeps the eye on one raid across the row.
                canvas.fillRect(x - 4, y, width + 8, RAID_LINE_H, color(BACKGROUND_CONTENT));
            }
            float centerY = y + RAID_LINE_H / 2f;
            int clears = member.stats().completions(raid);
            drawText(
                    canvas,
                    fontName,
                    SMALL_FONT_SIZE,
                    color(TEXT_MUTED),
                    x,
                    centerY,
                    raid.shortName(),
                    UiCanvas.HorizontalAlign.LEFT);
            drawText(
                    canvas,
                    fontName,
                    SMALL_FONT_SIZE,
                    clears > 0 ? color(TEXT_PRIMARY) : color(TEXT_DISABLED),
                    x + RAID_NAME_W + RAID_COUNT_W,
                    centerY,
                    GuildMemberStats.formatCount(clears),
                    UiCanvas.HorizontalAlign.RIGHT);
            Set<String> matching = profile.isComplete() ? raid.matchingBuildKeys(profile.buildKeys()) : Set.of();
            renderChips(canvas, fontName, chipsX, centerY, matching, x + width - chipsX, Integer.MAX_VALUE);
            y += RAID_LINE_H;
        }
    }

    /** The private note, with the friend toggle beside it for anyone but yourself. */
    private void renderNoteRow(
            UiCanvas canvas, String fontName, float x, float y, float width, GuildMemberPresence member, boolean self) {
        renderSectionTitle(canvas, fontName, x, y, width, "YOUR NOTE", "only you see it");
        y += SECTION_TITLE_H;

        float inputWidth = self ? width : width - FRIEND_TOGGLE_W - 8;
        noteBounds = new Rect(x, y, inputWidth, NOTE_INPUT_H);
        canvas.fillRect(x, y, inputWidth, NOTE_INPUT_H, noteFocused ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
        if (noteFocused) {
            canvas.strokeRect(x, y, inputWidth, NOTE_INPUT_H, 1, color(ACCENT_PRIMARY));
        }
        boolean noteEmpty = noteInput.isEmpty();
        String shown;
        if (noteEmpty) {
            shown = "solid tna aco, knows the lineup";
        } else if (noteFocused) {
            // While typing, the end of the note is the part that matters.
            shown = fitTail(noteInput + "_", fontName, SMALL_FONT_SIZE, inputWidth - 14);
        } else {
            shown = fitToWidth(noteInput, fontName, SMALL_FONT_SIZE, inputWidth - 14);
        }
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                noteEmpty ? color(TEXT_DISABLED) : color(TEXT_PRIMARY),
                x + 7,
                y + NOTE_INPUT_H / 2f,
                shown,
                UiCanvas.HorizontalAlign.LEFT);

        if (self) {
            friendToggleBounds = null;
            return;
        }
        boolean isFriend = profiles().isFriend(member.username());
        friendToggleBounds = new Rect(x + width - FRIEND_TOGGLE_W, y, FRIEND_TOGGLE_W, NOTE_INPUT_H);
        renderButton(canvas, fontName, friendToggleBounds, isFriend ? "Remove friend" : "Add friend", true, !isFriend);
    }

    /** A small uppercase title over a thin rule, with an optional aside on the right. */
    private void renderSectionTitle(
            UiCanvas canvas, String fontName, float x, float y, float width, String title, String aside) {
        drawText(canvas, fontName, TINY_FONT_SIZE, color(ACCENT_PRIMARY), x, y + 6, title, UiCanvas.HorizontalAlign.LEFT);
        if (aside != null) {
            float room = width - textWidth(title, fontName, TINY_FONT_SIZE) - 12;
            drawText(
                    canvas,
                    fontName,
                    TINY_FONT_SIZE,
                    color(TEXT_MUTED),
                    x + width,
                    y + 6,
                    fitToWidth(aside, fontName, TINY_FONT_SIZE, room),
                    UiCanvas.HorizontalAlign.RIGHT);
        }
        canvas.fillRect(x, y + 12, width, 1, color(ACCENT_PRIMARY_DARK));
    }

    /** A row of equal tiles, each a number over its label. */
    private void renderStatTiles(
            UiCanvas canvas, String fontName, float x, float y, float width, List<StatTile> tiles) {
        float tileWidth = (width - TILE_GAP * (tiles.size() - 1)) / tiles.size();
        float cursorX = x;
        for (StatTile tile : tiles) {
            canvas.fillRect(cursorX, y, tileWidth, TILE_H, color(CONTROL_INPUT));
            boolean known = !"?".equals(tile.value());
            drawText(
                    canvas,
                    fontName,
                    ROW_FONT_SIZE,
                    known ? color(TEXT_PRIMARY) : color(TEXT_DISABLED),
                    cursorX + tileWidth / 2f,
                    y + 12,
                    fitToWidth(tile.value(), fontName, ROW_FONT_SIZE, tileWidth - 6),
                    UiCanvas.HorizontalAlign.CENTER);
            drawText(
                    canvas,
                    fontName,
                    TINY_FONT_SIZE,
                    color(TEXT_MUTED),
                    cursorX + tileWidth / 2f,
                    y + 25,
                    fitToWidth(tile.label(), fontName, TINY_FONT_SIZE, tileWidth - 6),
                    UiCanvas.HorizontalAlign.CENTER);
            cursorX += tileWidth + TILE_GAP;
        }
    }

    private void renderInfoPair(UiCanvas canvas, String fontName, float x, float y, String label, String value) {
        float centerY = y + INFO_LINE_H / 2f;
        drawText(canvas, fontName, SMALL_FONT_SIZE, color(TEXT_MUTED), x, centerY, label, UiCanvas.HorizontalAlign.LEFT);
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                color(TEXT_PRIMARY),
                x + textWidth(label, fontName, SMALL_FONT_SIZE) + 8,
                centerY,
                value,
                UiCanvas.HorizontalAlign.LEFT);
    }

    /** The filtered raid's abbreviation, or a placeholder while the meta loads. */
    private String shortRaidName() {
        RaidType raid = filteredRaid();
        return raid == null ? String.valueOf(filter.raidKey()) : raid.shortName();
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
            background = color(ACCENT_DISABLED);
        } else if (accent) {
            background = hovered ? color(CONTROL_SUCCESS) : color(STATUS_SUCCESS_BACKGROUND);
        } else {
            background = hovered ? color(ACCENT_PRIMARY_HOVER) : color(ACCENT_PRIMARY);
        }
        canvas.fillRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), background);
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

        // As wide as the line needs, but never past the panel: an invite line with the
        // party finder's reply tacked on can outgrow a small window.
        float panelWidth = screenWidth - SIDEBAR_WIDTH - 16;
        float width = Math.min(
                panelWidth, Math.max(STATUS_BANNER_MIN_W, textWidth(statusBannerMessage, fontName, ROW_FONT_SIZE) + 32));
        // Centred on the panel, like every other overlay beside the sidebar.
        float x = SIDEBAR_WIDTH + (screenWidth - SIDEBAR_WIDTH - width) / 2f;
        float y = screenHeight - STATUS_BANNER_H - 16;
        canvas.fillRect(x, y, width, STATUS_BANNER_H, color(BACKGROUND_POPUP));
        canvas.fillRect(x, y, 2, STATUS_BANNER_H, color(ACCENT_PRIMARY));
        drawText(
                canvas,
                fontName,
                ROW_FONT_SIZE,
                color(TEXT_SECONDARY),
                x + width / 2f,
                y + STATUS_BANNER_H / 2f,
                fitToWidth(statusBannerMessage, fontName, ROW_FONT_SIZE, width - 16),
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
        if (mx < SIDEBAR_WIDTH) {
            return SequoiaSidebarNavigation.click(
                    mx, my, uiHeight, SequoiaSidebarNavigation.Destination.MEMBERS, parent);
        }
        // The open list is drawn over the column headings and the first rows, so it
        // answers first; otherwise a click on "All raids" would sort by name instead.
        if (raidDropdownOpen) {
            Integer row = raidDropdownRowAt(mx, my);
            if (row != null) {
                // Row zero is "All raids", the rest follow the catalog.
                filter = filter.withRaid(row == 0 ? null : catalog().raids().get(row - 1));
                scrollOffset = 0;
            }
            // A click anywhere else closes the list rather than acting through it.
            raidDropdownOpen = false;
            searchFocused = false;
            return true;
        }

        for (TabHitbox hitbox : tabHitboxes) {
            if (hitbox.bounds().contains(mx, my)) {
                tab = hitbox.tab();
                scrollOffset = 0;
                searchFocused = false;
                return true;
            }
        }
        // Party finder chips come first: they sit inside the row, which opens the card.
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
        if (raidDropdownBounds != null && raidDropdownBounds.contains(mx, my)) {
            raidDropdownOpen = true;
            searchFocused = false;
            return true;
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
                case INVITE -> {
                    GuildPresenceManager.InviteOutcome outcome = presence().inviteToParty(hitbox.member().username());
                    String line = inviteLine(outcome, hitbox.member());
                    showStatusBanner(line);
                    if (outcome.sent()) {
                        linkPartyFinder(line, filteredRaid());
                    }
                }
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
                case INVITE -> {
                    GuildPresenceManager.InviteOutcome outcome = presence().inviteToParty(hitbox.friend());
                    showStatusBanner(outcome.message());
                    if (outcome.sent()) {
                        linkPartyFinder(outcome.message(), null);
                    }
                }
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
                case INVITE -> invitePremade(hitbox.party());
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
                invitePremade(selectedPremade);
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

    /** The confirmation for an invite, naming the filtered raid and the builds they bring to it. */
    private String inviteLine(GuildPresenceManager.InviteOutcome outcome, GuildMemberPresence member) {
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
        if (raidDropdownOpen && key == GLFW.GLFW_KEY_ESCAPE) {
            // Escape closes the open list first, not the whole panel.
            raidDropdownOpen = false;
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

    /**
     * Runs whenever this screen is replaced, however it happened: a server window, a
     * command, a disconnect. A note being typed is saved rather than lost with it.
     */
    @Override
    public void removed() {
        closeProfile();
        super.removed();
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
        INVITE,
        OPEN_PROFILE
    }

    private record ActionHitbox(Rect bounds, ActionType type, GuildMemberPresence member) {}

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
