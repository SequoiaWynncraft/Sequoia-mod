package com.seqwawa.seq.ui;

import java.util.ArrayList;
import java.util.List;

/** Pure screen-space geometry shared by territory-picker rendering and input routing. */
record PickerControlLayout(
        Bounds sidebar,
        Bounds header,
        Bounds map,
        Bounds resourceToggle,
        Bounds lockToggle,
        Bounds nameField,
        Bounds colorWidget,
        float teamsLabelY,
        List<Bounds> teamRows,
        Bounds teamScroll,
        float selectionLabelY,
        Bounds clear,
        Bounds cancel,
        Bounds save) {
    static final float SIDEBAR_WIDTH = 236;
    static final float HEADER_HEIGHT = 34;
    static final float PADDING = 10;
    static final float HEADER_RESOURCE_WIDTH = 100;
    static final float HEADER_LOCK_WIDTH = 110;

    private static final float FIELD_HEIGHT = 24;
    private static final float BUTTON_HEIGHT = 23;
    private static final float COLOR_Y = 94;
    private static final float TEAM_ROW_HEIGHT = 21;
    private static final float TEAM_ROW_STEP = 24;

    PickerControlLayout {
        teamRows = List.copyOf(teamRows);
    }

    static PickerControlLayout create(float width, float height, float colorWidgetHeight, boolean canManage) {
        float safeWidth = Math.max(0, width);
        float safeHeight = Math.max(0, height);
        float right = safeWidth - PADDING;
        boolean compact = safeWidth < 500;
        float resourceWidth = compact ? 70 : HEADER_RESOURCE_WIDTH;
        float lockWidth = compact ? 86 : HEADER_LOCK_WIDTH;
        Bounds lockToggle = canManage
                ? new Bounds(right - lockWidth, 5, lockWidth, BUTTON_HEIGHT)
                : Bounds.empty(right, 5);
        float resourceRight = canManage ? lockToggle.x() - 6 : right;
        Bounds resourceToggle = new Bounds(resourceRight - resourceWidth, 5, resourceWidth, BUTTON_HEIGHT);

        float safeColorHeight = Math.max(0, colorWidgetHeight);
        float teamsLabelY = COLOR_Y + safeColorHeight + 10;
        int visibleTeamRows = visibleTeamRows(safeHeight, safeColorHeight);
        List<Bounds> teamRows = new ArrayList<>(visibleTeamRows);
        for (int row = 0; row < visibleTeamRows; row++) {
            teamRows.add(new Bounds(
                    PADDING,
                    teamsLabelY + 14 + row * TEAM_ROW_STEP,
                    SIDEBAR_WIDTH - PADDING * 2,
                    TEAM_ROW_HEIGHT));
        }
        float selectionLabelY = teamsLabelY + 20 + visibleTeamRows * TEAM_ROW_STEP;

        return new PickerControlLayout(
                new Bounds(0, 0, SIDEBAR_WIDTH, safeHeight),
                new Bounds(SIDEBAR_WIDTH, 0, Math.max(0, safeWidth - SIDEBAR_WIDTH), HEADER_HEIGHT),
                new Bounds(
                        SIDEBAR_WIDTH,
                        HEADER_HEIGHT,
                        Math.max(1, safeWidth - SIDEBAR_WIDTH),
                        Math.max(1, safeHeight - HEADER_HEIGHT)),
                resourceToggle,
                lockToggle,
                new Bounds(PADDING, 60, SIDEBAR_WIDTH - PADDING * 2, FIELD_HEIGHT),
                new Bounds(2, COLOR_Y, SIDEBAR_WIDTH - 4, safeColorHeight),
                teamsLabelY,
                teamRows,
                new Bounds(0, teamsLabelY, SIDEBAR_WIDTH, 20 + visibleTeamRows * TEAM_ROW_STEP),
                selectionLabelY,
                new Bounds(PADDING, selectionLabelY + 45, 68, BUTTON_HEIGHT),
                new Bounds(PADDING, safeHeight - 38, 78, BUTTON_HEIGHT),
                new Bounds(SIDEBAR_WIDTH - PADDING - 92, safeHeight - 38, 92, BUTTON_HEIGHT));
    }

    static int visibleTeamRows(float height, float colorWidgetHeight) {
        return Math.max(1, Math.min(4,
                (int) ((height - 318 - Math.max(0, colorWidgetHeight - 42)) / TEAM_ROW_STEP)));
    }

    int visibleTeamRows() {
        return teamRows.size();
    }

    Bounds teamRow(int visibleRow) {
        return teamRows.get(visibleRow);
    }

    boolean headerTitleVisible() {
        return resourceToggle.x() > SIDEBAR_WIDTH + 150;
    }

    record Bounds(float x, float y, float width, float height) {
        Bounds {
            if (!Float.isFinite(x)
                    || !Float.isFinite(y)
                    || !Float.isFinite(width)
                    || !Float.isFinite(height)
                    || width < 0
                    || height < 0) {
                throw new IllegalArgumentException("Control bounds must be finite and non-negative in size.");
            }
        }

        static Bounds empty(float x, float y) {
            return new Bounds(x, y, 0, 0);
        }

        boolean contains(float pointX, float pointY) {
            return width > 0
                    && height > 0
                    && pointX >= x
                    && pointX < x + width
                    && pointY >= y
                    && pointY < y + height;
        }

        float centerX() {
            return x + width / 2;
        }

        float centerY() {
            return y + height / 2;
        }
    }
}
