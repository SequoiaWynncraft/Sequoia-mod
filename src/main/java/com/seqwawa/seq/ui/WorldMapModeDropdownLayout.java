package com.seqwawa.seq.ui;

import com.seqwawa.seq.map.MapDisplayMode;

/** Shared bounds for the map mode overlay and its input targets. */
record WorldMapModeDropdownLayout(float x, float y, float width, float rowHeight, float closeX) {
    static WorldMapModeDropdownLayout fit(float mapX, float mapWidth) {
        float padding = Math.min(12, Math.max(0, mapWidth) / 2f);
        float availableWidth = Math.max(0, mapWidth - padding * 2);
        float gap = Math.min(12, availableWidth);
        float width = Math.min(160, (availableWidth - gap) / 2f);
        return new WorldMapModeDropdownLayout(
                mapX + padding, 12, width, 24, mapX + mapWidth - padding - width);
    }

    boolean contains(float mouseX, float mouseY, boolean open) {
        int rows = open ? MapDisplayMode.values().length + 1 : 1;
        return width > 0 && mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + rowHeight * rows;
    }

    boolean containsClose(float mouseX, float mouseY) {
        return width > 0 && mouseX >= closeX && mouseX < closeX + width
                && mouseY >= y && mouseY < y + rowHeight;
    }

    int optionAt(float mouseX, float mouseY) {
        if (!contains(mouseX, mouseY, true) || mouseY < y + rowHeight) {
            return -1;
        }
        return (int) ((mouseY - y) / rowHeight) - 1;
    }
}
