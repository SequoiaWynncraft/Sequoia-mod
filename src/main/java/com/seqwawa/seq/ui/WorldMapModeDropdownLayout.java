package com.seqwawa.seq.ui;

import com.seqwawa.seq.map.MapDisplayMode;

/** Shared bounds for the map mode overlay and its input targets. */
record WorldMapModeDropdownLayout(float x, float y, float width, float rowHeight) {
    static WorldMapModeDropdownLayout fit(float mapX, float mapWidth) {
        float padding = Math.min(12, Math.max(0, mapWidth) / 2f);
        float width = Math.min(160, Math.max(0, mapWidth - padding * 2));
        return new WorldMapModeDropdownLayout(mapX + mapWidth - padding - width, 12, width, 24);
    }

    boolean contains(float mouseX, float mouseY, boolean open) {
        int rows = open ? MapDisplayMode.values().length + 1 : 1;
        return width > 0 && mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + rowHeight * rows;
    }

    int optionAt(float mouseX, float mouseY) {
        if (!contains(mouseX, mouseY, true) || mouseY < y + rowHeight) {
            return -1;
        }
        return (int) ((mouseY - y) / rowHeight) - 1;
    }
}
