package com.seqwawa.seq.ui;

import com.seqwawa.seq.map.MapViewport;

/** Current screen coordinates, supplied again for every render and input event. */
record WorldMapFrame(float width, float height, float mouseX, float mouseY, MapViewport viewport, boolean dragging) {
    float tooltipX(float tooltipWidth) {
        float right = viewport.screenX() + viewport.screenWidth();
        return Math.max(viewport.screenX() + 8, Math.min(mouseX + 12, right - tooltipWidth - 8));
    }
}
