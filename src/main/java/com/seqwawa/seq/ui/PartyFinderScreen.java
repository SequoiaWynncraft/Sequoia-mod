package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import java.awt.Color;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

import com.seqwawa.seq.model.Listing;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import com.seqwawa.seq.accessors.PartyAccessor;
import com.seqwawa.seq.client.Clocks;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.AssetManager;
import com.seqwawa.seq.managers.PartyListing;
import com.seqwawa.seq.managers.PartyMember;
import com.seqwawa.seq.model.Activity;
import com.seqwawa.seq.model.PartyJoinPolicy;
import com.seqwawa.seq.model.PartyRegion;
import com.seqwawa.seq.model.PartyStatus;
import com.seqwawa.seq.utils.TextInputFilters;
import com.seqwawa.seq.utils.TextInputHelper;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;

public class PartyFinderScreen extends SequoiaPanelScreen implements PartyAccessor {

    // ── Raid types ──
    private static final String[] RAID_TYPES = {
        "Nest of the Grootslangs",
        "Nexus of Light",
        "The Canyon Colossus",
        "The Nameless Anomaly",
        "The Wartorn Palace",
        "Prelude to Annihilation",
    };
    private static final PartyRegion[] PARTY_REGIONS = {
        PartyRegion.NA, PartyRegion.EU, PartyRegion.AS,
    };

    // ── Layout ──
    private static final float SIDEBAR_WIDTH = SequoiaSidebarNavigation.WIDTH;
    private static final float HEADER_HEIGHT = SequoiaUiStyle.HEADER_HEIGHT;
    private static final float PADDING = 8;
    private static final float SEARCH_BAR_HEIGHT = SequoiaUiStyle.HEADER_CONTROL_HEIGHT;
    private static final float SEARCH_BAR_MARGIN = 8;
    private static final float HEADER_BUTTON_SPACING = 6;
    private static final float HEADER_BUTTON_HORIZONTAL_PADDING = 8;
    private static final float HEADER_ROLE_DROPDOWN_MIN_W = 80;
    private static final float HEADER_ROLE_DROPDOWN_TRAILING_SPACE = 24;
    private static final float SCROLL_SPEED = 12;
    private static final long LOADING_NAME_REFRESH_MS = 1500L;

    // Party card layout
    private static final float CARD_PADDING = 10;
    private static final float CARD_SPACING = 6;
    private static final float CARD_HEADER_HEIGHT = 52;
    private static final float MEMBER_ROW_HEIGHT = 26;
    private static final float COLLAPSED_ROW_HEIGHT = 56;
    private static final float CLASS_ICON_SIZE = 14;
    private static final float STAR_ICON_SIZE = 16;
    private static final float TYPE_ICON_SIZE = 48;
    private static final float BUTTON_HEIGHT = 24;
    private static final float JOIN_BUTTON_WIDTH = 64;
    private static final float STATUS_BADGE_H = 18;
    private static final float STATUS_BADGE_W = 58;

    // Modal layout
    private static final float MODAL_WIDTH = 300;
    private static final float MODAL_HEIGHT = 200;
    private static final float PARTY_MODAL_HEIGHT = 294;
    private static final float RAID_CIRCLE_SIZE = 36;
    private static final float RAID_CIRCLE_SPACING = 12;
    private static final float MODAL_DROPDOWN_W = 80;
    private static final float MODAL_DROPDOWN_H = 20;
    private static final float MODAL_BUTTON_W = 80;
    private static final float MODAL_BUTTON_H = 24;
    private static final float MODAL_BUTTON_HORIZONTAL_PADDING = 10;
    private static final float REGION_BUTTON_W = 40;
    private static final float REGION_BUTTON_SPACING = 8;
    private static final float JOIN_POLICY_BUTTON_W = 88;
    private static final float JOIN_POLICY_BUTTON_SPACING = 8;

    // Tag selector/filter overlay layout
    private static final float TAG_OVERLAY_WIDTH = 260;
    private static final float TAG_OVERLAY_HEIGHT = 220;
    private static final float TAG_BOX_HEIGHT = 66;

    // Filter button
    private static final float FILTER_BUTTON_W = 70;
    private static final float FILTER_BUTTON_H = 24;
    private static final float FILTER_BUTTON_MARGIN = 12;
    private static final float STATUS_BANNER_MIN_W = 280;
    private static final float STATUS_BANNER_H = 26;
    private static final long STATUS_BANNER_DURATION_MS = 3500L;

    // Leader member-management buttons
    private static final float LEADER_ACTION_BUTTON_HEIGHT = 20;
    private static final float LEADER_ACTION_BUTTON_SPACING = 4;
    private static final float LEADER_ACTION_BUTTON_HORIZONTAL_PADDING = 8;

    // ── Font sizes ──
    private static final float TITLE_FONT_SIZE = 18;
    private static final float HEADER_BUTTON_SIZE = 12;
    private static final float CARD_TITLE_SIZE = 16;
    private static final float MEMBER_FONT_SIZE = 14;
    private static final float SEARCH_FONT_SIZE = 12;
    private static final float ROLE_FONT_SIZE = 13;
    private static final float TYPE_FONT_SIZE = 14;
    private static final float RAID_LABEL_SIZE = 9;
    private static final float MODAL_TITLE_SIZE = 16;
    private static final float MODAL_LABEL_SIZE = 12;
    private static final float TAG_CHIP_FONT_SIZE = 11;

    private static final String GAZ_EARS_ASSET = "gaz_ears";
    private static final String GAZ_EARS_UUID = "66efb975-31b4-499e-9b46-a34980edd8ee";
    private static final String LEA_UUID = "7792daec-00d8-49ce-b44e-fe97c5ec4e75";
    private static final String NEXUS_OF_LIGHT = "Nexus of Light";
    private static final String NEXUS_OF_LEA = "Nexus of Lea";
    private static final String[] ROLES = {"DPS", "Healer", "Tank", "Other"};
    private static final PartyJoinPolicy[] JOIN_POLICIES = {PartyJoinPolicy.OPEN, PartyJoinPolicy.INVITE_ONLY};

    // ── State ──
    private final boolean openCreateModalOnInit;
    private final ScrollPane scroll = new ScrollPane();
    private final TextField search = new TextField();
    private final TimedBanner statusBanner = new TimedBanner(Clocks::monotonicMillis);

    private boolean roleDropdownOpen = false;
    private String selectedRole = null;
    /** Where the role dropdown was drawn, which its menu opens under. */
    private UiBounds roleDropdown = new UiBounds(0, 0, 0, 0);

    // ── Modal state ──
    private boolean modalOpen = false;
    private boolean inviteModalOpen = false;
    private TextField inviteUsername = new TextField();
    private final Set<String> modalSelectedRaids = new LinkedHashSet<>();
    private int modalReservedSlots = 0;
    private PartyRegion modalSelectedRegion = PartyRegion.NA;
    private PartyJoinPolicy modalJoinPolicy = PartyJoinPolicy.DEFAULT_CREATE_POLICY;
    private boolean reservedSlotsFocused = false;
    private String reservedSlotsInput = "0";
    private long nextLoadingNameRefreshAtMs = 0L;

    // ── Filter+ screen state ──
    private boolean filterScreenOpen = false;
    private final Set<String> activeFilterTags = new LinkedHashSet<>();
    private final Set<String> inactiveFilterTags = new LinkedHashSet<>();
    private final Map<String, Long> filterTagAnimStartTimes = new HashMap<>();
    private final List<TagChip> renderedFilterActiveChipBounds = new ArrayList<>();
    private final List<TagChip> renderedFilterInactiveChipBounds = new ArrayList<>();

    // ── Leader member management ──
    private final List<MemberButton> renderedMemberActionBounds = new ArrayList<>();
    private final List<JoinButton> renderedJoinButtonBounds = new ArrayList<>();
    private List<Card> renderedCards = List.of();
    /** The header as the last frame drew it: clicks are hit-tested against what the player saw, not a promotion since. */
    private HeaderControlsLayout drawnHeader;
    /** The leader's header buttons as the last frame drew them, or null: a click acts on what they showed, or nothing. */
    private LeaderControls drawnLeaderControls;

    /** A filter chip as drawn: turned {@code degrees} about its centre while it swings. */
    record TagChip(String tag, UiBounds bounds, float degrees) {
        boolean contains(UiPointer at) {
            double turn = Math.toRadians(-degrees);
            float dx = at.x() - bounds.centerX();
            float dy = at.y() - bounds.centerY();
            return bounds.contains(bounds.centerX() + (float) (dx * Math.cos(turn) - dy * Math.sin(turn)),
                    bounds.centerY() + (float) (dx * Math.sin(turn) + dy * Math.cos(turn)));
        }
    }

    private enum MemberAction {
        PROMOTE,
        KICK
    }

    // Buttons name their party and player by id, not list position: the list can be
    // rebuilt by a websocket update between the frame that drew them and the click.
    private record MemberButton(MemberAction action, long listingId, String memberUuid, UiBounds bounds) {}

    /** A card's Join or Leave button as drawn; {@code enabled} is false while it shows greyed out. */
    private record JoinButton(long listingId, boolean leave, boolean enabled, UiBounds bounds) {}

    /** What the header's open/close button does for the leader's listing, and says. */
    enum OpenClose {
        CLOSE("Close party"),
        OPEN("Open party"),
        AUTO_CLOSED("Auto-closed");

        final String label;

        OpenClose(String label) {
            this.label = label;
        }

        static OpenClose of(Listing listing) {
            if (listing != null && listing.isAutoCapacityClosed()) return AUTO_CLOSED;
            return listing != null && listing.status() == PartyStatus.CLOSED ? OPEN : CLOSE;
        }
    }

    /** The leader's header buttons as drawn: for which listing, and what its open/close button does. */
    record LeaderControls(long listingId, OpenClose openClose) {
        /** The buttons the player sees on {@code listing} while they {@code lead} it; else none. */
        static LeaderControls of(Listing listing, boolean lead) {
            return lead && listing != null ? new LeaderControls(listing.id(), OpenClose.of(listing)) : null;
        }

        /**
         * What a click on {@code drawn} acts on once {@code current} is the player's listing: the listing they were
         * drawn for, while it is the same one and they still lead it; else nothing (null).
         */
        static LeaderControls clicked(LeaderControls drawn, Listing current, boolean lead) {
            LeaderControls now = of(current, lead);
            return drawn != null && now != null && now.listingId() == drawn.listingId() ? drawn : null;
        }

        /** What a click on the drawn open/close button does: what it showed, while it still would; else nothing. */
        static OpenClose openCloseClicked(LeaderControls drawn, Listing current, boolean lead) {
            return drawn != null && drawn.equals(of(current, lead)) ? drawn.openClose() : null;
        }
    }

    record HeaderControlsLayout(
            UiBounds searchBar,
            UiBounds manageButton,
            UiBounds inviteButton,
            UiBounds openCloseButton,
            UiBounds delistButton,
            UiBounds inviteAllButton,
            UiBounds scanButton,
            UiBounds newPartyButton,
            UiBounds roleDropdown,
            float titleRight,
            float height) {}

    /** A card as laid out this frame: the party, and where it is. */
    private record Card(PartyListing party, UiBounds bounds) {}

    /** The create or update modal, laid out once for drawing and clicking. */
    private record PartyModal(float x, float y) {
        UiBounds box() {
            return new UiBounds(x, y, MODAL_WIDTH, PARTY_MODAL_HEIGHT);
        }

        UiBounds raidCircle(int index) {
            float totalCirclesW = RAID_TYPES.length * RAID_CIRCLE_SIZE + (RAID_TYPES.length - 1) * RAID_CIRCLE_SPACING;
            float circleStartX = x + (MODAL_WIDTH - totalCirclesW) / 2f;
            return new UiBounds(circleStartX + index * (RAID_CIRCLE_SIZE + RAID_CIRCLE_SPACING), y + 38,
                    RAID_CIRCLE_SIZE, RAID_CIRCLE_SIZE);
        }

        /** Where the "Reserved slots" label sits, centred. */
        float reservedLabelY() {
            return y + 38 + RAID_CIRCLE_SIZE + 16;
        }

        UiBounds reservedField() {
            return new UiBounds(x + MODAL_WIDTH / 2f - MODAL_DROPDOWN_W / 2f, reservedLabelY() + 12,
                    MODAL_DROPDOWN_W - 16, MODAL_DROPDOWN_H);
        }

        UiBounds upArrow() {
            UiBounds field = reservedField();
            return new UiBounds(field.x() + field.width(), field.y(), 16, MODAL_DROPDOWN_H / 2f);
        }

        UiBounds downArrow() {
            UiBounds up = upArrow();
            return new UiBounds(up.x(), up.y() + MODAL_DROPDOWN_H / 2f, 16, MODAL_DROPDOWN_H / 2f);
        }

        float regionLabelY() {
            return reservedField().y() + MODAL_DROPDOWN_H + 18;
        }

        UiBounds region(int index) {
            float totalRegionButtonsW =
                    PARTY_REGIONS.length * REGION_BUTTON_W + (PARTY_REGIONS.length - 1) * REGION_BUTTON_SPACING;
            float regionStartX = x + (MODAL_WIDTH - totalRegionButtonsW) / 2f;
            return new UiBounds(regionStartX + index * (REGION_BUTTON_W + REGION_BUTTON_SPACING), regionLabelY() + 12,
                    REGION_BUTTON_W, MODAL_DROPDOWN_H);
        }

        float joinPolicyLabelY() {
            return regionLabelY() + 12 + MODAL_DROPDOWN_H + 16;
        }

        UiBounds joinPolicy(int index) {
            float total = JOIN_POLICIES.length * JOIN_POLICY_BUTTON_W
                    + (JOIN_POLICIES.length - 1) * JOIN_POLICY_BUTTON_SPACING;
            float startX = x + (MODAL_WIDTH - total) / 2f;
            return new UiBounds(startX + index * (JOIN_POLICY_BUTTON_W + JOIN_POLICY_BUTTON_SPACING),
                    joinPolicyLabelY() + 12, JOIN_POLICY_BUTTON_W, MODAL_DROPDOWN_H);
        }

        UiBounds createButton(float width) {
            return new UiBounds(x + (MODAL_WIDTH - width) / 2f, y + PARTY_MODAL_HEIGHT - MODAL_BUTTON_H - 14,
                    width, MODAL_BUTTON_H);
        }
    }

    public PartyFinderScreen(Screen parent) {
        this(parent, false);
    }

    public PartyFinderScreen(Screen parent, boolean openCreateModalOnInit) {
        super(Component.literal("Party Finder"), parent, SequoiaSidebarNavigation.Destination.PARTY_FINDER);
        this.openCreateModalOnInit = openCreateModalOnInit;
        // Initialize filter with every activity active.
        for (String tag : RAID_TYPES) {
            activeFilterTags.add(tag);
        }
    }

    // ══════════════════════════════ INIT ══════════════════════════════

    @Override
    protected void init() {
        super.init();
        party().actions().refreshData();
        if (openCreateModalOnInit && party().getJoinedPartyIndex() < 0 && !party().isPartyLeader()) {
            openModal(false);
        }
    }

    // ══════════════════════════════ RENDER ══════════════════════════════

    @Override
    protected Screen sidebarParent() {
        return this;
    }

    @Override
    protected void draw(UiCanvas canvas) {
        float screenWidth = canvas.metrics().width();
        float screenHeight = canvas.metrics().height();
        String fontName = SeqClient.getFontManager().getSelectedFont();

        canvas.fillRect(0, 0, screenWidth, screenHeight, color(BACKGROUND_OVERLAY));
        drawSidebar(canvas);

        float panelX = SIDEBAR_WIDTH;
        float panelWidth = screenWidth - SIDEBAR_WIDTH;
        HeaderControlsLayout headerLayout = computeHeaderControlsLayout(panelX, panelWidth, fontName);
        drawnHeader = headerLayout;
        drawnLeaderControls = LeaderControls.of(party().getCurrentListing(), party().isPartyLeader());
        float headerHeight = headerLayout.height();

        canvas.fillRect(panelX, 0, panelWidth, screenHeight, color(BACKGROUND_BODY));
        canvas.fillRect(panelX, 0, panelWidth, headerHeight, color(BACKGROUND_HEADER));
        renderHeaderControls(canvas, fontName, headerLayout);

        // Content area
        float contentY = headerHeight;
        float contentHeight = screenHeight - headerHeight;

        renderedMemberActionBounds.clear();
        renderedJoinButtonBounds.clear();

        canvas.save();
        canvas.scissor(panelX, contentY, panelWidth, contentHeight);
        Listing joined = party().getCurrentListing();
        renderedCards = layoutCards(panelX, panelWidth, contentY, contentHeight);
        for (Card card : renderedCards) {
            renderPartyCard(canvas, fontName, card, joined);
        }
        canvas.restore();

        scroll.draw(canvas, scrollTrack(panelX, panelWidth, contentY, contentHeight));

        // Filter + button (bottom right of content area)
        UiBounds filterButton = filterButton(panelX, panelWidth, screenHeight);
        filterButton.fill(canvas, filterButton.contains(pointer) ? color(ACCENT_PRIMARY_HOVER) : color(ACCENT_PRIMARY));
        drawText(canvas, fontName, HEADER_BUTTON_SIZE, color(TEXT_PRIMARY), filterButton.centerX(),
                filterButton.centerY(), "Filter +", UiCanvas.HorizontalAlign.CENTER);

        // Role dropdown overlay
        if (roleDropdownOpen && !modalOpen && !inviteModalOpen && !filterScreenOpen) {
            renderRoleDropdownMenu(canvas);
        }

        // Modal overlay
        if (modalOpen) {
            renderModal(canvas, fontName, panelX, panelWidth, screenHeight);
        }

        if (inviteModalOpen) {
            renderInviteModal(canvas, fontName, panelX, panelWidth, screenHeight);
        }

        // Filter+ screen overlay (highest priority)
        if (filterScreenOpen) {
            renderFilterScreen(canvas, fontName, panelX, panelWidth, screenHeight);
        }

        renderStatusBanner(canvas, fontName, panelX, panelWidth, screenHeight);
    }

    /** Lays out the cards the search and tag filter let through, as this frame draws them and its clicks find them. */
    private List<Card> layoutCards(float panelX, float panelWidth, float contentY, float contentHeight) {
        List<PartyListing> shown = party().getParties().stream().filter(this::matchesFilters).toList();
        List<UiBounds> bounds = stackCards(shown.stream().map(PartyFinderScreen::cardHeight).toList(), scroll,
                new UiBounds(panelX + PADDING, contentY, panelWidth - PADDING * 2 - 6, contentHeight));
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < shown.size(); i++) {
            cards.add(new Card(shown.get(i), bounds.get(i)));
        }
        return cards;
    }

    private static float cardHeight(PartyListing party) {
        return party.expanded
                ? CARD_HEADER_HEIGHT + party.members.size() * MEMBER_ROW_HEIGHT + CARD_PADDING
                : COLLAPSED_ROW_HEIGHT;
    }

    /**
     * Cards {@code heights} tall, stacked down {@code view} and as wide as it. {@code scroll} is first
     * kept within how far they reach past the view, then they are placed at its offset, so a card
     * collapsing at the bottom of the list moves the cards before they are drawn, not after.
     */
    static List<UiBounds> stackCards(List<Float> heights, ScrollPane scroll, UiBounds view) {
        float total = PADDING;
        for (float height : heights) {
            total += height + CARD_SPACING;
        }
        scroll.setMax(total - view.height());
        float y = view.y() + PADDING - scroll.offset();
        List<UiBounds> cards = new ArrayList<>();
        for (float height : heights) {
            cards.add(new UiBounds(view.x(), y, view.width(), height));
            y += height + CARD_SPACING;
        }
        return cards;
    }

    private static UiBounds scrollTrack(float panelX, float panelWidth, float contentY, float contentHeight) {
        return new UiBounds(panelX + panelWidth - 5, contentY, 4, contentHeight);
    }

    private static UiBounds filterButton(float panelX, float panelWidth, float screenHeight) {
        return new UiBounds(panelX + panelWidth - FILTER_BUTTON_W - FILTER_BUTTON_MARGIN,
                screenHeight - FILTER_BUTTON_H - FILTER_BUTTON_MARGIN, FILTER_BUTTON_W, FILTER_BUTTON_H);
    }

    @Override
    public void tick() {
        super.tick();
        statusBanner.show(party().consumeLatestPartyError(), STATUS_BANNER_DURATION_MS);
        maybeRefreshForLoadingNames();
    }

    private void showStatusBanner(String message) {
        statusBanner.show(message, STATUS_BANNER_DURATION_MS);
    }

    private void renderStatusBanner(
            UiCanvas canvas, String fontName, float panelX, float panelWidth, float screenHeight) {
        String message = statusBanner.message();
        if (message == null) {
            return;
        }

        float textW = textWidth(message, fontName, 12);
        float maxPopupW = Math.max(180f, panelWidth - 20f);
        float popupW = Math.min(maxPopupW, Math.max(STATUS_BANNER_MIN_W, textW + 28f));

        float popupX = panelX + (panelWidth - popupW) / 2f;
        float popupY = screenHeight - STATUS_BANNER_H - 10;

        canvas.fillRect(popupX, popupY, popupW, STATUS_BANNER_H, color(ACCENT_PRIMARY_DARK));
        canvas.strokeRect(popupX, popupY, popupW, STATUS_BANNER_H, 1, color(ACCENT_PRIMARY));
        drawText(
                canvas,
                fontName,
                12,
                color(TEXT_PRIMARY),
                popupX + popupW / 2f,
                popupY + STATUS_BANNER_H / 2f,
                message,
                UiCanvas.HorizontalAlign.CENTER);
    }

    // ── Header ──

    private void renderHeaderControls(UiCanvas canvas, String fontName, HeaderControlsLayout layout) {
        drawText(canvas, fontName, TITLE_FONT_SIZE, color(ACCENT_PRIMARY_HOVER),
                layout.titleRight(), HEADER_HEIGHT / 2f, "Party Finder", UiCanvas.HorizontalAlign.RIGHT);
        search.draw(canvas, layout.searchBar(), fontName, SEARCH_FONT_SIZE, "Search...");

        if (party().isPartyLeader()) {
            drawHeaderButton(canvas, fontName, layout.manageButton(), "Manage Party", color(ACCENT_PRIMARY), color(ACCENT_PRIMARY_HOVER));
            drawHeaderButton(canvas, fontName, layout.inviteButton(), "Invite", color(ACCENT_PRIMARY), color(ACCENT_PRIMARY_HOVER));
            OpenClose openClose = OpenClose.of(party().getCurrentListing());
            boolean autoClosed = openClose == OpenClose.AUTO_CLOSED;
            Color openCloseBg = autoClosed ? color(ACCENT_DISABLED) : color(ACCENT_PRIMARY_DARK);
            Color openCloseHover = autoClosed ? color(ACCENT_DISABLED) : color(ACCENT_PRIMARY_DARK_HOVER);
            drawHeaderButton(
                    canvas,
                    fontName,
                    layout.openCloseButton(),
                    openClose.label,
                    openCloseBg,
                    openCloseHover);
            drawHeaderButton(
                    canvas, fontName, layout.delistButton(), "Delist party", color(CONTROL_DANGER), color(CONTROL_DANGER_HOVER));
            drawHeaderButton(canvas, fontName, layout.inviteAllButton(), "Invite all", color(ACCENT_PRIMARY), color(ACCENT_PRIMARY_HOVER));
            drawHeaderButton(canvas, fontName, layout.scanButton(), "Scan party", color(ACCENT_PRIMARY), color(ACCENT_PRIMARY_HOVER));
        } else {
            boolean inPartyAsMember = party().getJoinedPartyIndex() >= 0;
            Color newBg = inPartyAsMember ? color(ACCENT_DISABLED) : color(ACCENT_PRIMARY);
            Color newHover = inPartyAsMember ? color(ACCENT_DISABLED) : color(ACCENT_PRIMARY_HOVER);
            drawHeaderButton(canvas, fontName, layout.newPartyButton(), "New party +", newBg, newHover);
        }

        roleDropdown = layout.roleDropdown();
        renderRoleDropdownButton(canvas, roleDropdown);
    }

    private void drawHeaderButton(
            UiCanvas canvas, String fontName, UiBounds bounds, String label, Color bg, Color hoverBg) {
        if (bounds == null) {
            return;
        }
        bounds.fill(canvas, bounds.contains(pointer) ? hoverBg : bg);
        drawText(canvas, fontName, HEADER_BUTTON_SIZE, color(TEXT_PRIMARY), bounds.centerX(), bounds.centerY(), label,
                UiCanvas.HorizontalAlign.CENTER);
    }

    private void drawStatusBadge(
            UiCanvas canvas,
            String fontName,
            float x,
            float y,
            float w,
            float h,
            PartyStatus status,
            PartyJoinPolicy joinPolicy) {
        Color bg = statusBadgeBackground(status);
        Color border = statusBadgeBorder(status);
        canvas.fillRect(x, y, w, h, bg);
        canvas.strokeRect(x, y, w, h, 1, border);
        drawText(
                canvas,
                fontName,
                HEADER_BUTTON_SIZE,
                color(TEXT_PRIMARY),
                x + w / 2f,
                y + h / 2f,
                statusBadgeLabel(status, joinPolicy),
                UiCanvas.HorizontalAlign.CENTER);
    }

    private Color statusBadgeBackground(PartyStatus status) {
        return switch (status) {
            case OPEN -> color(STATUS_SUCCESS_BACKGROUND);
            case FULL -> color(STATUS_DANGER_BACKGROUND);
            case CLOSED -> color(STATUS_WARNING_BACKGROUND);
            default -> color(BACKGROUND_POPUP);
        };
    }

    private Color statusBadgeBorder(PartyStatus status) {
        return switch (status) {
            case OPEN -> color(STATUS_SUCCESS_BORDER);
            case FULL -> color(STATUS_DANGER_BORDER);
            case CLOSED -> color(STATUS_WARNING_BORDER);
            default -> color(CONTROL_INPUT_SECONDARY);
        };
    }

    private String statusBadgeLabel(PartyStatus status, PartyJoinPolicy joinPolicy) {
        if (status == PartyStatus.OPEN && joinPolicy == PartyJoinPolicy.INVITE_ONLY) {
            return "INVITE";
        }
        return switch (status) {
            case OPEN -> "OPEN";
            case FULL -> "FULL";
            case CLOSED -> "CLOSED";
            default -> status.name();
        };
    }

    private HeaderControlsLayout drawnHeader(float panelWidth) {
        return hitTestedHeader(drawnHeader, () ->
                computeHeaderControlsLayout(SIDEBAR_WIDTH, panelWidth, SeqClient.getFontManager().getSelectedFont()));
    }

    /** The header a click is hit-tested against: as the last frame {@code drawn} it; before the first, as {@code now}. */
    static HeaderControlsLayout hitTestedHeader(HeaderControlsLayout drawn, Supplier<HeaderControlsLayout> now) {
        return drawn != null ? drawn : now.get();
    }

    private HeaderControlsLayout computeHeaderControlsLayout(float panelX, float panelWidth, String fontName) {
        List<Float> widths = new ArrayList<>();
        if (party().isPartyLeader()) {
            widths.add(paddedHeaderButtonWidth("Manage Party", fontName));
            widths.add(paddedHeaderButtonWidth("Invite", fontName));
            widths.add(paddedHeaderButtonWidth(OpenClose.of(party().getCurrentListing()).label, fontName));
            widths.add(paddedHeaderButtonWidth("Delist party", fontName));
            widths.add(paddedHeaderButtonWidth("Invite all", fontName));
            widths.add(paddedHeaderButtonWidth("Scan party", fontName));
        } else {
            widths.add(paddedHeaderButtonWidth("New party +", fontName));
        }

        return computeHeaderControlsLayout(panelX, panelWidth, widths, roleDropdownWidth(fontName),
                textWidth("Party Finder", fontName, TITLE_FONT_SIZE), party().isPartyLeader());
    }

    static HeaderControlsLayout computeHeaderControlsLayout(
            float panelX, float panelWidth, List<Float> actionWidths, float preferredRoleWidth,
            float titleWidth, boolean leader) {
        float searchX = panelX + SEARCH_BAR_MARGIN;
        float searchY = (HEADER_HEIGHT - SEARCH_BAR_HEIGHT) / 2f;
        float rightEdge = panelX + panelWidth - SEARCH_BAR_MARGIN;
        float availableWidth = Math.max(0, rightEdge - searchX);
        float rowRightEdge = Math.max(searchX, rightEdge - titleWidth - HEADER_BUTTON_SPACING);
        float searchWidth = SequoiaUiStyle.searchWidth(rowRightEdge - searchX - HEADER_BUTTON_SPACING);
        UiBounds searchBar = new UiBounds(searchX, searchY, searchWidth, SEARCH_BAR_HEIGHT);
        float roleWidth = Math.min(preferredRoleWidth, Math.max(50f, panelWidth * 0.3f));
        List<Float> widths = new ArrayList<>(actionWidths);
        widths.add(1, roleWidth);

        float nextButtonX = searchX + searchWidth + HEADER_BUTTON_SPACING;
        float buttonY = searchY;
        UiBounds[] buttons = new UiBounds[widths.size()];
        for (int index = 0; index < widths.size(); index++) {
            float width = Math.min(widths.get(index), availableWidth);
            // Keep New/Manage Party and Your role together whenever a full row can fit both.
            float groupWidth = index == 0 ? width + HEADER_BUTTON_SPACING + roleWidth : width;
            float requiredWidth = groupWidth <= availableWidth ? groupWidth : width;
            if (nextButtonX + requiredWidth > rowRightEdge) {
                buttonY += SEARCH_BAR_HEIGHT + HEADER_BUTTON_SPACING;
                nextButtonX = searchX;
                rowRightEdge = rightEdge;
            }
            buttons[index] = new UiBounds(nextButtonX, buttonY, width, SEARCH_BAR_HEIGHT);
            nextButtonX += width + HEADER_BUTTON_SPACING;
        }

        float headerHeight = Math.max(HEADER_HEIGHT, buttonY + SEARCH_BAR_HEIGHT + searchY);
        return new HeaderControlsLayout(
                searchBar,
                leader ? buttons[0] : null,
                leader ? buttons[2] : null,
                leader ? buttons[3] : null,
                leader ? buttons[4] : null,
                leader ? buttons[5] : null,
                leader ? buttons[6] : null,
                leader ? null : buttons[0],
                buttons[1],
                rightEdge,
                headerHeight);
    }

    private static float paddedHeaderButtonWidth(String label, String fontName) {
        return (float) Math.ceil(textWidth(label, fontName, HEADER_BUTTON_SIZE))
                + HEADER_BUTTON_HORIZONTAL_PADDING * 2;
    }

    private static float roleDropdownWidth(String fontName) {
        float widestLabel = textWidth("Your role", fontName, HEADER_BUTTON_SIZE);
        for (String role : ROLES) {
            widestLabel = Math.max(widestLabel, textWidth(role, fontName, HEADER_BUTTON_SIZE));
        }
        return Math.max(
                HEADER_ROLE_DROPDOWN_MIN_W,
                (float) Math.ceil(widestLabel) + HEADER_BUTTON_HORIZONTAL_PADDING + HEADER_ROLE_DROPDOWN_TRAILING_SPACE);
    }

    // ── Role dropdown ──

    private void renderRoleDropdownButton(UiCanvas canvas, UiBounds bounds) {
        DropdownMenu.trigger(canvas, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                selectedRole != null ? selectedRole : "Your role", roleDropdownOpen, true, pointer.x(), pointer.y());
    }

    private void renderRoleDropdownMenu(UiCanvas canvas) {
        DropdownMenu.list(canvas, roleDropdown.x(), roleDropdown.y() + SEARCH_BAR_HEIGHT, roleDropdown.width(),
                DropdownMenu.ROW_HEIGHT, java.util.Arrays.asList(ROLES), i -> ROLES[i].equals(selectedRole),
                0, ROLES.length, pointer.x(), pointer.y());
    }

    // ── Party cards ──

    /** Draws {@code card}; {@code joined} is the listing the player is in, or null. */
    private void renderPartyCard(UiCanvas canvas, String fontName, Card card, Listing joined) {
        PartyListing party = card.party();
        float x = card.bounds().x();
        float y = card.bounds().y();
        float w = card.bounds().width();
        float h = card.bounds().height();
        boolean isJoined = joined != null && joined.id() == party.id;
        canvas.fillRect(x, y, w, h, party.expanded ? color(BACKGROUND_CONTENT_FOCUSED) : color(BACKGROUND_CONTENT));

        if (party.expanded) {
            renderExpandedCard(canvas, fontName, x, y, w, h, party, isJoined, joined != null);
        } else {
            renderCollapsedCard(canvas, fontName, x, y, w, party);
        }
    }

    private void renderExpandedCard(
            UiCanvas canvas,
            String fontName,
            float x,
            float y,
            float w,
            float h,
            PartyListing party,
            boolean isJoined,
            boolean inParty) {
        float rowX = x + CARD_PADDING;
        List<String> raidTags = getRenderedRaidTags(party);

        drawRaidIconCircle(
                canvas,
                fontName,
                rowX,
                y + (CARD_HEADER_HEIGHT - TYPE_ICON_SIZE) / 2f,
                TYPE_ICON_SIZE,
                raidTags,
                hasGazEarsMember(party));
        rowX += TYPE_ICON_SIZE + 6;

        drawText(
                canvas,
                fontName,
                CARD_TITLE_SIZE,
                color(TEXT_PRIMARY),
                rowX,
                y + CARD_HEADER_HEIGHT / 2f,
                party.occupiedSlots + "/" + party.maxSize,
                UiCanvas.HorizontalAlign.LEFT);
        rowX += 46;

        drawStatusBadge(
                canvas,
                fontName,
                rowX,
                y + (CARD_HEADER_HEIGHT - STATUS_BADGE_H) / 2f,
                STATUS_BADGE_W,
                STATUS_BADGE_H,
                party.status,
                party.joinPolicy);

        // Collapse arrow
        drawText(
                canvas,
                fontName,
                16,
                color(ACCENT_SECONDARY),
                x + w - CARD_PADDING,
                y + CARD_HEADER_HEIGHT / 2f,
                "-",
                UiCanvas.HorizontalAlign.RIGHT);

        // Members
        boolean isMyParty = isJoined && party().isPartyLeader();
        boolean amLeaderOfThisParty = isMyParty && party().isPartyLeader();
        float memberY = y + CARD_HEADER_HEIGHT;
        for (int mi = 0; mi < party.members.size(); mi++) {
            PartyMember member = party.members.get(mi);
            renderMemberRow(
                    canvas,
                    fontName,
                    x + CARD_PADDING + 10,
                    memberY,
                    w - CARD_PADDING * 2 - 10,
                    member,
                    party.id,
                    amLeaderOfThisParty);
            memberY += MEMBER_ROW_HEIGHT;
        }

        float lastMemberCenterY = memberY - MEMBER_ROW_HEIGHT / 2f;

        // Join/Leave. Leaders already know they are in their own party, and keeping
        // this area free gives their member-management buttons a stable home.
        float joinX = x + w - CARD_PADDING - JOIN_BUTTON_WIDTH;
        float joinY = memberY - MEMBER_ROW_HEIGHT + (MEMBER_ROW_HEIGHT - BUTTON_HEIGHT) / 2f;
        boolean alreadyInParty = inParty && !isJoined;
        boolean listingUnavailable = !isJoined && !party.isJoinable();
        if (!isMyParty) {
            boolean buttonDisabled = alreadyInParty || listingUnavailable;
            UiBounds joinButton = new UiBounds(joinX, joinY, JOIN_BUTTON_WIDTH, BUTTON_HEIGHT);
            boolean joinHovered = !buttonDisabled && joinButton.contains(pointer);
            Color joinBg = buttonDisabled
                    ? color(ACCENT_DISABLED)
                    : (joinHovered ? color(ACCENT_PRIMARY_HOVER) : color(ACCENT_PRIMARY));
            canvas.fillRect(joinX, joinY, JOIN_BUTTON_WIDTH, BUTTON_HEIGHT, joinBg);

            renderedJoinButtonBounds.add(new JoinButton(party.id, isJoined, !buttonDisabled, joinButton));

            Color textCol = buttonDisabled ? color(TEXT_DISABLED) : color(TEXT_PRIMARY);
            String actionText = isJoined ? "Leave" : partyActionLabel(party);
            drawText(
                    canvas,
                    fontName,
                    MEMBER_FONT_SIZE,
                    textCol,
                    joinX + JOIN_BUTTON_WIDTH / 2f,
                    joinY + BUTTON_HEIGHT / 2f,
                    actionText,
                    UiCanvas.HorizontalAlign.CENTER);
        }

        // Tag label
        PartyMember lastMember = party.members.isEmpty() ? null : party.members.getLast();
        boolean lastMemberHasLeaderActions = amLeaderOfThisParty
                && lastMember != null
                && !lastMember.isLeader
                && !lastMember.isReserved
                && !lastMember.isObserved;
        float rightSideWidth = isMyParty
                ? (lastMemberHasLeaderActions ? leaderActionButtonsWidth(fontName) + 8 : 0)
                : JOIN_BUTTON_WIDTH + 8;
        float labelRightX = x + w - CARD_PADDING - rightSideWidth;
        drawText(
                canvas,
                fontName,
                TYPE_FONT_SIZE,
                color(TEXT_MUTED),
                labelRightX,
                lastMemberCenterY,
                getPartyCardLabel(party),
                UiCanvas.HorizontalAlign.RIGHT);
    }

    private void renderCollapsedCard(
            UiCanvas canvas, String fontName, float x, float y, float w, PartyListing party) {
        float rowX = x + CARD_PADDING;
        float centerY = y + COLLAPSED_ROW_HEIGHT / 2f;
        List<String> raidTags = getRenderedRaidTags(party);

        drawRaidIconCircle(
                canvas,
                fontName,
                rowX,
                y + (COLLAPSED_ROW_HEIGHT - TYPE_ICON_SIZE) / 2f,
                TYPE_ICON_SIZE,
                raidTags,
                hasGazEarsMember(party));
        rowX += TYPE_ICON_SIZE + 6;

        drawText(
                canvas,
                fontName,
                CARD_TITLE_SIZE,
                color(TEXT_PRIMARY),
                rowX,
                centerY,
                party.occupiedSlots + "/" + party.maxSize,
                UiCanvas.HorizontalAlign.LEFT);
        rowX += 42;

        drawStatusBadge(
                canvas,
                fontName,
                rowX,
                centerY - STATUS_BADGE_H / 2f,
                STATUS_BADGE_W,
                STATUS_BADGE_H,
                party.status,
                party.joinPolicy);
        rowX += STATUS_BADGE_W + 8;

        float rightX = x + w - CARD_PADDING;
        List<AssetManager.Asset> classIcons = party.members.stream()
                .map(this::getMemberClassIcon)
                .filter(Objects::nonNull)
                .toList();
        float reservedRightWidth = 22 + classIcons.size() * (CLASS_ICON_SIZE + 4);
        reservedRightWidth += 6;
        reservedRightWidth += textWidth(getPartyCardLabel(party), fontName, TYPE_FONT_SIZE);
        float leaderTextMaxX = rightX - reservedRightWidth;

        PartyMember leader = party.getLeader();
        if (leader != null) {
            String leaderName = leader.displayName();
            AssetManager.Asset starIcon = getClassIcon("star");
            if (starIcon != null) {
                float starY = centerY - STAR_ICON_SIZE / 2f;
                drawImage(canvas, starIcon, rowX, starY, STAR_ICON_SIZE, STAR_ICON_SIZE, 255);
            }
            rowX += STAR_ICON_SIZE + 4;

            String clippedLeaderName =
                    fitTextToWidth(leaderName, fontName, MEMBER_FONT_SIZE, Math.max(0, leaderTextMaxX - rowX));
            if (!clippedLeaderName.isEmpty()) {
                drawText(
                        canvas,
                        fontName,
                        MEMBER_FONT_SIZE,
                        color(TEXT_SECONDARY),
                        rowX,
                        centerY,
                        clippedLeaderName,
                        UiCanvas.HorizontalAlign.LEFT);
            }
        }

        // Expand "+"
        drawText(
                canvas,
                fontName,
                16,
                color(ACCENT_SECONDARY),
                rightX,
                centerY,
                "+",
                UiCanvas.HorizontalAlign.RIGHT);
        rightX -= 22;

        for (int j = classIcons.size() - 1; j >= 0; j--) {
            float iconX = rightX - CLASS_ICON_SIZE;
            float iconY = centerY - CLASS_ICON_SIZE / 2f;
            drawImage(canvas, classIcons.get(j), iconX, iconY, CLASS_ICON_SIZE, CLASS_ICON_SIZE, 255);
            rightX -= CLASS_ICON_SIZE + 4;
        }

        rightX -= 6;
        drawText(
                canvas,
                fontName,
                TYPE_FONT_SIZE,
                color(TEXT_MUTED),
                rightX,
                centerY,
                getPartyCardLabel(party),
                UiCanvas.HorizontalAlign.RIGHT);
    }

    private String fitTextToWidth(String text, String fontName, float fontSize, float maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }

        float fullWidth = textWidth(text, fontName, fontSize);
        if (fullWidth <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        float ellipsisWidth = textWidth(ellipsis, fontName, fontSize);
        if (ellipsisWidth > maxWidth) {
            return "";
        }

        for (int end = text.length() - 1; end > 0; end--) {
            String candidate = text.substring(0, end) + ellipsis;
            if (textWidth(candidate, fontName, fontSize) <= maxWidth) {
                return candidate;
            }
        }

        return ellipsis;
    }

    private void renderMemberRow(
            UiCanvas canvas,
            String fontName,
            float x,
            float y,
            float w,
            PartyMember member,
            long listingId,
            boolean amLeaderOfThisParty) {
        float rowX = x;
        float centerY = y + MEMBER_ROW_HEIGHT / 2f;

        boolean showLeaderActions =
                amLeaderOfThisParty && !member.isLeader && !member.isReserved && !member.isObserved;

        if (member.isLeader) {
            AssetManager.Asset starIcon = getClassIcon("star");
            if (starIcon != null) {
                float starY = centerY - STAR_ICON_SIZE / 2f;
                drawImage(canvas, starIcon, rowX, starY, STAR_ICON_SIZE, STAR_ICON_SIZE, 255);
            }
        }
        rowX += STAR_ICON_SIZE + 4;

        float actionButtonsWidth = showLeaderActions ? leaderActionButtonsWidth(fontName) + 8 : 0;
        float memberSuffixWidth = memberSuffixWidth(member, fontName);
        String memberName = fitTextToWidth(
                member.displayName(),
                fontName,
                MEMBER_FONT_SIZE,
                Math.max(0, x + w - actionButtonsWidth - memberSuffixWidth - rowX));
        drawText(
                canvas,
                fontName,
                MEMBER_FONT_SIZE,
                color(TEXT_SECONDARY),
                rowX,
                centerY,
                memberName,
                UiCanvas.HorizontalAlign.LEFT);

        float nameW = textWidth(memberName, fontName, MEMBER_FONT_SIZE);

        if (showLeaderActions) {
            renderLeaderMemberActions(canvas, fontName, x, y, w, listingId, member.playerUUID);
        }

        rowX += nameW + 8;

        AssetManager.Asset icon = getMemberClassIcon(member);
        if (icon != null) {
            float iconY = y + (MEMBER_ROW_HEIGHT - CLASS_ICON_SIZE) / 2f;
            drawImage(canvas, icon, rowX, iconY, CLASS_ICON_SIZE, CLASS_ICON_SIZE, 255);
            rowX += CLASS_ICON_SIZE + 6;
        }

        if (!member.isReserved && !member.isObserved) {
            drawText(
                    canvas,
                    fontName,
                    ROLE_FONT_SIZE,
                    color(TEXT_MUTED),
                    rowX,
                    centerY,
                    "(" + member.role + ")",
                    UiCanvas.HorizontalAlign.LEFT);
        }
    }

    private float memberSuffixWidth(PartyMember member, String fontName) {
        float width = 8;
        if (getMemberClassIcon(member) != null) {
            width += CLASS_ICON_SIZE + 6;
        }
        if (!member.isReserved && !member.isObserved) {
            width += textWidth("(" + member.role + ")", fontName, ROLE_FONT_SIZE);
        }
        return width;
    }

    private void renderLeaderMemberActions(
            UiCanvas canvas,
            String fontName,
            float rowX,
            float rowY,
            float rowWidth,
            long listingId,
            String memberUuid) {
        float promoteButtonWidth = leaderActionButtonWidth("Promote", fontName);
        float kickButtonWidth = leaderActionButtonWidth("Kick", fontName);
        float buttonY = rowY + (MEMBER_ROW_HEIGHT - LEADER_ACTION_BUTTON_HEIGHT) / 2f;
        float kickX = rowX + rowWidth - kickButtonWidth;
        float promoteX = kickX - LEADER_ACTION_BUTTON_SPACING - promoteButtonWidth;
        drawMemberButton(canvas, fontName, new MemberButton(MemberAction.PROMOTE, listingId, memberUuid,
                new UiBounds(promoteX, buttonY, promoteButtonWidth, LEADER_ACTION_BUTTON_HEIGHT)), "Promote",
                color(ACCENT_PRIMARY), color(ACCENT_PRIMARY_HOVER));
        drawMemberButton(canvas, fontName, new MemberButton(MemberAction.KICK, listingId, memberUuid,
                new UiBounds(kickX, buttonY, kickButtonWidth, LEADER_ACTION_BUTTON_HEIGHT)), "Kick",
                color(CONTROL_DANGER), color(CONTROL_DANGER_HOVER));
    }

    private void drawMemberButton(
            UiCanvas canvas, String fontName, MemberButton button, String label, Color bg, Color hoverBg) {
        UiBounds bounds = button.bounds();
        bounds.fill(canvas, bounds.contains(pointer) ? hoverBg : bg);
        drawText(canvas, fontName, HEADER_BUTTON_SIZE, color(TEXT_PRIMARY), bounds.centerX(), bounds.centerY(), label,
                UiCanvas.HorizontalAlign.CENTER);
        renderedMemberActionBounds.add(button);
    }

    private static float leaderActionButtonsWidth(String fontName) {
        return leaderActionButtonWidth("Promote", fontName)
                + LEADER_ACTION_BUTTON_SPACING
                + leaderActionButtonWidth("Kick", fontName);
    }

    private static float leaderActionButtonWidth(String label, String fontName) {
        return (float) Math.ceil(textWidth(label, fontName, HEADER_BUTTON_SIZE))
                + LEADER_ACTION_BUTTON_HORIZONTAL_PADDING * 2;
    }

    private static float modalActionButtonWidth(String label, String fontName) {
        return Math.max(
                MODAL_BUTTON_W,
                (float) Math.ceil(textWidth(label, fontName, MODAL_LABEL_SIZE))
                        + MODAL_BUTTON_HORIZONTAL_PADDING * 2);
    }

    // ── Small triangle arrow (pointing up or down) ──

    private void drawTriangle(UiCanvas canvas, float cx, float cy, float size, boolean up, Color color) {
        float half = size / 2f;
        canvas.beginPath();
        if (up) {
            canvas.moveTo(cx, cy - half);
            canvas.lineTo(cx - half, cy + half);
            canvas.lineTo(cx + half, cy + half);
        } else {
            canvas.moveTo(cx, cy + half);
            canvas.lineTo(cx - half, cy - half);
            canvas.lineTo(cx + half, cy - half);
        }
        canvas.closePath();
        canvas.fillPath(color);
    }

    // ── Pizza-slice raid icon circle ──

    private void drawRaidIconCircle(
            UiCanvas canvas,
            String fontName,
            float x,
            float y,
            float size,
            List<String> raidTags,
            boolean drawGazEarsOverlay) {
        float cx = x + size / 2f;
        float cy = y + size / 2f;
        float radius = size / 2f - 1;

        if (raidTags.isEmpty()) {
            return;
        }

        // Check if this raid has no icon asset (text fallback, e.g. Prelude to
        // Annihilation)
        if (raidTags.size() == 1 && PartyListing.displayNameToAssetKey(canonicalizeRaidTag(raidTags.get(0))) == null) {
            drawText(
                    canvas,
                    fontName,
                    RAID_LABEL_SIZE,
                    color(TEXT_PRIMARY),
                    cx,
                    cy,
                    raidTags.get(0),
                    UiCanvas.HorizontalAlign.CENTER);
            return;
        }

        int count = raidTags.size();
        List<String> renderTags = raidTags;
        if (count == 3) {
            int nolIndex = indexOfCanonicalNexusOfLight(raidTags);
            if (nolIndex > 0) {
                List<String> reordered = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    reordered.add(raidTags.get((nolIndex + i) % count));
                }
                renderTags = reordered;
            }
        }

        float anglePerSlice = (float) ((2.0 * Math.PI) / count);
        float startAngle;
        if (count == 2) {
            // Diagonal split (bottom-left to top-right).
            startAngle = (float) (-Math.PI / 4.0);
        } else if (count == 3) {
            // Top slice spans compass 300°→60° (NOL at top).
            startAngle = (float) (-5.0 * Math.PI / 6.0);
        } else if (count == 4) {
            // Rotate 4-slice icons by 45° to form an X split.
            startAngle = (float) (-Math.PI / 4.0);
        } else {
            startAngle = (float) (-Math.PI / 2.0);
        }

        for (int i = 0; i < count; i++) {
            String tag = renderTags.get(i);
            String assetKey = PartyListing.displayNameToAssetKey(canonicalizeRaidTag(tag));
            AssetManager.Asset raidIcon = assetKey != null ? getClassIcon(assetKey) : null;

            float sliceStart = startAngle + i * anglePerSlice;
            float sliceEnd = sliceStart + anglePerSlice;

            if (count == 1) {
                // Full circle - just fill with image
                if (raidIcon != null) {
                    canvas.save();
                    canvas.beginPath();
                    canvas.circle(cx, cy, radius);
                    canvas.fillCurrentPathWithImage(raidIcon.getImage(), x, y, size, size, 1.0f);
                    canvas.closePath();
                    canvas.restore();
                } else {
                    // Fallback to text
                    drawText(
                            canvas,
                            fontName,
                            RAID_LABEL_SIZE,
                            color(TEXT_PRIMARY),
                            cx,
                            cy,
                            tag,
                            UiCanvas.HorizontalAlign.CENTER);
                }
            } else if (count == 2) {
                // Semicircle clip: edge-to-edge arc, no center vertex.
                // This ensures opaque images are truly clipped at the diagonal.
                float edgeX1 = cx + radius * (float) Math.cos(sliceStart);
                float edgeY1 = cy + radius * (float) Math.sin(sliceStart);

                canvas.save();
                canvas.beginPath();
                canvas.moveTo(edgeX1, edgeY1);
                canvas.arc(cx, cy, radius, sliceStart, sliceEnd, UiCanvas.ArcDirection.CLOCKWISE);
                canvas.closePath();

                if (raidIcon != null) {
                    canvas.fillCurrentPathWithImage(raidIcon.getImage(), x, y, size, size, 1.0f);
                } else {
                    canvas.fillPath(color(ACCENT_PRIMARY));
                }
                canvas.restore();
            } else {
                // Pie slice (3+ way)
                canvas.save();
                canvas.beginPath();
                canvas.moveTo(cx, cy);
                canvas.arc(cx, cy, radius, sliceStart, sliceEnd, UiCanvas.ArcDirection.CLOCKWISE);
                canvas.closePath();

                if (raidIcon != null) {
                    canvas.fillCurrentPathWithImage(raidIcon.getImage(), x, y, size, size, 1.0f);
                } else {
                    // Fallback solid color for missing icon
                    canvas.fillPath(color(ACCENT_PRIMARY));
                }
                canvas.restore();
            }
        }

        if (count >= 2) {
            if (count == 2) {
                float splitAngle = startAngle;
                float dx = radius * (float) Math.cos(splitAngle);
                float dy = radius * (float) Math.sin(splitAngle);
                canvas.strokeLine(cx - dx, cy - dy, cx + dx, cy + dy, 1.25f, color(ACCENT_PRIMARY_DARK));
            } else {
                for (int i = 0; i < count; i++) {
                    float splitAngle = startAngle + i * anglePerSlice;
                    float edgeX = cx + radius * (float) Math.cos(splitAngle);
                    float edgeY = cy + radius * (float) Math.sin(splitAngle);
                    canvas.strokeLine(cx, cy, edgeX, edgeY, 1.25f, color(ACCENT_PRIMARY_DARK));
                }
            }
        }

        if (drawGazEarsOverlay) {
            AssetManager.Asset gazEars = getClassIcon(GAZ_EARS_ASSET);
            if (gazEars != null) {
                drawImage(canvas, gazEars, x + size * 0.1f, y - size * 0.55f, size, size, 255);
            }
        }
    }

    // ── Create/Manage Party Modal ──

    private static PartyModal partyModal(float panelX, float panelWidth, float screenHeight) {
        return new PartyModal(panelX + (panelWidth - MODAL_WIDTH) / 2f, (screenHeight - PARTY_MODAL_HEIGHT) / 2f);
    }

    private String createLabel() {
        return party().isPartyLeader() ? "Update party" : "Create party";
    }

    private void renderModal(UiCanvas canvas, String fontName, float panelX, float panelWidth, float screenHeight) {
        // Darken background
        canvas.fillRect(panelX, 0, panelWidth, screenHeight, color(BACKGROUND_MODAL_OVERLAY));

        // Modal centered in main panel area
        PartyModal modal = partyModal(panelX, panelWidth, screenHeight);
        float modalX = modal.x();
        float modalY = modal.y();

        canvas.fillRect(modalX, modalY, MODAL_WIDTH, PARTY_MODAL_HEIGHT, color(BACKGROUND_BODY_OPAQUE));
        canvas.strokeRect(modalX, modalY, MODAL_WIDTH, PARTY_MODAL_HEIGHT, 1, color(ACCENT_SECONDARY));

        // Title
        String modalTitle = party().isPartyLeader() ? "Update Party" : "Create Party";
        drawText(canvas, fontName, MODAL_TITLE_SIZE, color(TEXT_PRIMARY), modalX + MODAL_WIDTH / 2f, modalY + 18,
                modalTitle, UiCanvas.HorizontalAlign.CENTER);

        // Raid type icons row
        for (int i = 0; i < RAID_TYPES.length; i++) {
            String rt = RAID_TYPES[i];
            UiBounds circle = modal.raidCircle(i);
            float iconX = circle.x();
            float iconY = circle.y();
            float rcx = iconX + RAID_CIRCLE_SIZE / 2f;
            float rcy = iconY + RAID_CIRCLE_SIZE / 2f;
            boolean selected = modalSelectedRaids.contains(rt);

            // Selection highlight behind icon
            if (selected) {
                canvas.fillCircle(rcx, rcy, RAID_CIRCLE_SIZE / 2f - 2, color(ACCENT_PRIMARY));
            }

            // Draw raid icon image (or text fallback for raids without an asset)
            String assetKey = PartyListing.displayNameToAssetKey(rt);
            AssetManager.Asset raidIcon = assetKey != null ? getClassIcon(assetKey) : null;
            if (raidIcon != null) {
                float imgInset = 4;
                drawImage(canvas, raidIcon, iconX + imgInset, iconY + imgInset, RAID_CIRCLE_SIZE - imgInset * 2,
                        RAID_CIRCLE_SIZE - imgInset * 2, selected ? 255 : 160);
            } else {
                // Text fallback (e.g. ANNI)
                drawText(canvas, fontName, RAID_LABEL_SIZE, selected ? color(TEXT_PRIMARY) : color(TEXT_MUTED), rcx,
                        rcy, rt, UiCanvas.HorizontalAlign.CENTER);
            }
        }

        // Reserved slots with up/down arrows
        drawText(canvas, fontName, MODAL_LABEL_SIZE, color(TEXT_MUTED), modalX + MODAL_WIDTH / 2f,
                modal.reservedLabelY(), "Reserved slots", UiCanvas.HorizontalAlign.CENTER);

        UiBounds field = modal.reservedField();
        field.fill(canvas, reservedSlotsFocused ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
        field.stroke(canvas, 1, reservedSlotsFocused ? color(CONTROL_BORDER) : color(CONTROL_INPUT_SECONDARY));
        drawText(canvas, fontName, MODAL_LABEL_SIZE, color(TEXT_PRIMARY), field.centerX(), field.centerY(),
                reservedSlotsFocused ? reservedSlotsInput : String.valueOf(modalReservedSlots),
                UiCanvas.HorizontalAlign.CENTER);

        UiBounds up = modal.upArrow();
        up.fill(canvas, up.contains(pointer) ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
        up.stroke(canvas, 1, color(CONTROL_INPUT_SECONDARY));
        drawTriangle(canvas, up.centerX(), up.centerY(), 4, true, color(TEXT_PRIMARY));

        UiBounds down = modal.downArrow();
        down.fill(canvas, down.contains(pointer) ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
        down.stroke(canvas, 1, color(CONTROL_INPUT_SECONDARY));
        drawTriangle(canvas, down.centerX(), down.centerY(), 4, false, color(TEXT_PRIMARY));

        drawText(canvas, fontName, MODAL_LABEL_SIZE, color(TEXT_MUTED), modalX + MODAL_WIDTH / 2f,
                modal.regionLabelY(), "Region", UiCanvas.HorizontalAlign.CENTER);
        PartyRegion selectedRegion = modalSelectedRegion != null ? modalSelectedRegion : PartyRegion.NA;
        for (int i = 0; i < PARTY_REGIONS.length; i++) {
            drawChoice(canvas, fontName, modal.region(i), PARTY_REGIONS[i].name(), selectedRegion == PARTY_REGIONS[i]);
        }

        drawText(canvas, fontName, MODAL_LABEL_SIZE, color(TEXT_MUTED), modalX + MODAL_WIDTH / 2f,
                modal.joinPolicyLabelY(), "Who can join?", UiCanvas.HorizontalAlign.CENTER);
        for (int i = 0; i < JOIN_POLICIES.length; i++) {
            drawChoice(canvas, fontName, modal.joinPolicy(i), joinPolicyLabel(JOIN_POLICIES[i]),
                    modalJoinPolicy == JOIN_POLICIES[i]);
        }

        // Create/Update button
        String createLabel = createLabel();
        UiBounds create = modal.createButton(modalActionButtonWidth(createLabel, fontName));
        create.fill(canvas, create.contains(pointer) ? color(ACCENT_PRIMARY_HOVER) : color(ACCENT_PRIMARY));
        drawText(canvas, fontName, MODAL_LABEL_SIZE, color(TEXT_PRIMARY), create.centerX(), create.centerY(),
                createLabel, UiCanvas.HorizontalAlign.CENTER);
    }

    /** One of a row of choices, such as a region: filled while it is the one chosen. */
    private void drawChoice(UiCanvas canvas, String fontName, UiBounds bounds, String label, boolean selected) {
        bounds.fill(canvas, selected
                ? color(ACCENT_PRIMARY)
                : (bounds.contains(pointer) ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT)));
        bounds.stroke(canvas, 1, selected ? color(CONTROL_BORDER) : color(CONTROL_INPUT_SECONDARY));
        drawText(canvas, fontName, MODAL_LABEL_SIZE, color(TEXT_PRIMARY), bounds.centerX(), bounds.centerY(), label,
                UiCanvas.HorizontalAlign.CENTER);
    }

    // ── Filter+ Screen ──

    private void renderFilterScreen(
            UiCanvas canvas, String fontName, float panelX, float panelWidth, float screenHeight) {
        canvas.fillRect(panelX, 0, panelWidth, screenHeight, color(BACKGROUND_MODAL_OVERLAY));

        UiBounds overlay = filterOverlay(panelX, panelWidth, screenHeight);
        float filterW = TAG_OVERLAY_WIDTH;
        float filterH = TAG_OVERLAY_HEIGHT;
        float filterX = overlay.x();
        float filterY = overlay.y();

        canvas.fillRect(filterX, filterY, filterW, filterH, color(BACKGROUND_BODY_OPAQUE));
        canvas.strokeRect(filterX, filterY, filterW, filterH, 1, color(ACCENT_SECONDARY));

        // Title
        drawText(
                canvas,
                fontName,
                MODAL_TITLE_SIZE,
                color(TEXT_PRIMARY),
                filterX + filterW / 2f,
                filterY + 18,
                "Tag filter selection",
                UiCanvas.HorizontalAlign.CENTER);

        float boxPadding = 12;
        float boxW = filterW - boxPadding * 2;
        float boxH = TAG_BOX_HEIGHT;
        float boxX = filterX + boxPadding;

        // Active filters box
        float activeBoxY = filterY + 34;
        canvas.fillRect(boxX, activeBoxY, boxW, boxH, color(BACKGROUND_BODY_OPAQUE));
        drawText(
                canvas,
                fontName,
                9,
                color(TEXT_MUTED),
                boxX + 4,
                activeBoxY + 2,
                "Active filters",
                UiCanvas.HorizontalAlign.LEFT,
                UiCanvas.VerticalAlign.TOP);

        renderTagChips(
                canvas,
                fontName,
                boxX + 4,
                activeBoxY + 14,
                boxW - 8,
                activeFilterTags,
                true,
                filterTagAnimStartTimes,
                renderedFilterActiveChipBounds);

        // Inactive filters box
        float inactiveBoxY = activeBoxY + boxH + 8;
        canvas.fillRect(boxX, inactiveBoxY, boxW, boxH, color(BACKGROUND_BODY_OPAQUE));
        drawText(
                canvas,
                fontName,
                9,
                color(TEXT_MUTED),
                boxX + 4,
                inactiveBoxY + 2,
                "Inactive filters",
                UiCanvas.HorizontalAlign.LEFT,
                UiCanvas.VerticalAlign.TOP);

        renderTagChips(
                canvas,
                fontName,
                boxX + 4,
                inactiveBoxY + 14,
                boxW - 8,
                inactiveFilterTags,
                false,
                filterTagAnimStartTimes,
                renderedFilterInactiveChipBounds);

        // Back button
        UiBounds back = filterBackButton(overlay);
        back.fill(canvas, back.contains(pointer) ? color(ACCENT_PRIMARY_HOVER) : color(ACCENT_PRIMARY));
        drawText(canvas, fontName, MEMBER_FONT_SIZE, color(TEXT_PRIMARY), back.centerX(), back.centerY(), "< Back",
                UiCanvas.HorizontalAlign.CENTER);
    }

    private static UiBounds filterOverlay(float panelX, float panelWidth, float screenHeight) {
        return new UiBounds(panelX + (panelWidth - TAG_OVERLAY_WIDTH) / 2f, (screenHeight - TAG_OVERLAY_HEIGHT) / 2f,
                TAG_OVERLAY_WIDTH, TAG_OVERLAY_HEIGHT);
    }

    private static UiBounds filterBackButton(UiBounds overlay) {
        return new UiBounds(overlay.x() + (TAG_OVERLAY_WIDTH - 70) / 2f, overlay.y() + TAG_OVERLAY_HEIGHT - 20 - 8, 70, 20);
    }

    // ── Tag chip rendering with pendulum animation ──

    private void renderTagChips(
            UiCanvas canvas,
            String fontName,
            float startX,
            float startY,
            float maxWidth,
            Set<String> tags,
            boolean isActive,
            Map<String, Long> animStartTimes,
            List<TagChip> renderedChipBounds) {
        float chipH = 16;
        float chipPadding = 6;
        float chipSpacing = 4;
        float curX = startX;
        float curY = startY;

        renderedChipBounds.clear();

        for (String tag : tags) {
            String label = getTagChipLabel(tag, isActive);
            float chipW = textWidth(label, fontName, TAG_CHIP_FONT_SIZE) + chipPadding * 2;

            if (curX + chipW > startX + maxWidth && curX > startX) {
                curX = startX;
                curY += chipH + 3;
            }

            // Pendulum animation
            float angle = 0;
            Long animStart = animStartTimes.get(tag);
            if (animStart != null) {
                float elapsed = (System.currentTimeMillis() - animStart) / 1000f;
                if (elapsed < 1.0f) {
                    float amplitude = 15f;
                    float freq = 12f;
                    float damping = 4f;
                    angle = (float) (amplitude * Math.sin(freq * elapsed) * Math.exp(-damping * elapsed));
                } else {
                    animStartTimes.remove(tag);
                }
            }

            float chipCenterX = curX + chipW / 2f;
            float chipCenterY = curY + chipH / 2f;
            TagChip chip = new TagChip(tag, new UiBounds(curX, curY, chipW, chipH), angle);
            renderedChipBounds.add(chip);

            canvas.save();
            if (angle != 0) {
                canvas.translate(chipCenterX, chipCenterY);
                canvas.rotateDegrees(angle);
                canvas.translate(-chipCenterX, -chipCenterY);
            }

            boolean chipHovered = chip.contains(pointer);
            canvas.fillRect(
                    curX,
                    curY,
                    chipW,
                    chipH,
                    chipHovered ? color(CONTROL_INPUT_HOVER) : color(ACCENT_DIVIDER));

            drawText(
                    canvas,
                    fontName,
                    TAG_CHIP_FONT_SIZE,
                    color(ACCENT_PRIMARY),
                    chipCenterX,
                    chipCenterY,
                    label,
                    UiCanvas.HorizontalAlign.CENTER);

            canvas.restore();

            curX += chipW + chipSpacing;
        }
    }

    private void commitReservedSlotsInput() {
        int maxSlots = modalSelectedRaids.contains("Prelude to Annihilation") ? 9 : 3;
        try {
            int val = Integer.parseInt(reservedSlotsInput);
            modalReservedSlots = Math.max(0, Math.min(maxSlots, val));
        } catch (NumberFormatException e) {
            // invalid input, keep current value
        }
        reservedSlotsInput = String.valueOf(modalReservedSlots);
    }

    // ── Helpers ──

    private AssetManager.Asset getMemberClassIcon(PartyMember member) {
        AssetManager.Asset icon = getClassIcon(member.classIconKey());
        return icon != null && icon.getImage() != null ? icon : null;
    }

    private AssetManager.Asset getClassIcon(String className) {
        if (className == null || SeqClient.assetManager == null) return null;
        return SeqClient.assetManager.getAsset(className);
    }

    private boolean hasGazEarsMember(PartyListing party) {
        if (SeqClient.getEasterEggsSetting() == null || !SeqClient.getEasterEggsSetting().getValue()) {
            return false;
        }

        if (party == null) {
            return false;
        }

        for (PartyMember member : party.members) {
            if (member != null && member.playerUUID != null && member.playerUUID.equalsIgnoreCase(GAZ_EARS_UUID)) {
                return true;
            }
        }

        return false;
    }

    private List<String> getRenderedRaidTags(PartyListing party) {
        List<String> raidTags = party.getRaidTags();
        if (!hasLeaMember(party)) {
            return raidTags;
        }

        List<String> rendered = new ArrayList<>(raidTags.size());
        for (String raidTag : raidTags) {
            rendered.add(getRenderedRaidTag(raidTag));
        }
        return rendered;
    }

    private String getRenderedRaidTag(String raidTag) {
        return NEXUS_OF_LIGHT.equals(raidTag) ? NEXUS_OF_LEA : raidTag;
    }

    private String canonicalizeRaidTag(String raidTag) {
        return NEXUS_OF_LEA.equals(raidTag) ? NEXUS_OF_LIGHT : raidTag;
    }

    private String getPartyCardLabel(PartyListing party) {
        String label = party.getRaidTags().size() == 1 ? party.displayLabel() : party.displayShortLabel();
        if (!hasLeaMember(party)) {
            return label;
        }
        return label.replace(NEXUS_OF_LIGHT, NEXUS_OF_LEA);
    }

    private int indexOfCanonicalNexusOfLight(List<String> raidTags) {
        for (int i = 0; i < raidTags.size(); i++) {
            if (NEXUS_OF_LIGHT.equals(canonicalizeRaidTag(raidTags.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    private boolean hasLeaMember(PartyListing party) {
        if (SeqClient.getEasterEggsSetting() == null || !SeqClient.getEasterEggsSetting().getValue()) {
            return false;
        }

        if (party == null) {
            return false;
        }

        for (PartyMember member : party.members) {
            if (member != null && member.playerUUID != null && member.playerUUID.equalsIgnoreCase(LEA_UUID)) {
                return true;
            }
        }

        return false;
    }

    private String partyActionLabel(PartyListing party) {
        if (party == null || party.status == null) {
            return "Join";
        }
        if (party.status == PartyStatus.OPEN && party.joinPolicy == PartyJoinPolicy.INVITE_ONLY) {
            return "Invite";
        }
        return switch (party.status) {
            case OPEN -> "Join";
            case CLOSED -> "Closed";
            case FULL -> "Full";
            default -> "Join";
        };
    }

    private boolean matchesFilters(PartyListing party) {
        // Search filter
        if (!search.text().isEmpty()) {
            boolean matches = false;
            String q = search.text().toLowerCase();
            for (PartyMember m : party.members) {
                if (m.displayName().toLowerCase().contains(q)) {
                    matches = true;
                    break;
                }
            }
            boolean tagMatch = false;
            for (String tag : party.tags) {
                if (tag.toLowerCase().contains(q)) {
                    tagMatch = true;
                    break;
                }
            }
            if (!matches && !tagMatch) return false;
        }

        // Tag filter: party must have at least one tag in activeFilterTags
        if (!activeFilterTags.isEmpty()) {
            boolean hasActiveTag = false;
            for (String tag : party.tags) {
                if (activeFilterTags.contains(tag)) {
                    hasActiveTag = true;
                    break;
                }
            }
            if (!hasActiveTag) return false;
        }

        return true;
    }

    private void maybeRefreshForLoadingNames() {
        long now = System.currentTimeMillis();
        if (now < nextLoadingNameRefreshAtMs) {
            return;
        }

        boolean hasLoadingName = false;
        for (PartyListing listing : party().getParties()) {
            if (!matchesFilters(listing)) {
                continue;
            }
            for (PartyMember member : listing.members) {
                if ("Loading...".equals(member.displayName())) {
                    hasLoadingName = true;
                    break;
                }
            }
            if (hasLoadingName) {
                break;
            }
        }

        if (hasLoadingName) {
            party().actions().refreshData();
            nextLoadingNameRefreshAtMs = now + LOADING_NAME_REFRESH_MS;
        }
    }

    private static void drawText(
            UiCanvas canvas,
            String fontName,
            float fontSize,
            Color textColor,
            float x,
            float y,
            String text,
            UiCanvas.HorizontalAlign horizontalAlign) {
        drawText(
                canvas,
                fontName,
                fontSize,
                textColor,
                x,
                y,
                text,
                horizontalAlign,
                UiCanvas.VerticalAlign.MIDDLE);
    }

    private static void drawText(
            UiCanvas canvas,
            String fontName,
            float fontSize,
            Color textColor,
            float x,
            float y,
            String text,
            UiCanvas.HorizontalAlign horizontalAlign,
            UiCanvas.VerticalAlign verticalAlign) {
        canvas.drawText(
                text,
                x,
                y,
                new UiCanvas.TextStyle(fontName, fontSize, textColor, horizontalAlign, verticalAlign));
    }

    private static float textWidth(String text, String fontName, float fontSize) {
        return UiRenderer.measureText(text, fontName, fontSize).width();
    }

    private static void drawImage(
            UiCanvas canvas,
            AssetManager.Asset asset,
            float x,
            float y,
            float width,
            float height,
            int alpha) {
        if (asset != null && asset.getImage() != null) {
            canvas.drawImage(asset.getImage(), x, y, width, height, Math.max(0, Math.min(255, alpha)) / 255f);
        }
    }

    // ══════════════════════════════ INPUT ══════════════════════════════

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() != 0) return super.mouseClicked(click, outsideScreen);

        UiPointer at = UiPointer.at(click);
        float screenWidth = MinecraftUiRenderer.screenWidth();
        float screenHeight = MinecraftUiRenderer.screenHeight();
        float panelX = SIDEBAR_WIDTH;
        float panelWidth = screenWidth - SIDEBAR_WIDTH;

        // ── Filter screen (highest priority) ──
        if (filterScreenOpen) {
            return handleFilterScreenClick(at, filterOverlay(panelX, panelWidth, screenHeight));
        }
        if (inviteModalOpen) {
            return handleInviteModalClick(at, inviteModal(panelX, panelWidth, screenHeight));
        }
        if (modalOpen) {
            return handleModalClick(at, partyModal(panelX, panelWidth, screenHeight));
        }

        if (roleDropdownOpen) {
            int index = DropdownMenu.optionAt(at.x(), at.y(), roleDropdown.x(), roleDropdown.y() + SEARCH_BAR_HEIGHT,
                    roleDropdown.width(), DropdownMenu.ROW_HEIGHT, ROLES.length, 0);
            roleDropdownOpen = false;
            if (index >= 0) {
                selectedRole = ROLES[index].equals(selectedRole) ? null : ROLES[index];
                if (selectedRole != null) party().actions().setRole(selectedRole);
            }
            return true;
        }

        if (clickSidebar(at)) return true;

        // ── Header, and below it the cards, where they were drawn; then what the player may still do ──
        HeaderControlsLayout headerLayout = drawnHeader(panelWidth);
        if (headerLayout.searchBar().contains(at)) {
            search.focus();
            return true;
        }
        search.blur();

        if (party().isPartyLeader()) {
            // The leader's buttons act on the listing drawn, while the player still leads it, or do nothing.
            Listing current = party().getCurrentListing();
            LeaderControls leader = LeaderControls.clicked(drawnLeaderControls, current, true);
            if (contains(headerLayout.manageButton(), at)) {
                if (leader != null) openModal(true);
                return true;
            }
            if (contains(headerLayout.inviteButton(), at)) {
                if (leader != null) openInviteModal();
                return true;
            }
            if (contains(headerLayout.openCloseButton(), at)) {
                OpenClose clicked = LeaderControls.openCloseClicked(drawnLeaderControls, current, true);
                if (clicked == OpenClose.AUTO_CLOSED) {
                    showStatusBanner("Party is auto-closed at capacity and will reopen when a slot frees.");
                } else if (clicked == OpenClose.OPEN) {
                    party().actions().reopenParty(drawnLeaderControls.listingId());
                } else if (clicked == OpenClose.CLOSE) {
                    party().actions().closeParty(drawnLeaderControls.listingId());
                }
                return true;
            }
            if (contains(headerLayout.delistButton(), at)) {
                if (leader != null) party().actions().delistParty(leader.listingId());
                return true;
            }
            // Invite all and Scan party read the player's listing as they start, just checked to be the one drawn.
            if (contains(headerLayout.inviteAllButton(), at)) {
                if (leader != null) {
                    showStatusBanner("Preparing party invites...");
                    party().reply(party().game().inviteAllCurrentMembers(), (result, error) -> {
                        if (result != null) showStatusBanner(result.message());
                    });
                }
                return true;
            }
            if (contains(headerLayout.scanButton(), at)) {
                if (leader != null) showStatusBanner(party().game().scanCurrentWynnParty().message());
                return true;
            }
        } else if (contains(headerLayout.newPartyButton(), at)) {
            if (party().getJoinedPartyIndex() < 0) {
                openModal(false);
            }
            return true;
        }

        if (headerLayout.roleDropdown().contains(at)) {
            roleDropdownOpen = !roleDropdownOpen;
            return true;
        }

        if (filterButton(panelX, panelWidth, screenHeight).contains(at)) {
            filterScreenOpen = true;
            return true;
        }

        float contentY = headerLayout.height();
        float contentHeight = screenHeight - contentY;
        if (scroll.startDrag(at, scrollTrack(panelX, panelWidth, contentY, contentHeight).inset(-2, 0))) {
            return true;
        }

        if (at.x() < panelX || at.y() < contentY || at.y() > contentY + contentHeight)
            return super.mouseClicked(click, outsideScreen);

        // ── Leader member-management buttons ──
        for (MemberButton button : renderedMemberActionBounds) {
            if (button.bounds().contains(at)) {
                if (!party().isPartyLeader()) {
                    return true;
                }
                if (button.action() == MemberAction.PROMOTE) {
                    party().actions().promoteMemberById(button.listingId(), button.memberUuid());
                } else {
                    party().actions().kickMemberById(button.listingId(), button.memberUuid());
                }
                return true;
            }
        }

        // ── Join/Leave buttons ──
        for (JoinButton button : renderedJoinButtonBounds) {
            if (button.bounds().contains(at)) {
                if (button.leave()) {
                    party().actions().leavePartyById(button.listingId());
                } else if (button.enabled()) {
                    party().actions().joinPartyById(button.listingId(), selectedRole);
                }
                return true;
            }
        }

        // ── Party cards, where they were drawn: an open card closes from its header, a closed one opens anywhere ──
        for (Card card : renderedCards) {
            UiBounds toggle = card.party().expanded
                    ? new UiBounds(card.bounds().x(), card.bounds().y(), card.bounds().width(), CARD_HEADER_HEIGHT)
                    : card.bounds();
            if (toggle.contains(at)) {
                card.party().expanded = !card.party().expanded;
                return true;
            }
        }

        return super.mouseClicked(click, outsideScreen);
    }

    /** Whether {@code at} is on {@code bounds}, a header button that may not be shown. */
    private static boolean contains(UiBounds bounds, UiPointer at) {
        return bounds != null && bounds.contains(at);
    }

    private void openModal(boolean managing) {
        modalOpen = true;
        inviteModalOpen = false;
        reservedSlotsFocused = false;
        if (!managing) {
            applyDefaultModalSelections();
            modalReservedSlots = 0;
        } else {
            applyModalSelectionsFromCurrentListing();
            Listing current = party().getCurrentListing();
            modalReservedSlots = current == null ? 0 : current.reservedSlotCount();
        }
        reservedSlotsInput = String.valueOf(modalReservedSlots);
    }

    private void applyDefaultModalSelections() {
        modalSelectedRaids.clear();
        modalSelectedRegion = PartyRegion.NA;
        modalJoinPolicy = PartyJoinPolicy.DEFAULT_CREATE_POLICY;
    }

    private Set<String> getCurrentListingRaidTags() {
        Set<String> raidTags = new LinkedHashSet<>();
        Listing listing = party().getCurrentListing();
        if (listing == null) {
            return raidTags;
        }

        for (Activity activity : listing.resolvedActivities()) {
            if (activity == null || activity.name() == null || activity.name().isBlank()) {
                continue;
            }
            raidTags.add(PartyListing.backendNameToDisplayName(activity.name().trim()));
        }

        return raidTags;
    }

    private void applyModalSelectionsFromCurrentListing() {
        modalSelectedRaids.clear();
        modalSelectedRaids.addAll(getCurrentListingRaidTags());

        modalSelectedRegion = party().getCurrentListing() != null
                        && party().getCurrentListing().region() != null
                ? party().getCurrentListing().region()
                : PartyRegion.NA;
        modalJoinPolicy = party().getCurrentListing() != null
                ? party().getCurrentListing().resolvedJoinPolicy()
                : PartyJoinPolicy.DEFAULT_CREATE_POLICY;
    }

    private static String joinPolicyLabel(PartyJoinPolicy joinPolicy) {
        return joinPolicy == PartyJoinPolicy.INVITE_ONLY ? "Invite only" : "Open";
    }

    private void openInviteModal() {
        inviteModalOpen = true;
        modalOpen = false;
        filterScreenOpen = false;
        roleDropdownOpen = false;
        inviteUsername = new TextField();
        inviteUsername.focus();
    }

    private static UiBounds inviteModal(float panelX, float panelWidth, float screenHeight) {
        return new UiBounds(panelX + (panelWidth - MODAL_WIDTH) / 2f, (screenHeight - MODAL_HEIGHT) / 2f,
                MODAL_WIDTH, MODAL_HEIGHT);
    }

    private static UiBounds inviteInput(UiBounds modal) {
        return new UiBounds(modal.x() + (MODAL_WIDTH - 180) / 2f, modal.y() + 64 + 12, 180, MODAL_DROPDOWN_H);
    }

    private static UiBounds inviteSendButton(UiBounds modal) {
        return new UiBounds(modal.x() + (MODAL_WIDTH - MODAL_BUTTON_W) / 2f, modal.y() + MODAL_HEIGHT - MODAL_BUTTON_H - 14,
                MODAL_BUTTON_W, MODAL_BUTTON_H);
    }

    private void renderInviteModal(
            UiCanvas canvas, String fontName, float panelX, float panelWidth, float screenHeight) {
        canvas.fillRect(panelX, 0, panelWidth, screenHeight, color(BACKGROUND_MODAL_OVERLAY));

        UiBounds modal = inviteModal(panelX, panelWidth, screenHeight);
        modal.fill(canvas, color(BACKGROUND_BODY_OPAQUE));
        modal.stroke(canvas, 1, color(ACCENT_SECONDARY));
        drawText(canvas, fontName, MODAL_TITLE_SIZE, color(TEXT_PRIMARY), modal.centerX(), modal.y() + 22,
                "Invite Player", UiCanvas.HorizontalAlign.CENTER);
        drawText(canvas, fontName, MODAL_LABEL_SIZE, color(TEXT_MUTED), modal.centerX(), modal.y() + 64, "Username",
                UiCanvas.HorizontalAlign.CENTER);

        UiBounds input = inviteInput(modal);
        boolean focused = inviteUsername.focused();
        input.fill(canvas, focused ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
        input.stroke(canvas, 1, focused ? color(CONTROL_BORDER) : color(CONTROL_INPUT_SECONDARY));
        boolean placeholder = inviteUsername.text().isBlank() && !focused;
        drawText(canvas, fontName, MODAL_LABEL_SIZE, placeholder ? color(TEXT_MUTED) : color(TEXT_PRIMARY),
                input.x() + 8, input.centerY(), placeholder ? "Enter username" : inviteUsername.text(),
                UiCanvas.HorizontalAlign.LEFT);

        UiBounds send = inviteSendButton(modal);
        send.fill(canvas, send.contains(pointer) ? color(ACCENT_PRIMARY_HOVER) : color(ACCENT_PRIMARY));
        drawText(canvas, fontName, MEMBER_FONT_SIZE, color(TEXT_PRIMARY), send.centerX(), send.centerY(), "Send",
                UiCanvas.HorizontalAlign.CENTER);
    }

    private boolean handleInviteModalClick(UiPointer at, UiBounds modal) {
        if (!modal.contains(at)) {
            inviteModalOpen = false;
            inviteUsername.blur();
            return true;
        }
        if (inviteInput(modal).contains(at)) {
            inviteUsername.focus();
            return true;
        }
        inviteUsername.blur();
        if (inviteSendButton(modal).contains(at)) {
            submitInviteFromModal();
        }
        return true;
    }

    private void submitInviteFromModal() {
        String username = inviteUsername.text().trim();
        if (username.isEmpty()) {
            showStatusBanner("Enter a username to invite.");
            return;
        }
        party().actions().createInvite(username);
        inviteModalOpen = false;
        inviteUsername.blur();
    }

    private boolean handleModalClick(UiPointer at, PartyModal modal) {
        // Click outside modal closes it
        if (!modal.box().contains(at)) {
            modalOpen = false;
            reservedSlotsFocused = false;
            return true;
        }

        for (int i = 0; i < RAID_TYPES.length; i++) {
            String rt = RAID_TYPES[i];
            if (modal.raidCircle(i).contains(at)) {
                if (modalSelectedRaids.contains(rt)) {
                    modalSelectedRaids.remove(rt);
                } else if ("Prelude to Annihilation".equals(rt)) {
                    modalSelectedRaids.clear();
                    modalSelectedRaids.add(rt);
                } else {
                    modalSelectedRaids.remove("Prelude to Annihilation");
                    modalSelectedRaids.add(rt);
                }
                modalReservedSlots = Math.min(modalReservedSlots, maxReservedSlots());
                reservedSlotsInput = String.valueOf(modalReservedSlots);
                return true;
            }
        }

        if (modal.upArrow().contains(at)) {
            setReservedSlots(modalReservedSlots + 1);
            reservedSlotsFocused = false;
            return true;
        }
        if (modal.downArrow().contains(at)) {
            setReservedSlots(modalReservedSlots - 1);
            reservedSlotsFocused = false;
            return true;
        }
        if (modal.reservedField().contains(at)) {
            reservedSlotsFocused = true;
            reservedSlotsInput = String.valueOf(modalReservedSlots);
            return true;
        }
        if (reservedSlotsFocused) {
            commitReservedSlotsInput();
            reservedSlotsFocused = false;
        }

        for (int i = 0; i < PARTY_REGIONS.length; i++) {
            if (modal.region(i).contains(at)) {
                modalSelectedRegion = PARTY_REGIONS[i];
                reservedSlotsFocused = false;
                return true;
            }
        }
        for (int i = 0; i < JOIN_POLICIES.length; i++) {
            if (modal.joinPolicy(i).contains(at)) {
                modalJoinPolicy = JOIN_POLICIES[i];
                reservedSlotsFocused = false;
                return true;
            }
        }

        String createLabel = createLabel();
        if (modal.createButton(modalActionButtonWidth(createLabel, SeqClient.getFontManager().getSelectedFont())).contains(at)) {
            submitPartyFromModal();
        }
        return true;
    }

    private void submitPartyFromModal() {
        boolean updatingParty = party().getJoinedPartyIndex() >= 0;
        Set<String> selectedRaids = new LinkedHashSet<>(modalSelectedRaids);
        if (updatingParty && selectedRaids.isEmpty()) {
            selectedRaids.addAll(getCurrentListingRaidTags());
        }
        if (selectedRaids.isEmpty()) {
            showStatusBanner(updatingParty
                    ? "No raid selected and no current raid could be reused."
                    : "Select at least one raid before creating a party.");
            return;
        }
        if (selectedRaids.contains("Prelude to Annihilation") && selectedRaids.size() > 1) {
            showStatusBanner("Anni cannot be selected alongside other raids.");
            return;
        }

        commitReservedSlotsInput();
        List<String> activityNames = new ArrayList<>(selectedRaids);
        if (updatingParty) {
            party().actions().updateParty(
                    activityNames, selectedRole, modalReservedSlots, modalSelectedRegion, modalJoinPolicy);
        } else {
            party().actions().createParty(
                    activityNames, selectedRole, modalReservedSlots, modalSelectedRegion, modalJoinPolicy);
        }
        modalOpen = false;
        reservedSlotsFocused = false;
        scroll.reset();
    }

    /** Up to three reserved slots, or nine for Prelude to Annihilation. */
    private int maxReservedSlots() {
        return modalSelectedRaids.contains("Prelude to Annihilation") ? 9 : 3;
    }

    private void setReservedSlots(int count) {
        modalReservedSlots = Math.max(0, Math.min(maxReservedSlots(), count));
        reservedSlotsInput = String.valueOf(modalReservedSlots);
    }

    private boolean handleFilterScreenClick(UiPointer at, UiBounds overlay) {
        // The back button, and a click outside the overlay, close it.
        if (filterBackButton(overlay).contains(at) || !overlay.contains(at)) {
            filterScreenOpen = false;
            return true;
        }
        if (moveChip(at, renderedFilterActiveChipBounds, activeFilterTags, inactiveFilterTags)) {
            return true;
        }
        moveChip(at, renderedFilterInactiveChipBounds, inactiveFilterTags, activeFilterTags);
        return true;
    }

    /** Moves the tag whose chip is under {@code at} from one box to the other, with a swing. */
    private boolean moveChip(UiPointer at, List<TagChip> chips, Set<String> from, Set<String> to) {
        for (TagChip chip : chips) {
            if (chip.contains(at)) {
                from.remove(chip.tag());
                to.add(chip.tag());
                filterTagAnimStartTimes.put(chip.tag(), System.currentTimeMillis());
                return true;
            }
        }
        return false;
    }

    private String getTagChipLabel(String tag, boolean isActive) {
        return getDisplayTag(tag) + (isActive ? " -" : " +");
    }

    private String getDisplayTag(String tag) {
        return switch (tag) {
            case "Nest of the Grootslangs" -> "NOG";
            case "The Nameless Anomaly" -> "TNA";
            case "The Canyon Colossus" -> "TCC";
            case "Nexus of Light" -> "NOL";
            case "The Wartorn Palace" -> "TWP";
            case "Prelude to Annihilation" -> "PTA";
            default -> tag;
        };
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent click) {
        scroll.release();
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        float screenHeight = MinecraftUiRenderer.screenHeight();
        float panelWidth = MinecraftUiRenderer.screenWidth() - SIDEBAR_WIDTH;
        float headerHeight = drawnHeader(panelWidth).height();
        UiBounds track = scrollTrack(SIDEBAR_WIDTH, panelWidth, headerHeight, screenHeight - headerHeight);
        return scroll.drag(UiPointer.at(click), track) || super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!modalOpen && !inviteModalOpen && !filterScreenOpen) {
            scroll.scrollBy(-(float) scrollY * SCROLL_SPEED);
        }
        return true;
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent keyEvent) {
        if (roleDropdownOpen && keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) {
            roleDropdownOpen = false;
            return true;
        }

        if (filterScreenOpen) {
            if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) {
                filterScreenOpen = false;
                return true;
            }
            return true;
        }
        if (inviteModalOpen) {
            int keyCode = keyEvent.key();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                inviteModalOpen = false;
                inviteUsername.blur();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitInviteFromModal();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                inviteUsername.backspace();
                return true;
            }
            return true;
        }
        if (modalOpen) {
            int keyCode = keyEvent.key();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (reservedSlotsFocused) {
                    commitReservedSlotsInput();
                    reservedSlotsFocused = false;
                } else {
                    modalOpen = false;
                }
                return true;
            }
            if (reservedSlotsFocused) {
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    commitReservedSlotsInput();
                    reservedSlotsFocused = false;
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                    if (!reservedSlotsInput.isEmpty()) {
                        reservedSlotsInput = reservedSlotsInput.substring(0, reservedSlotsInput.length() - 1);
                    }
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_UP) {
                    setReservedSlots(modalReservedSlots + 1);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_DOWN) {
                    setReservedSlots(modalReservedSlots - 1);
                    return true;
                }
            }
            return true;
        }
        if (search.focused()) {
            int keyCode = keyEvent.key();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                search.blur();
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && search.backspace()) {
                scroll.reset();
            }
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean charTyped(@NotNull CharacterEvent characterEvent) {
        String typedText = TextInputHelper.getTypedText(characterEvent);
        if (filterScreenOpen) {
            return true;
        }
        if (inviteModalOpen) {
            if (typedText != null
                    && typedText.length() == 1
                    && TextInputFilters.isMinecraftUsernameCharacter(typedText.charAt(0))
                    && inviteUsername.text().length() < 16) {
                inviteUsername.type(typedText);
            }
            return true;
        }
        if (modalOpen) {
            if (reservedSlotsFocused
                    && typedText != null
                    && typedText.length() == 1
                    && typedText.charAt(0) >= '0'
                    && typedText.charAt(0) <= '9'
                    && reservedSlotsInput.length() < 2) {
                reservedSlotsInput += typedText;
            }
            return true;
        }
        if (search.focused()) {
            if (search.type(typedText)) {
                scroll.reset();
            }
            return true;
        }
        return super.charTyped(characterEvent);
    }
}
