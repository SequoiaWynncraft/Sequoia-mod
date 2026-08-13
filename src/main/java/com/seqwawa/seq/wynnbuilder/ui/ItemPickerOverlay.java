package com.seqwawa.seq.wynnbuilder.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_DIVIDER;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_PRIMARY;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_BODY_OPAQUE;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_MODAL_OVERLAY;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_INPUT;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_INPUT_HOVER;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_THUMB;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_TRACK;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_MUTED;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_PRIMARY;

import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.wynnbuilder.data.WynnItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A searchable list overlay for choosing an item, ingredient, tome or recipe.
 *
 * <p>Rendered as a modal panel above the owning screen; the screen forwards input to it while
 * {@link #isOpen()}, which keeps its own hit testing separate from the screen behind it.
 */
public final class ItemPickerOverlay {
    private static final float ROW_HEIGHT = 24;
    private static final float SEARCH_HEIGHT = 26;
    private static final float PADDING = 10;
    private static final float SCROLL_STEP = 30;
    private static final int MAX_QUERY_LENGTH = 60;

    /** One selectable row. */
    public record Entry(int id, String label, String detail, WynnItem.Tier tier, int level) {
        public static Entry of(WynnItem item) {
            return new Entry(item.id(), item.displayName(), "Lv. " + item.level(), item.tier(), item.level());
        }
    }

    private boolean open;
    private String title = "";
    private String query = "";
    private List<Entry> allEntries = List.of();
    private List<Entry> visible = List.of();
    private Consumer<Entry> onSelect;
    private Runnable onClear;
    private float scroll;
    private float maxScroll;

    private float panelX;
    private float panelY;
    private float panelWidth;
    private float panelHeight;

    public boolean isOpen() {
        return open;
    }

    /**
     * Opens the picker.
     *
     * @param onClear invoked when the user chooses to empty the slot; {@code null} hides that option
     */
    public void open(String title, List<Entry> entries, Consumer<Entry> onSelect, Runnable onClear) {
        this.open = true;
        this.title = title;
        this.query = "";
        this.scroll = 0;
        this.onSelect = onSelect;
        this.onClear = onClear;
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingInt(Entry::level).reversed().thenComparing(Entry::label));
        this.allEntries = List.copyOf(sorted);
        refresh();
    }

    public void close() {
        open = false;
        onSelect = null;
        onClear = null;
    }

    private void refresh() {
        String normalised = query.trim().toLowerCase(Locale.ROOT);
        if (normalised.isEmpty()) {
            visible = allEntries;
            return;
        }
        String[] terms = normalised.split("\\s+");
        List<Entry> matches = new ArrayList<>();
        for (Entry entry : allEntries) {
            String haystack = (entry.label() + " " + entry.detail()).toLowerCase(Locale.ROOT);
            boolean all = true;
            for (String term : terms) {
                if (!haystack.contains(term)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                matches.add(entry);
            }
        }
        visible = List.copyOf(matches);
    }

    public void draw(UiCanvas canvas, float mouseX, float mouseY) {
        if (!open) {
            return;
        }
        float screenWidth = canvas.metrics().width();
        float screenHeight = canvas.metrics().height();
        canvas.fillRect(0, 0, screenWidth, screenHeight, color(BACKGROUND_MODAL_OVERLAY, 200));

        panelWidth = WynnBuilderUi.clamp(screenWidth * 0.5f, 280, 420);
        panelHeight = WynnBuilderUi.clamp(screenHeight * 0.7f, 220, 460);
        panelX = (screenWidth - panelWidth) / 2f;
        panelY = (screenHeight - panelHeight) / 2f;

        canvas.fillRoundedRect(panelX, panelY, panelWidth, panelHeight, WynnBuilderUi.PANEL_RADIUS,
                color(BACKGROUND_BODY_OPAQUE, 250));
        canvas.strokeRect(panelX, panelY, panelWidth, panelHeight, 1, color(ACCENT_DIVIDER));

        WynnBuilderUi.drawLeft(canvas, title, panelX + PADDING, panelY + 16, 13, color(ACCENT_PRIMARY));
        WynnBuilderUi.drawRight(canvas, visible.size() + " results", panelX + panelWidth - PADDING,
                panelY + 16, 10, color(TEXT_MUTED));

        // Search field
        float searchY = panelY + 30;
        canvas.fillRoundedRect(panelX + PADDING, searchY, panelWidth - PADDING * 2, SEARCH_HEIGHT, 5,
                color(CONTROL_INPUT, 235));
        String shown = query.isEmpty() ? "Search..." : query + "_";
        WynnBuilderUi.drawLeft(canvas, WynnBuilderUi.ellipsize(shown, panelWidth - PADDING * 2 - 16, 11),
                panelX + PADDING + 8, searchY + SEARCH_HEIGHT / 2f, 11,
                query.isEmpty() ? color(TEXT_MUTED) : color(TEXT_PRIMARY));

        float listTop = searchY + SEARCH_HEIGHT + 8;
        float clearHeight = onClear != null ? 26 : 0;
        float listHeight = Math.max(0, panelY + panelHeight - listTop - PADDING - clearHeight);

        maxScroll = Math.max(0, visible.size() * ROW_HEIGHT - listHeight);
        scroll = WynnBuilderUi.clamp(scroll, 0, maxScroll);

        canvas.scissor(panelX, listTop, panelWidth, listHeight);
        try {
            for (int i = 0; i < visible.size(); i++) {
                float rowY = listTop + i * ROW_HEIGHT - scroll;
                if (rowY + ROW_HEIGHT < listTop || rowY > listTop + listHeight) {
                    continue;
                }
                Entry entry = visible.get(i);
                boolean hovered = WynnBuilderUi.contains(
                        mouseX, mouseY, panelX + PADDING, rowY, panelWidth - PADDING * 2, ROW_HEIGHT);
                if (hovered) {
                    canvas.fillRoundedRect(panelX + PADDING, rowY, panelWidth - PADDING * 2, ROW_HEIGHT, 4,
                            color(CONTROL_INPUT_HOVER, 220));
                }
                WynnBuilderUi.drawLeft(canvas,
                        WynnBuilderUi.ellipsize(entry.label(), panelWidth - PADDING * 2 - 70, 11),
                        panelX + PADDING + 8, rowY + ROW_HEIGHT / 2f, 11,
                        WynnBuilderUi.rarityColor(entry.tier()));
                WynnBuilderUi.drawRight(canvas, entry.detail(), panelX + panelWidth - PADDING - 8,
                        rowY + ROW_HEIGHT / 2f, 10, color(TEXT_MUTED));
            }
        } finally {
            canvas.resetScissor();
        }

        if (maxScroll > 0) {
            float trackX = panelX + panelWidth - 6;
            canvas.fillRect(trackX, listTop, 2, listHeight, color(CONTROL_TRACK, 160));
            float thumbHeight = Math.max(20, listHeight * (listHeight / (visible.size() * ROW_HEIGHT)));
            float thumbY = listTop + (listHeight - thumbHeight) * (scroll / maxScroll);
            canvas.fillRect(trackX, thumbY, 2, thumbHeight, color(CONTROL_THUMB, 220));
        }

        if (onClear != null) {
            WynnBuilderUi.drawButton(canvas, panelX + PADDING, panelY + panelHeight - PADDING - 22,
                    panelWidth - PADDING * 2, 22, "Clear slot", mouseX, mouseY);
        }
    }

    /** @return whether the click was consumed by the overlay */
    public boolean mouseClicked(float mouseX, float mouseY) {
        if (!open) {
            return false;
        }
        if (!WynnBuilderUi.contains(mouseX, mouseY, panelX, panelY, panelWidth, panelHeight)) {
            close();
            return true;
        }
        if (onClear != null
                && WynnBuilderUi.contains(mouseX, mouseY, panelX + PADDING,
                        panelY + panelHeight - PADDING - 22, panelWidth - PADDING * 2, 22)) {
            Runnable clear = onClear;
            close();
            clear.run();
            return true;
        }

        float listTop = panelY + 30 + SEARCH_HEIGHT + 8;
        float clearHeight = onClear != null ? 26 : 0;
        float listHeight = Math.max(0, panelY + panelHeight - listTop - PADDING - clearHeight);
        if (WynnBuilderUi.contains(mouseX, mouseY, panelX + PADDING, listTop, panelWidth - PADDING * 2, listHeight)) {
            int index = (int) ((mouseY - listTop + scroll) / ROW_HEIGHT);
            if (index >= 0 && index < visible.size()) {
                Entry entry = visible.get(index);
                Consumer<Entry> select = onSelect;
                close();
                if (select != null) {
                    select.accept(entry);
                }
            }
        }
        return true;
    }

    public boolean mouseScrolled(double amount) {
        if (!open) {
            return false;
        }
        scroll = WynnBuilderUi.clamp(scroll - (float) amount * SCROLL_STEP, 0, maxScroll);
        return true;
    }

    /** @return whether the key was consumed */
    public boolean keyPressed(int keyCode) {
        if (!open) {
            return false;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
            if (!query.isEmpty()) {
                query = query.substring(0, query.length() - 1);
                scroll = 0;
                refresh();
            }
            return true;
        }
        return true;
    }

    public boolean charTyped(String typed) {
        if (!open || typed == null) {
            return false;
        }
        if (query.length() + typed.length() <= MAX_QUERY_LENGTH) {
            query += typed;
            scroll = 0;
            refresh();
        }
        return true;
    }
}
