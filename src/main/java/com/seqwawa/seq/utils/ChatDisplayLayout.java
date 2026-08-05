package com.seqwawa.seq.utils;

/** Geometry shared by the chat-line-count setting and its unit tests. */
public final class ChatDisplayLayout {

    private ChatDisplayLayout() {}

    /**
     * Height occupied by {@code requestedLines}, capped to complete lines that fit in
     * the available screen area. Returning a multiple of {@code lineHeight} keeps
     * Minecraft's pagination, rendering and pointer hit-testing on the same boundary.
     */
    public static int heightForVisibleLines(int requestedLines, int lineHeight, int availableHeight) {
        if (lineHeight <= 0) {
            return Math.max(0, availableHeight);
        }

        int requested = Math.max(1, requestedLines);
        int availableLines = Math.max(0, availableHeight / lineHeight);
        int visibleLines = Math.min(requested, availableLines);
        return visibleLines * lineHeight;
    }
}
