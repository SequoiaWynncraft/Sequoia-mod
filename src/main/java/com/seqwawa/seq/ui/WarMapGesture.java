package com.seqwawa.seq.ui;

import com.seqwawa.seq.ui.WarQueueMapOverlay.PendingWarQueueClick;

/** Pointer gesture lifecycle shared by map panning, territory selection and queue double-clicks. */
final class WarMapGesture {
    private boolean dragging;
    private float pressX, pressY;
    private boolean moved;
    private PendingWarQueueClick pendingQueueClick;

    boolean dragging() { return dragging; }

    boolean press(Long queueId, String territory, float x, float y, long now) {
        if (queueId != null && WarQueueMapOverlay.isWarQueueDoubleClick(pendingQueueClick, queueId, territory, x, y, now)) {
            reset();
            return true;
        }
        pendingQueueClick = queueId == null ? null : new PendingWarQueueClick(queueId, territory, x, y, now);
        dragging = true;
        pressX = x;
        pressY = y;
        moved = false;
        return false;
    }

    boolean drag(float x, float y) {
        if (!dragging) return false;
        clearQueueClick();
        if (Math.hypot(x - pressX, y - pressY) >= 4) moved = true;
        return true;
    }

    boolean release() {
        boolean clicked = dragging && !moved;
        dragging = false;
        return clicked;
    }

    void cancelDrag() { dragging = false; }
    void clearQueueClick() { pendingQueueClick = null; }
    void reset() { cancelDrag(); clearQueueClick(); }
}
