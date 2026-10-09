package com.seqwawa.seq.ui;

/** A clipped list whose rendered rows and selectable rows use identical bounds. */
record UiRowList(UiBounds area, float step, float rowHeight) {
    UiBounds row(int visibleIndex) {
        return new UiBounds(area.x(), area.y() + visibleIndex * step + 2, area.width(), rowHeight);
    }

    boolean fullyVisible(int visibleIndex) {
        return visibleIndex >= 0 && area.height() >= (visibleIndex + 1) * step;
    }

    int indexAt(float mouseX, float mouseY, int start, int size) {
        int visibleIndex = (int) Math.floor((mouseY - area.y()) / step);
        int index = start + visibleIndex;
        return index >= 0 && index < size && fullyVisible(visibleIndex)
                && row(visibleIndex).contains(mouseX, mouseY) ? index : -1;
    }
}
