package com.seqwawa.seq.ui;

import java.util.function.ToDoubleFunction;

final class IngredientGuideViewLayout {
    private static final float SEARCH_HEIGHT = 28;
    private static final float SORT_ROW_HEIGHT = 22;
    private static final float SORT_ROW_GAP = 4;
    private static final float MIN_SCROLLBAR_THUMB_HEIGHT = 20;
    private static final float SCROLLBAR_WIDTH = 3;
    private static final float SCROLLBAR_HIT_WIDTH = 9;

    private IngredientGuideViewLayout() {}

    static IngredientListLayout ingredientListLayout(float panelY, boolean showSecondarySort) {
        float primarySortY = panelY + 9 + SEARCH_HEIGHT + 8;
        float secondarySortY = primarySortY + SORT_ROW_HEIGHT + SORT_ROW_GAP;
        float finalSortY = showSecondarySort ? secondarySortY : primarySortY;
        float summaryY = finalSortY + SORT_ROW_HEIGHT + 11;
        return new IngredientListLayout(primarySortY, secondarySortY, summaryY, summaryY + 12);
    }

    static ScrollbarGeometry scrollbarGeometry(float x, float y, float height, float scroll, float maxScroll) {
        if (maxScroll <= 0 || height <= 0) {
            return null;
        }
        float thumbHeight = scrollbarThumbHeight(height, maxScroll);
        float thumbY = y + (height - thumbHeight) * (clamp(scroll, 0, maxScroll) / maxScroll);
        return new ScrollbarGeometry(x, y, height, thumbY, thumbHeight);
    }

    static float scrollbarThumbHeight(float trackHeight, float maxScroll) {
        if (trackHeight <= 0) {
            return 0;
        }
        if (maxScroll <= 0) {
            return trackHeight;
        }
        return Math.min(
                trackHeight,
                Math.max(MIN_SCROLLBAR_THUMB_HEIGHT, trackHeight * trackHeight / (trackHeight + maxScroll)));
    }

    static String ellipsize(String text, float maxWidth, ToDoubleFunction<String> width) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (width.applyAsDouble(text) <= maxWidth) {
            return text;
        }
        String suffix = "…";
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            String candidate = text.substring(0, mid) + suffix;
            if (width.applyAsDouble(candidate) <= maxWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return text.substring(0, low) + suffix;
    }

    static boolean contains(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    record IngredientListLayout(float primarySortY, float secondarySortY, float summaryY, float rowsTop) {}

    record ScrollbarGeometry(float x, float y, float height, float thumbY, float thumbHeight) {
        boolean containsTrack(float mouseX, float mouseY) {
            return IngredientGuideViewLayout.contains(
                    mouseX,
                    mouseY,
                    x - (SCROLLBAR_HIT_WIDTH - SCROLLBAR_WIDTH) / 2f,
                    y,
                    SCROLLBAR_HIT_WIDTH,
                    height);
        }
    }
}
