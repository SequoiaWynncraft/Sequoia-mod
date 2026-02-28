package org.sequoia.seq.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.nanovg.NVGPaint;
import org.sequoia.seq.accessors.PartyAccessor;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.managers.AssetManager;
import org.sequoia.seq.model.*;
import org.sequoia.seq.utils.PlayerNameCache;
import org.sequoia.seq.utils.rendering.nvg.NVGContext;
import org.sequoia.seq.utils.rendering.nvg.NVGWrapper;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.lwjgl.nanovg.NanoVG.*;

public class PartyFinderScreen extends Screen implements PartyAccessor {

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
    private static final float SCROLL_SPEED = 12;

    // Party card layout
    private static final float CARD_PADDING = 10;
    private static final float CARD_SPACING = 6;
    private static final float CARD_HEADER_HEIGHT = 32;
    private static final float MEMBER_ROW_HEIGHT = 26;
    private static final float COLLAPSED_ROW_HEIGHT = 36;
    private static final float STAR_ICON_SIZE = 16;
    private static final float BUTTON_HEIGHT = 24;
    private static final float JOIN_BUTTON_WIDTH = 64;
    private static final float STATUS_BADGE_W = 50;

    // Modal layout
    private static final float MODAL_WIDTH = 320;
    private static final float MODAL_HEIGHT = 260;
    private static final float MODAL_DROPDOWN_W = 120;
    private static final float MODAL_DROPDOWN_H = 20;
    private static final float MODAL_BUTTON_W = 80;
    private static final float MODAL_BUTTON_H = 24;
    private static final float MODAL_ROW_SPACING = 28;

    // Filter button
    private static final float FILTER_BUTTON_W = 70;
    private static final float FILTER_BUTTON_H = 24;
    private static final float FILTER_BUTTON_MARGIN = 12;

    // Leader management icon sizes
    private static final float LEADER_ICON_SIZE = 14;

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
    private static final float MODAL_TITLE_SIZE = 16;
    private static final float MODAL_LABEL_SIZE = 12;
    private static final float STATUS_FONT_SIZE = 10;

    // ── Colors ──
    private static final Color BG_COLOR = new Color(10, 10, 16, 100);
    private static final Color SIDEBAR_COLOR = new Color(18, 18, 26, 200);
    private static final Color PANEL_COLOR = new Color(22, 22, 30, 100);
    private static final Color HEADER_COLOR = new Color(26, 26, 36, 110);
    private static final Color TITLE_COLOR = new Color(160, 130, 220, 255);
    private static final Color TEXT_COLOR = new Color(255, 255, 255, 255);
    private static final Color DIVIDER_COLOR = new Color(40, 40, 55, 255);

    private static final Color SIDEBAR_BUTTON_COLOR = new Color(30, 30, 42, 110);
    private static final Color SIDEBAR_BUTTON_HOVER = new Color(42, 42, 58, 120);
    private static final Color SIDEBAR_BUTTON_ACTIVE = new Color(80, 50, 140, 120);

    private static final Color SEARCH_BG = new Color(30, 30, 40, 255);
    private static final Color SEARCH_ACTIVE_BG = new Color(40, 40, 55, 255);
    private static final Color SEARCH_BORDER = new Color(130, 100, 200, 180);
    private static final Color SEARCH_PLACEHOLDER = new Color(100, 100, 120, 200);

    private static final Color CARD_BG = new Color(30, 30, 42, 110);
    private static final Color CARD_EXPANDED_BG = new Color(26, 26, 36, 120);
    private static final Color MEMBER_TEXT_COLOR = new Color(220, 220, 230, 255);
    private static final Color MEMBER_DIM_COLOR = new Color(120, 120, 140, 180);
    private static final Color ROLE_TEXT_COLOR = new Color(160, 160, 180, 255);

    private static final Color PARTY_TYPE_TEXT = new Color(180, 180, 200, 255);
    private static final Color EXPAND_ARROW_COLOR = new Color(140, 140, 160, 255);

    private static final Color JOIN_BUTTON_COLOR = new Color(160, 130, 220, 255);
    private static final Color JOIN_BUTTON_HOVER = new Color(180, 150, 240, 255);
    private static final Color JOINED_BUTTON_COLOR = new Color(140, 110, 200, 255);
    private static final Color NEW_PARTY_COLOR = new Color(160, 130, 220, 200);
    private static final Color NEW_PARTY_HOVER = new Color(180, 150, 240, 220);
    private static final Color MANAGE_PARTY_COLOR = new Color(160, 130, 220, 200);
    private static final Color DELIST_PARTY_COLOR = new Color(200, 60, 60, 200);
    private static final Color DELIST_PARTY_HOVER = new Color(220, 80, 80, 220);

    private static final Color DROPDOWN_BG = new Color(40, 40, 55, 240);
    private static final Color DROPDOWN_HOVER = new Color(55, 55, 75, 240);
    private static final Color DROPDOWN_BORDER = new Color(80, 80, 100, 200);

    private static final Color SCROLLBAR_TRACK = new Color(30, 30, 42, 255);
    private static final Color SCROLLBAR_THUMB = new Color(160, 130, 220, 150);

    private static final Color MODAL_BG = new Color(20, 20, 30, 255);
    private static final Color MODAL_BORDER = new Color(80, 80, 100, 255);
    private static final Color MODAL_OVERLAY = new Color(0, 0, 0, 160);
    private static final Color MODAL_DROPDOWN_BG = new Color(35, 35, 48, 255);
    private static final Color MODAL_DROPDOWN_BORDER = new Color(80, 80, 100, 200);

    private static final Color STATUS_OPEN = new Color(60, 180, 80, 220);
    private static final Color STATUS_FULL = new Color(200, 160, 40, 220);
    private static final Color STATUS_CLOSED = new Color(200, 60, 60, 220);
    private static final Color LOADING_COLOR = new Color(140, 140, 160, 200);

    private static final String GITHUB_URL = "https://github.com/SequoiaWynncraft/sequoia-mod";

    // ── State ──
    private final Screen parent;
    private float nvgMouseX, nvgMouseY;
    private float scrollOffset = 0;
    private float maxScroll = 0;
    private boolean scrollbarDragging = false;
    private float scrollbarDragStart = 0;
    private float scrollOffsetDragStart = 0;

    private boolean searchFocused = false;
    private String searchQuery = "";
    private int searchCursorBlink = 0;

    private boolean roleDropdownOpen = false;
    private PartyRole selectedRole = PartyRole.DPS;
    private float dropdownRenderX, dropdownRenderY, dropdownRenderW;

    // Loading state
    private boolean loadingActivities = false;
    private boolean loadingListings = false;

    // ── Modal state ──
    private boolean modalOpen = false;
    private int modalActivityIndex = 0;
    private PartyMode modalMode = PartyMode.CHILL;
    private PartyRegion modalRegion = PartyRegion.NA;
    private PartyRole modalRole = PartyRole.DPS;
    private String modalNote = "";
    private boolean modalNoteFocused = false;

    // Modal dropdown open states
    private boolean modalActivityDropdownOpen = false;
    private boolean modalModeDropdownOpen = false;
    private boolean modalRegionDropdownOpen = false;
    private boolean modalRoleDropdownOpen = false;

    // ── Filter state ──
    private Long filterActivityId = null;
    private PartyRegion filterRegion = null;
    private boolean filterDropdownOpen = false;
    private boolean regionFilterDropdownOpen = false;

    // ── Leader member management ──
    private int hoveredMemberPartyIndex = -1;
    private int hoveredMemberIndex = -1;

    public PartyFinderScreen(Screen parent) {
        super(Component.literal("Party Finder"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        // Load activities and listings from backend
        loadActivities();
        refreshListings();
    }

    private void loadActivities() {
        loadingActivities = true;
        party().loadActivities().thenRun(() -> loadingActivities = false);
    }

    private void refreshListings() {
        loadingListings = true;
        party().loadListings(filterActivityId, filterRegion).thenRun(() -> loadingListings = false);
    }

    // ══════════════════════════════ RENDER ══════════════════════════════

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        double guiScale = SeqClient.mc.getWindow().getGuiScale();
        nvgMouseX = (float) (mouseX * guiScale / 2.0);
        nvgMouseY = (float) (mouseY * guiScale / 2.0);

        NVGContext.renderDeferred(nvg -> {
            float screenWidth = SeqClient.mc.getWindow().getWidth() / 2f;
            float screenHeight = SeqClient.mc.getWindow().getHeight() / 2f;
            String fontName = SeqClient.getFontManager().getSelectedFont();

            NVGWrapper.drawRect(nvg, 0, 0, screenWidth, screenHeight, BG_COLOR);
            renderSidebar(nvg, fontName, screenHeight);

            float panelX = SIDEBAR_WIDTH;
            float panelWidth = screenWidth - SIDEBAR_WIDTH;

            NVGWrapper.drawRect(nvg, panelX, 0, panelWidth, screenHeight, PANEL_COLOR);
            NVGWrapper.drawRect(nvg, panelX, 0, panelWidth, HEADER_HEIGHT, HEADER_COLOR);
            renderHeaderControls(nvg, fontName, panelX, panelWidth);

            nvgFontFace(nvg, fontName);
            nvgFontSize(nvg, TITLE_FONT_SIZE);
            nvgTextAlign(nvg, NVG_ALIGN_RIGHT | NVG_ALIGN_MIDDLE);
            var titleCol = NVGContext.nvgColor(TITLE_COLOR);
            nvgFillColor(nvg, titleCol);
            nvgText(nvg, panelX + panelWidth - SEARCH_BAR_MARGIN, HEADER_HEIGHT / 2f, "Party Finder");
            titleCol.free();

            // Content area
            float contentX = panelX;
            float contentY = HEADER_HEIGHT;
            float contentWidth = panelWidth;
            float contentHeight = screenHeight - HEADER_HEIGHT;

            hoveredMemberPartyIndex = -1;
            hoveredMemberIndex = -1;

            nvgSave(nvg);
            nvgScissor(nvg, contentX, contentY, contentWidth, contentHeight);

            if (loadingListings) {
                nvgFontFace(nvg, fontName);
                nvgFontSize(nvg, MEMBER_FONT_SIZE);
                nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
                var lc = NVGContext.nvgColor(LOADING_COLOR);
                nvgFillColor(nvg, lc);
                nvgText(nvg, contentX + contentWidth / 2f, contentY + contentHeight / 2f, "Loading...");
                lc.free();
            } else {
                float cursorY = contentY - scrollOffset + PADDING;
                List<Listing> listings = party().getListings();
                for (int i = 0; i < listings.size(); i++) {
                    Listing listing = listings.get(i);
                    if (!matchesSearch(listing)) continue;

                    float cardH = listing.isExpanded()
                            ? CARD_HEADER_HEIGHT + listing.members().size() * MEMBER_ROW_HEIGHT + CARD_PADDING
                            : COLLAPSED_ROW_HEIGHT;

                    renderPartyCard(nvg, fontName, contentX + PADDING, cursorY,
                            contentWidth - PADDING * 2 - 6, cardH, listing, i);
                    cursorY += cardH + CARD_SPACING;
                }
                maxScroll = Math.max(0, cursorY + scrollOffset - contentY - contentHeight);
            }

            nvgRestore(nvg);

            // Scrollbar
            if (maxScroll > 0) {
                float scrollbarX = panelX + panelWidth - 5;
                float contentH = screenHeight - HEADER_HEIGHT;
                NVGWrapper.drawRect(nvg, scrollbarX, contentY, 4, contentH, SCROLLBAR_TRACK);
                float thumbRatio = contentH / (contentH + maxScroll);
                float thumbH = Math.max(20, contentH * thumbRatio);
                float thumbY = contentY + (scrollOffset / maxScroll) * (contentH - thumbH);
                NVGWrapper.drawRect(nvg, scrollbarX, thumbY, 4, thumbH, SCROLLBAR_THUMB);
            }

            // Filter button
            float filterX = panelX + panelWidth - FILTER_BUTTON_W - FILTER_BUTTON_MARGIN;
            float filterY = screenHeight - FILTER_BUTTON_H - FILTER_BUTTON_MARGIN;
            boolean filterHovered = isHovered(nvgMouseX, nvgMouseY, filterX, filterY, FILTER_BUTTON_W, FILTER_BUTTON_H);
            NVGWrapper.drawRect(nvg, filterX, filterY, FILTER_BUTTON_W, FILTER_BUTTON_H,
                    filterHovered ? NEW_PARTY_HOVER : NEW_PARTY_COLOR);
            nvgFontFace(nvg, fontName);
            nvgFontSize(nvg, HEADER_BUTTON_SIZE);
            nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
            var ftc = NVGContext.nvgColor(TEXT_COLOR);
            nvgFillColor(nvg, ftc);
            String filterLabel = (filterActivityId != null || filterRegion != null) ? "Filter *" : "Filter +";
            nvgText(nvg, filterX + FILTER_BUTTON_W / 2f, filterY + FILTER_BUTTON_H / 2f, filterLabel);
            ftc.free();

            // Role dropdown overlay
            if (roleDropdownOpen && !modalOpen) {
                renderRoleDropdownMenu(nvg, fontName);
            }

            // Filter dropdown overlays
            if (filterDropdownOpen) {
                renderFilterDropdown(nvg, fontName, panelX, panelWidth, screenHeight);
            }

            // Modal overlay
            if (modalOpen) {
                renderModal(nvg, fontName, panelX, panelWidth, screenHeight);
            }
        });
    }

    // ── Sidebar ──

    private void renderSidebar(long nvg, String fontName, float screenHeight) {
        NVGWrapper.drawRect(nvg, 0, 0, SIDEBAR_WIDTH, screenHeight, SIDEBAR_COLOR);

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, SIDEBAR_TITLE_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var col = NVGContext.nvgColor(TITLE_COLOR);
        nvgFillColor(nvg, col);
        nvgText(nvg, SIDEBAR_WIDTH / 2f, 22, "Sequoia");
        col.free();

        NVGWrapper.drawRect(nvg, SIDEBAR_PADDING, 40, SIDEBAR_WIDTH - SIDEBAR_PADDING * 2, 1, DIVIDER_COLOR);

        float btnX = SIDEBAR_PADDING;
        float btnW = SIDEBAR_WIDTH - SIDEBAR_PADDING * 2;
        float btnY = 50;

        drawSidebarButton(nvg, fontName, btnX, btnY, btnW, "Partyfinder", true);
        drawSidebarButton(nvg, fontName, btnX, btnY + (SIDEBAR_BUTTON_HEIGHT + SIDEBAR_BUTTON_SPACING), btnW, "Settings", false);
        drawSidebarButton(nvg, fontName, btnX, btnY + (SIDEBAR_BUTTON_HEIGHT + SIDEBAR_BUTTON_SPACING) * 2, btnW, "Github", false);
    }

    private void drawSidebarButton(long nvg, String fontName, float x, float y, float w, String label, boolean active) {
        boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y, w, SIDEBAR_BUTTON_HEIGHT);
        Color bg = active ? SIDEBAR_BUTTON_ACTIVE : (hovered ? SIDEBAR_BUTTON_HOVER : SIDEBAR_BUTTON_COLOR);
        NVGWrapper.drawRect(nvg, x, y, w, SIDEBAR_BUTTON_HEIGHT, bg);

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, SIDEBAR_BUTTON_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var c = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, c);
        nvgText(nvg, x + w / 2f, y + SIDEBAR_BUTTON_HEIGHT / 2f, label);
        c.free();
    }

    // ── Header ──

    private void renderHeaderControls(long nvg, String fontName, float panelX, float panelWidth) {
        searchCursorBlink++;
        float searchX = panelX + SEARCH_BAR_MARGIN;
        float searchY = (HEADER_HEIGHT - SEARCH_BAR_HEIGHT) / 2f;

        Color searchBg = searchFocused ? SEARCH_ACTIVE_BG : SEARCH_BG;
        NVGWrapper.drawRect(nvg, searchX, searchY, SEARCH_BAR_WIDTH, SEARCH_BAR_HEIGHT, searchBg);
        if (searchFocused) {
            NVGWrapper.drawRectOutline(nvg, searchX, searchY, SEARCH_BAR_WIDTH, SEARCH_BAR_HEIGHT, 1, SEARCH_BORDER);
        }

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, SEARCH_FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);

        nvgSave(nvg);
        nvgScissor(nvg, searchX, searchY, SEARCH_BAR_WIDTH, SEARCH_BAR_HEIGHT);
        if (searchQuery.isEmpty() && !searchFocused) {
            var ph = NVGContext.nvgColor(SEARCH_PLACEHOLDER);
            nvgFillColor(nvg, ph);
            nvgText(nvg, searchX + 6, searchY + SEARCH_BAR_HEIGHT / 2f, "Search...");
            ph.free();
        } else {
            var tc = NVGContext.nvgColor(TEXT_COLOR);
            nvgFillColor(nvg, tc);
            nvgText(nvg, searchX + 6, searchY + SEARCH_BAR_HEIGHT / 2f, searchQuery);
            tc.free();
        }
        nvgRestore(nvg);

        if (searchFocused && (searchCursorBlink / 1000) % 2 == 0) {
            float[] bounds = new float[4];
            nvgFontFace(nvg, fontName);
            nvgFontSize(nvg, SEARCH_FONT_SIZE);
            float textW = searchQuery.isEmpty() ? 0 : nvgTextBounds(nvg, 0, 0, searchQuery, bounds);
            NVGWrapper.drawRect(nvg, searchX + 6 + textW + 1, searchY + 3, 1, SEARCH_BAR_HEIGHT - 6, TEXT_COLOR);
        }

        float btnX = searchX + SEARCH_BAR_WIDTH + SEARCH_BAR_MARGIN;
        float btnY = searchY;

        if (party().isPartyLeader()) {
            float manageW = 95;
            drawHeaderButton(nvg, fontName, btnX, btnY, manageW, SEARCH_BAR_HEIGHT, "Manage Party", MANAGE_PARTY_COLOR, NEW_PARTY_HOVER);
            btnX += manageW + 6;

            float delistW = 80;
            drawHeaderButton(nvg, fontName, btnX, btnY, delistW, SEARCH_BAR_HEIGHT, "Disband", DELIST_PARTY_COLOR, DELIST_PARTY_HOVER);
            btnX += delistW + 6;
        } else {
            boolean inParty = party().isInParty();
            Color newBg = inParty ? new Color(60, 60, 70, 180) : NEW_PARTY_COLOR;
            Color newHover = inParty ? new Color(60, 60, 70, 180) : NEW_PARTY_HOVER;
            drawHeaderButton(nvg, fontName, btnX, btnY, 80, SEARCH_BAR_HEIGHT, "New party +", newBg, newHover);
            btnX += 86;
        }

        float dropW = 80;
        dropdownRenderX = btnX;
        dropdownRenderY = btnY;
        dropdownRenderW = dropW;
        renderRoleDropdownButton(nvg, fontName, btnX, btnY, dropW, SEARCH_BAR_HEIGHT);
    }

    private void drawHeaderButton(long nvg, String fontName, float x, float y, float w, float h,
                                  String label, Color bg, Color hoverBg) {
        boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y, w, h);
        NVGWrapper.drawRect(nvg, x, y, w, h, hovered ? hoverBg : bg);

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, HEADER_BUTTON_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var c = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, c);
        nvgText(nvg, x + w / 2f, y + h / 2f, label);
        c.free();
    }

    // ── Role dropdown ──

    private void renderRoleDropdownButton(long nvg, String fontName, float x, float y, float w, float h) {
        boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y, w, h);
        NVGWrapper.drawRect(nvg, x, y, w, h, hovered ? SEARCH_ACTIVE_BG : SEARCH_BG);
        NVGWrapper.drawRectOutline(nvg, x, y, w, h, 1, DROPDOWN_BORDER);

        String label = selectedRole != null ? selectedRole.name() : "Your role";
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, HEADER_BUTTON_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        var tc = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, tc);
        nvgText(nvg, x + 6, y + h / 2f, label);
        tc.free();

        drawTriangle(nvg, x + w - 8, y + h / 2f, 5, false, EXPAND_ARROW_COLOR);
    }

    private void renderRoleDropdownMenu(long nvg, String fontName) {
        float x = dropdownRenderX;
        float y = dropdownRenderY + SEARCH_BAR_HEIGHT;
        float w = dropdownRenderW;
        float itemH = 20;
        PartyRole[] roles = PartyRole.values();
        float totalH = roles.length * itemH;

        NVGWrapper.drawRect(nvg, x, y, w, totalH, DROPDOWN_BG);
        NVGWrapper.drawRectOutline(nvg, x, y, w, totalH, 1, DROPDOWN_BORDER);

        for (int i = 0; i < roles.length; i++) {
            float itemY = y + i * itemH;
            boolean itemHovered = isHovered(nvgMouseX, nvgMouseY, x, itemY, w, itemH);
            if (itemHovered) NVGWrapper.drawRect(nvg, x, itemY, w, itemH, DROPDOWN_HOVER);

            nvgFontFace(nvg, fontName);
            nvgFontSize(nvg, MEMBER_FONT_SIZE);
            nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            var tc = NVGContext.nvgColor(TEXT_COLOR);
            nvgFillColor(nvg, tc);
            nvgText(nvg, x + 6, itemY + itemH / 2f, roles[i].name());
            tc.free();
        }
    }

    // ── Party cards ──

    private void renderPartyCard(long nvg, String fontName, float x, float y, float w, float h,
                                 Listing listing, int listingIndex) {
        Listing current = party().getCurrentListing();
        boolean isJoined = current != null && current.id() == listing.id();
        NVGWrapper.drawRect(nvg, x, y, w, h, listing.isExpanded() ? CARD_EXPANDED_BG : CARD_BG);

        if (listing.isExpanded()) {
            renderExpandedCard(nvg, fontName, x, y, w, h, listing, listingIndex, isJoined);
        } else {
            renderCollapsedCard(nvg, fontName, x, y, w, listing);
        }
    }

    private void renderExpandedCard(long nvg, String fontName, float x, float y, float w, float h,
                                    Listing listing, int listingIndex, boolean isJoined) {
        float rowX = x + CARD_PADDING;

        // Activity name
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, CARD_TITLE_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        var nc = NVGContext.nvgColor(TITLE_COLOR);
        nvgFillColor(nvg, nc);
        nvgText(nvg, rowX, y + CARD_HEADER_HEIGHT / 2f, listing.activity().name());
        nc.free();

        // Member count
        float[] bounds = new float[4];
        nvgTextBounds(nvg, 0, 0, listing.activity().name(), bounds);
        float nameW = bounds[2] - bounds[0];

        nvgFontSize(nvg, MEMBER_FONT_SIZE);
        var cc = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, cc);
        nvgText(nvg, rowX + nameW + 8, y + CARD_HEADER_HEIGHT / 2f,
                listing.members().size() + "/" + listing.activity().maxPartySize());
        cc.free();

        // Status badge
        renderStatusBadge(nvg, fontName, x + w - CARD_PADDING - STATUS_BADGE_W,
                y + (CARD_HEADER_HEIGHT - 14) / 2f, listing.status());

        // Mode badge
        nvgFontSize(nvg, STATUS_FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_RIGHT | NVG_ALIGN_MIDDLE);
        var mc = NVGContext.nvgColor(PARTY_TYPE_TEXT);
        nvgFillColor(nvg, mc);
        nvgText(nvg, x + w - CARD_PADDING - STATUS_BADGE_W - 6, y + CARD_HEADER_HEIGHT / 2f,
                listing.mode().name() + " · " + listing.region().name());
        mc.free();

        // Collapse arrow
        nvgFontSize(nvg, 16);
        nvgTextAlign(nvg, NVG_ALIGN_RIGHT | NVG_ALIGN_MIDDLE);
        var arrCol = NVGContext.nvgColor(EXPAND_ARROW_COLOR);
        nvgFillColor(nvg, arrCol);
        nvgText(nvg, x + w - CARD_PADDING, y + CARD_HEADER_HEIGHT / 2f, "-");
        arrCol.free();

        // Members
        boolean isMyListing = party().isPartyLeader() && party().getCurrentListing() != null
                && party().getCurrentListing().id() == listing.id();
        float memberY = y + CARD_HEADER_HEIGHT;
        for (int mi = 0; mi < listing.members().size(); mi++) {
            Member member = listing.members().get(mi);
            boolean isLeader = member.playerUUID().equals(listing.leaderUUID());
            renderMemberRow(nvg, fontName, x + CARD_PADDING + 10, memberY,
                    w - CARD_PADDING * 2 - 10, member, isLeader, listingIndex, mi, isMyListing);
            memberY += MEMBER_ROW_HEIGHT;
        }

        float lastMemberCenterY = memberY - MEMBER_ROW_HEIGHT / 2f;

        // Join/Leave button (don't show on your own listing)
        if (!isMyListing) {
            float joinX = x + w - CARD_PADDING - JOIN_BUTTON_WIDTH;
            float joinY = memberY - MEMBER_ROW_HEIGHT + (MEMBER_ROW_HEIGHT - BUTTON_HEIGHT) / 2f;
            boolean alreadyInOtherParty = party().isInParty() && !isJoined;
            boolean joinHovered = !alreadyInOtherParty && isHovered(nvgMouseX, nvgMouseY, joinX, joinY, JOIN_BUTTON_WIDTH, BUTTON_HEIGHT);
            Color joinBg = isJoined ? JOINED_BUTTON_COLOR
                    : alreadyInOtherParty ? new Color(60, 60, 70, 180)
                    : (joinHovered ? JOIN_BUTTON_HOVER : JOIN_BUTTON_COLOR);
            NVGWrapper.drawRect(nvg, joinX, joinY, JOIN_BUTTON_WIDTH, BUTTON_HEIGHT, joinBg);

            nvgFontFace(nvg, fontName);
            nvgFontSize(nvg, MEMBER_FONT_SIZE);
            nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
            Color textCol = alreadyInOtherParty ? new Color(120, 120, 130, 200) : TEXT_COLOR;
            var jtc = NVGContext.nvgColor(textCol);
            nvgFillColor(nvg, jtc);
            nvgText(nvg, joinX + JOIN_BUTTON_WIDTH / 2f, joinY + BUTTON_HEIGHT / 2f, isJoined ? "Leave" : "Join");
            jtc.free();
        }

        // Note (if present)
        if (listing.note() != null && !listing.note().isBlank()) {
            float noteX = x + CARD_PADDING + 10;
            nvgFontFace(nvg, fontName);
            nvgFontSize(nvg, ROLE_FONT_SIZE);
            nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            var ntc = NVGContext.nvgColor(PARTY_TYPE_TEXT);
            nvgFillColor(nvg, ntc);
            nvgText(nvg, noteX, lastMemberCenterY, "\"" + listing.note() + "\"");
            ntc.free();
        }
    }

    private void renderCollapsedCard(long nvg, String fontName, float x, float y, float w, Listing listing) {
        float rowX = x + CARD_PADDING;
        float centerY = y + COLLAPSED_ROW_HEIGHT / 2f;

        // Activity name
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, CARD_TITLE_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        var nc = NVGContext.nvgColor(TITLE_COLOR);
        nvgFillColor(nvg, nc);
        nvgText(nvg, rowX, centerY, listing.activity().name());
        nc.free();

        float[] bounds = new float[4];
        nvgTextBounds(nvg, 0, 0, listing.activity().name(), bounds);
        rowX += (bounds[2] - bounds[0]) + 8;

        // Member count
        nvgFontSize(nvg, MEMBER_FONT_SIZE);
        var cc = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, cc);
        nvgText(nvg, rowX, centerY, listing.members().size() + "/" + listing.activity().maxPartySize());
        cc.free();
        rowX += 42;

        // Leader name
        Member leader = listing.getLeader();
        if (leader != null) {
            AssetManager.Asset starIcon = getClassIcon("star");
            if (starIcon != null) {
                float starY = centerY - STAR_ICON_SIZE / 2f;
                NVGWrapper.drawImage(nvg, starIcon, rowX, starY, STAR_ICON_SIZE, STAR_ICON_SIZE, 255);
            }
            rowX += STAR_ICON_SIZE + 4;

            nvgFontFace(nvg, fontName);
            nvgFontSize(nvg, MEMBER_FONT_SIZE);
            nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            var lc = NVGContext.nvgColor(MEMBER_TEXT_COLOR);
            nvgFillColor(nvg, lc);
            nvgText(nvg, rowX, centerY, PlayerNameCache.resolve(leader.playerUUID()));
            lc.free();
        }

        float rightX = x + w - CARD_PADDING;

        // Expand "+"
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, 16);
        nvgTextAlign(nvg, NVG_ALIGN_RIGHT | NVG_ALIGN_MIDDLE);
        var pc = NVGContext.nvgColor(EXPAND_ARROW_COLOR);
        nvgFillColor(nvg, pc);
        nvgText(nvg, rightX, centerY, "+");
        pc.free();
        rightX -= 22;

        // Status badge
        renderStatusBadge(nvg, fontName, rightX - STATUS_BADGE_W, y + (COLLAPSED_ROW_HEIGHT - 14) / 2f, listing.status());
        rightX -= STATUS_BADGE_W + 6;

        // Mode/Region label
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, TYPE_FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_RIGHT | NVG_ALIGN_MIDDLE);
        var tc = NVGContext.nvgColor(PARTY_TYPE_TEXT);
        nvgFillColor(nvg, tc);
        nvgText(nvg, rightX, centerY, listing.mode().name() + " · " + listing.region().name());
        tc.free();
    }

    private void renderStatusBadge(long nvg, String fontName, float x, float y, PartyStatus status) {
        Color bg = switch (status) {
            case OPEN -> STATUS_OPEN;
            case FULL -> STATUS_FULL;
            case CLOSED, DISBANDED -> STATUS_CLOSED;
        };
        NVGWrapper.drawRoundedRect(nvg, x, y, STATUS_BADGE_W, 14, 3, bg);
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, STATUS_FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var tc = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, tc);
        nvgText(nvg, x + STATUS_BADGE_W / 2f, y + 7, status.name());
        tc.free();
    }

    private void renderMemberRow(long nvg, String fontName, float x, float y, float w,
                                 Member member, boolean isLeader, int listingIndex, int memberIndex,
                                 boolean amLeaderOfThisListing) {
        float rowX = x;
        float centerY = y + MEMBER_ROW_HEIGHT / 2f;

        boolean isHoveredMember = false;
        if (amLeaderOfThisListing && !isLeader) {
            if (isHovered(nvgMouseX, nvgMouseY, x, y, w, MEMBER_ROW_HEIGHT)) {
                isHoveredMember = true;
                hoveredMemberPartyIndex = listingIndex;
                hoveredMemberIndex = memberIndex;
            }
        }

        if (isLeader) {
            AssetManager.Asset starIcon = getClassIcon("star");
            if (starIcon != null) {
                float starY = centerY - STAR_ICON_SIZE / 2f;
                NVGWrapper.drawImage(nvg, starIcon, rowX, starY, STAR_ICON_SIZE, STAR_ICON_SIZE, 255);
            }
        }
        rowX += STAR_ICON_SIZE + 4;

        // Member name
        String memberName = PlayerNameCache.resolve(member.playerUUID());
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, MEMBER_FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        Color nameColor = isHoveredMember ? MEMBER_DIM_COLOR : MEMBER_TEXT_COLOR;
        var nc = NVGContext.nvgColor(nameColor);
        nvgFillColor(nvg, nc);
        nvgText(nvg, rowX, centerY, memberName);
        nc.free();

        float[] bounds = new float[4];
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, MEMBER_FONT_SIZE);
        float nameW = nvgTextBounds(nvg, 0, 0, memberName, bounds);

        // Draw promote/kick icons on hover
        if (isHoveredMember) {
            AssetManager.Asset starupIcon = getClassIcon("starup");
            AssetManager.Asset crossIcon = getClassIcon("cross");
            if (starupIcon != null) {
                float iconY = centerY - LEADER_ICON_SIZE / 2f;
                NVGWrapper.drawImage(nvg, starupIcon, rowX, iconY, LEADER_ICON_SIZE, LEADER_ICON_SIZE, 255);
            }
            if (crossIcon != null) {
                float iconY = centerY - LEADER_ICON_SIZE / 2f;
                float crossX = rowX + nameW - LEADER_ICON_SIZE;
                NVGWrapper.drawImage(nvg, crossIcon, crossX, iconY, LEADER_ICON_SIZE, LEADER_ICON_SIZE, 255);
            }
        }

        rowX += nameW + 8;

        // Role
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, ROLE_FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        var rc = NVGContext.nvgColor(ROLE_TEXT_COLOR);
        nvgFillColor(nvg, rc);
        nvgText(nvg, rowX, centerY, "(" + member.role().name() + ")");
        rc.free();
    }

    // ── Small triangle arrow ──

    private void drawTriangle(long nvg, float cx, float cy, float size, boolean up, Color color) {
        float half = size / 2f;
        nvgBeginPath(nvg);
        if (up) {
            nvgMoveTo(nvg, cx, cy - half);
            nvgLineTo(nvg, cx - half, cy + half);
            nvgLineTo(nvg, cx + half, cy + half);
        } else {
            nvgMoveTo(nvg, cx, cy + half);
            nvgLineTo(nvg, cx - half, cy - half);
            nvgLineTo(nvg, cx + half, cy - half);
        }
        nvgClosePath(nvg);
        var c = NVGContext.nvgColor(color);
        nvgFillColor(nvg, c);
        nvgFill(nvg);
        c.free();
    }

    // ── Create Party Modal ──

    private void renderModal(long nvg, String fontName, float panelX, float panelWidth, float screenHeight) {
        NVGWrapper.drawRect(nvg, panelX, 0, panelWidth, screenHeight, MODAL_OVERLAY);

        float mX = panelX + (panelWidth - MODAL_WIDTH) / 2f;
        float mY = (screenHeight - MODAL_HEIGHT) / 2f;

        NVGWrapper.drawRect(nvg, mX, mY, MODAL_WIDTH, MODAL_HEIGHT, MODAL_BG);
        NVGWrapper.drawRectOutline(nvg, mX, mY, MODAL_WIDTH, MODAL_HEIGHT, 1, MODAL_BORDER);

        // Title
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, MODAL_TITLE_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var tc = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, tc);
        nvgText(nvg, mX + MODAL_WIDTH / 2f, mY + 18, "Create Party");
        tc.free();

        float leftLabelX = mX + 20;
        float dropdownX = mX + MODAL_WIDTH / 2f;
        float rowY = mY + 44;

        // Activity
        renderModalLabel(nvg, fontName, leftLabelX, rowY, "Activity");
        String activityName = getSelectedActivityName();
        renderModalDropdown(nvg, fontName, dropdownX, rowY, MODAL_DROPDOWN_W, activityName, modalActivityDropdownOpen);
        rowY += MODAL_ROW_SPACING;

        // Mode
        renderModalLabel(nvg, fontName, leftLabelX, rowY, "Mode");
        renderModalDropdown(nvg, fontName, dropdownX, rowY, MODAL_DROPDOWN_W, modalMode.name(), modalModeDropdownOpen);
        rowY += MODAL_ROW_SPACING;

        // Region
        renderModalLabel(nvg, fontName, leftLabelX, rowY, "Region");
        renderModalDropdown(nvg, fontName, dropdownX, rowY, MODAL_DROPDOWN_W, modalRegion.name(), modalRegionDropdownOpen);
        rowY += MODAL_ROW_SPACING;

        // Role
        renderModalLabel(nvg, fontName, leftLabelX, rowY, "Role");
        renderModalDropdown(nvg, fontName, dropdownX, rowY, MODAL_DROPDOWN_W, modalRole.name(), modalRoleDropdownOpen);
        rowY += MODAL_ROW_SPACING;

        // Note
        renderModalLabel(nvg, fontName, leftLabelX, rowY, "Note");
        Color noteBg = modalNoteFocused ? SEARCH_ACTIVE_BG : MODAL_DROPDOWN_BG;
        NVGWrapper.drawRect(nvg, dropdownX, rowY, MODAL_DROPDOWN_W, MODAL_DROPDOWN_H, noteBg);
        NVGWrapper.drawRectOutline(nvg, dropdownX, rowY, MODAL_DROPDOWN_W, MODAL_DROPDOWN_H, 1,
                modalNoteFocused ? SEARCH_BORDER : MODAL_DROPDOWN_BORDER);
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, MODAL_LABEL_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        nvgSave(nvg);
        nvgScissor(nvg, dropdownX, rowY, MODAL_DROPDOWN_W, MODAL_DROPDOWN_H);
        if (modalNote.isEmpty() && !modalNoteFocused) {
            var ph = NVGContext.nvgColor(SEARCH_PLACEHOLDER);
            nvgFillColor(nvg, ph);
            nvgText(nvg, dropdownX + 6, rowY + MODAL_DROPDOWN_H / 2f, "Optional...");
            ph.free();
        } else {
            var ntc = NVGContext.nvgColor(TEXT_COLOR);
            nvgFillColor(nvg, ntc);
            nvgText(nvg, dropdownX + 6, rowY + MODAL_DROPDOWN_H / 2f, modalNote);
            ntc.free();
        }
        nvgRestore(nvg);

        // Create button
        float createBtnX = mX + (MODAL_WIDTH - MODAL_BUTTON_W) / 2f;
        float createBtnY = mY + MODAL_HEIGHT - MODAL_BUTTON_H - 14;
        boolean createHovered = isHovered(nvgMouseX, nvgMouseY, createBtnX, createBtnY, MODAL_BUTTON_W, MODAL_BUTTON_H);
        NVGWrapper.drawRect(nvg, createBtnX, createBtnY, MODAL_BUTTON_W, MODAL_BUTTON_H,
                createHovered ? NEW_PARTY_HOVER : NEW_PARTY_COLOR);

        nvgFontSize(nvg, MEMBER_FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var cbc = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, cbc);
        nvgText(nvg, createBtnX + MODAL_BUTTON_W / 2f, createBtnY + MODAL_BUTTON_H / 2f, "Create!");
        cbc.free();

        // Render open dropdowns on top
        float ddRowY = mY + 44;
        if (modalActivityDropdownOpen) {
            List<Activity> acts = party().getActivities();
            renderDropdownItems(nvg, fontName, dropdownX, ddRowY + MODAL_DROPDOWN_H, MODAL_DROPDOWN_W,
                    acts.stream().map(Activity::name).toArray(String[]::new), modalActivityIndex);
        }
        ddRowY += MODAL_ROW_SPACING;
        if (modalModeDropdownOpen) {
            renderDropdownItems(nvg, fontName, dropdownX, ddRowY + MODAL_DROPDOWN_H, MODAL_DROPDOWN_W,
                    Arrays.stream(PartyMode.values()).map(Enum::name).toArray(String[]::new), modalMode.ordinal());
        }
        ddRowY += MODAL_ROW_SPACING;
        if (modalRegionDropdownOpen) {
            renderDropdownItems(nvg, fontName, dropdownX, ddRowY + MODAL_DROPDOWN_H, MODAL_DROPDOWN_W,
                    Arrays.stream(PartyRegion.values()).map(Enum::name).toArray(String[]::new), modalRegion.ordinal());
        }
        ddRowY += MODAL_ROW_SPACING;
        if (modalRoleDropdownOpen) {
            renderDropdownItems(nvg, fontName, dropdownX, ddRowY + MODAL_DROPDOWN_H, MODAL_DROPDOWN_W,
                    Arrays.stream(PartyRole.values()).map(Enum::name).toArray(String[]::new), modalRole.ordinal());
        }
    }

    private void renderModalLabel(long nvg, String fontName, float x, float y, String label) {
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, MODAL_LABEL_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        var c = NVGContext.nvgColor(PARTY_TYPE_TEXT);
        nvgFillColor(nvg, c);
        nvgText(nvg, x, y + MODAL_DROPDOWN_H / 2f, label);
        c.free();
    }

    private void renderModalDropdown(long nvg, String fontName, float x, float y, float w, String value, boolean open) {
        boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y, w, MODAL_DROPDOWN_H);
        NVGWrapper.drawRect(nvg, x, y, w, MODAL_DROPDOWN_H, hovered ? SEARCH_ACTIVE_BG : MODAL_DROPDOWN_BG);
        NVGWrapper.drawRectOutline(nvg, x, y, w, MODAL_DROPDOWN_H, 1, open ? SEARCH_BORDER : MODAL_DROPDOWN_BORDER);

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, MODAL_LABEL_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        var tc = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, tc);
        nvgText(nvg, x + 6, y + MODAL_DROPDOWN_H / 2f, value);
        tc.free();

        drawTriangle(nvg, x + w - 8, y + MODAL_DROPDOWN_H / 2f, 4, open, EXPAND_ARROW_COLOR);
    }

    private void renderDropdownItems(long nvg, String fontName, float x, float y, float w,
                                     String[] items, int selectedIndex) {
        float itemH = 20;
        float totalH = items.length * itemH;
        NVGWrapper.drawRect(nvg, x, y, w, totalH, DROPDOWN_BG);
        NVGWrapper.drawRectOutline(nvg, x, y, w, totalH, 1, DROPDOWN_BORDER);

        for (int i = 0; i < items.length; i++) {
            float itemY = y + i * itemH;
            boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, itemY, w, itemH);
            if (hovered || i == selectedIndex) {
                NVGWrapper.drawRect(nvg, x, itemY, w, itemH, hovered ? DROPDOWN_HOVER : new Color(50, 40, 80, 200));
            }
            nvgFontFace(nvg, fontName);
            nvgFontSize(nvg, MODAL_LABEL_SIZE);
            nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            var tc = NVGContext.nvgColor(TEXT_COLOR);
            nvgFillColor(nvg, tc);
            nvgText(nvg, x + 6, itemY + itemH / 2f, items[i]);
            tc.free();
        }
    }

    // ── Filter dropdown ──

    private void renderFilterDropdown(long nvg, String fontName, float panelX, float panelWidth, float screenHeight) {
        NVGWrapper.drawRect(nvg, panelX, 0, panelWidth, screenHeight, MODAL_OVERLAY);

        float filterW = 260;
        float filterH = 130;
        float filterX = panelX + (panelWidth - filterW) / 2f;
        float filterY = (screenHeight - filterH) / 2f;

        NVGWrapper.drawRect(nvg, filterX, filterY, filterW, filterH, MODAL_BG);
        NVGWrapper.drawRectOutline(nvg, filterX, filterY, filterW, filterH, 1, MODAL_BORDER);

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, MODAL_TITLE_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var tc = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, tc);
        nvgText(nvg, filterX + filterW / 2f, filterY + 18, "Filter Listings");
        tc.free();

        float labelX = filterX + 20;
        float ddX = filterX + filterW / 2f;
        float ddW = filterW / 2f - 20;
        float rowY = filterY + 40;

        // Activity filter
        renderModalLabel(nvg, fontName, labelX, rowY, "Activity");
        String actLabel = filterActivityId == null ? "All" : getActivityNameById(filterActivityId);
        renderModalDropdown(nvg, fontName, ddX, rowY, ddW, actLabel, false);
        rowY += MODAL_ROW_SPACING;

        // Region filter
        renderModalLabel(nvg, fontName, labelX, rowY, "Region");
        String regLabel = filterRegion == null ? "All" : filterRegion.name();
        renderModalDropdown(nvg, fontName, ddX, rowY, ddW, regLabel, false);

        // Apply / Clear buttons
        float backW = 60;
        float backH = 20;
        float clearW = 60;
        float totalBtnW = backW + 8 + clearW;
        float btnStartX = filterX + (filterW - totalBtnW) / 2f;
        float btnY = filterY + filterH - backH - 10;

        boolean applyHovered = isHovered(nvgMouseX, nvgMouseY, btnStartX, btnY, backW, backH);
        NVGWrapper.drawRect(nvg, btnStartX, btnY, backW, backH, applyHovered ? NEW_PARTY_HOVER : NEW_PARTY_COLOR);
        nvgFontSize(nvg, MODAL_LABEL_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var bc = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, bc);
        nvgText(nvg, btnStartX + backW / 2f, btnY + backH / 2f, "Apply");
        bc.free();

        float clearX = btnStartX + backW + 8;
        boolean clearHovered = isHovered(nvgMouseX, nvgMouseY, clearX, btnY, clearW, backH);
        NVGWrapper.drawRect(nvg, clearX, btnY, clearW, backH, clearHovered ? DELIST_PARTY_HOVER : DELIST_PARTY_COLOR);
        var clc = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, clc);
        nvgText(nvg, clearX + clearW / 2f, btnY + backH / 2f, "Clear");
        clc.free();
    }

    // ── Helpers ──

    private String getSelectedActivityName() {
        List<Activity> activities = party().getActivities();
        if (activities.isEmpty()) return loadingActivities ? "Loading..." : "None";
        if (modalActivityIndex >= 0 && modalActivityIndex < activities.size()) {
            return activities.get(modalActivityIndex).name();
        }
        return "Select...";
    }

    private String getActivityNameById(long id) {
        for (Activity a : party().getActivities()) {
            if (a.id() == id) return a.name();
        }
        return "Unknown";
    }

    private AssetManager.Asset getClassIcon(String className) {
        if (SeqClient.assetManager == null) return null;
        return SeqClient.assetManager.getAsset(className);
    }

    private boolean matchesSearch(Listing listing) {
        if (searchQuery.isEmpty()) return true;
        String q = searchQuery.toLowerCase();
        if (listing.activity().name().toLowerCase().contains(q)) return true;
        if (listing.note() != null && listing.note().toLowerCase().contains(q)) return true;
        for (Member m : listing.members()) {
            if (PlayerNameCache.resolve(m.playerUUID()).toLowerCase().contains(q)) return true;
        }
        return false;
    }

    private boolean isHovered(float mx, float my, float bx, float by, float bw, float bh) {
        return mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
    }

    // ══════════════════════════════ INPUT ══════════════════════════════

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() != 0) return super.mouseClicked(click, outsideScreen);

        double guiScale = SeqClient.mc.getWindow().getGuiScale();
        float mx = (float) (click.x() * guiScale / 2.0);
        float my = (float) (click.y() * guiScale / 2.0);

        float screenWidth = SeqClient.mc.getWindow().getWidth() / 2f;
        float screenHeight = SeqClient.mc.getWindow().getHeight() / 2f;

        // ── Filter dropdown ──
        if (filterDropdownOpen) {
            return handleFilterClick(mx, my, screenWidth, screenHeight);
        }

        // ── Modal clicks ──
        if (modalOpen) {
            return handleModalClick(mx, my, screenWidth, screenHeight);
        }

        // ── Role dropdown menu ──
        if (roleDropdownOpen) {
            float itemH = 20;
            float menuY = dropdownRenderY + SEARCH_BAR_HEIGHT;
            PartyRole[] roles = PartyRole.values();
            for (int i = 0; i < roles.length; i++) {
                float itemY = menuY + i * itemH;
                if (isHovered(mx, my, dropdownRenderX, itemY, dropdownRenderW, itemH)) {
                    selectedRole = roles[i];
                    roleDropdownOpen = false;
                    if (party().isInParty()) {
                        party().changeMyRole(selectedRole);
                    }
                    return true;
                }
            }
            roleDropdownOpen = false;
        }

        // ── Sidebar ──
        float btnX = SIDEBAR_PADDING;
        float btnW = SIDEBAR_WIDTH - SIDEBAR_PADDING * 2;
        float btnStartY = 50;

        if (isHovered(mx, my, btnX, btnStartY, btnW, SIDEBAR_BUTTON_HEIGHT)) return true;
        if (isHovered(mx, my, btnX, btnStartY + (SIDEBAR_BUTTON_HEIGHT + SIDEBAR_BUTTON_SPACING), btnW, SIDEBAR_BUTTON_HEIGHT)) {
            SeqClient.mc.setScreen(new SettingsScreen(this));
            return true;
        }
        if (isHovered(mx, my, btnX, btnStartY + (SIDEBAR_BUTTON_HEIGHT + SIDEBAR_BUTTON_SPACING) * 2, btnW, SIDEBAR_BUTTON_HEIGHT)) {
            try { java.awt.Desktop.getDesktop().browse(java.net.URI.create(GITHUB_URL)); } catch (Exception ignored) {}
            return true;
        }

        // ── Header ──
        float panelX = SIDEBAR_WIDTH;
        float panelWidth = screenWidth - SIDEBAR_WIDTH;

        float searchX = panelX + SEARCH_BAR_MARGIN;
        float searchY = (HEADER_HEIGHT - SEARCH_BAR_HEIGHT) / 2f;
        if (isHovered(mx, my, searchX, searchY, SEARCH_BAR_WIDTH, SEARCH_BAR_HEIGHT)) {
            searchFocused = true;
            searchCursorBlink = 0;
            return true;
        } else {
            searchFocused = false;
        }

        float headerBtnX = searchX + SEARCH_BAR_WIDTH + SEARCH_BAR_MARGIN;
        float headerBtnY = searchY;

        if (party().isPartyLeader()) {
            float manageW = 95;
            if (isHovered(mx, my, headerBtnX, headerBtnY, manageW, SEARCH_BAR_HEIGHT)) {
                openModal();
                return true;
            }
            headerBtnX += manageW + 6;

            float delistW = 80;
            if (isHovered(mx, my, headerBtnX, headerBtnY, delistW, SEARCH_BAR_HEIGHT)) {
                Listing current = party().getCurrentListing();
                if (current != null) {
                    party().disbandParty(current.id()).thenRun(this::refreshListings);
                }
                return true;
            }
            headerBtnX += delistW + 6;
        } else {
            float newW = 80;
            if (isHovered(mx, my, headerBtnX, headerBtnY, newW, SEARCH_BAR_HEIGHT)) {
                if (!party().isInParty()) {
                    openModal();
                }
                return true;
            }
            headerBtnX += 86;
        }

        if (isHovered(mx, my, dropdownRenderX, dropdownRenderY, dropdownRenderW, SEARCH_BAR_HEIGHT)) {
            roleDropdownOpen = !roleDropdownOpen;
            return true;
        }

        // ── Filter button ──
        float filterX = panelX + panelWidth - FILTER_BUTTON_W - FILTER_BUTTON_MARGIN;
        float filterY = screenHeight - FILTER_BUTTON_H - FILTER_BUTTON_MARGIN;
        if (isHovered(mx, my, filterX, filterY, FILTER_BUTTON_W, FILTER_BUTTON_H)) {
            filterDropdownOpen = true;
            return true;
        }

        // ── Scrollbar ──
        float contentY = HEADER_HEIGHT;
        float contentHeight = screenHeight - HEADER_HEIGHT;

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

        // ── Listing cards ──
        float cursorY = contentY - scrollOffset + PADDING;
        float contentWidth = panelWidth;
        List<Listing> listings = party().getListings();

        for (int i = 0; i < listings.size(); i++) {
            Listing listing = listings.get(i);
            if (!matchesSearch(listing)) continue;

            float cardX = panelX + PADDING;
            float cardW = contentWidth - PADDING * 2 - 6;
            float cardH;

            if (listing.isExpanded()) {
                cardH = CARD_HEADER_HEIGHT + listing.members().size() * MEMBER_ROW_HEIGHT + CARD_PADDING;

                // Leader management: promote/kick clicks
                Listing current = party().getCurrentListing();
                boolean isMyListing = current != null && current.id() == listing.id() && party().isPartyLeader();
                if (isMyListing) {
                    float memberY = cursorY + CARD_HEADER_HEIGHT;
                    float memberRowX = cardX + CARD_PADDING + 10;
                    float memberRowW = cardW - CARD_PADDING * 2 - 10;
                    for (int mi = 0; mi < listing.members().size(); mi++) {
                        Member member = listing.members().get(mi);
                        boolean isLeader = member.playerUUID().equals(listing.leaderUUID());
                        if (!isLeader && isHovered(mx, my, memberRowX, memberY, memberRowW, MEMBER_ROW_HEIGHT)) {
                            float nameStartX = memberRowX + STAR_ICON_SIZE + 4;
                            float nameAreaW = memberRowW - STAR_ICON_SIZE - 4;
                            float nameMidX = nameStartX + nameAreaW / 2f;

                            UUID targetUUID = UUID.fromString(member.playerUUID());
                            if (mx < nameMidX) {
                                party().transferLeadership(listing.id(), targetUUID);
                            } else {
                                party().kickMember(listing.id(), targetUUID)
                                        .thenRun(this::refreshListings);
                            }
                            return true;
                        }
                        memberY += MEMBER_ROW_HEIGHT;
                    }
                }

                float joinBtnX = cardX + cardW - CARD_PADDING - JOIN_BUTTON_WIDTH;
                float joinBtnY = cursorY + CARD_HEADER_HEIGHT + (listing.members().size() - 1) * MEMBER_ROW_HEIGHT
                        + (MEMBER_ROW_HEIGHT - BUTTON_HEIGHT) / 2f;

                boolean isJoined = current != null && current.id() == listing.id();
                if (!isMyListing && isHovered(mx, my, joinBtnX, joinBtnY, JOIN_BUTTON_WIDTH, BUTTON_HEIGHT)) {
                    if (isJoined) {
                        party().leaveParty(listing.id()).thenRun(this::refreshListings);
                    } else if (!party().isInParty()) {
                        party().joinParty(listing.id(), selectedRole).thenRun(this::refreshListings);
                    }
                    return true;
                }

                if (isHovered(mx, my, cardX, cursorY, cardW, CARD_HEADER_HEIGHT)) {
                    listing.setExpanded(false);
                    return true;
                }
            } else {
                cardH = COLLAPSED_ROW_HEIGHT;
                if (isHovered(mx, my, cardX, cursorY, cardW, cardH)) {
                    listing.setExpanded(true);
                    return true;
                }
            }
            cursorY += cardH + CARD_SPACING;
        }

        return super.mouseClicked(click, outsideScreen);
    }

    private void openModal() {
        modalOpen = false;
        closeAllModalDropdowns();
        modalActivityIndex = 0;
        modalMode = PartyMode.CHILL;
        modalRegion = PartyRegion.NA;
        modalRole = selectedRole != null ? selectedRole : PartyRole.DPS;
        modalNote = "";
        modalNoteFocused = false;
        modalOpen = true;
    }

    private void closeAllModalDropdowns() {
        modalActivityDropdownOpen = false;
        modalModeDropdownOpen = false;
        modalRegionDropdownOpen = false;
        modalRoleDropdownOpen = false;
    }

    private boolean handleModalClick(float mx, float my, float screenWidth, float screenHeight) {
        float panelX = SIDEBAR_WIDTH;
        float panelWidth = screenWidth - SIDEBAR_WIDTH;
        float mX = panelX + (panelWidth - MODAL_WIDTH) / 2f;
        float mY = (screenHeight - MODAL_HEIGHT) / 2f;
        float dropdownX = mX + MODAL_WIDTH / 2f;

        // Handle open dropdown clicks first
        float ddRowY = mY + 44;
        if (modalActivityDropdownOpen) {
            List<Activity> acts = party().getActivities();
            float itemH = 20;
            float menuY = ddRowY + MODAL_DROPDOWN_H;
            for (int i = 0; i < acts.size(); i++) {
                float itemY = menuY + i * itemH;
                if (isHovered(mx, my, dropdownX, itemY, MODAL_DROPDOWN_W, itemH)) {
                    modalActivityIndex = i;
                    modalActivityDropdownOpen = false;
                    return true;
                }
            }
            modalActivityDropdownOpen = false;
            return true;
        }
        ddRowY += MODAL_ROW_SPACING;
        if (modalModeDropdownOpen) {
            float itemH = 20;
            float menuY = ddRowY + MODAL_DROPDOWN_H;
            PartyMode[] modes = PartyMode.values();
            for (int i = 0; i < modes.length; i++) {
                float itemY = menuY + i * itemH;
                if (isHovered(mx, my, dropdownX, itemY, MODAL_DROPDOWN_W, itemH)) {
                    modalMode = modes[i];
                    modalModeDropdownOpen = false;
                    return true;
                }
            }
            modalModeDropdownOpen = false;
            return true;
        }
        ddRowY += MODAL_ROW_SPACING;
        if (modalRegionDropdownOpen) {
            float itemH = 20;
            float menuY = ddRowY + MODAL_DROPDOWN_H;
            PartyRegion[] regions = PartyRegion.values();
            for (int i = 0; i < regions.length; i++) {
                float itemY = menuY + i * itemH;
                if (isHovered(mx, my, dropdownX, itemY, MODAL_DROPDOWN_W, itemH)) {
                    modalRegion = regions[i];
                    modalRegionDropdownOpen = false;
                    return true;
                }
            }
            modalRegionDropdownOpen = false;
            return true;
        }
        ddRowY += MODAL_ROW_SPACING;
        if (modalRoleDropdownOpen) {
            float itemH = 20;
            float menuY = ddRowY + MODAL_DROPDOWN_H;
            PartyRole[] roles = PartyRole.values();
            for (int i = 0; i < roles.length; i++) {
                float itemY = menuY + i * itemH;
                if (isHovered(mx, my, dropdownX, itemY, MODAL_DROPDOWN_W, itemH)) {
                    modalRole = roles[i];
                    modalRoleDropdownOpen = false;
                    return true;
                }
            }
            modalRoleDropdownOpen = false;
            return true;
        }

        // Click outside modal closes it
        if (!isHovered(mx, my, mX, mY, MODAL_WIDTH, MODAL_HEIGHT)) {
            modalOpen = false;
            closeAllModalDropdowns();
            modalNoteFocused = false;
            return true;
        }

        // Dropdown toggle clicks
        float rowY = mY + 44;
        if (isHovered(mx, my, dropdownX, rowY, MODAL_DROPDOWN_W, MODAL_DROPDOWN_H)) {
            closeAllModalDropdowns();
            modalActivityDropdownOpen = true;
            modalNoteFocused = false;
            return true;
        }
        rowY += MODAL_ROW_SPACING;
        if (isHovered(mx, my, dropdownX, rowY, MODAL_DROPDOWN_W, MODAL_DROPDOWN_H)) {
            closeAllModalDropdowns();
            modalModeDropdownOpen = true;
            modalNoteFocused = false;
            return true;
        }
        rowY += MODAL_ROW_SPACING;
        if (isHovered(mx, my, dropdownX, rowY, MODAL_DROPDOWN_W, MODAL_DROPDOWN_H)) {
            closeAllModalDropdowns();
            modalRegionDropdownOpen = true;
            modalNoteFocused = false;
            return true;
        }
        rowY += MODAL_ROW_SPACING;
        if (isHovered(mx, my, dropdownX, rowY, MODAL_DROPDOWN_W, MODAL_DROPDOWN_H)) {
            closeAllModalDropdowns();
            modalRoleDropdownOpen = true;
            modalNoteFocused = false;
            return true;
        }
        rowY += MODAL_ROW_SPACING;
        // Note field click
        if (isHovered(mx, my, dropdownX, rowY, MODAL_DROPDOWN_W, MODAL_DROPDOWN_H)) {
            closeAllModalDropdowns();
            modalNoteFocused = true;
            return true;
        }
        modalNoteFocused = false;

        // Create button
        float createBtnX = mX + (MODAL_WIDTH - MODAL_BUTTON_W) / 2f;
        float createBtnY = mY + MODAL_HEIGHT - MODAL_BUTTON_H - 14;
        if (isHovered(mx, my, createBtnX, createBtnY, MODAL_BUTTON_W, MODAL_BUTTON_H)) {
            List<Activity> activities = party().getActivities();
            if (!activities.isEmpty() && modalActivityIndex >= 0 && modalActivityIndex < activities.size()) {
                Activity activity = activities.get(modalActivityIndex);
                String note = modalNote.isBlank() ? null : modalNote.trim();
                party().createParty(activity.id(), modalMode, modalRegion, modalRole, note)
                        .thenRun(this::refreshListings);
                modalOpen = false;
                closeAllModalDropdowns();
                scrollOffset = 0;
            }
            return true;
        }

        return true;
    }

    private boolean handleFilterClick(float mx, float my, float screenWidth, float screenHeight) {
        float panelX = SIDEBAR_WIDTH;
        float panelWidth = screenWidth - SIDEBAR_WIDTH;
        float filterW = 260;
        float filterH = 130;
        float filterX = panelX + (panelWidth - filterW) / 2f;
        float filterY = (screenHeight - filterH) / 2f;
        float ddX = filterX + filterW / 2f;
        float ddW = filterW / 2f - 20;

        // Click outside closes
        if (!isHovered(mx, my, filterX, filterY, filterW, filterH)) {
            filterDropdownOpen = false;
            return true;
        }

        // Activity filter dropdown click
        float rowY = filterY + 40;
        if (isHovered(mx, my, ddX, rowY, ddW, MODAL_DROPDOWN_H)) {
            // Cycle through: All → Activity 1 → Activity 2 → ... → All
            List<Activity> acts = party().getActivities();
            if (acts.isEmpty()) return true;
            if (filterActivityId == null) {
                filterActivityId = acts.get(0).id();
            } else {
                int idx = -1;
                for (int i = 0; i < acts.size(); i++) {
                    if (acts.get(i).id() == filterActivityId) { idx = i; break; }
                }
                if (idx >= 0 && idx < acts.size() - 1) {
                    filterActivityId = acts.get(idx + 1).id();
                } else {
                    filterActivityId = null;
                }
            }
            return true;
        }
        rowY += MODAL_ROW_SPACING;

        // Region filter dropdown click
        if (isHovered(mx, my, ddX, rowY, ddW, MODAL_DROPDOWN_H)) {
            PartyRegion[] regions = PartyRegion.values();
            if (filterRegion == null) {
                filterRegion = regions[0];
            } else {
                int idx = filterRegion.ordinal();
                if (idx < regions.length - 1) {
                    filterRegion = regions[idx + 1];
                } else {
                    filterRegion = null;
                }
            }
            return true;
        }

        // Apply button
        float backW = 60;
        float backH = 20;
        float clearW = 60;
        float totalBtnW = backW + 8 + clearW;
        float btnStartX = filterX + (filterW - totalBtnW) / 2f;
        float btnY = filterY + filterH - backH - 10;

        if (isHovered(mx, my, btnStartX, btnY, backW, backH)) {
            filterDropdownOpen = false;
            refreshListings();
            return true;
        }

        // Clear button
        float clearX = btnStartX + backW + 8;
        if (isHovered(mx, my, clearX, btnY, clearW, backH)) {
            filterActivityId = null;
            filterRegion = null;
            filterDropdownOpen = false;
            refreshListings();
            return true;
        }

        return true;
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent click) {
        scrollbarDragging = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (scrollbarDragging && maxScroll > 0) {
            double guiScale = SeqClient.mc.getWindow().getGuiScale();
            float my = (float) (click.y() * guiScale / 2.0);

            float screenHeight = SeqClient.mc.getWindow().getHeight() / 2f;
            float contentHeight = screenHeight - HEADER_HEIGHT;
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
        if (modalOpen || filterDropdownOpen) return true;
        scrollOffset -= (float) scrollY * SCROLL_SPEED;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        return true;
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent keyEvent) {
        if (filterDropdownOpen) {
            if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) { filterDropdownOpen = false; return true; }
            return true;
        }
        if (modalOpen) {
            int keyCode = keyEvent.key();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (modalActivityDropdownOpen || modalModeDropdownOpen || modalRegionDropdownOpen || modalRoleDropdownOpen) {
                    closeAllModalDropdowns();
                } else if (modalNoteFocused) {
                    modalNoteFocused = false;
                } else {
                    modalOpen = false;
                    closeAllModalDropdowns();
                }
                return true;
            }
            if (modalNoteFocused) {
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    modalNoteFocused = false;
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                    if (!modalNote.isEmpty()) {
                        modalNote = modalNote.substring(0, modalNote.length() - 1);
                    }
                    return true;
                }
                if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
                    boolean shift = (keyEvent.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
                    char letter = (char) ('a' + (keyCode - GLFW.GLFW_KEY_A));
                    if (modalNote.length() < 100) modalNote += shift ? Character.toUpperCase(letter) : letter;
                    return true;
                }
                if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
                    if (modalNote.length() < 100) modalNote += (char) ('0' + (keyCode - GLFW.GLFW_KEY_0));
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_SPACE) {
                    if (modalNote.length() < 100) modalNote += ' ';
                    return true;
                }
            }
            return true;
        }
        if (searchFocused) {
            int keyCode = keyEvent.key();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { searchFocused = false; return true; }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) { searchQuery = searchQuery.substring(0, searchQuery.length() - 1); scrollOffset = 0; }
                return true;
            }
            if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
                boolean shift = (keyEvent.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
                char letter = (char) ('a' + (keyCode - GLFW.GLFW_KEY_A));
                searchQuery += shift ? Character.toUpperCase(letter) : letter;
                scrollOffset = 0;
                return true;
            }
            if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
                searchQuery += (char) ('0' + (keyCode - GLFW.GLFW_KEY_0));
                scrollOffset = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_SPACE) { searchQuery += ' '; scrollOffset = 0; return true; }
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
