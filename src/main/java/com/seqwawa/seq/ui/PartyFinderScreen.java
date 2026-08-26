package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import java.awt.Color;
import java.util.*;
import java.util.List;

import com.seqwawa.seq.model.Listing;
import com.seqwawa.seq.model.Member;
import com.seqwawa.seq.model.PartyCloseReason;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import com.seqwawa.seq.accessors.PartyAccessor;
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

public class PartyFinderScreen extends Screen implements PartyAccessor {

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
    private static final float SIDEBAR_WIDTH = 140;
    private static final float SIDEBAR_PADDING = 10;
    private static final float SIDEBAR_BUTTON_HEIGHT = 22;
    private static final float SIDEBAR_BUTTON_SPACING = 6;
    private static final float HEADER_HEIGHT = 30;
    private static final float PADDING = 8;
    private static final float SEARCH_BAR_HEIGHT = 18;
    private static final float SEARCH_BAR_WIDTH = 140;
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
    private static final float SIDEBAR_TITLE_SIZE = 16;
    private static final float SIDEBAR_BUTTON_SIZE = 12;
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

    // ── State ──
    private final Screen parent;
    private final boolean openCreateModalOnInit;
    private float uiMouseX, uiMouseY;
    private float scrollOffset = 0;
    private float maxScroll = 0;
    private boolean scrollbarDragging = false;
    private float scrollbarDragStart = 0;
    private float scrollOffsetDragStart = 0;

    private boolean searchFocused = false;
    private String searchQuery = "";
    private int searchCursorBlink = 0;

    private boolean roleDropdownOpen = false;
    private String selectedRole = null;

    private float dropdownRenderX, dropdownRenderY, dropdownRenderW;

    // ── Modal state ──
    private boolean modalOpen = false;
    private boolean inviteModalOpen = false;
    private boolean inviteUsernameFocused = false;
    private String inviteUsernameInput = "";
    private final Set<String> modalSelectedRaids = new LinkedHashSet<>();
    private int modalReservedSlots = 0;
    private PartyRegion modalSelectedRegion = PartyRegion.NA;
    private PartyJoinPolicy modalJoinPolicy = PartyJoinPolicy.DEFAULT_CREATE_POLICY;
    private boolean reservedSlotsFocused = false;
    private String reservedSlotsInput = "0";
    private long nextLoadingNameRefreshAtMs = 0L;

    // Cached modal position
    private float modalX, modalY;

    // ── Filter+ screen state ──
    private boolean filterScreenOpen = false;
    private final Set<String> activeFilterTags = new LinkedHashSet<>();
    private final Set<String> inactiveFilterTags = new LinkedHashSet<>();
    private final Map<String, Long> filterTagAnimStartTimes = new HashMap<>();
    private final List<TagChipHitbox> renderedFilterActiveChipBounds = new ArrayList<>();
    private final List<TagChipHitbox> renderedFilterInactiveChipBounds = new ArrayList<>();

    // ── Leader member management ──
    private final List<MemberActionHitbox> renderedMemberActionBounds = new ArrayList<>();
    private String activeStatusBannerMessage;
    private long activeStatusBannerExpiresAtMs;

    private static class TagChipHitbox {
        private final String tag;
        private final float x;
        private final float y;
        private final float w;
        private final float h;

        private TagChipHitbox(String tag, float x, float y, float w, float h) {
            this.tag = tag;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        private boolean contains(float mx, float my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    private enum MemberAction {
        PROMOTE,
        KICK
    }

    private record MemberActionHitbox(
            MemberAction action, int partyIndex, int memberIndex, float x, float y, float w, float h) {
        private boolean contains(float mouseX, float mouseY) {
            return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        }
    }

    private record HeaderButtonBounds(float x, float y, float w, float h) {}

    private record HeaderControlsLayout(
            HeaderButtonBounds searchBar,
            HeaderButtonBounds manageButton,
            HeaderButtonBounds inviteButton,
            HeaderButtonBounds openCloseButton,
            HeaderButtonBounds delistButton,
            HeaderButtonBounds inviteAllButton,
            HeaderButtonBounds scanButton,
            HeaderButtonBounds newPartyButton,
            HeaderButtonBounds roleDropdown,
            float height) {}

    public PartyFinderScreen(Screen parent) {
        this(parent, false);
    }

    public PartyFinderScreen(Screen parent, boolean openCreateModalOnInit) {
        super(Component.literal("Party Finder"));
        this.parent = parent;
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
        party().refreshData();
        if (openCreateModalOnInit && party().getJoinedPartyIndex() < 0 && !party().hasListedParty()) {
            openModal(false);
        }
    }

    // ══════════════════════════════ RENDER ══════════════════════════════

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        uiMouseX = MinecraftUiRenderer.mouseX(mouseX);
        uiMouseY = MinecraftUiRenderer.mouseY(mouseY);

        UiRenderer.renderScreen(this, canvas -> {
            float screenWidth = canvas.metrics().width();
            float screenHeight = canvas.metrics().height();
            String fontName = SeqClient.getFontManager().getSelectedFont();

            canvas.fillRect(0, 0, screenWidth, screenHeight, color(BACKGROUND_OVERLAY));
            renderSidebar(canvas, fontName, screenHeight);

            float panelX = SIDEBAR_WIDTH;
            float panelWidth = screenWidth - SIDEBAR_WIDTH;
            HeaderControlsLayout headerLayout = computeHeaderControlsLayout(panelX, panelWidth, fontName);
            float headerHeight = headerLayout.height();

            canvas.fillRect(panelX, 0, panelWidth, screenHeight, color(BACKGROUND_BODY));
            canvas.fillRect(panelX, 0, panelWidth, headerHeight, color(BACKGROUND_HEADER));
            renderHeaderControls(canvas, fontName, headerLayout);

            // Content area
            float contentX = panelX;
            float contentY = headerHeight;
            float contentWidth = panelWidth;
            float contentHeight = screenHeight - headerHeight;

            renderedMemberActionBounds.clear();

            canvas.save();
            canvas.scissor(contentX, contentY, contentWidth, contentHeight);

            float cursorY = contentY - scrollOffset + PADDING;
            for (int i = 0; i < party().getParties().size(); i++) {
                PartyListing party = party().getParties().get(i);
                if (!matchesFilters(party)) continue;

                float cardH = party.expanded
                        ? CARD_HEADER_HEIGHT + party.members.size() * MEMBER_ROW_HEIGHT + CARD_PADDING
                        : COLLAPSED_ROW_HEIGHT;

                renderPartyCard(
                        canvas, fontName, contentX + PADDING, cursorY, contentWidth - PADDING * 2 - 6, cardH, party, i);
                cursorY += cardH + CARD_SPACING;
            }

            maxScroll = Math.max(0, cursorY + scrollOffset - contentY - contentHeight);
            canvas.restore();

            // Scrollbar
            if (maxScroll > 0) {
                float scrollbarX = panelX + panelWidth - 5;
                canvas.fillRect(scrollbarX, contentY, 4, contentHeight, color(CONTROL_TRACK));
                float thumbRatio = contentHeight / (contentHeight + maxScroll);
                float thumbH = Math.max(20, contentHeight * thumbRatio);
                float thumbY = contentY + (scrollOffset / maxScroll) * (contentHeight - thumbH);
                canvas.fillRect(scrollbarX, thumbY, 4, thumbH, color(CONTROL_THUMB));
            }

            // Filter + button (bottom right of content area)
            float filterX = panelX + panelWidth - FILTER_BUTTON_W - FILTER_BUTTON_MARGIN;
            float filterY = screenHeight - FILTER_BUTTON_H - FILTER_BUTTON_MARGIN;
            boolean filterHovered = isHovered(uiMouseX, uiMouseY, filterX, filterY, FILTER_BUTTON_W, FILTER_BUTTON_H);
            canvas.fillRect(
                    filterX,
                    filterY,
                    FILTER_BUTTON_W,
                    FILTER_BUTTON_H,
                    filterHovered ? color(ACCENT_PRIMARY_HOVER, 220) : color(ACCENT_PRIMARY, 200));
            drawText(
                    canvas,
                    fontName,
                    HEADER_BUTTON_SIZE,
                    color(TEXT_PRIMARY),
                    filterX + FILTER_BUTTON_W / 2f,
                    filterY + FILTER_BUTTON_H / 2f,
                    "Filter +",
                    UiCanvas.HorizontalAlign.CENTER);

            // Role dropdown overlay
            if (roleDropdownOpen && !modalOpen && !inviteModalOpen && !filterScreenOpen) {
                renderRoleDropdownMenu(canvas, fontName);
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
        });
    }

    @Override
    public void tick() {
        super.tick();
        String managerError = party().consumeLatestPartyError();
        if (managerError != null && !managerError.isBlank()) {
            showStatusBanner(managerError);
        }
        maybeRefreshForLoadingNames();
    }

    private void showStatusBanner(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        activeStatusBannerMessage = message;
        activeStatusBannerExpiresAtMs = System.currentTimeMillis() + STATUS_BANNER_DURATION_MS;
    }

    private void showErrorPopup(String message) {
        showStatusBanner(message);
    }

    private void renderStatusBanner(
            UiCanvas canvas, String fontName, float panelX, float panelWidth, float screenHeight) {
        if (activeStatusBannerMessage == null || activeStatusBannerMessage.isBlank()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now >= activeStatusBannerExpiresAtMs) {
            activeStatusBannerMessage = null;
            return;
        }

        float textW = textWidth(activeStatusBannerMessage, fontName, 12);
        float maxPopupW = Math.max(180f, panelWidth - 20f);
        float popupW = Math.min(maxPopupW, Math.max(STATUS_BANNER_MIN_W, textW + 28f));

        float popupX = panelX + (panelWidth - popupW) / 2f;
        float popupY = screenHeight - STATUS_BANNER_H - 10;

        canvas.fillRect(popupX, popupY, popupW, STATUS_BANNER_H, color(ACCENT_PRIMARY_DARK, 235));
        canvas.strokeRect(popupX, popupY, popupW, STATUS_BANNER_H, 1, color(ACCENT_PRIMARY));
        drawText(
                canvas,
                fontName,
                12,
                color(TEXT_PRIMARY),
                popupX + popupW / 2f,
                popupY + STATUS_BANNER_H / 2f,
                activeStatusBannerMessage,
                UiCanvas.HorizontalAlign.CENTER);
    }

    // ── Sidebar ──

    private void renderSidebar(UiCanvas canvas, String fontName, float screenHeight) {
        canvas.fillRect(0, 0, SIDEBAR_WIDTH, screenHeight, color(BACKGROUND_SIDEBAR));
        drawText(
                canvas,
                fontName,
                SIDEBAR_TITLE_SIZE,
                color(ACCENT_PRIMARY),
                SIDEBAR_WIDTH / 2f,
                22,
                "Sequoia",
                UiCanvas.HorizontalAlign.CENTER);
        canvas.fillRect(SIDEBAR_PADDING, 40, SIDEBAR_WIDTH - SIDEBAR_PADDING * 2, 1, color(ACCENT_DIVIDER));

        float btnX = SIDEBAR_PADDING;
        float btnW = SIDEBAR_WIDTH - SIDEBAR_PADDING * 2;

        var destinations = SequoiaSidebarNavigation.destinations();
        var layout = SequoiaSidebarNavigation.sidebarLayout(
                screenHeight, destinations.size(), SIDEBAR_BUTTON_HEIGHT, SIDEBAR_BUTTON_SPACING);
        for (int row = 0; row < destinations.size(); row++) {
            var destination = destinations.get(row);
            drawSidebarButton(
                    canvas,
                    fontName,
                    btnX,
                    layout.buttonY(row),
                    btnW,
                    layout.buttonHeight(),
                    destination.label(),
                    destination == SequoiaSidebarNavigation.Destination.PARTY_FINDER);
        }
    }

    private void drawSidebarButton(
            UiCanvas canvas, String fontName, float x, float y, float w, float h, String label, boolean active) {
        boolean hovered = isHovered(uiMouseX, uiMouseY, x, y, w, h);
        Color bg = active ? color(ACCENT_PRIMARY_DARK) : (hovered ? color(BACKGROUND_CONTENT_FOCUSED) : color(BACKGROUND_CONTENT));
        canvas.fillRect(x, y, w, h, bg);
        drawText(
                canvas,
                fontName,
                Math.min(SIDEBAR_BUTTON_SIZE, Math.max(8, h - 2)),
                color(TEXT_PRIMARY),
                x + w / 2f,
                y + h / 2f,
                label,
                UiCanvas.HorizontalAlign.CENTER);
    }

    // ── Header ──

    private void renderHeaderControls(UiCanvas canvas, String fontName, HeaderControlsLayout layout) {
        searchCursorBlink++;
        HeaderButtonBounds searchBar = layout.searchBar();
        float searchX = searchBar.x();
        float searchY = searchBar.y();

        Color searchBg = searchFocused ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT);
        canvas.fillRect(searchX, searchY, searchBar.w(), searchBar.h(), searchBg);
        if (searchFocused) {
            canvas.strokeRect(searchX, searchY, searchBar.w(), searchBar.h(), 1, color(CONTROL_BORDER));
        }

        canvas.save();
        canvas.scissor(searchX, searchY, searchBar.w(), searchBar.h());
        if (searchQuery.isEmpty() && !searchFocused) {
            drawText(
                    canvas,
                    fontName,
                    SEARCH_FONT_SIZE,
                    color(TEXT_DISABLED),
                    searchX + 6,
                    searchY + SEARCH_BAR_HEIGHT / 2f,
                    "Search...",
                    UiCanvas.HorizontalAlign.LEFT);
        } else {
            drawText(
                    canvas,
                    fontName,
                    SEARCH_FONT_SIZE,
                    color(TEXT_PRIMARY),
                    searchX + 6,
                    searchY + SEARCH_BAR_HEIGHT / 2f,
                    searchQuery,
                    UiCanvas.HorizontalAlign.LEFT);
        }
        canvas.restore();

        if (searchFocused && (searchCursorBlink / 1000) % 2 == 0) {
            float textW = searchQuery.isEmpty() ? 0 : textWidth(searchQuery, fontName, SEARCH_FONT_SIZE);
            canvas.fillRect(searchX + 6 + textW + 1, searchY + 3, 1, searchBar.h() - 6, color(TEXT_PRIMARY));
        }

        if (party().isPartyLeader()) {
            String manageLabel = party().hasListedParty() ? "Manage Party" : "New party +";
            drawHeaderButton(canvas, fontName, layout.manageButton(), manageLabel, color(ACCENT_PRIMARY, 200), color(ACCENT_PRIMARY_HOVER, 220));
            drawHeaderButton(canvas, fontName, layout.inviteButton(), "Invite", color(ACCENT_PRIMARY, 200), color(ACCENT_PRIMARY_HOVER, 220));
            boolean autoClosed = isCurrentListingAutoClosed();
            String openCloseLabel = autoClosed ? "Auto-closed" : (isCurrentListingClosed() ? "Open party" : "Close party");
            Color openCloseBg = autoClosed ? color(ACCENT_DISABLED) : color(ACCENT_PRIMARY_DARK_HOVER, 200);
            Color openCloseHover = autoClosed ? color(ACCENT_DISABLED) : color(ACCENT_PRIMARY_DARK_HOVER, 220);
            drawHeaderButton(
                    canvas,
                    fontName,
                    layout.openCloseButton(),
                    openCloseLabel,
                    openCloseBg,
                    openCloseHover);
            drawHeaderButton(
                    canvas, fontName, layout.delistButton(), "Delist party", color(CONTROL_DANGER, 200), color(CONTROL_DANGER_HOVER));
            drawHeaderButton(canvas, fontName, layout.inviteAllButton(), "Invite all", color(ACCENT_PRIMARY, 200), color(ACCENT_PRIMARY_HOVER, 220));
            drawHeaderButton(canvas, fontName, layout.scanButton(), "Scan party", color(ACCENT_PRIMARY, 200), color(ACCENT_PRIMARY_HOVER, 220));
        } else {
            boolean inPartyAsMember = party().getJoinedPartyIndex() >= 0;
            Color newBg = inPartyAsMember ? color(ACCENT_DISABLED, 180) : color(ACCENT_PRIMARY, 200);
            Color newHover = inPartyAsMember ? color(ACCENT_DISABLED, 180) : color(ACCENT_PRIMARY_HOVER, 220);
            drawHeaderButton(canvas, fontName, layout.newPartyButton(), "New party +", newBg, newHover);
        }

        dropdownRenderX = layout.roleDropdown().x();
        dropdownRenderY = layout.roleDropdown().y();
        dropdownRenderW = layout.roleDropdown().w();
        renderRoleDropdownButton(canvas, fontName, layout.roleDropdown());
    }

    private void drawHeaderButton(
            UiCanvas canvas, String fontName, float x, float y, float w, float h, String label, Color bg, Color hoverBg) {
        boolean hovered = isHovered(uiMouseX, uiMouseY, x, y, w, h);
        canvas.fillRect(x, y, w, h, hovered ? hoverBg : bg);
        drawText(
                canvas,
                fontName,
                HEADER_BUTTON_SIZE,
                color(TEXT_PRIMARY),
                x + w / 2f,
                y + h / 2f,
                label,
                UiCanvas.HorizontalAlign.CENTER);
    }

    private void drawHeaderButton(
            UiCanvas canvas, String fontName, HeaderButtonBounds bounds, String label, Color bg, Color hoverBg) {
        if (bounds == null) {
            return;
        }
        drawHeaderButton(canvas, fontName, bounds.x(), bounds.y(), bounds.w(), bounds.h(), label, bg, hoverBg);
    }

    private void drawStatusBadge(
            UiCanvas canvas,
            String fontName,
            HeaderButtonBounds bounds,
            PartyStatus status,
            PartyCloseReason closeReason,
            PartyJoinPolicy joinPolicy) {
        if (bounds == null) {
            return;
        }
        drawStatusBadge(
                canvas,
                fontName,
                bounds.x(),
                bounds.y(),
                bounds.w(),
                bounds.h(),
                status,
                closeReason,
                joinPolicy);
    }

    private void drawStatusBadge(
            UiCanvas canvas,
            String fontName,
            float x,
            float y,
            float w,
            float h,
            PartyStatus status,
            PartyCloseReason closeReason,
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
                statusBadgeLabel(status, closeReason, joinPolicy),
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

    private String statusBadgeLabel(
            PartyStatus status, PartyCloseReason closeReason, PartyJoinPolicy joinPolicy) {
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

    private HeaderControlsLayout computeHeaderControlsLayout(float panelX, float panelWidth, String fontName) {
        float searchX = panelX + SEARCH_BAR_MARGIN;
        float searchY = (HEADER_HEIGHT - SEARCH_BAR_HEIGHT) / 2f;
        float rightEdge = panelX + panelWidth - SEARCH_BAR_MARGIN;
        float roleDropdownWidth = Math.min(roleDropdownWidth(fontName), Math.max(50f, panelWidth * 0.3f));
        float roleDropdownX = rightEdge - roleDropdownWidth;
        float searchWidth = Math.min(
                SEARCH_BAR_WIDTH,
                Math.max(60f, roleDropdownX - searchX - HEADER_BUTTON_SPACING));
        HeaderButtonBounds searchBar = new HeaderButtonBounds(searchX, searchY, searchWidth, SEARCH_BAR_HEIGHT);
        HeaderButtonBounds roleDropdown =
                new HeaderButtonBounds(roleDropdownX, searchY, roleDropdownWidth, SEARCH_BAR_HEIGHT);

        float nextButtonX = searchX + searchWidth + HEADER_BUTTON_SPACING;
        float buttonY = searchY;
        float rowRightEdge = roleDropdownX - HEADER_BUTTON_SPACING;
        HeaderButtonBounds manageButton = null;
        HeaderButtonBounds inviteButton = null;
        HeaderButtonBounds openCloseButton = null;
        HeaderButtonBounds delistButton = null;
        HeaderButtonBounds inviteAllButton = null;
        HeaderButtonBounds scanButton = null;
        HeaderButtonBounds newPartyButton = null;

        List<Float> widths = new ArrayList<>();
        if (party().isPartyLeader()) {
            widths.add(paddedHeaderButtonWidth(party().hasListedParty() ? "Manage Party" : "New party +", fontName));
            widths.add(paddedHeaderButtonWidth("Invite", fontName));
            widths.add(paddedHeaderButtonWidth(
                    isCurrentListingAutoClosed()
                            ? "Auto-closed"
                            : (isCurrentListingClosed() ? "Open party" : "Close party"),
                    fontName));
            widths.add(paddedHeaderButtonWidth("Delist party", fontName));
            widths.add(paddedHeaderButtonWidth("Invite all", fontName));
            widths.add(paddedHeaderButtonWidth("Scan party", fontName));
        } else {
            widths.add(paddedHeaderButtonWidth("New party +", fontName));
        }

        HeaderButtonBounds[] buttons = new HeaderButtonBounds[widths.size()];
        for (int index = 0; index < widths.size(); index++) {
            float width = Math.min(widths.get(index), rightEdge - searchX);
            if (nextButtonX + width > rowRightEdge) {
                buttonY += SEARCH_BAR_HEIGHT + HEADER_BUTTON_SPACING;
                nextButtonX = searchX;
                rowRightEdge = rightEdge;
            }
            buttons[index] = new HeaderButtonBounds(nextButtonX, buttonY, width, SEARCH_BAR_HEIGHT);
            nextButtonX += width + HEADER_BUTTON_SPACING;
        }

        if (party().isPartyLeader()) {
            manageButton = buttons[0];
            inviteButton = buttons[1];
            openCloseButton = buttons[2];
            delistButton = buttons[3];
            inviteAllButton = buttons[4];
            scanButton = buttons[5];
        } else {
            newPartyButton = buttons[0];
        }

        float headerHeight = Math.max(HEADER_HEIGHT, buttonY + SEARCH_BAR_HEIGHT + SEARCH_BAR_MARGIN);
        return new HeaderControlsLayout(
                searchBar,
                manageButton,
                inviteButton,
                openCloseButton,
                delistButton,
                inviteAllButton,
                scanButton,
                newPartyButton,
                roleDropdown,
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

    private void renderRoleDropdownButton(UiCanvas canvas, String fontName, HeaderButtonBounds bounds) {
        float x = bounds.x();
        float y = bounds.y();
        float w = bounds.w();
        float h = bounds.h();
        boolean hovered = isHovered(uiMouseX, uiMouseY, x, y, w, h);
        canvas.fillRect(x, y, w, h, hovered ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
        canvas.strokeRect(x, y, w, h, 1, color(CONTROL_INPUT_SECONDARY));

        String label = selectedRole != null ? selectedRole : "Your role";
        drawText(
                canvas,
                fontName,
                HEADER_BUTTON_SIZE,
                color(TEXT_PRIMARY),
                x + HEADER_BUTTON_HORIZONTAL_PADDING,
                y + h / 2f,
                label,
                UiCanvas.HorizontalAlign.LEFT);
        drawTriangle(canvas, x + w - 8, y + h / 2f, 5, false, color(ACCENT_SECONDARY));
    }

    private void renderRoleDropdownMenu(UiCanvas canvas, String fontName) {
        float x = dropdownRenderX;
        float y = dropdownRenderY + SEARCH_BAR_HEIGHT;
        float w = dropdownRenderW;
        float itemH = 20;
        float totalH = ROLES.length * itemH;

        canvas.fillRect(x, y, w, totalH, color(BACKGROUND_POPUP));
        canvas.strokeRect(x, y, w, totalH, 1, color(CONTROL_INPUT_SECONDARY));

        for (int i = 0; i < ROLES.length; i++) {
            float itemY = y + i * itemH;
            boolean itemHovered = isHovered(uiMouseX, uiMouseY, x, itemY, w, itemH);
            if (itemHovered) {
                canvas.fillRect(x, itemY, w, itemH, color(CONTROL_INPUT_HOVER));
            }
            drawText(
                    canvas,
                    fontName,
                    MEMBER_FONT_SIZE,
                    color(TEXT_PRIMARY),
                    x + 6,
                    itemY + itemH / 2f,
                    ROLES[i],
                    UiCanvas.HorizontalAlign.LEFT);
        }
    }

    // ── Party cards ──

    private void renderPartyCard(
            UiCanvas canvas, String fontName, float x, float y, float w, float h, PartyListing party, int partyIndex) {
        boolean isJoined = party().getJoinedPartyIndex() == partyIndex;
        canvas.fillRect(x, y, w, h, party.expanded ? color(BACKGROUND_CONTENT_FOCUSED) : color(BACKGROUND_CONTENT));

        if (party.expanded) {
            renderExpandedCard(canvas, fontName, x, y, w, h, party, partyIndex, isJoined);
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
            int partyIndex,
            boolean isJoined) {
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
                party.closeReason,
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
        boolean isMyParty = partyIndex == party().getMyPartyIndex() && party().isPartyLeader();
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
                    partyIndex,
                    mi,
                    amLeaderOfThisParty);
            memberY += MEMBER_ROW_HEIGHT;
        }

        float lastMemberCenterY = memberY - MEMBER_ROW_HEIGHT / 2f;

        // Join/Leave. Leaders already know they are in their own party, and keeping
        // this area free gives their member-management buttons a stable home.
        float joinX = x + w - CARD_PADDING - JOIN_BUTTON_WIDTH;
        float joinY = memberY - MEMBER_ROW_HEIGHT + (MEMBER_ROW_HEIGHT - BUTTON_HEIGHT) / 2f;
        boolean alreadyInParty = party().getJoinedPartyIndex() >= 0 && !isJoined;
        boolean listingUnavailable = !isJoined && !party.isJoinable();
        if (!isMyParty) {
            boolean buttonDisabled = alreadyInParty || listingUnavailable;
            boolean joinHovered =
                    !buttonDisabled && isHovered(uiMouseX, uiMouseY, joinX, joinY, JOIN_BUTTON_WIDTH, BUTTON_HEIGHT);
            Color joinBg = buttonDisabled
                    ? color(ACCENT_DISABLED)
                    : (joinHovered ? color(ACCENT_PRIMARY_HOVER) : color(ACCENT_PRIMARY));
            canvas.fillRect(joinX, joinY, JOIN_BUTTON_WIDTH, BUTTON_HEIGHT, joinBg);

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
                party.closeReason,
                party.joinPolicy);
        rowX += STATUS_BADGE_W + 8;

        float rightX = x + w - CARD_PADDING;
        float reservedRightWidth = 22;
        for (int j = 0; j < party.members.size(); j++) {
            if (getClassIcon(party.members.get(j).className) != null) {
                reservedRightWidth += CLASS_ICON_SIZE + 4;
            }
        }
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

        for (int j = party.members.size() - 1; j >= 0; j--) {
            AssetManager.Asset icon = getClassIcon(party.members.get(j).className);
            if (icon != null) {
                float iconX = rightX - CLASS_ICON_SIZE;
                float iconY = y + (COLLAPSED_ROW_HEIGHT - CLASS_ICON_SIZE) / 2f;
                drawImage(canvas, icon, iconX, iconY, CLASS_ICON_SIZE, CLASS_ICON_SIZE, 255);
                rightX -= CLASS_ICON_SIZE + 4;
            }
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
            int partyIndex,
            int memberIndex,
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
            renderLeaderMemberActions(canvas, fontName, x, y, w, partyIndex, memberIndex);
        }

        rowX += nameW + 8;

        AssetManager.Asset icon = getClassIcon(member.className);
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
        if (getClassIcon(member.className) != null) {
            width += CLASS_ICON_SIZE + 6;
        }
        if (!member.isReserved && !member.isObserved) {
            width += textWidth("(" + member.role + ")", fontName, ROLE_FONT_SIZE);
        }
        return width;
    }

    private void renderLeaderMemberActions(
            UiCanvas canvas, String fontName, float rowX, float rowY, float rowWidth, int partyIndex, int memberIndex) {
        float promoteButtonWidth = leaderActionButtonWidth("Promote", fontName);
        float kickButtonWidth = leaderActionButtonWidth("Kick", fontName);
        float buttonY = rowY + (MEMBER_ROW_HEIGHT - LEADER_ACTION_BUTTON_HEIGHT) / 2f;
        float kickX = rowX + rowWidth - kickButtonWidth;
        float promoteX = kickX - LEADER_ACTION_BUTTON_SPACING - promoteButtonWidth;
        boolean promoteHovered = isHovered(
                uiMouseX,
                uiMouseY,
                promoteX,
                buttonY,
                promoteButtonWidth,
                LEADER_ACTION_BUTTON_HEIGHT);
        boolean kickHovered = isHovered(
                uiMouseX, uiMouseY, kickX, buttonY, kickButtonWidth, LEADER_ACTION_BUTTON_HEIGHT);

        canvas.fillRect(
                promoteX,
                buttonY,
                promoteButtonWidth,
                LEADER_ACTION_BUTTON_HEIGHT,
                promoteHovered ? color(ACCENT_PRIMARY_HOVER) : color(ACCENT_PRIMARY));
        drawText(
                canvas,
                fontName,
                HEADER_BUTTON_SIZE,
                color(TEXT_PRIMARY),
                promoteX + promoteButtonWidth / 2f,
                buttonY + LEADER_ACTION_BUTTON_HEIGHT / 2f,
                "Promote",
                UiCanvas.HorizontalAlign.CENTER);

        canvas.fillRect(
                kickX,
                buttonY,
                kickButtonWidth,
                LEADER_ACTION_BUTTON_HEIGHT,
                kickHovered ? color(CONTROL_DANGER_HOVER) : color(CONTROL_DANGER, 200));
        drawText(
                canvas,
                fontName,
                HEADER_BUTTON_SIZE,
                color(TEXT_PRIMARY),
                kickX + kickButtonWidth / 2f,
                buttonY + LEADER_ACTION_BUTTON_HEIGHT / 2f,
                "Kick",
                UiCanvas.HorizontalAlign.CENTER);

        renderedMemberActionBounds.add(new MemberActionHitbox(
                MemberAction.PROMOTE,
                partyIndex,
                memberIndex,
                promoteX,
                buttonY,
                promoteButtonWidth,
                LEADER_ACTION_BUTTON_HEIGHT));
        renderedMemberActionBounds.add(new MemberActionHitbox(
                MemberAction.KICK,
                partyIndex,
                memberIndex,
                kickX,
                buttonY,
                kickButtonWidth,
                LEADER_ACTION_BUTTON_HEIGHT));
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
                    canvas.fillPath(color(ACCENT_PRIMARY, 120));
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
                    canvas.fillPath(color(ACCENT_PRIMARY, 120));
                }
                canvas.restore();
            }
        }

        if (count >= 2) {
            if (count == 2) {
                float splitAngle = startAngle;
                float dx = radius * (float) Math.cos(splitAngle);
                float dy = radius * (float) Math.sin(splitAngle);
                canvas.strokeLine(cx - dx, cy - dy, cx + dx, cy + dy, 1.25f, color(ACCENT_DIVIDER));
            } else {
                for (int i = 0; i < count; i++) {
                    float splitAngle = startAngle + i * anglePerSlice;
                    float edgeX = cx + radius * (float) Math.cos(splitAngle);
                    float edgeY = cy + radius * (float) Math.sin(splitAngle);
                    canvas.strokeLine(cx, cy, edgeX, edgeY, 1.25f, color(ACCENT_DIVIDER));
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

    private void renderModal(UiCanvas canvas, String fontName, float panelX, float panelWidth, float screenHeight) {
        // Darken background
        canvas.fillRect(panelX, 0, panelWidth, screenHeight, color(BACKGROUND_MODAL_OVERLAY));

        // Modal centered in main panel area
        modalX = panelX + (panelWidth - MODAL_WIDTH) / 2f;
        modalY = (screenHeight - PARTY_MODAL_HEIGHT) / 2f;

        canvas.fillRect(modalX, modalY, MODAL_WIDTH, PARTY_MODAL_HEIGHT, color(BACKGROUND_BODY_OPAQUE));
        canvas.strokeRect(modalX, modalY, MODAL_WIDTH, PARTY_MODAL_HEIGHT, 1, color(ACCENT_SECONDARY));

        // Title
        String modalTitle = party().hasListedParty() ? "Update Party" : "Create Party";
        drawText(
                canvas,
                fontName,
                MODAL_TITLE_SIZE,
                color(TEXT_PRIMARY),
                modalX + MODAL_WIDTH / 2f,
                modalY + 18,
                modalTitle,
                UiCanvas.HorizontalAlign.CENTER);

        // Raid type icons row
        float totalCirclesW = RAID_TYPES.length * RAID_CIRCLE_SIZE + (RAID_TYPES.length - 1) * RAID_CIRCLE_SPACING;
        float circleStartX = modalX + (MODAL_WIDTH - totalCirclesW) / 2f;
        float circleY = modalY + 38;

        for (int i = 0; i < RAID_TYPES.length; i++) {
            String rt = RAID_TYPES[i];
            float iconX = circleStartX + i * (RAID_CIRCLE_SIZE + RAID_CIRCLE_SPACING);
            float iconY = circleY;
            float rcx = iconX + RAID_CIRCLE_SIZE / 2f;
            float rcy = iconY + RAID_CIRCLE_SIZE / 2f;
            boolean selected = modalSelectedRaids.contains(rt);

            // Selection highlight behind icon
            if (selected) {
                canvas.fillCircle(rcx, rcy, RAID_CIRCLE_SIZE / 2f - 2, color(ACCENT_PRIMARY, 120));
            }

            // Draw raid icon image (or text fallback for raids without an asset)
            String assetKey = PartyListing.displayNameToAssetKey(rt);
            AssetManager.Asset raidIcon = assetKey != null ? getClassIcon(assetKey) : null;
            if (raidIcon != null) {
                float imgInset = 4;
                drawImage(
                        canvas,
                        raidIcon,
                        iconX + imgInset,
                        iconY + imgInset,
                        RAID_CIRCLE_SIZE - imgInset * 2,
                        RAID_CIRCLE_SIZE - imgInset * 2,
                        selected ? 255 : 160);
            } else {
                // Text fallback (e.g. ANNI)
                drawText(
                        canvas,
                        fontName,
                        RAID_LABEL_SIZE,
                        selected ? color(TEXT_PRIMARY) : color(TEXT_MUTED),
                        rcx,
                        rcy,
                        rt,
                        UiCanvas.HorizontalAlign.CENTER);
            }
        }

        // "Reserved slots" label
        float rowY = circleY + RAID_CIRCLE_SIZE + 16;
        float rightColX = modalX + MODAL_WIDTH / 2f;

        drawText(
                canvas,
                fontName,
                MODAL_LABEL_SIZE,
                color(TEXT_MUTED),
                rightColX,
                rowY,
                "Reserved slots",
                UiCanvas.HorizontalAlign.CENTER);

        float etBtnY = rowY + 12;
        // Reserved slots with up/down arrows
        float arrowW = 16;
        float rsFieldW = MODAL_DROPDOWN_W - arrowW;
        float rsBoxX = rightColX - MODAL_DROPDOWN_W / 2f;
        float rsBoxY = etBtnY;
        float arrowX = rsBoxX + rsFieldW;
        float halfArrowH = MODAL_DROPDOWN_H / 2f;

        Color rsFieldBg = reservedSlotsFocused ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT);
        canvas.fillRect(rsBoxX, rsBoxY, rsFieldW, MODAL_DROPDOWN_H, rsFieldBg);
        canvas.strokeRect(
                rsBoxX,
                rsBoxY,
                rsFieldW,
                MODAL_DROPDOWN_H,
                1,
                reservedSlotsFocused ? color(CONTROL_BORDER) : color(CONTROL_INPUT_SECONDARY));

        drawText(
                canvas,
                fontName,
                MODAL_LABEL_SIZE,
                color(TEXT_PRIMARY),
                rsBoxX + rsFieldW / 2f,
                rsBoxY + MODAL_DROPDOWN_H / 2f,
                reservedSlotsFocused ? reservedSlotsInput : String.valueOf(modalReservedSlots),
                UiCanvas.HorizontalAlign.CENTER);

        // Up arrow button
        boolean upHovered = isHovered(uiMouseX, uiMouseY, arrowX, rsBoxY, arrowW, halfArrowH);
        canvas.fillRect(arrowX, rsBoxY, arrowW, halfArrowH, upHovered ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
        canvas.strokeRect(arrowX, rsBoxY, arrowW, halfArrowH, 1, color(CONTROL_INPUT_SECONDARY));
        drawTriangle(canvas, arrowX + arrowW / 2f, rsBoxY + halfArrowH / 2f, 4, true, color(TEXT_PRIMARY));

        // Down arrow button
        boolean downHovered = isHovered(uiMouseX, uiMouseY, arrowX, rsBoxY + halfArrowH, arrowW, halfArrowH);
        canvas.fillRect(
                arrowX,
                rsBoxY + halfArrowH,
                arrowW,
                halfArrowH,
                downHovered ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
        canvas.strokeRect(
                arrowX, rsBoxY + halfArrowH, arrowW, halfArrowH, 1, color(CONTROL_INPUT_SECONDARY));
        drawTriangle(
                canvas,
                arrowX + arrowW / 2f,
                rsBoxY + halfArrowH + halfArrowH / 2f,
                4,
                false,
                color(TEXT_PRIMARY));

        float regionLabelY = rsBoxY + MODAL_DROPDOWN_H + 18;
        drawText(
                canvas,
                fontName,
                MODAL_LABEL_SIZE,
                color(TEXT_MUTED),
                modalX + MODAL_WIDTH / 2f,
                regionLabelY,
                "Region",
                UiCanvas.HorizontalAlign.CENTER);

        float regionButtonsY = regionLabelY + 12;
        float totalRegionButtonsW =
                PARTY_REGIONS.length * REGION_BUTTON_W + (PARTY_REGIONS.length - 1) * REGION_BUTTON_SPACING;
        float regionStartX = modalX + (MODAL_WIDTH - totalRegionButtonsW) / 2f;
        PartyRegion selectedRegion = modalSelectedRegion != null ? modalSelectedRegion : PartyRegion.NA;

        for (int i = 0; i < PARTY_REGIONS.length; i++) {
            PartyRegion region = PARTY_REGIONS[i];
            float regionX = regionStartX + i * (REGION_BUTTON_W + REGION_BUTTON_SPACING);
            boolean regionHovered =
                    isHovered(uiMouseX, uiMouseY, regionX, regionButtonsY, REGION_BUTTON_W, MODAL_DROPDOWN_H);
            boolean regionSelected = selectedRegion == region;

            canvas.fillRect(
                    regionX,
                    regionButtonsY,
                    REGION_BUTTON_W,
                    MODAL_DROPDOWN_H,
                    regionSelected ? color(ACCENT_PRIMARY, 120) : (regionHovered ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT)));
            canvas.strokeRect(
                    regionX,
                    regionButtonsY,
                    REGION_BUTTON_W,
                    MODAL_DROPDOWN_H,
                    1,
                    regionSelected ? color(CONTROL_BORDER) : color(CONTROL_INPUT_SECONDARY));

            drawText(
                    canvas,
                    fontName,
                    MODAL_LABEL_SIZE,
                    color(TEXT_PRIMARY),
                    regionX + REGION_BUTTON_W / 2f,
                    regionButtonsY + MODAL_DROPDOWN_H / 2f,
                    region.name(),
                    UiCanvas.HorizontalAlign.CENTER);
        }

        float joinPolicyLabelY = regionButtonsY + MODAL_DROPDOWN_H + 16;
        drawText(
                canvas,
                fontName,
                MODAL_LABEL_SIZE,
                color(TEXT_MUTED),
                modalX + MODAL_WIDTH / 2f,
                joinPolicyLabelY,
                "Who can join?",
                UiCanvas.HorizontalAlign.CENTER);

        float joinPolicyButtonsY = joinPolicyLabelY + 12;
        PartyJoinPolicy[] joinPolicies = {PartyJoinPolicy.OPEN, PartyJoinPolicy.INVITE_ONLY};
        float totalJoinPolicyButtonsW = joinPolicies.length * JOIN_POLICY_BUTTON_W
                + (joinPolicies.length - 1) * JOIN_POLICY_BUTTON_SPACING;
        float joinPolicyStartX = modalX + (MODAL_WIDTH - totalJoinPolicyButtonsW) / 2f;
        for (int i = 0; i < joinPolicies.length; i++) {
            PartyJoinPolicy joinPolicy = joinPolicies[i];
            float joinPolicyX = joinPolicyStartX + i * (JOIN_POLICY_BUTTON_W + JOIN_POLICY_BUTTON_SPACING);
            boolean joinPolicyHovered = isHovered(
                    uiMouseX,
                    uiMouseY,
                    joinPolicyX,
                    joinPolicyButtonsY,
                    JOIN_POLICY_BUTTON_W,
                    MODAL_DROPDOWN_H);
            boolean joinPolicySelected = modalJoinPolicy == joinPolicy;

            canvas.fillRect(
                    joinPolicyX,
                    joinPolicyButtonsY,
                    JOIN_POLICY_BUTTON_W,
                    MODAL_DROPDOWN_H,
                    joinPolicySelected
                            ? color(ACCENT_PRIMARY, 120)
                            : (joinPolicyHovered ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT)));
            canvas.strokeRect(
                    joinPolicyX,
                    joinPolicyButtonsY,
                    JOIN_POLICY_BUTTON_W,
                    MODAL_DROPDOWN_H,
                    1,
                    joinPolicySelected ? color(CONTROL_BORDER) : color(CONTROL_INPUT_SECONDARY));

            drawText(
                    canvas,
                    fontName,
                    MODAL_LABEL_SIZE,
                    color(TEXT_PRIMARY),
                    joinPolicyX + JOIN_POLICY_BUTTON_W / 2f,
                    joinPolicyButtonsY + MODAL_DROPDOWN_H / 2f,
                    joinPolicyLabel(joinPolicy),
                    UiCanvas.HorizontalAlign.CENTER);
        }

        // Create/Update button
        String createLabel = party().hasListedParty() ? "Update party" : "Create party";
        float createButtonWidth = modalActionButtonWidth(createLabel, fontName);
        float createBtnX = modalX + (MODAL_WIDTH - createButtonWidth) / 2f;
        float createBtnY = modalY + PARTY_MODAL_HEIGHT - MODAL_BUTTON_H - 14;
        boolean createHovered =
                isHovered(uiMouseX, uiMouseY, createBtnX, createBtnY, createButtonWidth, MODAL_BUTTON_H);
        canvas.fillRect(
                createBtnX,
                createBtnY,
                createButtonWidth,
                MODAL_BUTTON_H,
                createHovered ? color(ACCENT_PRIMARY_HOVER, 220) : color(ACCENT_PRIMARY, 200));

        drawText(
                canvas,
                fontName,
                MODAL_LABEL_SIZE,
                color(TEXT_PRIMARY),
                createBtnX + createButtonWidth / 2f,
                createBtnY + MODAL_BUTTON_H / 2f,
                createLabel,
                UiCanvas.HorizontalAlign.CENTER);

    }

    // ── Filter+ Screen ──

    private void renderFilterScreen(
            UiCanvas canvas, String fontName, float panelX, float panelWidth, float screenHeight) {
        canvas.fillRect(panelX, 0, panelWidth, screenHeight, color(BACKGROUND_MODAL_OVERLAY));

        float filterW = TAG_OVERLAY_WIDTH;
        float filterH = TAG_OVERLAY_HEIGHT;
        float filterX = panelX + (panelWidth - filterW) / 2f;
        float filterY = (screenHeight - filterH) / 2f;

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
        canvas.fillRect(boxX, activeBoxY, boxW, boxH, color(BACKGROUND_BODY_OPAQUE, 240));
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
        canvas.fillRect(boxX, inactiveBoxY, boxW, boxH, color(BACKGROUND_BODY_OPAQUE, 240));
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
        float backW = 70;
        float backH = 20;
        float backX = filterX + (filterW - backW) / 2f;
        float backY = filterY + filterH - backH - 8;
        boolean backHovered = isHovered(uiMouseX, uiMouseY, backX, backY, backW, backH);
        canvas.fillRect(
                backX,
                backY,
                backW,
                backH,
                backHovered ? color(ACCENT_PRIMARY_HOVER, 220) : color(ACCENT_PRIMARY, 200));
        drawText(
                canvas,
                fontName,
                MEMBER_FONT_SIZE,
                color(TEXT_PRIMARY),
                backX + backW / 2f,
                backY + backH / 2f,
                "< Back",
                UiCanvas.HorizontalAlign.CENTER);
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
            List<TagChipHitbox> renderedChipBounds) {
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

            renderedChipBounds.add(new TagChipHitbox(tag, curX, curY, chipW, chipH));

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

            canvas.save();
            if (angle != 0) {
                canvas.translate(chipCenterX, chipCenterY);
                canvas.rotateDegrees(angle);
                canvas.translate(-chipCenterX, -chipCenterY);
            }

            boolean chipHovered = isHovered(uiMouseX, uiMouseY, curX, curY, chipW, chipH);
            canvas.fillRoundedRect(
                    curX,
                    curY,
                    chipW,
                    chipH,
                    4,
                    chipHovered ? color(CONTROL_INPUT_HOVER) : color(ACCENT_DIVIDER, 220));

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

    private boolean isCurrentListingClosed() {
        return party().getCurrentListing() != null
                && party().getCurrentListing().status() == PartyStatus.CLOSED;
    }

    private boolean isCurrentListingAutoClosed() {
        return party().getCurrentListing() != null
                && party().getCurrentListing().status() == PartyStatus.CLOSED
                && party().getCurrentListing().closeReason() == PartyCloseReason.AUTO_CAPACITY;
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
        if (!searchQuery.isEmpty()) {
            boolean matches = false;
            String q = searchQuery.toLowerCase();
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
            party().refreshData();
            nextLoadingNameRefreshAtMs = now + LOADING_NAME_REFRESH_MS;
        }
    }

    private boolean isHovered(float mx, float my, float bx, float by, float bw, float bh) {
        return mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
    }

    private boolean isHovered(float mx, float my, HeaderButtonBounds bounds) {
        return bounds != null && isHovered(mx, my, bounds.x(), bounds.y(), bounds.w(), bounds.h());
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

        float mx = MinecraftUiRenderer.mouseX(click.x());
        float my = MinecraftUiRenderer.mouseY(click.y());

        float screenWidth = MinecraftUiRenderer.screenWidth();
        float screenHeight = MinecraftUiRenderer.screenHeight();

        // ── Filter screen (highest priority) ──
        if (filterScreenOpen) {
            return handleFilterScreenClick(mx, my, screenWidth, screenHeight);
        }

        if (inviteModalOpen) {
            return handleInviteModalClick(mx, my, screenWidth, screenHeight);
        }

        // ── Modal clicks ──
        if (modalOpen) {
            return handleModalClick(mx, my, screenWidth, screenHeight);
        }

        // ── Role dropdown menu ──
        if (roleDropdownOpen) {
            float itemH = 20;
            float menuY = dropdownRenderY + SEARCH_BAR_HEIGHT;
            for (int i = 0; i < ROLES.length; i++) {
                float itemY = menuY + i * itemH;
                if (isHovered(mx, my, dropdownRenderX, itemY, dropdownRenderW, itemH)) {
                    selectedRole = ROLES[i].equals(selectedRole) ? null : ROLES[i];
                    roleDropdownOpen = false;
                    if (selectedRole != null) {
                        party().setRole(selectedRole);
                    }
                    return true;
                }
            }
            roleDropdownOpen = false;
        }

        // ── Sidebar ──
        float btnX = SIDEBAR_PADDING;
        float btnW = SIDEBAR_WIDTH - SIDEBAR_PADDING * 2;

        var destinations = SequoiaSidebarNavigation.destinations();
        var layout = SequoiaSidebarNavigation.sidebarLayout(
                screenHeight, destinations.size(), SIDEBAR_BUTTON_HEIGHT, SIDEBAR_BUTTON_SPACING);
        for (int row = 0; row < destinations.size(); row++) {
            if (!isHovered(mx, my, btnX, layout.buttonY(row), btnW, layout.buttonHeight())) {
                continue;
            }
            var destination = destinations.get(row);
            if (destination != SequoiaSidebarNavigation.Destination.PARTY_FINDER) {
                SequoiaSidebarNavigation.open(destination, this);
            }
            return true;
        }

        // ── Header ──
        float panelX = SIDEBAR_WIDTH;
        float panelWidth = screenWidth - SIDEBAR_WIDTH;
        String fontName = SeqClient.getFontManager().getSelectedFont();
        HeaderControlsLayout headerLayout = computeHeaderControlsLayout(panelX, panelWidth, fontName);

        if (isHovered(mx, my, headerLayout.searchBar())) {
            searchFocused = true;
            searchCursorBlink = 0;
            return true;
        } else {
            searchFocused = false;
        }

        if (party().isPartyLeader()) {
            if (isHovered(mx, my, headerLayout.manageButton())) {
                openModal(true);
                return true;
            }
            if (isHovered(mx, my, headerLayout.inviteButton())) {
                openInviteModal();
                return true;
            }
            if (isHovered(mx, my, headerLayout.openCloseButton())) {
                if (party().getCurrentListing() == null) {
                    showErrorPopup("Unable to update party state: no active listing.");
                } else if (isCurrentListingAutoClosed()) {
                    showStatusBanner("Party is auto-closed at capacity and will reopen when a slot frees.");
                } else if (isCurrentListingClosed()) {
                    party().reopenParty(party().getCurrentListing().id());
                } else {
                    party().closeParty(party().getCurrentListing().id());
                }
                return true;
            }
            if (isHovered(mx, my, headerLayout.delistButton())) {
                party().delistParty();
                return true;
            }
            if (isHovered(mx, my, headerLayout.inviteAllButton())) {
                showStatusBanner("Preparing party invites...");
                party().inviteAllCurrentMembers()
                        .thenAccept(inviteAllResult ->
                                SeqClient.mc.execute(() -> showStatusBanner(inviteAllResult.message())));
                return true;
            }
            if (isHovered(mx, my, headerLayout.scanButton())) {
                var result = party().scanCurrentWynnParty();
                showStatusBanner(result.message());
                return true;
            }
        } else {
            if (isHovered(mx, my, headerLayout.newPartyButton())) {
                if (party().getJoinedPartyIndex() < 0) {
                    openModal(false);
                }
                return true;
            }
        }

        if (isHovered(mx, my, headerLayout.roleDropdown())) {
            roleDropdownOpen = !roleDropdownOpen;
            return true;
        }

        // ── Filter button ──
        float filterX = panelX + panelWidth - FILTER_BUTTON_W - FILTER_BUTTON_MARGIN;
        float filterY = screenHeight - FILTER_BUTTON_H - FILTER_BUTTON_MARGIN;
        if (isHovered(mx, my, filterX, filterY, FILTER_BUTTON_W, FILTER_BUTTON_H)) {
            filterScreenOpen = true;
            return true;
        }

        // ── Scrollbar ──
        float contentY = headerLayout.height();
        float contentHeight = screenHeight - contentY;

        if (maxScroll > 0) {
            float scrollbarX = panelX + panelWidth - 5;
            if (isHovered(mx, my, scrollbarX - 2, contentY, 8, contentHeight)) {
                scrollbarDragging = true;
                scrollbarDragStart = my;
                scrollOffsetDragStart = scrollOffset;
                return true;
            }
        }

        if (mx < panelX || my < contentY || my > contentY + contentHeight)
            return super.mouseClicked(click, outsideScreen);

        // ── Leader member-management buttons ──
        for (MemberActionHitbox action : renderedMemberActionBounds) {
            if (!action.contains(mx, my)) {
                continue;
            }
            if (action.action() == MemberAction.PROMOTE) {
                party().promoteMember(action.partyIndex(), action.memberIndex());
            } else {
                party().kickMember(action.partyIndex(), action.memberIndex());
            }
            return true;
        }

        // ── Party cards ──
        float cursorY = contentY - scrollOffset + PADDING;
        float contentWidth = panelWidth;

        for (int i = 0; i < party().getParties().size(); i++) {
            PartyListing party = party().getParties().get(i);
            if (!matchesFilters(party)) continue;

            float cardX = panelX + PADDING;
            float cardW = contentWidth - PADDING * 2 - 6;
            float cardH;

            if (party.expanded) {
                cardH = CARD_HEADER_HEIGHT + party.members.size() * MEMBER_ROW_HEIGHT + CARD_PADDING;

                boolean isMyParty = i == party().getMyPartyIndex() && party().isPartyLeader();

                float joinBtnX = cardX + cardW - CARD_PADDING - JOIN_BUTTON_WIDTH;
                float joinBtnY = cursorY
                        + CARD_HEADER_HEIGHT
                        + (party.members.size() - 1) * MEMBER_ROW_HEIGHT
                        + (MEMBER_ROW_HEIGHT - BUTTON_HEIGHT) / 2f;
                if (!isMyParty && isHovered(mx, my, joinBtnX, joinBtnY, JOIN_BUTTON_WIDTH, BUTTON_HEIGHT)) {
                    if (party().getJoinedPartyIndex() == i) {
                        party().leaveParty();
                    } else if (party().getJoinedPartyIndex() < 0 && party.isJoinable()) {
                        party().joinParty(i, selectedRole);
                    }
                    return true;
                }

                if (isHovered(mx, my, cardX, cursorY, cardW, CARD_HEADER_HEIGHT)) {
                    party.expanded = false;
                    return true;
                }
            } else {
                cardH = COLLAPSED_ROW_HEIGHT;
                if (isHovered(mx, my, cardX, cursorY, cardW, cardH)) {
                    party.expanded = true;
                    return true;
                }
            }
            cursorY += cardH + CARD_SPACING;
        }

        return super.mouseClicked(click, outsideScreen);
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
            modalReservedSlots = estimateReservedSlotCount(party().getCurrentListing());
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
        inviteUsernameFocused = true;
        inviteUsernameInput = "";
    }

    private void renderInviteModal(
            UiCanvas canvas, String fontName, float panelX, float panelWidth, float screenHeight) {
        canvas.fillRect(panelX, 0, panelWidth, screenHeight, color(BACKGROUND_MODAL_OVERLAY));

        float inviteModalX = panelX + (panelWidth - MODAL_WIDTH) / 2f;
        float inviteModalY = (screenHeight - MODAL_HEIGHT) / 2f;

        canvas.fillRect(inviteModalX, inviteModalY, MODAL_WIDTH, MODAL_HEIGHT, color(BACKGROUND_BODY_OPAQUE));
        canvas.strokeRect(inviteModalX, inviteModalY, MODAL_WIDTH, MODAL_HEIGHT, 1, color(ACCENT_SECONDARY));
        drawText(
                canvas,
                fontName,
                MODAL_TITLE_SIZE,
                color(TEXT_PRIMARY),
                inviteModalX + MODAL_WIDTH / 2f,
                inviteModalY + 22,
                "Invite Player",
                UiCanvas.HorizontalAlign.CENTER);

        float labelY = inviteModalY + 64;
        drawText(
                canvas,
                fontName,
                MODAL_LABEL_SIZE,
                color(TEXT_MUTED),
                inviteModalX + MODAL_WIDTH / 2f,
                labelY,
                "Username",
                UiCanvas.HorizontalAlign.CENTER);

        float inputW = 180;
        float inputH = MODAL_DROPDOWN_H;
        float inputX = inviteModalX + (MODAL_WIDTH - inputW) / 2f;
        float inputY = labelY + 12;

        canvas.fillRect(
                inputX,
                inputY,
                inputW,
                inputH,
                inviteUsernameFocused ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
        canvas.strokeRect(
                inputX,
                inputY,
                inputW,
                inputH,
                1,
                inviteUsernameFocused ? color(CONTROL_BORDER) : color(CONTROL_INPUT_SECONDARY));

        String inputText =
                inviteUsernameInput.isBlank() && !inviteUsernameFocused ? "Enter username" : inviteUsernameInput;
        Color inputColor = inviteUsernameInput.isBlank() && !inviteUsernameFocused ? color(TEXT_MUTED) : color(TEXT_PRIMARY);

        drawText(
                canvas,
                fontName,
                MODAL_LABEL_SIZE,
                inputColor,
                inputX + 8,
                inputY + inputH / 2f,
                inputText,
                UiCanvas.HorizontalAlign.LEFT);

        float sendBtnX = inviteModalX + (MODAL_WIDTH - MODAL_BUTTON_W) / 2f;
        float sendBtnY = inviteModalY + MODAL_HEIGHT - MODAL_BUTTON_H - 14;
        boolean sendHovered = isHovered(uiMouseX, uiMouseY, sendBtnX, sendBtnY, MODAL_BUTTON_W, MODAL_BUTTON_H);
        canvas.fillRect(
                sendBtnX,
                sendBtnY,
                MODAL_BUTTON_W,
                MODAL_BUTTON_H,
                sendHovered ? color(ACCENT_PRIMARY_HOVER, 220) : color(ACCENT_PRIMARY, 200));

        drawText(
                canvas,
                fontName,
                MEMBER_FONT_SIZE,
                color(TEXT_PRIMARY),
                sendBtnX + MODAL_BUTTON_W / 2f,
                sendBtnY + MODAL_BUTTON_H / 2f,
                "Send",
                UiCanvas.HorizontalAlign.CENTER);
    }

    private static int estimateReservedSlotCount(Listing listing) {
        if (listing == null) {
            return 0;
        }

        if (listing.reservedSlots() != null) {
            return (int) listing.reservedSlots().stream()
                    .filter(Objects::nonNull)
                    .filter(slot -> !slot.isObservedWynnMember())
                    .count();
        }

        if (listing.members() == null) {
            return 0;
        }

        int count = 0;
        for (Member member : listing.members()) {
            if (member == null) {
                continue;
            }

            String playerUUID = member.playerUUID();
            if (playerUUID == null || playerUUID.isBlank()) {
                continue;
            }

            String normalized = playerUUID.trim().toLowerCase(Locale.ROOT);
            if ("reserved".equals(normalized)) {
                count++;
            }
        }
        return count;
    }

    private boolean handleInviteModalClick(float mx, float my, float screenWidth, float screenHeight) {
        float panelX = SIDEBAR_WIDTH;
        float panelWidth = screenWidth - SIDEBAR_WIDTH;
        float mX = panelX + (panelWidth - MODAL_WIDTH) / 2f;
        float mY = (screenHeight - MODAL_HEIGHT) / 2f;

        if (!isHovered(mx, my, mX, mY, MODAL_WIDTH, MODAL_HEIGHT)) {
            inviteModalOpen = false;
            inviteUsernameFocused = false;
            return true;
        }

        float inputW = 180;
        float inputH = MODAL_DROPDOWN_H;
        float inputX = mX + (MODAL_WIDTH - inputW) / 2f;
        float inputY = mY + 76;

        if (isHovered(mx, my, inputX, inputY, inputW, inputH)) {
            inviteUsernameFocused = true;
            return true;
        }

        if (inviteUsernameFocused) {
            inviteUsernameFocused = false;
        }

        float sendBtnX = mX + (MODAL_WIDTH - MODAL_BUTTON_W) / 2f;
        float sendBtnY = mY + MODAL_HEIGHT - MODAL_BUTTON_H - 14;
        if (isHovered(mx, my, sendBtnX, sendBtnY, MODAL_BUTTON_W, MODAL_BUTTON_H)) {
            submitInviteFromModal();
            return true;
        }

        return true;
    }

    private void submitInviteFromModal() {
        String username = inviteUsernameInput == null ? "" : inviteUsernameInput.trim();
        if (username.isEmpty()) {
            showErrorPopup("Enter a username to invite.");
            return;
        }
        party().createInvite(username);
        inviteModalOpen = false;
        inviteUsernameFocused = false;
    }

    private boolean handleModalClick(float mx, float my, float screenWidth, float screenHeight) {
        float panelX = SIDEBAR_WIDTH;
        float panelWidth = screenWidth - SIDEBAR_WIDTH;
        float mX = panelX + (panelWidth - MODAL_WIDTH) / 2f;
        float mY = (screenHeight - PARTY_MODAL_HEIGHT) / 2f;

        // Click outside modal closes it
        if (!isHovered(mx, my, mX, mY, MODAL_WIDTH, PARTY_MODAL_HEIGHT)) {
            modalOpen = false;
            reservedSlotsFocused = false;
            return true;
        }

        // Raid type circles
        float totalCirclesW = RAID_TYPES.length * RAID_CIRCLE_SIZE + (RAID_TYPES.length - 1) * RAID_CIRCLE_SPACING;
        float circleStartX = mX + (MODAL_WIDTH - totalCirclesW) / 2f;
        float circleY = mY + 38;

        for (int i = 0; i < RAID_TYPES.length; i++) {
            String rt = RAID_TYPES[i];
            float cx = circleStartX + i * (RAID_CIRCLE_SIZE + RAID_CIRCLE_SPACING);
            if (isHovered(mx, my, cx, circleY, RAID_CIRCLE_SIZE, RAID_CIRCLE_SIZE)) {
                if (modalSelectedRaids.contains(rt)) {
                    modalSelectedRaids.remove(rt);
                } else {
                    if ("Prelude to Annihilation".equals(rt)) {
                        modalSelectedRaids.clear();
                        modalSelectedRaids.add(rt);
                    } else {
                        modalSelectedRaids.remove("Prelude to Annihilation");
                        modalSelectedRaids.add(rt);
                    }
                }
                int maxSlots = modalSelectedRaids.contains("Prelude to Annihilation") ? 9 : 3;
                modalReservedSlots = Math.min(modalReservedSlots, maxSlots);
                reservedSlotsInput = String.valueOf(modalReservedSlots);
                return true;
            }
        }

        float rowY = circleY + RAID_CIRCLE_SIZE + 16;
        float etBtnY = rowY + 12;

        // Reserved slots - up/down arrows and number field
        float rightColX = mX + MODAL_WIDTH / 2f;
        float arrowW = 16;
        float rsFieldW = MODAL_DROPDOWN_W - arrowW;
        float rsBoxX = rightColX - MODAL_DROPDOWN_W / 2f;
        float rsBoxY = etBtnY;
        float arrowX = rsBoxX + rsFieldW;
        float halfArrowH = MODAL_DROPDOWN_H / 2f;
        int maxSlots = modalSelectedRaids.contains("Prelude to Annihilation") ? 9 : 3;

        if (isHovered(mx, my, arrowX, rsBoxY, arrowW, halfArrowH)) {
            modalReservedSlots = Math.min(maxSlots, modalReservedSlots + 1);
            reservedSlotsInput = String.valueOf(modalReservedSlots);
            reservedSlotsFocused = false;
            return true;
        }
        if (isHovered(mx, my, arrowX, rsBoxY + halfArrowH, arrowW, halfArrowH)) {
            modalReservedSlots = Math.max(0, modalReservedSlots - 1);
            reservedSlotsInput = String.valueOf(modalReservedSlots);
            reservedSlotsFocused = false;
            return true;
        }
        if (isHovered(mx, my, rsBoxX, rsBoxY, rsFieldW, MODAL_DROPDOWN_H)) {
            reservedSlotsFocused = true;
            reservedSlotsInput = String.valueOf(modalReservedSlots);
            return true;
        }
        if (reservedSlotsFocused) {
            commitReservedSlotsInput();
            reservedSlotsFocused = false;
        }

        float regionLabelY = rsBoxY + MODAL_DROPDOWN_H + 18;
        float regionButtonsY = regionLabelY + 12;
        float totalRegionButtonsW =
                PARTY_REGIONS.length * REGION_BUTTON_W + (PARTY_REGIONS.length - 1) * REGION_BUTTON_SPACING;
        float regionStartX = mX + (MODAL_WIDTH - totalRegionButtonsW) / 2f;
        for (int i = 0; i < PARTY_REGIONS.length; i++) {
            PartyRegion region = PARTY_REGIONS[i];
            float regionX = regionStartX + i * (REGION_BUTTON_W + REGION_BUTTON_SPACING);
            if (isHovered(mx, my, regionX, regionButtonsY, REGION_BUTTON_W, MODAL_DROPDOWN_H)) {
                modalSelectedRegion = region;
                reservedSlotsFocused = false;
                return true;
            }
        }

        float joinPolicyLabelY = regionButtonsY + MODAL_DROPDOWN_H + 16;
        float joinPolicyButtonsY = joinPolicyLabelY + 12;
        PartyJoinPolicy[] joinPolicies = {PartyJoinPolicy.OPEN, PartyJoinPolicy.INVITE_ONLY};
        float totalJoinPolicyButtonsW = joinPolicies.length * JOIN_POLICY_BUTTON_W
                + (joinPolicies.length - 1) * JOIN_POLICY_BUTTON_SPACING;
        float joinPolicyStartX = mX + (MODAL_WIDTH - totalJoinPolicyButtonsW) / 2f;
        for (int i = 0; i < joinPolicies.length; i++) {
            float joinPolicyX = joinPolicyStartX + i * (JOIN_POLICY_BUTTON_W + JOIN_POLICY_BUTTON_SPACING);
            if (isHovered(
                    mx,
                    my,
                    joinPolicyX,
                    joinPolicyButtonsY,
                    JOIN_POLICY_BUTTON_W,
                    MODAL_DROPDOWN_H)) {
                modalJoinPolicy = joinPolicies[i];
                reservedSlotsFocused = false;
                return true;
            }
        }

        // Create/Update button
        String fontName = SeqClient.getFontManager().getSelectedFont();
        String createLabel = party().hasListedParty() ? "Update party" : "Create party";
        float createButtonWidth = modalActionButtonWidth(createLabel, fontName);
        float createBtnX = mX + (MODAL_WIDTH - createButtonWidth) / 2f;
        float createBtnY = mY + PARTY_MODAL_HEIGHT - MODAL_BUTTON_H - 14;
        if (isHovered(mx, my, createBtnX, createBtnY, createButtonWidth, MODAL_BUTTON_H)) {
            boolean updatingParty = party().getMyPartyIndex() >= 0;

            Set<String> selectedRaids = new LinkedHashSet<>(modalSelectedRaids);
            if (updatingParty && selectedRaids.isEmpty()) {
                selectedRaids.addAll(getCurrentListingRaidTags());
            }

            if (selectedRaids.isEmpty()) {
                showErrorPopup(
                        updatingParty
                                ? "No raid selected and no current raid could be reused."
                                : "Select at least one raid before creating a party.");
                return true;
            }

            if (selectedRaids.contains("Prelude to Annihilation") && selectedRaids.size() > 1) {
                showErrorPopup("Anni cannot be selected alongside other raids.");
                return true;
            }

            commitReservedSlotsInput();
            List<String> activityNames = new ArrayList<>(selectedRaids);

            if (updatingParty) {
                party().updateParty(
                        activityNames,
                        selectedRole,
                        modalReservedSlots,
                        modalSelectedRegion,
                        modalJoinPolicy);
            } else {
                party().createParty(
                        activityNames,
                        selectedRole,
                        modalReservedSlots,
                        modalSelectedRegion,
                        modalJoinPolicy);
            }

            modalOpen = false;
            reservedSlotsFocused = false;
            scrollOffset = 0;
            return true;
        }

        return true;
    }

    private boolean handleFilterScreenClick(float mx, float my, float screenWidth, float screenHeight) {
        float panelX = SIDEBAR_WIDTH;
        float panelWidth = screenWidth - SIDEBAR_WIDTH;
        float filterW = TAG_OVERLAY_WIDTH;
        float filterH = TAG_OVERLAY_HEIGHT;
        float filterX = panelX + (panelWidth - filterW) / 2f;
        float filterY = (screenHeight - filterH) / 2f;

        // Back button
        float backW = 70;
        float backH = 20;
        float backX = filterX + (filterW - backW) / 2f;
        float backY = filterY + filterH - backH - 8;
        if (isHovered(mx, my, backX, backY, backW, backH)) {
            filterScreenOpen = false;
            return true;
        }

        // Click outside filter screen closes it
        if (!isHovered(mx, my, filterX, filterY, filterW, filterH)) {
            filterScreenOpen = false;
            return true;
        }

        // Active tags area
        String clickedActive = findClickedTagChip(mx, my, renderedFilterActiveChipBounds);
        if (clickedActive != null) {
            activeFilterTags.remove(clickedActive);
            inactiveFilterTags.add(clickedActive);
            filterTagAnimStartTimes.put(clickedActive, System.currentTimeMillis());
            return true;
        }

        // Inactive tags area
        String clickedInactive = findClickedTagChip(mx, my, renderedFilterInactiveChipBounds);
        if (clickedInactive != null) {
            inactiveFilterTags.remove(clickedInactive);
            activeFilterTags.add(clickedInactive);
            filterTagAnimStartTimes.put(clickedInactive, System.currentTimeMillis());
            return true;
        }

        return true;
    }

    private String findClickedTagChip(float mx, float my, List<TagChipHitbox> renderedChipBounds) {
        for (TagChipHitbox chip : renderedChipBounds) {
            if (chip.contains(mx, my)) {
                return chip.tag;
            }
        }
        return null;
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
        scrollbarDragging = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (scrollbarDragging && maxScroll > 0) {
            float my = MinecraftUiRenderer.mouseY(click.y());

            float screenHeight = MinecraftUiRenderer.screenHeight();
            float panelWidth = MinecraftUiRenderer.screenWidth() - SIDEBAR_WIDTH;
            float headerHeight = computeHeaderControlsLayout(
                            SIDEBAR_WIDTH, panelWidth, SeqClient.getFontManager().getSelectedFont())
                    .height();
            float contentHeight = screenHeight - headerHeight;
            float thumbRatio = contentHeight / (contentHeight + maxScroll);
            float thumbH = Math.max(20, contentHeight * thumbRatio);
            float scrollRange = contentHeight - thumbH;

            float delta = my - scrollbarDragStart;
            scrollOffset = scrollOffsetDragStart + (delta / scrollRange) * maxScroll;
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (modalOpen || inviteModalOpen || filterScreenOpen) return true;
        scrollOffset -= (float) scrollY * SCROLL_SPEED;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        return true;
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent keyEvent) {
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
                inviteUsernameFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitInviteFromModal();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!inviteUsernameInput.isEmpty()) {
                    inviteUsernameInput = inviteUsernameInput.substring(0, inviteUsernameInput.length() - 1);
                }
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
                    int maxSlots = modalSelectedRaids.contains("Prelude to Annihilation") ? 9 : 3;
                    modalReservedSlots = Math.min(maxSlots, modalReservedSlots + 1);
                    reservedSlotsInput = String.valueOf(modalReservedSlots);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_DOWN) {
                    modalReservedSlots = Math.max(0, modalReservedSlots - 1);
                    reservedSlotsInput = String.valueOf(modalReservedSlots);
                    return true;
                }
            }
            return true;
        }
        if (searchFocused) {
            int keyCode = keyEvent.key();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                    scrollOffset = 0;
                }
                return true;
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
                    && inviteUsernameInput.length() < 16) {
                inviteUsernameInput += typedText;
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
        if (searchFocused) {
            if (typedText != null) {
                searchQuery += typedText;
                scrollOffset = 0;
            }
            return true;
        }
        return super.charTyped(characterEvent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
