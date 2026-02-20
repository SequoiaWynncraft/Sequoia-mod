package org.sequoia.seq.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.managers.AssetManager;

import org.sequoia.seq.utils.rendering.nvg.NVGContext;
import org.sequoia.seq.utils.rendering.nvg.NVGWrapper;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.lwjgl.nanovg.NanoVG.*;

public class PartyFinderScreen extends Screen {

    // ── Raid types ──
    private static final String[] RAID_TYPES = {"NOTG", "NOL", "TCC", "TNA", "ANNI"};

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

    private static final Color TYPE_ICON_RING = new Color(200, 50, 50, 255);
    private static final Color TYPE_ICON_SELECTED = new Color(200, 50, 50, 80);

    private static final Color SCROLLBAR_TRACK = new Color(30, 30, 42, 255);
    private static final Color SCROLLBAR_THUMB = new Color(160, 130, 220, 150);

    private static final Color MODAL_BG = new Color(20, 20, 30, 255);
    private static final Color MODAL_BORDER = new Color(80, 80, 100, 255);
    private static final Color MODAL_OVERLAY = new Color(0, 0, 0, 160);
    private static final Color MODAL_DROPDOWN_BG = new Color(35, 35, 48, 255);
    private static final Color MODAL_DROPDOWN_BORDER = new Color(80, 80, 100, 200);

    private static final String GITHUB_URL = "https://github.com/SequoiaWynncraft/sequoia-mod";
    private static final String[] ROLES = {"DPS", "Tank", "Support", "Other"};
    private static final String[] PARTY_TYPES = {"Casual", "Grind"};

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

    private int joinedPartyIndex = -1;
    private int myPartyIndex = -1;
    private boolean isPartyLeader = false;
    private boolean hasListedParty = false;
    private final List<PartyListing> parties = new ArrayList<>();

    private float dropdownRenderX, dropdownRenderY, dropdownRenderW;

    // ── Modal state ──
    private boolean modalOpen = false;
    private final Set<String> modalSelectedRaids = new HashSet<>();
    private String modalPartyType = "Casual";
    private int modalReservedSlots = 0;
    private boolean modalPartyTypeDropdownOpen = false;
    private boolean reservedSlotsFocused = false;
    private String reservedSlotsInput = "0";

    // Cached modal position
    private float modalX, modalY;

    public PartyFinderScreen(Screen parent) {
        super(Component.literal("Party Finder"));
        this.parent = parent;
        buildDemoData();
    }

    private void buildDemoData() {
        PartyListing p1 = new PartyListing(List.of("NOTG"), "Casual");
        p1.members.add(new PartyMember("GAZTheMiner", "archer", "DPS", true));
        p1.members.add(new PartyMember("Vicvir", "warrior", "Tank", false));
        p1.expanded = true;
        parties.add(p1);

        PartyListing p2 = new PartyListing(List.of("TCC", "NOL"), "Grind");
        p2.members.add(new PartyMember("PlayerOne", "mage", "Support", true));
        p2.members.add(new PartyMember("PlayerTwo", "warrior", "DPS", false));
        parties.add(p2);

        PartyListing p3 = new PartyListing(List.of("NOL", "NOTG"), "Casual");
        p3.members.add(new PartyMember("Leader123", "assassin", "DPS", true));
        p3.members.add(new PartyMember("MemberA", "warrior", "Tank", false));
        p3.members.add(new PartyMember("MemberB", "mage", "Support", false));
        p3.members.add(new PartyMember("MemberC", "archer", "DPS", false));
        parties.add(p3);

        PartyListing p4 = new PartyListing(List.of("ANNI"), "Casual");
        p4.members.add(new PartyMember("RaidLeader", "shaman", "Support", true));
        p4.members.add(new PartyMember("DPS1", "assassin", "DPS", false));
        p4.members.add(new PartyMember("DPS2", "archer", "DPS", false));
        parties.add(p4);
    }

    private static int maxSizeForRaids(List<String> raidTypes) {
        return raidTypes.contains("ANNI") ? 10 : 4;
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

            nvgSave(nvg);
            nvgScissor(nvg, contentX, contentY, contentWidth, contentHeight);

            float cursorY = contentY - scrollOffset + PADDING;
            for (int i = 0; i < parties.size(); i++) {
                PartyListing party = parties.get(i);
                if (!matchesFilters(party)) continue;

                float cardH = party.expanded
                        ? CARD_HEADER_HEIGHT + party.members.size() * MEMBER_ROW_HEIGHT + CARD_PADDING
                        : COLLAPSED_ROW_HEIGHT;

                renderPartyCard(nvg, fontName, contentX + PADDING, cursorY,
                        contentWidth - PADDING * 2 - 6, cardH, party, i);
                cursorY += cardH + CARD_SPACING;
            }

            maxScroll = Math.max(0, cursorY + scrollOffset - contentY - contentHeight);
            nvgRestore(nvg);

            // Scrollbar
            if (maxScroll > 0) {
                float scrollbarX = panelX + panelWidth - 5;
                NVGWrapper.drawRect(nvg, scrollbarX, contentY, 4, contentHeight, SCROLLBAR_TRACK);
                float thumbRatio = contentHeight / (contentHeight + maxScroll);
                float thumbH = Math.max(20, contentHeight * thumbRatio);
                float thumbY = contentY + (scrollOffset / maxScroll) * (contentHeight - thumbH);
                NVGWrapper.drawRect(nvg, scrollbarX, thumbY, 4, thumbH, SCROLLBAR_THUMB);
            }

            // Filter + button (bottom right of content area)
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
            nvgText(nvg, filterX + FILTER_BUTTON_W / 2f, filterY + FILTER_BUTTON_H / 2f, "Filter +");
            ftc.free();

            // Role dropdown overlay
            if (roleDropdownOpen && !modalOpen) {
                renderRoleDropdownMenu(nvg, fontName);
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

        if (isPartyLeader) {
            String manageLabel = hasListedParty ? "Manage Party" : "New party +";
            float manageW = 95;
            drawHeaderButton(nvg, fontName, btnX, btnY, manageW, SEARCH_BAR_HEIGHT, manageLabel, MANAGE_PARTY_COLOR, NEW_PARTY_HOVER);
            btnX += manageW + 6;

            float delistW = 80;
            drawHeaderButton(nvg, fontName, btnX, btnY, delistW, SEARCH_BAR_HEIGHT, "Delist party", DELIST_PARTY_COLOR, DELIST_PARTY_HOVER);
            btnX += delistW + 6;
        } else {
            boolean inPartyAsMember = joinedPartyIndex >= 0;
            Color newBg = inPartyAsMember ? new Color(60, 60, 70, 180) : NEW_PARTY_COLOR;
            Color newHover = inPartyAsMember ? new Color(60, 60, 70, 180) : NEW_PARTY_HOVER;
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
            boolean itemHovered = isHovered(nvgMouseX, nvgMouseY, x, itemY, w, itemH);
            if (itemHovered) NVGWrapper.drawRect(nvg, x, itemY, w, itemH, DROPDOWN_HOVER);

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

    private void renderPartyCard(long nvg, String fontName, float x, float y, float w, float h,
                                 PartyListing party, int partyIndex) {
        boolean isJoined = joinedPartyIndex == partyIndex;
        NVGWrapper.drawRect(nvg, x, y, w, h, party.expanded ? CARD_EXPANDED_BG : CARD_BG);

        if (party.expanded) {
            renderExpandedCard(nvg, fontName, x, y, w, h, party, partyIndex, isJoined);
        } else {
            renderCollapsedCard(nvg, fontName, x, y, w, party);
        }
    }

    private void renderExpandedCard(long nvg, String fontName, float x, float y, float w, float h,
                                    PartyListing party, int partyIndex, boolean isJoined) {
        float rowX = x + CARD_PADDING;

        drawRaidTypeCircle(nvg, fontName, rowX, y + (CARD_HEADER_HEIGHT - TYPE_ICON_SIZE) / 2f, TYPE_ICON_SIZE, party.raidTypes.get(0));
        rowX += TYPE_ICON_SIZE + 6;

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, CARD_TITLE_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        var countCol = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, countCol);
        nvgText(nvg, rowX, y + CARD_HEADER_HEIGHT / 2f, party.members.size() + "/" + party.maxSize);
        countCol.free();

        // Collapse arrow
        nvgFontSize(nvg, 16);
        nvgTextAlign(nvg, NVG_ALIGN_RIGHT | NVG_ALIGN_MIDDLE);
        var arrCol = NVGContext.nvgColor(EXPAND_ARROW_COLOR);
        nvgFillColor(nvg, arrCol);
        nvgText(nvg, x + w - CARD_PADDING, y + CARD_HEADER_HEIGHT / 2f, "-");
        arrCol.free();

        // Members
        float memberY = y + CARD_HEADER_HEIGHT;
        for (PartyMember member : party.members) {
            renderMemberRow(nvg, fontName, x + CARD_PADDING + 10, memberY, w - CARD_PADDING * 2 - 10, member);
            memberY += MEMBER_ROW_HEIGHT;
        }

        float lastMemberCenterY = memberY - MEMBER_ROW_HEIGHT / 2f;

        // Join/Joined (don't show on your own party)
        boolean isMyParty = partyIndex == myPartyIndex;
        if (!isMyParty) {
            float joinX = x + w - CARD_PADDING - JOIN_BUTTON_WIDTH;
            float joinY = memberY - MEMBER_ROW_HEIGHT + (MEMBER_ROW_HEIGHT - BUTTON_HEIGHT) / 2f;
            boolean alreadyInParty = joinedPartyIndex >= 0 && !isJoined;
            boolean joinHovered = !alreadyInParty && isHovered(nvgMouseX, nvgMouseY, joinX, joinY, JOIN_BUTTON_WIDTH, BUTTON_HEIGHT);
            Color joinBg = isJoined ? JOINED_BUTTON_COLOR
                    : alreadyInParty ? new Color(60, 60, 70, 180)
                    : (joinHovered ? JOIN_BUTTON_HOVER : JOIN_BUTTON_COLOR);
            NVGWrapper.drawRect(nvg, joinX, joinY, JOIN_BUTTON_WIDTH, BUTTON_HEIGHT, joinBg);

            nvgFontFace(nvg, fontName);
            nvgFontSize(nvg, MEMBER_FONT_SIZE);
            nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
            Color textCol = alreadyInParty ? new Color(120, 120, 130, 200) : TEXT_COLOR;
            var jtc = NVGContext.nvgColor(textCol);
            nvgFillColor(nvg, jtc);
            nvgText(nvg, joinX + JOIN_BUTTON_WIDTH / 2f, joinY + BUTTON_HEIGHT / 2f, isJoined ? "Leave" : "Join");
            jtc.free();
        }

        // Raid(Type) label
        float labelRightX = x + w - CARD_PADDING - (isMyParty ? 0 : JOIN_BUTTON_WIDTH + 8);
        nvgFontSize(nvg, TYPE_FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_RIGHT | NVG_ALIGN_MIDDLE);
        var ptc = NVGContext.nvgColor(PARTY_TYPE_TEXT);
        nvgFillColor(nvg, ptc);
        nvgText(nvg, labelRightX, lastMemberCenterY, party.displayLabel());
        ptc.free();
    }

    private void renderCollapsedCard(long nvg, String fontName, float x, float y, float w, PartyListing party) {
        float rowX = x + CARD_PADDING;
        float centerY = y + COLLAPSED_ROW_HEIGHT / 2f;

        drawRaidTypeCircle(nvg, fontName, rowX, y + (COLLAPSED_ROW_HEIGHT - TYPE_ICON_SIZE) / 2f, TYPE_ICON_SIZE, party.raidTypes.get(0));
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
                NVGWrapper.drawImage(nvg, starIcon, rowX, starY, STAR_ICON_SIZE, STAR_ICON_SIZE, 255);
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
            AssetManager.Asset icon = getClassIcon(party.members.get(j).className);
            if (icon != null) {
                float iconX = rightX - CLASS_ICON_SIZE;
                float iconY = y + (COLLAPSED_ROW_HEIGHT - CLASS_ICON_SIZE) / 2f;
                NVGWrapper.drawImage(nvg, icon, iconX, iconY, CLASS_ICON_SIZE, CLASS_ICON_SIZE, 255);
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

    private void renderMemberRow(long nvg, String fontName, float x, float y, float w, PartyMember member) {
        float rowX = x;
        float centerY = y + MEMBER_ROW_HEIGHT / 2f;

        if (member.isLeader) {
            AssetManager.Asset starIcon = getClassIcon("star");
            if (starIcon != null) {
                float starY = centerY - STAR_ICON_SIZE / 2f;
                NVGWrapper.drawImage(nvg, starIcon, rowX, starY, STAR_ICON_SIZE, STAR_ICON_SIZE, 255);
            }
        }
        rowX += STAR_ICON_SIZE + 4;

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, MEMBER_FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        var nc = NVGContext.nvgColor(MEMBER_TEXT_COLOR);
        nvgFillColor(nvg, nc);
        nvgText(nvg, rowX, centerY, member.name);
        nc.free();

        float[] bounds = new float[4];
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, MEMBER_FONT_SIZE);
        float nameW = nvgTextBounds(nvg, 0, 0, member.name, bounds);
        rowX += nameW + 8;

        AssetManager.Asset icon = getClassIcon(member.className);
        if (icon != null) {
            float iconY = y + (MEMBER_ROW_HEIGHT - CLASS_ICON_SIZE) / 2f;
            NVGWrapper.drawImage(nvg, icon, rowX, iconY, CLASS_ICON_SIZE, CLASS_ICON_SIZE, 255);
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

    // ── Raid type circle (with abbreviation text) ──

    private void drawRaidTypeCircle(long nvg, String fontName, float x, float y, float size, String raidType) {
        float cx = x + size / 2f;
        float cy = y + size / 2f;

        nvgBeginPath(nvg);
        nvgCircle(nvg, cx, cy, size / 2f - 1);
        nvgStrokeWidth(nvg, 2f);
        var sc = NVGContext.nvgColor(TYPE_ICON_RING);
        nvgStrokeColor(nvg, sc);
        nvgStroke(nvg);
        nvgClosePath(nvg);
        sc.free();

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, RAID_LABEL_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var tc = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, tc);
        nvgText(nvg, cx, cy, raidType);
        tc.free();
    }

    // ── Create/Manage Party Modal ──

    private void renderModal(long nvg, String fontName, float panelX, float panelWidth, float screenHeight) {
        // Darken background
        NVGWrapper.drawRect(nvg, panelX, 0, panelWidth, screenHeight, MODAL_OVERLAY);

        // Modal centered in main panel area (NOT accounting for sidebar)
        modalX = panelX + (panelWidth - MODAL_WIDTH) / 2f;
        modalY = (screenHeight - MODAL_HEIGHT) / 2f;

        NVGWrapper.drawRect(nvg, modalX, modalY, MODAL_WIDTH, MODAL_HEIGHT, MODAL_BG);
        NVGWrapper.drawRectOutline(nvg, modalX, modalY, MODAL_WIDTH, MODAL_HEIGHT, 1, MODAL_BORDER);

        // Title
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, MODAL_TITLE_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var tc = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, tc);
        nvgText(nvg, modalX + MODAL_WIDTH / 2f, modalY + 18, "Create Party");
        tc.free();

        // Raid type circles row
        float totalCirclesW = RAID_TYPES.length * RAID_CIRCLE_SIZE + (RAID_TYPES.length - 1) * RAID_CIRCLE_SPACING;
        float circleStartX = modalX + (MODAL_WIDTH - totalCirclesW) / 2f;
        float circleY = modalY + 38;

        for (int i = 0; i < RAID_TYPES.length; i++) {
            String rt = RAID_TYPES[i];
            float cx = circleStartX + i * (RAID_CIRCLE_SIZE + RAID_CIRCLE_SPACING) + RAID_CIRCLE_SIZE / 2f;
            float cy = circleY + RAID_CIRCLE_SIZE / 2f;
            boolean selected = modalSelectedRaids.contains(rt);

            // Fill if selected
            if (selected) {
                nvgBeginPath(nvg);
                nvgCircle(nvg, cx, cy, RAID_CIRCLE_SIZE / 2f - 2);
                var fill = NVGContext.nvgColor(TYPE_ICON_SELECTED);
                nvgFillColor(nvg, fill);
                nvgFill(nvg);
                nvgClosePath(nvg);
                fill.free();
            }

            // Ring
            nvgBeginPath(nvg);
            nvgCircle(nvg, cx, cy, RAID_CIRCLE_SIZE / 2f - 1);
            nvgStrokeWidth(nvg, 2f);
            var ringCol = NVGContext.nvgColor(TYPE_ICON_RING);
            nvgStrokeColor(nvg, ringCol);
            nvgStroke(nvg);
            nvgClosePath(nvg);
            ringCol.free();

            // Label
            nvgFontFace(nvg, fontName);
            nvgFontSize(nvg, RAID_LABEL_SIZE);
            nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
            var lc = NVGContext.nvgColor(TEXT_COLOR);
            nvgFillColor(nvg, lc);
            nvgText(nvg, cx, cy, rt);
            lc.free();
        }

        // "Party type" and "Reserved slots" labels
        float rowY = circleY + RAID_CIRCLE_SIZE + 16;
        float leftColX = modalX + MODAL_WIDTH * 0.25f;
        float rightColX = modalX + MODAL_WIDTH * 0.75f;

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, MODAL_LABEL_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var l1 = NVGContext.nvgColor(PARTY_TYPE_TEXT);
        nvgFillColor(nvg, l1);
        nvgText(nvg, leftColX, rowY, "Party type");
        l1.free();

        var l2 = NVGContext.nvgColor(PARTY_TYPE_TEXT);
        nvgFillColor(nvg, l2);
        nvgText(nvg, rightColX, rowY, "Reserved slots");
        l2.free();

        // Party type dropdown
        float ptDropX = leftColX - MODAL_DROPDOWN_W / 2f;
        float ptDropY = rowY + 12;
        boolean ptHovered = isHovered(nvgMouseX, nvgMouseY, ptDropX, ptDropY, MODAL_DROPDOWN_W, MODAL_DROPDOWN_H);
        NVGWrapper.drawRect(nvg, ptDropX, ptDropY, MODAL_DROPDOWN_W, MODAL_DROPDOWN_H,
                ptHovered ? SEARCH_ACTIVE_BG : MODAL_DROPDOWN_BG);
        NVGWrapper.drawRectOutline(nvg, ptDropX, ptDropY, MODAL_DROPDOWN_W, MODAL_DROPDOWN_H, 1, MODAL_DROPDOWN_BORDER);

        nvgFontSize(nvg, MODAL_LABEL_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var ptc = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, ptc);
        nvgText(nvg, leftColX, ptDropY + MODAL_DROPDOWN_H / 2f, modalPartyType);
        ptc.free();

        // Reserved slots with up/down arrows
        float arrowW = 16;
        float rsFieldW = MODAL_DROPDOWN_W - arrowW;
        float rsBoxX = rightColX - MODAL_DROPDOWN_W / 2f;
        float rsBoxY = ptDropY;
        float arrowX = rsBoxX + rsFieldW;
        float halfArrowH = MODAL_DROPDOWN_H / 2f;

        // Number field
        Color rsFieldBg = reservedSlotsFocused ? SEARCH_ACTIVE_BG : MODAL_DROPDOWN_BG;
        NVGWrapper.drawRect(nvg, rsBoxX, rsBoxY, rsFieldW, MODAL_DROPDOWN_H, rsFieldBg);
        NVGWrapper.drawRectOutline(nvg, rsBoxX, rsBoxY, rsFieldW, MODAL_DROPDOWN_H, 1,
                reservedSlotsFocused ? SEARCH_BORDER : MODAL_DROPDOWN_BORDER);

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, MODAL_LABEL_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var rsc = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, rsc);
        nvgText(nvg, rsBoxX + rsFieldW / 2f, rsBoxY + MODAL_DROPDOWN_H / 2f,
                reservedSlotsFocused ? reservedSlotsInput : String.valueOf(modalReservedSlots));
        rsc.free();

        // Up arrow button
        boolean upHovered = isHovered(nvgMouseX, nvgMouseY, arrowX, rsBoxY, arrowW, halfArrowH);
        NVGWrapper.drawRect(nvg, arrowX, rsBoxY, arrowW, halfArrowH, upHovered ? DROPDOWN_HOVER : MODAL_DROPDOWN_BG);
        NVGWrapper.drawRectOutline(nvg, arrowX, rsBoxY, arrowW, halfArrowH, 1, MODAL_DROPDOWN_BORDER);
        drawTriangle(nvg, arrowX + arrowW / 2f, rsBoxY + halfArrowH / 2f, 4, true, TEXT_COLOR);

        // Down arrow button
        boolean downHovered = isHovered(nvgMouseX, nvgMouseY, arrowX, rsBoxY + halfArrowH, arrowW, halfArrowH);
        NVGWrapper.drawRect(nvg, arrowX, rsBoxY + halfArrowH, arrowW, halfArrowH, downHovered ? DROPDOWN_HOVER : MODAL_DROPDOWN_BG);
        NVGWrapper.drawRectOutline(nvg, arrowX, rsBoxY + halfArrowH, arrowW, halfArrowH, 1, MODAL_DROPDOWN_BORDER);
        drawTriangle(nvg, arrowX + arrowW / 2f, rsBoxY + halfArrowH + halfArrowH / 2f, 4, false, TEXT_COLOR);

        // Create/Update button
        float createBtnX = modalX + (MODAL_WIDTH - MODAL_BUTTON_W) / 2f;
        float createBtnY = modalY + MODAL_HEIGHT - MODAL_BUTTON_H - 14;
        String createLabel = hasListedParty ? "Update..." : "Create!";
        boolean createHovered = isHovered(nvgMouseX, nvgMouseY, createBtnX, createBtnY, MODAL_BUTTON_W, MODAL_BUTTON_H);
        NVGWrapper.drawRect(nvg, createBtnX, createBtnY, MODAL_BUTTON_W, MODAL_BUTTON_H,
                createHovered ? NEW_PARTY_HOVER : NEW_PARTY_COLOR);

        nvgFontSize(nvg, MEMBER_FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var cbc = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, cbc);
        nvgText(nvg, createBtnX + MODAL_BUTTON_W / 2f, createBtnY + MODAL_BUTTON_H / 2f, createLabel);
        cbc.free();

        // Party type dropdown menu (if open, on top)
        if (modalPartyTypeDropdownOpen) {
            float ddY = ptDropY + MODAL_DROPDOWN_H;
            float ddH = PARTY_TYPES.length * MODAL_DROPDOWN_H;
            NVGWrapper.drawRect(nvg, ptDropX, ddY, MODAL_DROPDOWN_W, ddH, DROPDOWN_BG);
            NVGWrapper.drawRectOutline(nvg, ptDropX, ddY, MODAL_DROPDOWN_W, ddH, 1, DROPDOWN_BORDER);

            for (int i = 0; i < PARTY_TYPES.length; i++) {
                float itemY = ddY + i * MODAL_DROPDOWN_H;
                boolean ih = isHovered(nvgMouseX, nvgMouseY, ptDropX, itemY, MODAL_DROPDOWN_W, MODAL_DROPDOWN_H);
                if (ih) NVGWrapper.drawRect(nvg, ptDropX, itemY, MODAL_DROPDOWN_W, MODAL_DROPDOWN_H, DROPDOWN_HOVER);

                nvgFontFace(nvg, fontName);
                nvgFontSize(nvg, MODAL_LABEL_SIZE);
                nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
                var ic = NVGContext.nvgColor(TEXT_COLOR);
                nvgFillColor(nvg, ic);
                nvgText(nvg, leftColX, itemY + MODAL_DROPDOWN_H / 2f, PARTY_TYPES[i]);
                ic.free();
            }
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
        if (SeqClient.assetManager == null) return null;
        return SeqClient.assetManager.getAsset(className);
    }

    private boolean matchesFilters(PartyListing party) {
        if (!searchQuery.isEmpty()) {
            boolean matches = false;
            String q = searchQuery.toLowerCase();
            for (PartyMember m : party.members) {
                if (m.name.toLowerCase().contains(q)) { matches = true; break; }
            }
            boolean raidMatch = false;
            for (String rt : party.raidTypes) {
                if (rt.toLowerCase().contains(q)) { raidMatch = true; break; }
            }
            if (!matches && !party.partyType.toLowerCase().contains(q) && !raidMatch) return false;
        }
        return true;
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

        // ── Modal clicks (highest priority) ──
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
                    // Update role in current party
                    if (selectedRole != null && joinedPartyIndex >= 0 && joinedPartyIndex < parties.size()) {
                        String playerName = SeqClient.mc.getUser().getName();
                        for (PartyMember m : parties.get(joinedPartyIndex).members) {
                            if (m.name.equals(playerName)) {
                                m.role = selectedRole;
                                break;
                            }
                        }
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

        if (isPartyLeader) {
            float manageW = 95;
            if (isHovered(mx, my, headerBtnX, headerBtnY, manageW, SEARCH_BAR_HEIGHT)) {
                openModal(true);
                return true;
            }
            headerBtnX += manageW + 6;

            float delistW = 80;
            if (isHovered(mx, my, headerBtnX, headerBtnY, delistW, SEARCH_BAR_HEIGHT)) {
                if (myPartyIndex >= 0 && myPartyIndex < parties.size()) {
                    parties.remove(myPartyIndex);
                    if (joinedPartyIndex == myPartyIndex) {
                        joinedPartyIndex = -1;
                    } else if (joinedPartyIndex > myPartyIndex) {
                        joinedPartyIndex--;
                    }
                }
                myPartyIndex = -1;
                joinedPartyIndex = -1;
                isPartyLeader = false;
                hasListedParty = false;
                return true;
            }
            headerBtnX += delistW + 6;
        } else {
            float newW = 80;
            if (isHovered(mx, my, headerBtnX, headerBtnY, newW, SEARCH_BAR_HEIGHT)) {
                if (joinedPartyIndex < 0) {
                    openModal(false);
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
            // TODO: open filter options
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

        for (int i = 0; i < parties.size(); i++) {
            PartyListing party = parties.get(i);
            if (!matchesFilters(party)) continue;

            float cardX = panelX + PADDING;
            float cardW = contentWidth - PADDING * 2 - 6;
            float cardH;

            if (party.expanded) {
                cardH = CARD_HEADER_HEIGHT + party.members.size() * MEMBER_ROW_HEIGHT + CARD_PADDING;

                float joinBtnX = cardX + cardW - CARD_PADDING - JOIN_BUTTON_WIDTH;
                float joinBtnY = cursorY + CARD_HEADER_HEIGHT + (party.members.size() - 1) * MEMBER_ROW_HEIGHT
                        + (MEMBER_ROW_HEIGHT - BUTTON_HEIGHT) / 2f;
                boolean isMyParty = i == myPartyIndex;
                if (!isMyParty && isHovered(mx, my, joinBtnX, joinBtnY, JOIN_BUTTON_WIDTH, BUTTON_HEIGHT)) {
                    if (joinedPartyIndex == i) {
                        // Leave this party - remove self from members
                        String playerName = SeqClient.mc.getUser().getName();
                        party.members.removeIf(m -> m.name.equals(playerName));
                        joinedPartyIndex = -1;
                    } else if (joinedPartyIndex < 0) {
                        // Join (only if not already in a party)
                        String playerName = SeqClient.mc.getUser().getName();
                        String role = selectedRole != null ? selectedRole : "DPS";
                        party.members.add(new PartyMember(playerName, "assassin", role, false));
                        joinedPartyIndex = i;
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
        modalPartyTypeDropdownOpen = false;
        reservedSlotsFocused = false;
        hasListedParty = managing && isPartyLeader;
        if (!managing) {
            modalSelectedRaids.clear();
            modalPartyType = "Casual";
            modalReservedSlots = 0;
        }
        reservedSlotsInput = String.valueOf(modalReservedSlots);
    }

    private boolean handleModalClick(float mx, float my, float screenWidth, float screenHeight) {
        float panelX = SIDEBAR_WIDTH;
        float panelWidth = screenWidth - SIDEBAR_WIDTH;
        float mX = panelX + (panelWidth - MODAL_WIDTH) / 2f;
        float mY = (screenHeight - MODAL_HEIGHT) / 2f;

        // Click outside modal closes it
        if (!isHovered(mx, my, mX, mY, MODAL_WIDTH, MODAL_HEIGHT)) {
            modalOpen = false;
            modalPartyTypeDropdownOpen = false;
            reservedSlotsFocused = false;
            return true;
        }

        // Party type dropdown menu (check first)
        if (modalPartyTypeDropdownOpen) {
            float leftColX = mX + MODAL_WIDTH * 0.25f;
            float ptDropX = leftColX - MODAL_DROPDOWN_W / 2f;

            float totalCirclesW = RAID_TYPES.length * RAID_CIRCLE_SIZE + (RAID_TYPES.length - 1) * RAID_CIRCLE_SPACING;
            float circleY = mY + 38;
            float rowY = circleY + RAID_CIRCLE_SIZE + 16;
            float ptDropY = rowY + 12;
            float ddY = ptDropY + MODAL_DROPDOWN_H;

            for (int i = 0; i < PARTY_TYPES.length; i++) {
                float itemY = ddY + i * MODAL_DROPDOWN_H;
                if (isHovered(mx, my, ptDropX, itemY, MODAL_DROPDOWN_W, MODAL_DROPDOWN_H)) {
                    modalPartyType = PARTY_TYPES[i];
                    modalPartyTypeDropdownOpen = false;
                    return true;
                }
            }
            modalPartyTypeDropdownOpen = false;
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
                    // ANNI exclusivity
                    if ("ANNI".equals(rt)) {
                        modalSelectedRaids.clear();
                        modalSelectedRaids.add("ANNI");
                    } else {
                        modalSelectedRaids.remove("ANNI");
                        modalSelectedRaids.add(rt);
                    }
                }
                return true;
            }
        }

        // Party type dropdown button
        float leftColX = mX + MODAL_WIDTH * 0.25f;
        float rowY = circleY + RAID_CIRCLE_SIZE + 16;
        float ptDropX = leftColX - MODAL_DROPDOWN_W / 2f;
        float ptDropY = rowY + 12;

        if (isHovered(mx, my, ptDropX, ptDropY, MODAL_DROPDOWN_W, MODAL_DROPDOWN_H)) {
            modalPartyTypeDropdownOpen = !modalPartyTypeDropdownOpen;
            return true;
        }

        // Reserved slots - up/down arrows and number field
        float rightColX = mX + MODAL_WIDTH * 0.75f;
        float arrowW = 16;
        float rsFieldW = MODAL_DROPDOWN_W - arrowW;
        float rsBoxX = rightColX - MODAL_DROPDOWN_W / 2f;
        float rsBoxY = ptDropY;
        float arrowX = rsBoxX + rsFieldW;
        float halfArrowH = MODAL_DROPDOWN_H / 2f;
        int maxSlots = modalSelectedRaids.contains("ANNI") ? 9 : 3;

        // Up arrow
        if (isHovered(mx, my, arrowX, rsBoxY, arrowW, halfArrowH)) {
            modalReservedSlots = Math.min(maxSlots, modalReservedSlots + 1);
            reservedSlotsInput = String.valueOf(modalReservedSlots);
            reservedSlotsFocused = false;
            return true;
        }
        // Down arrow
        if (isHovered(mx, my, arrowX, rsBoxY + halfArrowH, arrowW, halfArrowH)) {
            modalReservedSlots = Math.max(0, modalReservedSlots - 1);
            reservedSlotsInput = String.valueOf(modalReservedSlots);
            reservedSlotsFocused = false;
            return true;
        }
        // Number field - click to focus and type
        if (isHovered(mx, my, rsBoxX, rsBoxY, rsFieldW, MODAL_DROPDOWN_H)) {
            reservedSlotsFocused = true;
            reservedSlotsInput = String.valueOf(modalReservedSlots);
            return true;
        }
        // Clicked elsewhere in modal - unfocus
        if (reservedSlotsFocused) {
            commitReservedSlotsInput();
            reservedSlotsFocused = false;
        }

        // Create/Update button
        float createBtnX = mX + (MODAL_WIDTH - MODAL_BUTTON_W) / 2f;
        float createBtnY = mY + MODAL_HEIGHT - MODAL_BUTTON_H - 14;
        if (isHovered(mx, my, createBtnX, createBtnY, MODAL_BUTTON_W, MODAL_BUTTON_H)) {
            if (!modalSelectedRaids.isEmpty()) {
                commitReservedSlotsInput();
                List<String> raidTypes = new ArrayList<>(modalSelectedRaids);
                String playerName = SeqClient.mc.getUser().getName();

                if (myPartyIndex >= 0 && myPartyIndex < parties.size()) {
                    // Update existing party
                    PartyListing existing = parties.get(myPartyIndex);
                    PartyListing updated = new PartyListing(raidTypes, modalPartyType);
                    updated.members.addAll(existing.members);
                    updated.expanded = true;
                    parties.set(myPartyIndex, updated);
                } else {
                    // Create new party
                    PartyListing newParty = new PartyListing(raidTypes, modalPartyType);
                    String role = selectedRole != null ? selectedRole : "DPS";
                    newParty.members.add(new PartyMember(playerName, "assassin", role, true));
                    newParty.expanded = true;
                    parties.add(0, newParty);
                    myPartyIndex = 0;
                    // Shift joinedPartyIndex if needed
                    if (joinedPartyIndex >= 0) joinedPartyIndex++;
                }

                isPartyLeader = true;
                hasListedParty = true;
                joinedPartyIndex = myPartyIndex;
                modalOpen = false;
                reservedSlotsFocused = false;
                scrollOffset = 0;
            }
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
        if (modalOpen) return true;
        scrollOffset -= (float) scrollY * SCROLL_SPEED;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        return true;
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent keyEvent) {
        if (modalOpen) {
            int keyCode = keyEvent.key();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (reservedSlotsFocused) {
                    commitReservedSlotsInput();
                    reservedSlotsFocused = false;
                } else {
                    modalOpen = false;
                    modalPartyTypeDropdownOpen = false;
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
                if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
                    if (reservedSlotsInput.length() < 2) {
                        reservedSlotsInput += (char) ('0' + (keyCode - GLFW.GLFW_KEY_0));
                    }
                    return true;
                }
                if (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9) {
                    if (reservedSlotsInput.length() < 2) {
                        reservedSlotsInput += (char) ('0' + (keyCode - GLFW.GLFW_KEY_KP_0));
                    }
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_UP) {
                    int maxSlots = modalSelectedRaids.contains("ANNI") ? 9 : 3;
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

    // ══════════════════════════════ DATA ══════════════════════════════

    private static class PartyMember {
        final String name;
        final String className;
        String role;
        final boolean isLeader;

        PartyMember(String name, String className, String role, boolean isLeader) {
            this.name = name;
            this.className = className;
            this.role = role;
            this.isLeader = isLeader;
        }
    }

    private static class PartyListing {
        final int maxSize;
        final List<String> raidTypes;
        final String partyType;
        final List<PartyMember> members = new ArrayList<>();
        boolean expanded = false;

        PartyListing(List<String> raidTypes, String partyType) {
            this.raidTypes = raidTypes;
            this.partyType = partyType;
            this.maxSize = maxSizeForRaids(raidTypes);
        }

        String raidLabel() {
            return String.join(", ", raidTypes);
        }

        String displayLabel() {
            return raidLabel() + "(" + partyType + ")";
        }

        PartyMember getLeader() {
            for (PartyMember m : members) { if (m.isLeader) return m; }
            return members.isEmpty() ? null : members.get(0);
        }
    }
}
