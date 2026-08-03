package com.seqwawa.seq.ui;

import java.util.function.ToDoubleFunction;

final class PartyFinderViewLayout {
    private static final float HEADER_HEIGHT = 30;
    private static final float SEARCH_BAR_HEIGHT = 18;
    private static final float SEARCH_BAR_WIDTH = 140;
    private static final float SEARCH_BAR_MARGIN = 8;
    private static final float BUTTON_SPACING = 6;
    private static final float MANAGE_BUTTON_WIDTH = 88;
    private static final float INVITE_BUTTON_WIDTH = 56;
    private static final float OPEN_CLOSE_BUTTON_WIDTH = 84;
    private static final float DELIST_BUTTON_WIDTH = 72;
    private static final float INVITE_ALL_BUTTON_WIDTH = 68;
    private static final float NEW_PARTY_BUTTON_WIDTH = 80;
    private static final float ROLE_DROPDOWN_WIDTH = 80;

    private PartyFinderViewLayout() {}

    static HeaderControls headerControls(float panelX, boolean partyLeader) {
        float searchX = panelX + SEARCH_BAR_MARGIN;
        float searchY = (HEADER_HEIGHT - SEARCH_BAR_HEIGHT) / 2f;
        Bounds searchBar = new Bounds(searchX, searchY, SEARCH_BAR_WIDTH, SEARCH_BAR_HEIGHT);

        float nextButtonX = searchX + SEARCH_BAR_WIDTH + SEARCH_BAR_MARGIN;
        Bounds manageButton = null;
        Bounds inviteButton = null;
        Bounds openCloseButton = null;
        Bounds delistButton = null;
        Bounds inviteAllButton = null;
        Bounds newPartyButton = null;

        if (partyLeader) {
            manageButton = new Bounds(nextButtonX, searchY, MANAGE_BUTTON_WIDTH, SEARCH_BAR_HEIGHT);
            nextButtonX += MANAGE_BUTTON_WIDTH + BUTTON_SPACING;
            inviteButton = new Bounds(nextButtonX, searchY, INVITE_BUTTON_WIDTH, SEARCH_BAR_HEIGHT);
            nextButtonX += INVITE_BUTTON_WIDTH + BUTTON_SPACING;
            openCloseButton = new Bounds(nextButtonX, searchY, OPEN_CLOSE_BUTTON_WIDTH, SEARCH_BAR_HEIGHT);
            nextButtonX += OPEN_CLOSE_BUTTON_WIDTH + BUTTON_SPACING;
            delistButton = new Bounds(nextButtonX, searchY, DELIST_BUTTON_WIDTH, SEARCH_BAR_HEIGHT);
            nextButtonX += DELIST_BUTTON_WIDTH + BUTTON_SPACING;
            inviteAllButton = new Bounds(nextButtonX, searchY, INVITE_ALL_BUTTON_WIDTH, SEARCH_BAR_HEIGHT);
            nextButtonX += INVITE_ALL_BUTTON_WIDTH + BUTTON_SPACING;
        } else {
            newPartyButton = new Bounds(nextButtonX, searchY, NEW_PARTY_BUTTON_WIDTH, SEARCH_BAR_HEIGHT);
            nextButtonX += NEW_PARTY_BUTTON_WIDTH + BUTTON_SPACING;
        }

        Bounds roleDropdown = new Bounds(nextButtonX, searchY, ROLE_DROPDOWN_WIDTH, SEARCH_BAR_HEIGHT);
        return new HeaderControls(
                searchBar,
                manageButton,
                inviteButton,
                openCloseButton,
                delistButton,
                inviteAllButton,
                newPartyButton,
                roleDropdown);
    }

    static String fitText(String text, double maxWidth, ToDoubleFunction<String> width) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (width.applyAsDouble(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        if (width.applyAsDouble(ellipsis) > maxWidth) {
            return "";
        }
        for (int end = text.length() - 1; end > 0; end--) {
            String candidate = text.substring(0, end) + ellipsis;
            if (width.applyAsDouble(candidate) <= maxWidth) {
                return candidate;
            }
        }
        return ellipsis;
    }

    record Bounds(float x, float y, float w, float h) {
        boolean contains(float mouseX, float mouseY) {
            return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        }
    }

    record HeaderControls(
            Bounds searchBar,
            Bounds manageButton,
            Bounds inviteButton,
            Bounds openCloseButton,
            Bounds delistButton,
            Bounds inviteAllButton,
            Bounds newPartyButton,
            Bounds roleDropdown) {}
}
