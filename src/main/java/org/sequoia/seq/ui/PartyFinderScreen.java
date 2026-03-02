package org.sequoia.seq.ui;

import static org.lwjgl.nanovg.NanoVG.*;

import java.awt.*;
import java.util.*;
import java.util.List;
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
import org.sequoia.seq.managers.PartyListing;
import org.sequoia.seq.managers.PartyMember;
import org.sequoia.seq.utils.rendering.nvg.NVGContext;
import org.sequoia.seq.utils.rendering.nvg.NVGWrapper;

public class PartyFinderScreen extends Screen implements PartyAccessor {

    // ── Raid types & Party tags ──
    private static final String[] RAID_TYPES = {
            "Nest of the Grootslangs",
            "Nexus of Light",
            "The Canyon Colossus",
            "The Nameless Anomaly",
            "Prelude to Annihilation",
    };
    private static final String[] PARTY_TAGS = { "Chill", "Grind" };

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
    private static final float CLASS_ICON_SIZE = 14;
    private static final float STAR_ICON_SIZE = 16;
    private static final float TYPE_ICON_SIZE = 24;
    private static final float BUTTON_HEIGHT = 24;
    private static final float JOIN_BUTTON_WIDTH = 64;

    // Modal layout
    private static final float MODAL_WIDTH = 300;
    private static final float MODAL_HEIGHT = 200;
    private static final float RAID_CIRCLE_SIZE = 36;
    private static final float RAID_CIRCLE_SPACING = 12;
    private static final float MODAL_DROPDOWN_W = 80;
    private static final float MODAL_DROPDOWN_H = 20;
    private static final float MODAL_BUTTON_W = 80;
    private static final float MODAL_BUTTON_H = 24;

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
    private static final float RAID_LABEL_SIZE = 9;
    private static final float MODAL_TITLE_SIZE = 16;
    private static final float MODAL_LABEL_SIZE = 12;
    private static final float TAG_CHIP_FONT_SIZE = 11;

    // ── Colors ──
    private static final Color BG_COLOR = new Color(10, 10, 16, 100);
    private static final Color SIDEBAR_COLOR = new Color(18, 18, 26, 200);
    private static final Color PANEL_COLOR = new Color(22, 22, 30, 100);
    private static final Color HEADER_COLOR = new Color(26, 26, 36, 110);
    private static final Color TITLE_COLOR = new Color(160, 130, 220, 255);
    private static final Color TEXT_COLOR = new Color(255, 255, 255, 255);
    private static final Color DIVIDER_COLOR = new Color(40, 40, 55, 255);

    private static final Color SIDEBAR_BUTTON_COLOR = new Color(
            30,
            30,
            42,
            110);
    private static final Color SIDEBAR_BUTTON_HOVER = new Color(
            42,
            42,
            58,
            120);
    private static final Color SIDEBAR_BUTTON_ACTIVE = new Color(
            80,
            50,
            140,
            120);

    private static final Color SEARCH_BG = new Color(30, 30, 40, 255);
    private static final Color SEARCH_ACTIVE_BG = new Color(40, 40, 55, 255);
    private static final Color SEARCH_BORDER = new Color(130, 100, 200, 180);
    private static final Color SEARCH_PLACEHOLDER = new Color(
            100,
            100,
            120,
            200);

    private static final Color CARD_BG = new Color(30, 30, 42, 110);
    private static final Color CARD_EXPANDED_BG = new Color(26, 26, 36, 120);
    private static final Color MEMBER_TEXT_COLOR = new Color(
            220,
            220,
            230,
            255);
    private static final Color MEMBER_DIM_COLOR = new Color(120, 120, 140, 180);
    private static final Color ROLE_TEXT_COLOR = new Color(160, 160, 180, 255);

    private static final Color PARTY_TYPE_TEXT = new Color(180, 180, 200, 255);
    private static final Color EXPAND_ARROW_COLOR = new Color(
            140,
            140,
            160,
            255);

    private static final Color JOIN_BUTTON_COLOR = new Color(
            160,
            130,
            220,
            255);
    private static final Color JOIN_BUTTON_HOVER = new Color(
            180,
            150,
            240,
            255);
    private static final Color JOINED_BUTTON_COLOR = new Color(
            140,
            110,
            200,
            255);
    private static final Color NEW_PARTY_COLOR = new Color(160, 130, 220, 200);
    private static final Color NEW_PARTY_HOVER = new Color(180, 150, 240, 220);
    private static final Color MANAGE_PARTY_COLOR = new Color(
            160,
            130,
            220,
            200);
    private static final Color DELIST_PARTY_COLOR = new Color(200, 60, 60, 200);
    private static final Color DELIST_PARTY_HOVER = new Color(220, 80, 80, 220);

    private static final Color DROPDOWN_BG = new Color(40, 40, 55, 240);
    private static final Color DROPDOWN_HOVER = new Color(55, 55, 75, 240);
    private static final Color DROPDOWN_BORDER = new Color(80, 80, 100, 200);

    private static final Color TYPE_ICON_SELECTED = new Color(200, 50, 50, 80);

    private static final Color SCROLLBAR_TRACK = new Color(30, 30, 42, 255);
    private static final Color SCROLLBAR_THUMB = new Color(160, 130, 220, 150);

    private static final Color MODAL_BG = new Color(20, 20, 30, 255);
    private static final Color MODAL_BORDER = new Color(80, 80, 100, 255);
    private static final Color MODAL_OVERLAY = new Color(0, 0, 0, 160);
    private static final Color MODAL_DROPDOWN_BG = new Color(35, 35, 48, 255);
    private static final Color MODAL_DROPDOWN_BORDER = new Color(
            80,
            80,
            100,
            200);

    private static final Color TAG_CHIP_BG = new Color(40, 40, 55, 220);
    private static final Color TAG_CHIP_HOVER = new Color(55, 55, 75, 240);
    private static final Color FILTER_BOX_BG = new Color(15, 15, 22, 240);

    private static final String GITHUB_URL = "https://github.com/SequoiaWynncraft/sequoia-mod";
    private static final String[] ROLES = { "DPS", "Healer", "Tank" };

    // All possible tags = RAID_TYPES + PARTY_TAGS
    private static final String[] ALL_TAGS;

    static {
        ALL_TAGS = new String[RAID_TYPES.length + PARTY_TAGS.length];
        System.arraycopy(RAID_TYPES, 0, ALL_TAGS, 0, RAID_TYPES.length);
        System.arraycopy(
                PARTY_TAGS,
                0,
                ALL_TAGS,
                RAID_TYPES.length,
                PARTY_TAGS.length);
    }

    private static final Set<String> RAID_TYPE_SET = new HashSet<>(
            Arrays.asList(RAID_TYPES));

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
    private String selectedRole = null;

    private float dropdownRenderX, dropdownRenderY, dropdownRenderW;

    // ── Modal state ──
    private boolean modalOpen = false;
    private final Set<String> modalSelectedRaids = new LinkedHashSet<>();
    private int modalReservedSlots = 0;
    private boolean reservedSlotsFocused = false;
    private String reservedSlotsInput = "0";

    // Edit tags sub-overlay in modal
    private boolean editTagsScreenOpen = false;
    private final Set<String> modalActiveTags = new LinkedHashSet<>();
    private final Set<String> modalInactiveTags = new LinkedHashSet<>();
    private final Map<String, Long> modalTagAnimStartTimes = new HashMap<>();

    // Cached modal position
    private float modalX, modalY;

    // ── Filter+ screen state ──
    private boolean filterScreenOpen = false;
    private final Set<String> activeFilterTags = new LinkedHashSet<>();
    private final Set<String> inactiveFilterTags = new LinkedHashSet<>();
    private final Map<String, Long> filterTagAnimStartTimes = new HashMap<>();

    // ── Leader member management ──
    private int hoveredMemberPartyIndex = -1;
    private int hoveredMemberIndex = -1;

    public PartyFinderScreen(Screen parent) {
        super(Component.literal("Party Finder"));
        this.parent = parent;
        // Initialize filter: all tags active
        for (String tag : ALL_TAGS) {
            activeFilterTags.add(tag);
        }
        // Initialize modal tags: Chill active, Grind inactive
        modalActiveTags.add("Chill");
        modalInactiveTags.add("Grind");
    }

    // ══════════════════════════════ INIT ══════════════════════════════

    @Override
    protected void init() {
        super.init();
        party().refreshData();
    }

    // ══════════════════════════════ RENDER ══════════════════════════════

    @Override
    public void render(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        double guiScale = SeqClient.mc.getWindow().getGuiScale();
        nvgMouseX = (float) ((mouseX * guiScale) / 2.0);
        nvgMouseY = (float) ((mouseY * guiScale) / 2.0);

        NVGContext.renderDeferred(nvg -> {
            float screenWidth = SeqClient.mc.getWindow().getWidth() / 2f;
            float screenHeight = SeqClient.mc.getWindow().getHeight() / 2f;
            String fontName = SeqClient.getFontManager().getSelectedFont();

            NVGWrapper.drawRect(nvg, 0, 0, screenWidth, screenHeight, BG_COLOR);
            renderSidebar(nvg, fontName, screenHeight);

            float panelX = SIDEBAR_WIDTH;
            float panelWidth = screenWidth - SIDEBAR_WIDTH;

            NVGWrapper.drawRect(
                    nvg,
                    panelX,
                    0,
                    panelWidth,
                    screenHeight,
                    PANEL_COLOR);
            NVGWrapper.drawRect(
                    nvg,
                    panelX,
                    0,
                    panelWidth,
                    HEADER_HEIGHT,
                    HEADER_COLOR);
            renderHeaderControls(nvg, fontName, panelX, panelWidth);

            nvgFontFace(nvg, fontName);
            nvgFontSize(nvg, TITLE_FONT_SIZE);
            nvgTextAlign(nvg, NVG_ALIGN_RIGHT | NVG_ALIGN_MIDDLE);
            var titleCol = NVGContext.nvgColor(TITLE_COLOR);
            nvgFillColor(nvg, titleCol);
            nvgText(
                    nvg,
                    panelX + panelWidth - SEARCH_BAR_MARGIN,
                    HEADER_HEIGHT / 2f,
                    "Party Finder");
            titleCol.free();

            // Content area
            float contentX = panelX;
            float contentY = HEADER_HEIGHT;
            float contentWidth = panelWidth;
            float contentHeight = screenHeight - HEADER_HEIGHT;

            // Update hovered member tracking
            hoveredMemberPartyIndex = -1;
            hoveredMemberIndex = -1;

            nvgSave(nvg);
            nvgScissor(nvg, contentX, contentY, contentWidth, contentHeight);

            float cursorY = contentY - scrollOffset + PADDING;
            for (int i = 0; i < party().getParties().size(); i++) {
                PartyListing party = party().getParties().get(i);
                if (!matchesFilters(party))
                    continue;

                float cardH = party.expanded
                        ? CARD_HEADER_HEIGHT +
                                party.members.size() * MEMBER_ROW_HEIGHT +
                                CARD_PADDING
                        : COLLAPSED_ROW_HEIGHT;

                renderPartyCard(
                        nvg,
                        fontName,
                        contentX + PADDING,
                        cursorY,
                        contentWidth - PADDING * 2 - 6,
                        cardH,
                        party,
                        i);
                cursorY += cardH + CARD_SPACING;
            }

            maxScroll = Math.max(
                    0,
                    cursorY + scrollOffset - contentY - contentHeight);
            nvgRestore(nvg);

            // Scrollbar
            if (maxScroll > 0) {
                float scrollbarX = panelX + panelWidth - 5;
                NVGWrapper.drawRect(
                        nvg,
                        scrollbarX,
                        contentY,
                        4,
                        contentHeight,
                        SCROLLBAR_TRACK);
                float thumbRatio = contentHeight / (contentHeight + maxScroll);
                float thumbH = Math.max(20, contentHeight * thumbRatio);
                float thumbY = contentY +
                        (scrollOffset / maxScroll) * (contentHeight - thumbH);
                NVGWrapper.drawRect(
                        nvg,
                        scrollbarX,
                        thumbY,
                        4,
                        thumbH,
                        SCROLLBAR_THUMB);
            }

            // Filter + button (bottom right of content area)
            float filterX = panelX + panelWidth - FILTER_BUTTON_W - FILTER_BUTTON_MARGIN;
            float filterY = screenHeight - FILTER_BUTTON_H - FILTER_BUTTON_MARGIN;
            boolean filterHovered = isHovered(
                    nvgMouseX,
                    nvgMouseY,
                    filterX,
                    filterY,
                    FILTER_BUTTON_W,
                    FILTER_BUTTON_H);
            NVGWrapper.drawRect(
                    nvg,
                    filterX,
                    filterY,
                    FILTER_BUTTON_W,
                    FILTER_BUTTON_H,
                    filterHovered ? NEW_PARTY_HOVER : NEW_PARTY_COLOR);
            nvgFontFace(nvg, fontName);
            nvgFontSize(nvg, HEADER_BUTTON_SIZE);
            nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
            var ftc = NVGContext.nvgColor(TEXT_COLOR);
            nvgFillColor(nvg, ftc);
            nvgText(
                    nvg,
                    filterX + FILTER_BUTTON_W / 2f,
                    filterY + FILTER_BUTTON_H / 2f,
                    "Filter +");
            ftc.free();

            // Role dropdown overlay
            if (roleDropdownOpen && !modalOpen && !filterScreenOpen) {
                renderRoleDropdownMenu(nvg, fontName);
            }

            // Modal overlay
            if (modalOpen) {
                renderModal(nvg, fontName, panelX, panelWidth, screenHeight);
            }

            // Filter+ screen overlay (highest priority)
            if (filterScreenOpen) {
                renderFilterScreen(
                        nvg,
                        fontName,
                        panelX,
                        panelWidth,
                        screenHeight);
            }
        });
    }

    // ── Sidebar ──

    private void renderSidebar(long nvg, String fontName, float screenHeight) {
        NVGWrapper.drawRect(
                nvg,
                0,
                0,
                SIDEBAR_WIDTH,
                screenHeight,
                SIDEBAR_COLOR);

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, SIDEBAR_TITLE_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var col = NVGContext.nvgColor(TITLE_COLOR);
        nvgFillColor(nvg, col);
        nvgText(nvg, SIDEBAR_WIDTH / 2f, 22, "Sequoia");
        col.free();

        NVGWrapper.drawRect(
                nvg,
                SIDEBAR_PADDING,
                40,
                SIDEBAR_WIDTH - SIDEBAR_PADDING * 2,
                1,
                DIVIDER_COLOR);

        float btnX = SIDEBAR_PADDING;
        float btnW = SIDEBAR_WIDTH - SIDEBAR_PADDING * 2;
        float btnY = 50;

        drawSidebarButton(nvg, fontName, btnX, btnY, btnW, "Partyfinder", true);
        drawSidebarButton(
                nvg,
                fontName,
                btnX,
                btnY + (SIDEBAR_BUTTON_HEIGHT + SIDEBAR_BUTTON_SPACING),
                btnW,
                "Settings",
                false);
        drawSidebarButton(
                nvg,
                fontName,
                btnX,
                btnY + (SIDEBAR_BUTTON_HEIGHT + SIDEBAR_BUTTON_SPACING) * 2,
                btnW,
                "Github",
                false);
    }

    private void drawSidebarButton(
            long nvg,
            String fontName,
            float x,
            float y,
            float w,
            String label,
            boolean active) {
        boolean hovered = isHovered(
                nvgMouseX,
                nvgMouseY,
                x,
                y,
                w,
                SIDEBAR_BUTTON_HEIGHT);
        Color bg = active
                ? SIDEBAR_BUTTON_ACTIVE
                : (hovered ? SIDEBAR_BUTTON_HOVER : SIDEBAR_BUTTON_COLOR);
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

    private void renderHeaderControls(
            long nvg,
            String fontName,
            float panelX,
            float panelWidth) {
        searchCursorBlink++;
        float searchX = panelX + SEARCH_BAR_MARGIN;
        float searchY = (HEADER_HEIGHT - SEARCH_BAR_HEIGHT) / 2f;

        Color searchBg = searchFocused ? SEARCH_ACTIVE_BG : SEARCH_BG;
        NVGWrapper.drawRect(
                nvg,
                searchX,
                searchY,
                SEARCH_BAR_WIDTH,
                SEARCH_BAR_HEIGHT,
                searchBg);
        if (searchFocused) {
            NVGWrapper.drawRectOutline(
                    nvg,
                    searchX,
                    searchY,
                    SEARCH_BAR_WIDTH,
                    SEARCH_BAR_HEIGHT,
                    1,
                    SEARCH_BORDER);
        }

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, SEARCH_FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);

        nvgSave(nvg);
        nvgScissor(nvg, searchX, searchY, SEARCH_BAR_WIDTH, SEARCH_BAR_HEIGHT);
        if (searchQuery.isEmpty() && !searchFocused) {
            var ph = NVGContext.nvgColor(SEARCH_PLACEHOLDER);
            nvgFillColor(nvg, ph);
            nvgText(
                    nvg,
                    searchX + 6,
                    searchY + SEARCH_BAR_HEIGHT / 2f,
                    "Search...");
            ph.free();
        } else {
            var tc = NVGContext.nvgColor(TEXT_COLOR);
            nvgFillColor(nvg, tc);
            nvgText(
                    nvg,
                    searchX + 6,
                    searchY + SEARCH_BAR_HEIGHT / 2f,
                    searchQuery);
            tc.free();
        }
        nvgRestore(nvg);

        if (searchFocused && (searchCursorBlink / 1000) % 2 == 0) {
            float[] bounds = new float[4];
            nvgFontFace(nvg, fontName);
            nvgFontSize(nvg, SEARCH_FONT_SIZE);
            float textW = searchQuery.isEmpty()
                    ? 0
                    : nvgTextBounds(nvg, 0, 0, searchQuery, bounds);
            NVGWrapper.drawRect(
                    nvg,
                    searchX + 6 + textW + 1,
                    searchY + 3,
                    1,
                    SEARCH_BAR_HEIGHT - 6,
                    TEXT_COLOR);
        }

        float btnX = searchX + SEARCH_BAR_WIDTH + SEARCH_BAR_MARGIN;
        float btnY = searchY;

        if (party().isPartyLeader()) {
            String manageLabel = party().hasListedParty()
                    ? "Manage Party"
                    : "New party +";
            float manageW = 95;
            drawHeaderButton(
                    nvg,
                    fontName,
                    btnX,
                    btnY,
                    manageW,
                    SEARCH_BAR_HEIGHT,
                    manageLabel,
                    MANAGE_PARTY_COLOR,
                    NEW_PARTY_HOVER);
            btnX += manageW + 6;

            float delistW = 80;
            drawHeaderButton(
                    nvg,
                    fontName,
                    btnX,
                    btnY,
                    delistW,
                    SEARCH_BAR_HEIGHT,
                    "Delist party",
                    DELIST_PARTY_COLOR,
                    DELIST_PARTY_HOVER);
            btnX += delistW + 6;
        } else {
            boolean inPartyAsMember = party().getJoinedPartyIndex() >= 0;
            Color newBg = inPartyAsMember
                    ? new Color(60, 60, 70, 180)
                    : NEW_PARTY_COLOR;
            Color newHover = inPartyAsMember
                    ? new Color(60, 60, 70, 180)
                    : NEW_PARTY_HOVER;
            drawHeaderButton(
                    nvg,
                    fontName,
                    btnX,
                    btnY,
                    80,
                    SEARCH_BAR_HEIGHT,
                    "New party +",
                    newBg,
                    newHover);
            btnX += 86;
        }

        float dropW = 80;
        dropdownRenderX = btnX;
        dropdownRenderY = btnY;
        dropdownRenderW = dropW;
        renderRoleDropdownButton(
                nvg,
                fontName,
                btnX,
                btnY,
                dropW,
                SEARCH_BAR_HEIGHT);
    }

    private void drawHeaderButton(
            long nvg,
            String fontName,
            float x,
            float y,
            float w,
            float h,
            String label,
            Color bg,
            Color hoverBg) {
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

    private void renderRoleDropdownButton(
            long nvg,
            String fontName,
            float x,
            float y,
            float w,
            float h) {
        boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y, w, h);
        NVGWrapper.drawRect(
                nvg,
                x,
                y,
                w,
                h,
                hovered ? SEARCH_ACTIVE_BG : SEARCH_BG);
        NVGWrapper.drawRectOutline(nvg, x, y, w, h, 1, DROPDOWN_BORDER);

        String label = selectedRole != null ? selectedRole : "Your role";
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
        float totalH = ROLES.length * itemH;

        NVGWrapper.drawRect(nvg, x, y, w, totalH, DROPDOWN_BG);
        NVGWrapper.drawRectOutline(nvg, x, y, w, totalH, 1, DROPDOWN_BORDER);

        for (int i = 0; i < ROLES.length; i++) {
            float itemY = y + i * itemH;
            boolean itemHovered = isHovered(
                    nvgMouseX,
                    nvgMouseY,
                    x,
                    itemY,
                    w,
                    itemH);
            if (itemHovered)
                NVGWrapper.drawRect(
                        nvg,
                        x,
                        itemY,
                        w,
                        itemH,
                        DROPDOWN_HOVER);

            nvgFontFace(nvg, fontName);
            nvgFontSize(nvg, MEMBER_FONT_SIZE);
            nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            var tc = NVGContext.nvgColor(TEXT_COLOR);
            nvgFillColor(nvg, tc);
            nvgText(nvg, x + 6, itemY + itemH / 2f, ROLES[i]);
            tc.free();
        }
    }

    // ── Party cards ──

    private void renderPartyCard(
            long nvg,
            String fontName,
            float x,
            float y,
            float w,
            float h,
            PartyListing party,
            int partyIndex) {
        boolean isJoined = party().getJoinedPartyIndex() == partyIndex;
        NVGWrapper.drawRect(
                nvg,
                x,
                y,
                w,
                h,
                party.expanded ? CARD_EXPANDED_BG : CARD_BG);

        if (party.expanded) {
            renderExpandedCard(
                    nvg,
                    fontName,
                    x,
                    y,
                    w,
                    h,
                    party,
                    partyIndex,
                    isJoined);
        } else {
            renderCollapsedCard(nvg, fontName, x, y, w, party);
        }
    }

    private void renderExpandedCard(
            long nvg,
            String fontName,
            float x,
            float y,
            float w,
            float h,
            PartyListing party,
            int partyIndex,
            boolean isJoined) {
        float rowX = x + CARD_PADDING;
        List<String> raidTags = party.getRaidTags();

        drawRaidIconCircle(
                nvg,
                fontName,
                rowX,
                y + (CARD_HEADER_HEIGHT - TYPE_ICON_SIZE) / 2f,
                TYPE_ICON_SIZE,
                raidTags);
        rowX += TYPE_ICON_SIZE + 6;

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, CARD_TITLE_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        var countCol = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, countCol);
        nvgText(
                nvg,
                rowX,
                y + CARD_HEADER_HEIGHT / 2f,
                party.members.size() + "/" + party.maxSize);
        countCol.free();

        // Collapse arrow
        nvgFontSize(nvg, 16);
        nvgTextAlign(nvg, NVG_ALIGN_RIGHT | NVG_ALIGN_MIDDLE);
        var arrCol = NVGContext.nvgColor(EXPAND_ARROW_COLOR);
        nvgFillColor(nvg, arrCol);
        nvgText(nvg, x + w - CARD_PADDING, y + CARD_HEADER_HEIGHT / 2f, "-");
        arrCol.free();

        // Members
        boolean isMyParty = partyIndex == party().getMyPartyIndex();
        boolean amLeaderOfThisParty = isMyParty && party().isPartyLeader();
        float memberY = y + CARD_HEADER_HEIGHT;
        for (int mi = 0; mi < party.members.size(); mi++) {
            PartyMember member = party.members.get(mi);
            renderMemberRow(
                    nvg,
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

        // Join/Joined (don't show on your own party)
        if (!isMyParty) {
            float joinX = x + w - CARD_PADDING - JOIN_BUTTON_WIDTH;
            float joinY = memberY -
                    MEMBER_ROW_HEIGHT +
                    (MEMBER_ROW_HEIGHT - BUTTON_HEIGHT) / 2f;
            boolean alreadyInParty = party().getJoinedPartyIndex() >= 0 && !isJoined;
            boolean joinHovered = !alreadyInParty &&
                    isHovered(
                            nvgMouseX,
                            nvgMouseY,
                            joinX,
                            joinY,
                            JOIN_BUTTON_WIDTH,
                            BUTTON_HEIGHT);
            Color joinBg = isJoined
                    ? JOINED_BUTTON_COLOR
                    : alreadyInParty
                            ? new Color(60, 60, 70, 180)
                            : (joinHovered ? JOIN_BUTTON_HOVER : JOIN_BUTTON_COLOR);
            NVGWrapper.drawRect(
                    nvg,
                    joinX,
                    joinY,
                    JOIN_BUTTON_WIDTH,
                    BUTTON_HEIGHT,
                    joinBg);

            nvgFontFace(nvg, fontName);
            nvgFontSize(nvg, MEMBER_FONT_SIZE);
            nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
            Color textCol = alreadyInParty
                    ? new Color(120, 120, 130, 200)
                    : TEXT_COLOR;
            var jtc = NVGContext.nvgColor(textCol);
            nvgFillColor(nvg, jtc);
            nvgText(
                    nvg,
                    joinX + JOIN_BUTTON_WIDTH / 2f,
                    joinY + BUTTON_HEIGHT / 2f,
                    isJoined ? "Leave" : "Join");
            jtc.free();
        }

        // Tag label
        float labelRightX = x + w - CARD_PADDING - (isMyParty ? 0 : JOIN_BUTTON_WIDTH + 8);
        nvgFontSize(nvg, TYPE_FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_RIGHT | NVG_ALIGN_MIDDLE);
        var ptc = NVGContext.nvgColor(PARTY_TYPE_TEXT);
        nvgFillColor(nvg, ptc);
        nvgText(nvg, labelRightX, lastMemberCenterY, party.displayLabel());
        ptc.free();
    }

    private void renderCollapsedCard(
            long nvg,
            String fontName,
            float x,
            float y,
            float w,
            PartyListing party) {
        float rowX = x + CARD_PADDING;
        float centerY = y + COLLAPSED_ROW_HEIGHT / 2f;
        List<String> raidTags = party.getRaidTags();

        drawRaidIconCircle(
                nvg,
                fontName,
                rowX,
                y + (COLLAPSED_ROW_HEIGHT - TYPE_ICON_SIZE) / 2f,
                TYPE_ICON_SIZE,
                raidTags);
        rowX += TYPE_ICON_SIZE + 6;

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, CARD_TITLE_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        var cc = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, cc);
        nvgText(nvg, rowX, centerY, party.members.size() + "/" + party.maxSize);
        cc.free();
        rowX += 42;

        PartyMember leader = party.getLeader();
        if (leader != null) {
            AssetManager.Asset starIcon = getClassIcon("star");
            if (starIcon != null) {
                float starY = centerY - STAR_ICON_SIZE / 2f;
                NVGWrapper.drawImage(
                        nvg,
                        starIcon,
                        rowX,
                        starY,
                        STAR_ICON_SIZE,
                        STAR_ICON_SIZE,
                        255);
            }
            rowX += STAR_ICON_SIZE + 4;

            nvgFontFace(nvg, fontName);
            nvgFontSize(nvg, MEMBER_FONT_SIZE);
            nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            var nc = NVGContext.nvgColor(MEMBER_TEXT_COLOR);
            nvgFillColor(nvg, nc);
            nvgText(nvg, rowX, centerY, leader.name);
            nc.free();
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

        for (int j = party.members.size() - 1; j >= 0; j--) {
            AssetManager.Asset icon = getClassIcon(
                    party.members.get(j).className);
            if (icon != null) {
                float iconX = rightX - CLASS_ICON_SIZE;
                float iconY = y + (COLLAPSED_ROW_HEIGHT - CLASS_ICON_SIZE) / 2f;
                NVGWrapper.drawImage(
                        nvg,
                        icon,
                        iconX,
                        iconY,
                        CLASS_ICON_SIZE,
                        CLASS_ICON_SIZE,
                        255);
                rightX -= CLASS_ICON_SIZE + 4;
            }
        }

        rightX -= 6;
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, TYPE_FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_RIGHT | NVG_ALIGN_MIDDLE);
        var tc = NVGContext.nvgColor(PARTY_TYPE_TEXT);
        nvgFillColor(nvg, tc);
        nvgText(nvg, rightX, centerY, party.displayLabel());
        tc.free();
    }

    private void renderMemberRow(
            long nvg,
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

        // Hover detection for leader management
        boolean isHoveredMember = false;
        if (amLeaderOfThisParty && !member.isLeader) {
            if (isHovered(nvgMouseX, nvgMouseY, x, y, w, MEMBER_ROW_HEIGHT)) {
                isHoveredMember = true;
                hoveredMemberPartyIndex = partyIndex;
                hoveredMemberIndex = memberIndex;
            }
        }

        if (member.isLeader) {
            AssetManager.Asset starIcon = getClassIcon("star");
            if (starIcon != null) {
                float starY = centerY - STAR_ICON_SIZE / 2f;
                NVGWrapper.drawImage(
                        nvg,
                        starIcon,
                        rowX,
                        starY,
                        STAR_ICON_SIZE,
                        STAR_ICON_SIZE,
                        255);
            }
        }
        rowX += STAR_ICON_SIZE + 4;

        // Member name - dimmed if hovered for management
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, MEMBER_FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        Color nameColor = isHoveredMember
                ? MEMBER_DIM_COLOR
                : MEMBER_TEXT_COLOR;
        var nc = NVGContext.nvgColor(nameColor);
        nvgFillColor(nvg, nc);
        nvgText(nvg, rowX, centerY, member.name);
        nc.free();

        float[] bounds = new float[4];
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, MEMBER_FONT_SIZE);
        float nameW = nvgTextBounds(nvg, 0, 0, member.name, bounds);

        // Draw promote/kick icons on hover
        if (isHoveredMember) {
            AssetManager.Asset starupIcon = getClassIcon("starup");
            AssetManager.Asset crossIcon = getClassIcon("cross");

            if (starupIcon != null) {
                float iconY = centerY - LEADER_ICON_SIZE / 2f;
                NVGWrapper.drawImage(
                        nvg,
                        starupIcon,
                        rowX,
                        iconY,
                        LEADER_ICON_SIZE,
                        LEADER_ICON_SIZE,
                        255);
            }
            if (crossIcon != null) {
                float iconY = centerY - LEADER_ICON_SIZE / 2f;
                float crossX = rowX + nameW - LEADER_ICON_SIZE;
                NVGWrapper.drawImage(
                        nvg,
                        crossIcon,
                        crossX,
                        iconY,
                        LEADER_ICON_SIZE,
                        LEADER_ICON_SIZE,
                        255);
            }
        }

        rowX += nameW + 8;

        AssetManager.Asset icon = getClassIcon(member.className);
        if (icon != null) {
            float iconY = y + (MEMBER_ROW_HEIGHT - CLASS_ICON_SIZE) / 2f;
            NVGWrapper.drawImage(
                    nvg,
                    icon,
                    rowX,
                    iconY,
                    CLASS_ICON_SIZE,
                    CLASS_ICON_SIZE,
                    255);
            rowX += CLASS_ICON_SIZE + 6;
        }

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, ROLE_FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        var rc = NVGContext.nvgColor(ROLE_TEXT_COLOR);
        nvgFillColor(nvg, rc);
        nvgText(nvg, rowX, centerY, "(" + member.role + ")");
        rc.free();
    }

    // ── Small triangle arrow (pointing up or down) ──

    private void drawTriangle(
            long nvg,
            float cx,
            float cy,
            float size,
            boolean up,
            Color color) {
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

    // ── Pizza-slice raid icon circle ──

    private void drawRaidIconCircle(
            long nvg,
            String fontName,
            float x,
            float y,
            float size,
            List<String> raidTags) {
        float cx = x + size / 2f;
        float cy = y + size / 2f;
        float radius = size / 2f - 1;

        if (raidTags.isEmpty()) {
            return;
        }

        // Check if this raid has no icon asset (text fallback, e.g. Prelude to
        // Annihilation)
        if (raidTags.size() == 1 &&
                PartyListing.displayNameToAssetKey(raidTags.get(0)) == null) {
            nvgFontFace(nvg, fontName);
            nvgFontSize(nvg, RAID_LABEL_SIZE);
            nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
            var tc = NVGContext.nvgColor(TEXT_COLOR);
            nvgFillColor(nvg, tc);
            nvgText(nvg, cx, cy, raidTags.get(0));
            tc.free();
            return;
        }

        int count = raidTags.size();
        float anglePerSlice = (float) ((2.0 * Math.PI) / count);
        // Start from top (-PI/2)
        float startAngle = (float) (-Math.PI / 2.0);

        for (int i = 0; i < count; i++) {
            String tag = raidTags.get(i);
            String assetKey = PartyListing.displayNameToAssetKey(tag);
            AssetManager.Asset raidIcon = assetKey != null ? getClassIcon(assetKey) : null;

            float sliceStart = startAngle + i * anglePerSlice;
            float sliceEnd = sliceStart + anglePerSlice;

            if (count == 1) {
                // Full circle - just fill with image
                if (raidIcon != null) {
                    nvgSave(nvg);
                    nvgBeginPath(nvg);
                    nvgCircle(nvg, cx, cy, radius);
                    try (NVGPaint paint = NVGPaint.calloc()) {
                        nvgImagePattern(
                                nvg,
                                x,
                                y,
                                size,
                                size,
                                0,
                                raidIcon.getImage(),
                                1.0f,
                                paint);
                        nvgFillPaint(nvg, paint);
                        nvgFill(nvg);
                    }
                    nvgClosePath(nvg);
                    nvgRestore(nvg);
                } else {
                    // Fallback to text
                    nvgFontFace(nvg, fontName);
                    nvgFontSize(nvg, RAID_LABEL_SIZE);
                    nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
                    var tc = NVGContext.nvgColor(TEXT_COLOR);
                    nvgFillColor(nvg, tc);
                    nvgText(nvg, cx, cy, tag);
                    tc.free();
                }
            } else {
                // Pie slice
                nvgSave(nvg);
                nvgBeginPath(nvg);
                nvgMoveTo(nvg, cx, cy);
                nvgArc(nvg, cx, cy, radius, sliceStart, sliceEnd, NVG_CW);
                nvgClosePath(nvg);

                if (raidIcon != null) {
                    try (NVGPaint paint = NVGPaint.calloc()) {
                        nvgImagePattern(
                                nvg,
                                x,
                                y,
                                size,
                                size,
                                0,
                                raidIcon.getImage(),
                                1.0f,
                                paint);
                        nvgFillPaint(nvg, paint);
                        nvgFill(nvg);
                    }
                } else {
                    // Fallback solid color for missing icon
                    var fc = NVGContext.nvgColor(TYPE_ICON_SELECTED);
                    nvgFillColor(nvg, fc);
                    nvgFill(nvg);
                    fc.free();
                }
                nvgRestore(nvg);
            }
        }
    }

    // ── Create/Manage Party Modal ──

    private void renderModal(
            long nvg,
            String fontName,
            float panelX,
            float panelWidth,
            float screenHeight) {
        // Darken background
        NVGWrapper.drawRect(
                nvg,
                panelX,
                0,
                panelWidth,
                screenHeight,
                MODAL_OVERLAY);

        // Modal centered in main panel area
        modalX = panelX + (panelWidth - MODAL_WIDTH) / 2f;
        modalY = (screenHeight - MODAL_HEIGHT) / 2f;

        NVGWrapper.drawRect(
                nvg,
                modalX,
                modalY,
                MODAL_WIDTH,
                MODAL_HEIGHT,
                MODAL_BG);
        NVGWrapper.drawRectOutline(
                nvg,
                modalX,
                modalY,
                MODAL_WIDTH,
                MODAL_HEIGHT,
                1,
                MODAL_BORDER);

        // Title
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, MODAL_TITLE_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var tc = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, tc);
        nvgText(nvg, modalX + MODAL_WIDTH / 2f, modalY + 18, "Create Party");
        tc.free();

        // Raid type icons row
        float totalCirclesW = RAID_TYPES.length * RAID_CIRCLE_SIZE +
                (RAID_TYPES.length - 1) * RAID_CIRCLE_SPACING;
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
                nvgBeginPath(nvg);
                nvgCircle(nvg, rcx, rcy, RAID_CIRCLE_SIZE / 2f - 2);
                var fill = NVGContext.nvgColor(TYPE_ICON_SELECTED);
                nvgFillColor(nvg, fill);
                nvgFill(nvg);
                nvgClosePath(nvg);
                fill.free();
            }

            // Draw raid icon image (or text fallback for raids without an asset)
            String assetKey = PartyListing.displayNameToAssetKey(rt);
            AssetManager.Asset raidIcon = assetKey != null ? getClassIcon(assetKey) : null;
            if (raidIcon != null) {
                float imgInset = 4;
                NVGWrapper.drawImage(
                        nvg,
                        raidIcon,
                        iconX + imgInset,
                        iconY + imgInset,
                        RAID_CIRCLE_SIZE - imgInset * 2,
                        RAID_CIRCLE_SIZE - imgInset * 2,
                        selected ? 255 : 160);
            } else {
                // Text fallback (e.g. ANNI)
                nvgFontFace(nvg, fontName);
                nvgFontSize(nvg, RAID_LABEL_SIZE);
                nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
                var lc = NVGContext.nvgColor(
                        selected ? TEXT_COLOR : PARTY_TYPE_TEXT);
                nvgFillColor(nvg, lc);
                nvgText(nvg, rcx, rcy, rt);
                lc.free();
            }
        }

        // "Reserved slots" label
        float rowY = circleY + RAID_CIRCLE_SIZE + 16;
        float leftColX = modalX + MODAL_WIDTH * 0.25f;
        float rightColX = modalX + MODAL_WIDTH * 0.75f;

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, MODAL_LABEL_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var l2 = NVGContext.nvgColor(PARTY_TYPE_TEXT);
        nvgFillColor(nvg, l2);
        nvgText(nvg, rightColX, rowY, "Reserved slots");
        l2.free();

        // Edit tags button (same color as Filter+)
        float etBtnX = leftColX - MODAL_DROPDOWN_W / 2f;
        float etBtnY = rowY + 12;
        boolean etHovered = isHovered(
                nvgMouseX,
                nvgMouseY,
                etBtnX,
                etBtnY,
                MODAL_DROPDOWN_W,
                MODAL_DROPDOWN_H);
        NVGWrapper.drawRect(
                nvg,
                etBtnX,
                etBtnY,
                MODAL_DROPDOWN_W,
                MODAL_DROPDOWN_H,
                etHovered ? NEW_PARTY_HOVER : NEW_PARTY_COLOR);

        nvgFontSize(nvg, MODAL_LABEL_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var ptc = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, ptc);
        nvgText(nvg, leftColX, etBtnY + MODAL_DROPDOWN_H / 2f, "Edit tags");
        ptc.free();

        // Reserved slots with up/down arrows
        float arrowW = 16;
        float rsFieldW = MODAL_DROPDOWN_W - arrowW;
        float rsBoxX = rightColX - MODAL_DROPDOWN_W / 2f;
        float rsBoxY = etBtnY;
        float arrowX = rsBoxX + rsFieldW;
        float halfArrowH = MODAL_DROPDOWN_H / 2f;

        Color rsFieldBg = reservedSlotsFocused
                ? SEARCH_ACTIVE_BG
                : MODAL_DROPDOWN_BG;
        NVGWrapper.drawRect(
                nvg,
                rsBoxX,
                rsBoxY,
                rsFieldW,
                MODAL_DROPDOWN_H,
                rsFieldBg);
        NVGWrapper.drawRectOutline(
                nvg,
                rsBoxX,
                rsBoxY,
                rsFieldW,
                MODAL_DROPDOWN_H,
                1,
                reservedSlotsFocused ? SEARCH_BORDER : MODAL_DROPDOWN_BORDER);

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, MODAL_LABEL_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var rsc = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, rsc);
        nvgText(
                nvg,
                rsBoxX + rsFieldW / 2f,
                rsBoxY + MODAL_DROPDOWN_H / 2f,
                reservedSlotsFocused
                        ? reservedSlotsInput
                        : String.valueOf(modalReservedSlots));
        rsc.free();

        // Up arrow button
        boolean upHovered = isHovered(
                nvgMouseX,
                nvgMouseY,
                arrowX,
                rsBoxY,
                arrowW,
                halfArrowH);
        NVGWrapper.drawRect(
                nvg,
                arrowX,
                rsBoxY,
                arrowW,
                halfArrowH,
                upHovered ? DROPDOWN_HOVER : MODAL_DROPDOWN_BG);
        NVGWrapper.drawRectOutline(
                nvg,
                arrowX,
                rsBoxY,
                arrowW,
                halfArrowH,
                1,
                MODAL_DROPDOWN_BORDER);
        drawTriangle(
                nvg,
                arrowX + arrowW / 2f,
                rsBoxY + halfArrowH / 2f,
                4,
                true,
                TEXT_COLOR);

        // Down arrow button
        boolean downHovered = isHovered(
                nvgMouseX,
                nvgMouseY,
                arrowX,
                rsBoxY + halfArrowH,
                arrowW,
                halfArrowH);
        NVGWrapper.drawRect(
                nvg,
                arrowX,
                rsBoxY + halfArrowH,
                arrowW,
                halfArrowH,
                downHovered ? DROPDOWN_HOVER : MODAL_DROPDOWN_BG);
        NVGWrapper.drawRectOutline(
                nvg,
                arrowX,
                rsBoxY + halfArrowH,
                arrowW,
                halfArrowH,
                1,
                MODAL_DROPDOWN_BORDER);
        drawTriangle(
                nvg,
                arrowX + arrowW / 2f,
                rsBoxY + halfArrowH + halfArrowH / 2f,
                4,
                false,
                TEXT_COLOR);

        // Create/Update button
        float createBtnX = modalX + (MODAL_WIDTH - MODAL_BUTTON_W) / 2f;
        float createBtnY = modalY + MODAL_HEIGHT - MODAL_BUTTON_H - 14;
        String createLabel = party().hasListedParty() ? "Update..." : "Create!";
        boolean createHovered = isHovered(
                nvgMouseX,
                nvgMouseY,
                createBtnX,
                createBtnY,
                MODAL_BUTTON_W,
                MODAL_BUTTON_H);
        NVGWrapper.drawRect(
                nvg,
                createBtnX,
                createBtnY,
                MODAL_BUTTON_W,
                MODAL_BUTTON_H,
                createHovered ? NEW_PARTY_HOVER : NEW_PARTY_COLOR);

        nvgFontSize(nvg, MEMBER_FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var cbc = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, cbc);
        nvgText(
                nvg,
                createBtnX + MODAL_BUTTON_W / 2f,
                createBtnY + MODAL_BUTTON_H / 2f,
                createLabel);
        cbc.free();

        // Edit tags sub-overlay (on top of modal)
        if (editTagsScreenOpen) {
            renderEditTagsOverlay(
                    nvg,
                    fontName,
                    panelX,
                    panelX +
                            (SeqClient.mc.getWindow().getWidth() / 2f - SIDEBAR_WIDTH),
                    screenHeight);
        }
    }

    // ── Edit Tags Sub-Overlay (in modal) — same layout as Filter+ screen ──

    private void renderEditTagsOverlay(
            long nvg,
            String fontName,
            float panelX,
            float panelWidth,
            float screenHeight) {
        float overlayW = 260;
        float overlayH = 180;
        float overlayX = modalX + (MODAL_WIDTH - overlayW) / 2f;
        float overlayY = modalY + (MODAL_HEIGHT - overlayH) / 2f;

        NVGWrapper.drawRect(
                nvg,
                modalX,
                modalY,
                MODAL_WIDTH,
                MODAL_HEIGHT,
                MODAL_OVERLAY);
        NVGWrapper.drawRect(
                nvg,
                overlayX,
                overlayY,
                overlayW,
                overlayH,
                MODAL_BG);
        NVGWrapper.drawRectOutline(
                nvg,
                overlayX,
                overlayY,
                overlayW,
                overlayH,
                1,
                MODAL_BORDER);

        // Title
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, MODAL_TITLE_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var tc = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, tc);
        nvgText(nvg, overlayX + overlayW / 2f, overlayY + 18, "Tag selection");
        tc.free();

        float boxPadding = 12;
        float boxW = overlayW - boxPadding * 2;
        float boxH = 46;
        float boxX = overlayX + boxPadding;

        // Active tags box
        float activeBoxY = overlayY + 34;
        NVGWrapper.drawRect(nvg, boxX, activeBoxY, boxW, boxH, FILTER_BOX_BG);
        nvgFontSize(nvg, 9);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        var al = NVGContext.nvgColor(PARTY_TYPE_TEXT);
        nvgFillColor(nvg, al);
        nvgText(nvg, boxX + 4, activeBoxY + 2, "Active tags");
        al.free();

        renderTagChips(
                nvg,
                fontName,
                boxX + 4,
                activeBoxY + 14,
                boxW - 8,
                modalActiveTags,
                true,
                modalTagAnimStartTimes);

        // Inactive tags box
        float inactiveBoxY = activeBoxY + boxH + 8;
        NVGWrapper.drawRect(nvg, boxX, inactiveBoxY, boxW, boxH, FILTER_BOX_BG);
        nvgFontSize(nvg, 9);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        var il = NVGContext.nvgColor(PARTY_TYPE_TEXT);
        nvgFillColor(nvg, il);
        nvgText(nvg, boxX + 4, inactiveBoxY + 2, "Inactive tags");
        il.free();

        renderTagChips(
                nvg,
                fontName,
                boxX + 4,
                inactiveBoxY + 14,
                boxW - 8,
                modalInactiveTags,
                false,
                modalTagAnimStartTimes);

        // Back button
        float backW = 70;
        float backH = 20;
        float backX = overlayX + (overlayW - backW) / 2f;
        float backY = overlayY + overlayH - backH - 8;
        boolean backHovered = isHovered(
                nvgMouseX,
                nvgMouseY,
                backX,
                backY,
                backW,
                backH);
        NVGWrapper.drawRect(
                nvg,
                backX,
                backY,
                backW,
                backH,
                backHovered ? NEW_PARTY_HOVER : NEW_PARTY_COLOR);

        nvgFontSize(nvg, MEMBER_FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var bc = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, bc);
        nvgText(nvg, backX + backW / 2f, backY + backH / 2f, "< Back");
        bc.free();
    }

    // ── Filter+ Screen ──

    private void renderFilterScreen(
            long nvg,
            String fontName,
            float panelX,
            float panelWidth,
            float screenHeight) {
        NVGWrapper.drawRect(
                nvg,
                panelX,
                0,
                panelWidth,
                screenHeight,
                MODAL_OVERLAY);

        float filterW = 260;
        float filterH = 180;
        float filterX = panelX + (panelWidth - filterW) / 2f;
        float filterY = (screenHeight - filterH) / 2f;

        NVGWrapper.drawRect(nvg, filterX, filterY, filterW, filterH, MODAL_BG);
        NVGWrapper.drawRectOutline(
                nvg,
                filterX,
                filterY,
                filterW,
                filterH,
                1,
                MODAL_BORDER);

        // Title
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, MODAL_TITLE_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var tc = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, tc);
        nvgText(
                nvg,
                filterX + filterW / 2f,
                filterY + 18,
                "Tag filter selection");
        tc.free();

        float boxPadding = 12;
        float boxW = filterW - boxPadding * 2;
        float boxH = 46;
        float boxX = filterX + boxPadding;

        // Active filters box
        float activeBoxY = filterY + 34;
        NVGWrapper.drawRect(nvg, boxX, activeBoxY, boxW, boxH, FILTER_BOX_BG);
        nvgFontSize(nvg, 9);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        var al = NVGContext.nvgColor(PARTY_TYPE_TEXT);
        nvgFillColor(nvg, al);
        nvgText(nvg, boxX + 4, activeBoxY + 2, "Active filters");
        al.free();

        renderTagChips(
                nvg,
                fontName,
                boxX + 4,
                activeBoxY + 14,
                boxW - 8,
                activeFilterTags,
                true,
                filterTagAnimStartTimes);

        // Inactive filters box
        float inactiveBoxY = activeBoxY + boxH + 8;
        NVGWrapper.drawRect(nvg, boxX, inactiveBoxY, boxW, boxH, FILTER_BOX_BG);
        nvgFontSize(nvg, 9);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        var il = NVGContext.nvgColor(PARTY_TYPE_TEXT);
        nvgFillColor(nvg, il);
        nvgText(nvg, boxX + 4, inactiveBoxY + 2, "Inactive filters");
        il.free();

        renderTagChips(
                nvg,
                fontName,
                boxX + 4,
                inactiveBoxY + 14,
                boxW - 8,
                inactiveFilterTags,
                false,
                filterTagAnimStartTimes);

        // Back button
        float backW = 70;
        float backH = 20;
        float backX = filterX + (filterW - backW) / 2f;
        float backY = filterY + filterH - backH - 8;
        boolean backHovered = isHovered(
                nvgMouseX,
                nvgMouseY,
                backX,
                backY,
                backW,
                backH);
        NVGWrapper.drawRect(
                nvg,
                backX,
                backY,
                backW,
                backH,
                backHovered ? NEW_PARTY_HOVER : NEW_PARTY_COLOR);

        nvgFontSize(nvg, MEMBER_FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var bc = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, bc);
        nvgText(nvg, backX + backW / 2f, backY + backH / 2f, "< Back");
        bc.free();
    }

    // ── Tag chip rendering with pendulum animation ──

    private void renderTagChips(
            long nvg,
            String fontName,
            float startX,
            float startY,
            float maxWidth,
            Set<String> tags,
            boolean isActive,
            Map<String, Long> animStartTimes) {
        float chipH = 16;
        float chipPadding = 6;
        float chipSpacing = 4;
        float curX = startX;
        float curY = startY;

        // Sort: raid tags first, then non-raid tags
        List<String> sorted = new ArrayList<>(tags.size());
        for (String tag : tags) {
            if (RAID_TYPE_SET.contains(tag))
                sorted.add(tag);
        }
        for (String tag : tags) {
            if (!RAID_TYPE_SET.contains(tag))
                sorted.add(tag);
        }

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, TAG_CHIP_FONT_SIZE);

        for (String tag : sorted) {
            String label = tag + (isActive ? " -" : " +");
            float[] bounds = new float[4];
            nvgTextBounds(nvg, 0, 0, label, bounds);
            float chipW = (bounds[2] - bounds[0]) + chipPadding * 2;

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
                    angle = (float) (amplitude *
                            Math.sin(freq * elapsed) *
                            Math.exp(-damping * elapsed));
                } else {
                    animStartTimes.remove(tag);
                }
            }

            float chipCenterX = curX + chipW / 2f;
            float chipCenterY = curY + chipH / 2f;

            nvgSave(nvg);
            if (angle != 0) {
                nvgTranslate(nvg, chipCenterX, chipCenterY);
                nvgRotate(nvg, (float) Math.toRadians(angle));
                nvgTranslate(nvg, -chipCenterX, -chipCenterY);
            }

            boolean chipHovered = isHovered(
                    nvgMouseX,
                    nvgMouseY,
                    curX,
                    curY,
                    chipW,
                    chipH);
            NVGWrapper.drawRoundedRect(
                    nvg,
                    curX,
                    curY,
                    chipW,
                    chipH,
                    4,
                    chipHovered ? TAG_CHIP_HOVER : TAG_CHIP_BG);

            nvgFontFace(nvg, fontName);
            nvgFontSize(nvg, TAG_CHIP_FONT_SIZE);
            nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
            Color chipTextColor = RAID_TYPE_SET.contains(tag)
                    ? TITLE_COLOR
                    : TEXT_COLOR;
            var cc = NVGContext.nvgColor(chipTextColor);
            nvgFillColor(nvg, cc);
            nvgText(nvg, chipCenterX, chipCenterY, label);
            cc.free();

            nvgRestore(nvg);

            curX += chipW + chipSpacing;
        }
    }

    private void commitReservedSlotsInput() {
        int maxSlots = modalSelectedRaids.contains("ANNI") ? 9 : 3;
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
        if (className == null || SeqClient.assetManager == null)
            return null;
        return SeqClient.assetManager.getAsset(className);
    }

    private boolean matchesFilters(PartyListing party) {
        // Search filter
        if (!searchQuery.isEmpty()) {
            boolean matches = false;
            String q = searchQuery.toLowerCase();
            for (PartyMember m : party.members) {
                if (m.name.toLowerCase().contains(q)) {
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
            if (!matches && !tagMatch)
                return false;
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
            if (!hasActiveTag)
                return false;
        }

        return true;
    }

    private boolean isHovered(
            float mx,
            float my,
            float bx,
            float by,
            float bw,
            float bh) {
        return mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
    }

    // ══════════════════════════════ INPUT ══════════════════════════════

    @Override
    public boolean mouseClicked(
            @NotNull MouseButtonEvent click,
            boolean outsideScreen) {
        if (click.button() != 0)
            return super.mouseClicked(
                    click,
                    outsideScreen);

        double guiScale = SeqClient.mc.getWindow().getGuiScale();
        float mx = (float) ((click.x() * guiScale) / 2.0);
        float my = (float) ((click.y() * guiScale) / 2.0);

        float screenWidth = SeqClient.mc.getWindow().getWidth() / 2f;
        float screenHeight = SeqClient.mc.getWindow().getHeight() / 2f;

        // ── Filter screen (highest priority) ──
        if (filterScreenOpen) {
            return handleFilterScreenClick(mx, my, screenWidth, screenHeight);
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
                if (isHovered(
                        mx,
                        my,
                        dropdownRenderX,
                        itemY,
                        dropdownRenderW,
                        itemH)) {
                    selectedRole = ROLES[i].equals(selectedRole)
                            ? null
                            : ROLES[i];
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
        float btnStartY = 50;

        if (isHovered(mx, my, btnX, btnStartY, btnW, SIDEBAR_BUTTON_HEIGHT))
            return true;
        if (isHovered(
                mx,
                my,
                btnX,
                btnStartY + (SIDEBAR_BUTTON_HEIGHT + SIDEBAR_BUTTON_SPACING),
                btnW,
                SIDEBAR_BUTTON_HEIGHT)) {
            SeqClient.mc.setScreen(new SettingsScreen(this));
            return true;
        }
        if (isHovered(
                mx,
                my,
                btnX,
                btnStartY +
                        (SIDEBAR_BUTTON_HEIGHT + SIDEBAR_BUTTON_SPACING) * 2,
                btnW,
                SIDEBAR_BUTTON_HEIGHT)) {
            try {
                java.awt.Desktop.getDesktop().browse(
                        java.net.URI.create(GITHUB_URL));
            } catch (Exception ignored) {
            }
            return true;
        }

        // ── Header ──
        float panelX = SIDEBAR_WIDTH;
        float panelWidth = screenWidth - SIDEBAR_WIDTH;

        float searchX = panelX + SEARCH_BAR_MARGIN;
        float searchY = (HEADER_HEIGHT - SEARCH_BAR_HEIGHT) / 2f;
        if (isHovered(
                mx,
                my,
                searchX,
                searchY,
                SEARCH_BAR_WIDTH,
                SEARCH_BAR_HEIGHT)) {
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
            if (isHovered(
                    mx,
                    my,
                    headerBtnX,
                    headerBtnY,
                    manageW,
                    SEARCH_BAR_HEIGHT)) {
                openModal(true);
                return true;
            }
            headerBtnX += manageW + 6;

            float delistW = 80;
            if (isHovered(
                    mx,
                    my,
                    headerBtnX,
                    headerBtnY,
                    delistW,
                    SEARCH_BAR_HEIGHT)) {
                party().delistParty();
                return true;
            }
            headerBtnX += delistW + 6;
        } else {
            float newW = 80;
            if (isHovered(
                    mx,
                    my,
                    headerBtnX,
                    headerBtnY,
                    newW,
                    SEARCH_BAR_HEIGHT)) {
                if (party().getJoinedPartyIndex() < 0) {
                    openModal(false);
                }
                return true;
            }
            headerBtnX += 86;
        }

        if (isHovered(
                mx,
                my,
                dropdownRenderX,
                dropdownRenderY,
                dropdownRenderW,
                SEARCH_BAR_HEIGHT)) {
            roleDropdownOpen = !roleDropdownOpen;
            return true;
        }

        // ── Filter button ──
        float filterX = panelX + panelWidth - FILTER_BUTTON_W - FILTER_BUTTON_MARGIN;
        float filterY = screenHeight - FILTER_BUTTON_H - FILTER_BUTTON_MARGIN;
        if (isHovered(
                mx,
                my,
                filterX,
                filterY,
                FILTER_BUTTON_W,
                FILTER_BUTTON_H)) {
            filterScreenOpen = true;
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

        // ── Party cards ──
        float cursorY = contentY - scrollOffset + PADDING;
        float contentWidth = panelWidth;

        for (int i = 0; i < party().getParties().size(); i++) {
            PartyListing party = party().getParties().get(i);
            if (!matchesFilters(party))
                continue;

            float cardX = panelX + PADDING;
            float cardW = contentWidth - PADDING * 2 - 6;
            float cardH;

            if (party.expanded) {
                cardH = CARD_HEADER_HEIGHT +
                        party.members.size() * MEMBER_ROW_HEIGHT +
                        CARD_PADDING;

                // Leader management: promote/kick clicks
                boolean isMyParty = i == party().getMyPartyIndex();
                boolean amLeaderOfThisParty = isMyParty && party().isPartyLeader();
                if (amLeaderOfThisParty) {
                    float memberY = cursorY + CARD_HEADER_HEIGHT;
                    float memberRowX = cardX + CARD_PADDING + 10;
                    float memberRowW = cardW - CARD_PADDING * 2 - 10;
                    for (int mi = 0; mi < party.members.size(); mi++) {
                        PartyMember member = party.members.get(mi);
                        if (!member.isLeader &&
                                isHovered(
                                        mx,
                                        my,
                                        memberRowX,
                                        memberY,
                                        memberRowW,
                                        MEMBER_ROW_HEIGHT)) {
                            // Compute name width to determine icon click zones
                            // Left half of name area = promote, Right half = kick
                            float nameStartX = memberRowX + STAR_ICON_SIZE + 4;
                            float nameAreaW = memberRowW - STAR_ICON_SIZE - 4;
                            float nameMidX = nameStartX + nameAreaW / 2f;

                            if (mx < nameMidX) {
                                party().promoteMember(i, mi);
                            } else {
                                party().kickMember(i, mi);
                            }
                            return true;
                        }
                        memberY += MEMBER_ROW_HEIGHT;
                    }
                }

                float joinBtnX = cardX + cardW - CARD_PADDING - JOIN_BUTTON_WIDTH;
                float joinBtnY = cursorY +
                        CARD_HEADER_HEIGHT +
                        (party.members.size() - 1) * MEMBER_ROW_HEIGHT +
                        (MEMBER_ROW_HEIGHT - BUTTON_HEIGHT) / 2f;
                if (!isMyParty &&
                        isHovered(
                                mx,
                                my,
                                joinBtnX,
                                joinBtnY,
                                JOIN_BUTTON_WIDTH,
                                BUTTON_HEIGHT)) {
                    if (party().getJoinedPartyIndex() == i) {
                        party().leaveParty();
                    } else if (party().getJoinedPartyIndex() < 0) {
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
        editTagsScreenOpen = false;
        reservedSlotsFocused = false;
        party().setHasListedParty(managing);
        if (!managing) {
            modalSelectedRaids.clear();
            modalActiveTags.clear();
            modalActiveTags.add("Chill");
            modalInactiveTags.clear();
            modalInactiveTags.add("Grind");
            modalReservedSlots = 0;
        }
        reservedSlotsInput = String.valueOf(modalReservedSlots);
    }

    private boolean handleModalClick(
            float mx,
            float my,
            float screenWidth,
            float screenHeight) {
        float panelX = SIDEBAR_WIDTH;
        float panelWidth = screenWidth - SIDEBAR_WIDTH;
        float mX = panelX + (panelWidth - MODAL_WIDTH) / 2f;
        float mY = (screenHeight - MODAL_HEIGHT) / 2f;

        // Edit tags sub-overlay (highest priority within modal)
        if (editTagsScreenOpen) {
            return handleEditTagsClick(mx, my, mX, mY);
        }

        // Click outside modal closes it
        if (!isHovered(mx, my, mX, mY, MODAL_WIDTH, MODAL_HEIGHT)) {
            modalOpen = false;
            editTagsScreenOpen = false;
            reservedSlotsFocused = false;
            return true;
        }

        // Raid type circles
        float totalCirclesW = RAID_TYPES.length * RAID_CIRCLE_SIZE +
                (RAID_TYPES.length - 1) * RAID_CIRCLE_SPACING;
        float circleStartX = mX + (MODAL_WIDTH - totalCirclesW) / 2f;
        float circleY = mY + 38;

        for (int i = 0; i < RAID_TYPES.length; i++) {
            String rt = RAID_TYPES[i];
            float cx = circleStartX + i * (RAID_CIRCLE_SIZE + RAID_CIRCLE_SPACING);
            if (isHovered(
                    mx,
                    my,
                    cx,
                    circleY,
                    RAID_CIRCLE_SIZE,
                    RAID_CIRCLE_SIZE)) {
                if (modalSelectedRaids.contains(rt)) {
                    modalSelectedRaids.remove(rt);
                } else {
                    modalSelectedRaids.add(rt);
                }
                return true;
            }
        }

        // Edit tags button click
        float leftColX = mX + MODAL_WIDTH * 0.25f;
        float rowY = circleY + RAID_CIRCLE_SIZE + 16;
        float etBtnX = leftColX - MODAL_DROPDOWN_W / 2f;
        float etBtnY = rowY + 12;

        if (isHovered(
                mx,
                my,
                etBtnX,
                etBtnY,
                MODAL_DROPDOWN_W,
                MODAL_DROPDOWN_H)) {
            editTagsScreenOpen = true;
            return true;
        }

        // Reserved slots - up/down arrows and number field
        float rightColX = mX + MODAL_WIDTH * 0.75f;
        float arrowW = 16;
        float rsFieldW = MODAL_DROPDOWN_W - arrowW;
        float rsBoxX = rightColX - MODAL_DROPDOWN_W / 2f;
        float rsBoxY = etBtnY;
        float arrowX = rsBoxX + rsFieldW;
        float halfArrowH = MODAL_DROPDOWN_H / 2f;
        int maxSlots = modalSelectedRaids.contains("Prelude to Annihilation")
                ? 9
                : 3;

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

        // Create/Update button
        float createBtnX = mX + (MODAL_WIDTH - MODAL_BUTTON_W) / 2f;
        float createBtnY = mY + MODAL_HEIGHT - MODAL_BUTTON_H - 14;
        if (isHovered(
                mx,
                my,
                createBtnX,
                createBtnY,
                MODAL_BUTTON_W,
                MODAL_BUTTON_H)) {
            if (!modalSelectedRaids.isEmpty()) {
                commitReservedSlotsInput();
                // Combine raids + active tags
                List<String> tags = new ArrayList<>(modalSelectedRaids);
                tags.addAll(modalActiveTags);

                if (party().getMyPartyIndex() >= 0) {
                    party().updateParty(tags);
                } else {
                    party().createParty(tags, selectedRole);
                }

                modalOpen = false;
                editTagsScreenOpen = false;
                reservedSlotsFocused = false;
                scrollOffset = 0;
            }
            return true;
        }

        return true;
    }

    private boolean handleEditTagsClick(
            float mx,
            float my,
            float modalX,
            float modalY) {
        float overlayW = 260;
        float overlayH = 180;
        float overlayX = modalX + (MODAL_WIDTH - overlayW) / 2f;
        float overlayY = modalY + (MODAL_HEIGHT - overlayH) / 2f;

        // Back button
        float backW = 70;
        float backH = 20;
        float backX = overlayX + (overlayW - backW) / 2f;
        float backY = overlayY + overlayH - backH - 8;
        if (isHovered(mx, my, backX, backY, backW, backH)) {
            editTagsScreenOpen = false;
            return true;
        }

        // Click outside overlay closes it
        if (!isHovered(mx, my, overlayX, overlayY, overlayW, overlayH)) {
            editTagsScreenOpen = false;
            return true;
        }

        // Check tag chip clicks
        float boxPadding = 12;
        float boxW = overlayW - boxPadding * 2;
        float boxX = overlayX + boxPadding;
        float boxH = 46;

        // Active tags area
        float activeBoxY = overlayY + 34;
        float activeChipY = activeBoxY + 14;
        String clickedActiveTag = findClickedTagChip(
                mx,
                my,
                boxX + 4,
                activeChipY,
                boxW - 8,
                modalActiveTags,
                true);
        if (clickedActiveTag != null) {
            modalActiveTags.remove(clickedActiveTag);
            modalInactiveTags.add(clickedActiveTag);
            modalTagAnimStartTimes.put(
                    clickedActiveTag,
                    System.currentTimeMillis());
            return true;
        }

        // Inactive tags area
        float inactiveBoxY = activeBoxY + boxH + 8;
        float inactiveChipY = inactiveBoxY + 14;
        String clickedInactiveTag = findClickedTagChip(
                mx,
                my,
                boxX + 4,
                inactiveChipY,
                boxW - 8,
                modalInactiveTags,
                false);
        if (clickedInactiveTag != null) {
            modalInactiveTags.remove(clickedInactiveTag);
            modalActiveTags.add(clickedInactiveTag);
            // Enforce single party tag: activating one deactivates the others
            if (Arrays.asList(PARTY_TAGS).contains(clickedInactiveTag)) {
                for (String pt : PARTY_TAGS) {
                    if (!pt.equals(clickedInactiveTag) &&
                            modalActiveTags.contains(pt)) {
                        modalActiveTags.remove(pt);
                        modalInactiveTags.add(pt);
                        modalTagAnimStartTimes.put(
                                pt,
                                System.currentTimeMillis());
                    }
                }
            }
            modalTagAnimStartTimes.put(
                    clickedInactiveTag,
                    System.currentTimeMillis());
            return true;
        }

        return true;
    }

    private boolean handleFilterScreenClick(
            float mx,
            float my,
            float screenWidth,
            float screenHeight) {
        float panelX = SIDEBAR_WIDTH;
        float panelWidth = screenWidth - SIDEBAR_WIDTH;
        float filterW = 260;
        float filterH = 180;
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

        float boxPadding = 12;
        float boxW = filterW - boxPadding * 2;
        float boxX = filterX + boxPadding;

        // Active tags area
        float activeBoxY = filterY + 34;
        float activeChipY = activeBoxY + 14;
        String clickedActive = findClickedTagChip(
                mx,
                my,
                boxX + 4,
                activeChipY,
                boxW - 8,
                activeFilterTags,
                true);
        if (clickedActive != null) {
            activeFilterTags.remove(clickedActive);
            inactiveFilterTags.add(clickedActive);
            filterTagAnimStartTimes.put(
                    clickedActive,
                    System.currentTimeMillis());
            return true;
        }

        // Inactive tags area
        float inactiveBoxY = activeBoxY + 46 + 8;
        float inactiveChipY = inactiveBoxY + 14;
        String clickedInactive = findClickedTagChip(
                mx,
                my,
                boxX + 4,
                inactiveChipY,
                boxW - 8,
                inactiveFilterTags,
                false);
        if (clickedInactive != null) {
            inactiveFilterTags.remove(clickedInactive);
            activeFilterTags.add(clickedInactive);
            filterTagAnimStartTimes.put(
                    clickedInactive,
                    System.currentTimeMillis());
            return true;
        }

        return true;
    }

    /**
     * Finds which tag chip was clicked given the layout parameters.
     * Returns the tag name or null if no chip was hit.
     */
    private String findClickedTagChip(
            float mx,
            float my,
            float startX,
            float startY,
            float maxWidth,
            Set<String> tags,
            boolean isActive) {
        float chipH = 16;
        float chipPadding = 6;
        float chipSpacing = 4;
        float curX = startX;
        float curY = startY;

        // Sort same as renderTagChips: raid tags first
        List<String> sorted = new ArrayList<>(tags.size());
        for (String tag : tags) {
            if (RAID_TYPE_SET.contains(tag))
                sorted.add(tag);
        }
        for (String tag : tags) {
            if (!RAID_TYPE_SET.contains(tag))
                sorted.add(tag);
        }

        for (String tag : sorted) {
            String label = tag + (isActive ? " -" : " +");
            // Approximate chip width: ~6px per char at TAG_CHIP_FONT_SIZE + padding
            float approxCharW = 5.5f;
            float chipW = label.length() * approxCharW + chipPadding * 2;

            if (curX + chipW > startX + maxWidth && curX > startX) {
                curX = startX;
                curY += chipH + 3;
            }

            if (isHovered(mx, my, curX, curY, chipW, chipH)) {
                return tag;
            }

            curX += chipW + chipSpacing;
        }
        return null;
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent click) {
        scrollbarDragging = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(
            MouseButtonEvent click,
            double deltaX,
            double deltaY) {
        if (scrollbarDragging && maxScroll > 0) {
            double guiScale = SeqClient.mc.getWindow().getGuiScale();
            float my = (float) ((click.y() * guiScale) / 2.0);

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
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY) {
        if (modalOpen || filterScreenOpen)
            return true;
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
        if (modalOpen) {
            int keyCode = keyEvent.key();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (editTagsScreenOpen) {
                    editTagsScreenOpen = false;
                } else if (reservedSlotsFocused) {
                    commitReservedSlotsInput();
                    reservedSlotsFocused = false;
                } else {
                    modalOpen = false;
                    editTagsScreenOpen = false;
                }
                return true;
            }
            if (reservedSlotsFocused) {
                if (keyCode == GLFW.GLFW_KEY_ENTER ||
                        keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    commitReservedSlotsInput();
                    reservedSlotsFocused = false;
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                    if (!reservedSlotsInput.isEmpty()) {
                        reservedSlotsInput = reservedSlotsInput.substring(
                                0,
                                reservedSlotsInput.length() - 1);
                    }
                    return true;
                }
                if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
                    if (reservedSlotsInput.length() < 2) {
                        reservedSlotsInput += (char) ('0' +
                                (keyCode - GLFW.GLFW_KEY_0));
                    }
                    return true;
                }
                if (keyCode >= GLFW.GLFW_KEY_KP_0 &&
                        keyCode <= GLFW.GLFW_KEY_KP_9) {
                    if (reservedSlotsInput.length() < 2) {
                        reservedSlotsInput += (char) ('0' +
                                (keyCode - GLFW.GLFW_KEY_KP_0));
                    }
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_UP) {
                    int maxSlots = modalSelectedRaids.contains("ANNI") ? 9 : 3;
                    modalReservedSlots = Math.min(
                            maxSlots,
                            modalReservedSlots + 1);
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
                    searchQuery = searchQuery.substring(
                            0,
                            searchQuery.length() - 1);
                    scrollOffset = 0;
                }
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
            if (keyCode == GLFW.GLFW_KEY_SPACE) {
                searchQuery += ' ';
                scrollOffset = 0;
                return true;
            }
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
