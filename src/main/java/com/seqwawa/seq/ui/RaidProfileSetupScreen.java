package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.RaidProfileStore;
import com.seqwawa.seq.model.PartyRegion;
import com.seqwawa.seq.model.RaidBuild;
import com.seqwawa.seq.model.RaidCatalog;
import com.seqwawa.seq.model.RaidTeamProfile;
import com.seqwawa.seq.model.RaidType;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/**
 * Asks a member, once, which meta raid builds they own.
 * <p>
 * This is the part no API can answer: Wynncraft publishes how many raids someone
 * cleared, not what they can bring to the next one. Keeping it to a handful of
 * toggles, an aura check and a region is what gets it filled in.
 */
public class RaidProfileSetupScreen extends Screen {

    // ── Layout ──
    private static final float PANEL_WIDTH = 470;
    private static final float PANEL_PADDING = 22;
    private static final float TITLE_FONT_SIZE = 20;
    private static final float SECTION_FONT_SIZE = 11;
    private static final float BODY_FONT_SIZE = 12;
    private static final float SMALL_FONT_SIZE = 10;

    private static final float BUILD_ROW_HEIGHT = 34;
    private static final float BUILD_COLUMN_GAP = 14;
    private static final int BUILD_COLUMNS = 2;
    private static final float CHECKBOX_SIZE = 13;

    private static final float TOGGLE_ROW_HEIGHT = 26;
    private static final float REGION_BUTTON_W = 52;
    private static final float REGION_BUTTON_H = 20;
    private static final float REGION_BUTTON_GAP = 6;
    private static final float INPUT_HEIGHT = 22;
    private static final float FOOTER_BUTTON_W = 120;
    private static final float FOOTER_BUTTON_H = 26;
    private static final float SECTION_GAP = 16;

    private final Screen parent;
    /** True for the once-only first run, false when reopened from "Edit profile". */
    private final boolean firstRun;

    private RaidTeamProfile draft;
    /** Set once the form is touched, so a late fetch cannot overwrite the edits. */
    private boolean edited;
    private String statusInput;
    private boolean statusFocused;
    private String error;
    private boolean saving;

    private float uiMouseX;
    private float uiMouseY;

    private final List<BuildHitbox> buildHitboxes = new ArrayList<>();
    private final List<RegionHitbox> regionHitboxes = new ArrayList<>();
    private Rect aurasBounds;
    private Rect statusBounds;
    private Rect saveBounds;
    private Rect skipBounds;

    public RaidProfileSetupScreen(Screen parent, boolean firstRun) {
        super(Component.literal("Raid profile"));
        this.parent = parent;
        this.firstRun = firstRun;
        this.draft = RaidProfileStore.getInstance().selfProfile();
        this.statusInput = draft.hasStatus() ? draft.status() : "";
    }

    private static RaidProfileStore profiles() {
        return RaidProfileStore.getInstance();
    }

    private static RaidCatalog catalog() {
        return profiles().catalog();
    }

    @Override
    protected void init() {
        super.init();
        // The form needs the meta list, and the draft starts from the stored profile.
        if (catalog().isEmpty() || !profiles().hasLoadedProfiles()) {
            profiles().refresh();
        }
    }

    /**
     * Adopts the stored profile when it lands after the screen opened, unless the form
     * has been edited. Without this, saving would overwrite it with the blank form.
     */
    private void syncDraftWithStore() {
        if (edited || saving) {
            return;
        }
        RaidTeamProfile stored = profiles().selfProfile();
        if (stored.updatedAtEpochMs() != draft.updatedAtEpochMs()) {
            draft = stored;
            statusInput = stored.hasStatus() ? stored.status() : "";
        }
    }

    // ══════════════════════════════ RENDER ══════════════════════════════

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        uiMouseX = MinecraftUiRenderer.mouseX(mouseX);
        uiMouseY = MinecraftUiRenderer.mouseY(mouseY);
        syncDraftWithStore();
        buildHitboxes.clear();
        regionHitboxes.clear();

        UiRenderer.renderScreen(this, canvas -> {
            float screenWidth = canvas.metrics().width();
            float screenHeight = canvas.metrics().height();
            String fontName = SeqClient.getFontManager().getSelectedFont();

            canvas.fillRect(0, 0, screenWidth, screenHeight, color(BACKGROUND_MODAL_OVERLAY));

            float panelWidth = Math.min(PANEL_WIDTH, screenWidth - 24);
            float panelHeight = panelHeight();
            float panelX = (screenWidth - panelWidth) / 2f;
            float panelY = Math.max(12, (screenHeight - panelHeight) / 2f);

            canvas.fillRect(panelX, panelY, panelWidth, panelHeight, color(BACKGROUND_POPUP));
            canvas.fillRect(panelX, panelY, 3, panelHeight, color(ACCENT_PRIMARY));

            float contentX = panelX + PANEL_PADDING;
            float contentWidth = panelWidth - PANEL_PADDING * 2;
            float cursorY = panelY + PANEL_PADDING;

            drawText(
                    canvas,
                    fontName,
                    TITLE_FONT_SIZE,
                    color(TEXT_PRIMARY),
                    contentX,
                    cursorY + 8,
                    firstRun ? "Set up your raid profile" : "Edit your raid profile",
                    UiCanvas.HorizontalAlign.LEFT);
            cursorY += 26;

            drawText(
                    canvas,
                    fontName,
                    SMALL_FONT_SIZE,
                    color(TEXT_MUTED),
                    contentX,
                    cursorY + 6,
                    "Tick the meta builds you own. Members forming a raid see this instead of asking in chat.",
                    UiCanvas.HorizontalAlign.LEFT);
            cursorY += SECTION_GAP + 6;

            cursorY = renderSectionLabel(canvas, fontName, contentX, cursorY, "Builds you own");
            cursorY = renderBuildGrid(canvas, fontName, contentX, cursorY, contentWidth);
            cursorY += SECTION_GAP;

            cursorY = renderSectionLabel(canvas, fontName, contentX, cursorY, "Extras");
            cursorY = renderAurasToggle(canvas, fontName, contentX, cursorY, contentWidth);
            cursorY = renderRegionRow(canvas, fontName, contentX, cursorY);
            cursorY = renderStatusInput(canvas, fontName, contentX, cursorY, contentWidth);
            cursorY += SECTION_GAP;

            renderFooter(canvas, fontName, contentX, cursorY, contentWidth);
        });
    }

    /** Height derived from the constants the sections lay themselves out with. */
    private float panelHeight() {
        int buildCount = Math.max(1, catalog().builds().size());
        int buildRows = (buildCount + BUILD_COLUMNS - 1) / BUILD_COLUMNS;
        return PANEL_PADDING * 2
                + 26
                + SECTION_GAP
                + 6
                + 18
                + buildRows * BUILD_ROW_HEIGHT
                + SECTION_GAP
                + 18
                + TOGGLE_ROW_HEIGHT
                + TOGGLE_ROW_HEIGHT
                + INPUT_HEIGHT
                + 16
                + SECTION_GAP
                + FOOTER_BUTTON_H;
    }

    private float renderSectionLabel(UiCanvas canvas, String fontName, float x, float y, String label) {
        drawText(
                canvas,
                fontName,
                SECTION_FONT_SIZE,
                color(ACCENT_PRIMARY),
                x,
                y + 7,
                label.toUpperCase(java.util.Locale.ROOT),
                UiCanvas.HorizontalAlign.LEFT);
        return y + 18;
    }

    private float renderBuildGrid(UiCanvas canvas, String fontName, float x, float y, float width) {
        float columnWidth = (width - BUILD_COLUMN_GAP * (BUILD_COLUMNS - 1)) / BUILD_COLUMNS;
        RaidCatalog catalog = catalog();
        List<RaidBuild> builds = catalog.builds();

        if (builds.isEmpty()) {
            drawText(
                    canvas,
                    fontName,
                    SMALL_FONT_SIZE,
                    color(TEXT_DISABLED),
                    x,
                    y + BUILD_ROW_HEIGHT / 2f,
                    profiles().isFetching() ? "Loading the guild's meta builds" : metaUnavailableMessage(),
                    UiCanvas.HorizontalAlign.LEFT);
            return y + BUILD_ROW_HEIGHT;
        }

        for (int i = 0; i < builds.size(); i++) {
            RaidBuild build = builds.get(i);
            int column = i % BUILD_COLUMNS;
            int row = i / BUILD_COLUMNS;
            float cellX = x + column * (columnWidth + BUILD_COLUMN_GAP);
            float cellY = y + row * BUILD_ROW_HEIGHT;

            Rect bounds = new Rect(cellX, cellY, columnWidth, BUILD_ROW_HEIGHT - 4);
            boolean checked = draft.hasBuild(build.key());
            boolean hovered = bounds.contains(uiMouseX, uiMouseY);

            if (hovered) {
                canvas.fillRect(
                        bounds.x() - 4, bounds.y(), bounds.width() + 8, bounds.height(), color(CONTROL_INPUT));
            }

            float checkboxY = cellY + (BUILD_ROW_HEIGHT - 4 - CHECKBOX_SIZE) / 2f;
            renderCheckbox(canvas, cellX, checkboxY, checked);

            float textX = cellX + CHECKBOX_SIZE + 8;
            drawText(
                    canvas,
                    fontName,
                    BODY_FONT_SIZE,
                    checked ? color(TEXT_PRIMARY) : color(TEXT_SECONDARY),
                    textX,
                    cellY + 11,
                    build.displayName(),
                    UiCanvas.HorizontalAlign.LEFT);
            drawText(
                    canvas,
                    fontName,
                    SMALL_FONT_SIZE,
                    checked ? color(ACCENT_PRIMARY) : color(TEXT_MUTED),
                    textX,
                    cellY + 23,
                    catalog.raidCoverageLabel(build.key()),
                    UiCanvas.HorizontalAlign.LEFT);

            buildHitboxes.add(new BuildHitbox(bounds, build.key()));
        }

        int rows = (builds.size() + BUILD_COLUMNS - 1) / BUILD_COLUMNS;
        return y + rows * BUILD_ROW_HEIGHT;
    }

    private void renderCheckbox(UiCanvas canvas, float x, float y, boolean checked) {
        canvas.fillRect(
                x, y, CHECKBOX_SIZE, CHECKBOX_SIZE, checked ? color(ACCENT_PRIMARY) : color(CONTROL_INPUT));
        canvas.strokeRect(
                x, y, CHECKBOX_SIZE, CHECKBOX_SIZE, 1, checked ? color(ACCENT_PRIMARY_HOVER) : color(CONTROL_BORDER));
        if (checked) {
            // Two strokes read at this size where a glyph would not.
            float inset = 3f;
            canvas.strokeLine(
                    x + inset,
                    y + CHECKBOX_SIZE / 2f,
                    x + CHECKBOX_SIZE / 2f - 0.5f,
                    y + CHECKBOX_SIZE - inset,
                    1.6f,
                    color(TEXT_PRIMARY));
            canvas.strokeLine(
                    x + CHECKBOX_SIZE / 2f - 0.5f,
                    y + CHECKBOX_SIZE - inset,
                    x + CHECKBOX_SIZE - inset,
                    y + inset,
                    1.6f,
                    color(TEXT_PRIMARY));
        }
    }

    private float renderAurasToggle(UiCanvas canvas, String fontName, float x, float y, float width) {
        aurasBounds = new Rect(x, y, width, TOGGLE_ROW_HEIGHT - 4);
        boolean hovered = aurasBounds.contains(uiMouseX, uiMouseY);
        if (hovered) {
            canvas.fillRect(
                    x - 4, y, width + 8, TOGGLE_ROW_HEIGHT - 4, color(CONTROL_INPUT));
        }
        float checkboxY = y + (TOGGLE_ROW_HEIGHT - 4 - CHECKBOX_SIZE) / 2f;
        renderCheckbox(canvas, x, checkboxY, draft.canBringAuras());
        drawText(
                canvas,
                fontName,
                BODY_FONT_SIZE,
                draft.canBringAuras() ? color(TEXT_PRIMARY) : color(TEXT_SECONDARY),
                x + CHECKBOX_SIZE + 8,
                y + (TOGGLE_ROW_HEIGHT - 4) / 2f,
                "I can bring auras",
                UiCanvas.HorizontalAlign.LEFT);
        return y + TOGGLE_ROW_HEIGHT;
    }

    private float renderRegionRow(UiCanvas canvas, String fontName, float x, float y) {
        drawText(
                canvas,
                fontName,
                BODY_FONT_SIZE,
                color(TEXT_SECONDARY),
                x,
                y + (TOGGLE_ROW_HEIGHT - 4) / 2f,
                "Region",
                UiCanvas.HorizontalAlign.LEFT);

        float cursorX = x + 70;
        float buttonY = y + (TOGGLE_ROW_HEIGHT - 4 - REGION_BUTTON_H) / 2f;
        for (PartyRegion region : PartyRegion.values()) {
            Rect bounds = new Rect(cursorX, buttonY, REGION_BUTTON_W, REGION_BUTTON_H);
            boolean selected = draft.region() == region;
            boolean hovered = bounds.contains(uiMouseX, uiMouseY);
            canvas.fillRect(
                    bounds.x(),
                    bounds.y(),
                    bounds.width(),
                    bounds.height(),
                    selected ? color(ACCENT_PRIMARY_DARK) : hovered ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
            drawText(
                    canvas,
                    fontName,
                    SMALL_FONT_SIZE,
                    selected ? color(TEXT_PRIMARY) : color(TEXT_SECONDARY),
                    bounds.x() + bounds.width() / 2f,
                    bounds.y() + bounds.height() / 2f,
                    region.name(),
                    UiCanvas.HorizontalAlign.CENTER);
            regionHitboxes.add(new RegionHitbox(bounds, region));
            cursorX += REGION_BUTTON_W + REGION_BUTTON_GAP;
        }
        return y + TOGGLE_ROW_HEIGHT;
    }

    private float renderStatusInput(UiCanvas canvas, String fontName, float x, float y, float width) {
        statusBounds = new Rect(x, y, width, INPUT_HEIGHT);
        canvas.fillRect(
                x, y, width, INPUT_HEIGHT, statusFocused ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
        if (statusFocused) {
            canvas.strokeRect(x, y, width, INPUT_HEIGHT, 1, color(ACCENT_PRIMARY));
        }

        boolean empty = statusInput.isEmpty();
        String shown = empty ? "Status, like \"down for tna all evening\"" : statusInput;
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                empty ? color(TEXT_DISABLED) : color(TEXT_PRIMARY),
                x + 8,
                y + INPUT_HEIGHT / 2f,
                statusFocused && !empty ? shown + "_" : shown,
                UiCanvas.HorizontalAlign.LEFT);
        return y + INPUT_HEIGHT + 16;
    }

    private void renderFooter(UiCanvas canvas, String fontName, float x, float y, float width) {
        List<RaidType> covered = draft.coveredRaids(catalog());
        String summary = draft.buildKeys().isEmpty()
                ? "No builds ticked yet"
                : draft.buildKeys().size() + " builds, covers " + coverageLabel(covered, catalog());
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                color(TEXT_MUTED),
                x,
                y + FOOTER_BUTTON_H / 2f,
                summary,
                UiCanvas.HorizontalAlign.LEFT);

        if (error != null && !error.isBlank()) {
            drawText(
                    canvas,
                    fontName,
                    SMALL_FONT_SIZE,
                    color(CONTROL_DANGER),
                    x,
                    y + FOOTER_BUTTON_H / 2f + 12,
                    error,
                    UiCanvas.HorizontalAlign.LEFT);
        }

        saveBounds = new Rect(x + width - FOOTER_BUTTON_W, y, FOOTER_BUTTON_W, FOOTER_BUTTON_H);
        boolean saveHovered = saveBounds.contains(uiMouseX, uiMouseY);
        canvas.fillRect(
                saveBounds.x(),
                saveBounds.y(),
                saveBounds.width(),
                saveBounds.height(),
                saving
                        ? color(ACCENT_DISABLED)
                        : saveHovered ? color(ACCENT_PRIMARY_HOVER) : color(ACCENT_PRIMARY));
        drawText(
                canvas,
                fontName,
                BODY_FONT_SIZE,
                color(TEXT_PRIMARY),
                saveBounds.x() + saveBounds.width() / 2f,
                saveBounds.y() + saveBounds.height() / 2f,
                saving ? "Saving" : "Save profile",
                UiCanvas.HorizontalAlign.CENTER);

        float skipWidth = 84;
        skipBounds = new Rect(saveBounds.x() - skipWidth - 8, y, skipWidth, FOOTER_BUTTON_H);
        boolean skipHovered = skipBounds.contains(uiMouseX, uiMouseY);
        canvas.fillRect(
                skipBounds.x(),
                skipBounds.y(),
                skipBounds.width(),
                skipBounds.height(),
                skipHovered ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
        drawText(
                canvas,
                fontName,
                BODY_FONT_SIZE,
                color(TEXT_SECONDARY),
                skipBounds.x() + skipBounds.width() / 2f,
                skipBounds.y() + skipBounds.height() / 2f,
                firstRun ? "Not now" : "Cancel",
                UiCanvas.HorizontalAlign.CENTER);
    }

    /** The raids a build selection covers, or a plain word when it covers none. */
    static String coverageLabel(List<RaidType> covered, RaidCatalog catalog) {
        if (covered == null || covered.isEmpty()) {
            return "no raid";
        }
        if (catalog != null && !catalog.raids().isEmpty() && covered.size() == catalog.raids().size()) {
            return "every raid";
        }
        StringBuilder label = new StringBuilder();
        for (RaidType raid : covered) {
            if (!label.isEmpty()) {
                label.append(" / ");
            }
            label.append(raid.shortName());
        }
        return label.toString();
    }

    /** Why the build list is empty, which is almost always the connection. */
    private String metaUnavailableMessage() {
        String failure = profiles().lastError();
        return failure == null || failure.isBlank()
                ? "The guild's meta builds have not loaded yet."
                : failure;
    }

    // ══════════════════════════════ INPUT ══════════════════════════════

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() != 0) {
            return super.mouseClicked(click, outsideScreen);
        }
        float mx = MinecraftUiRenderer.mouseX(click.x());
        float my = MinecraftUiRenderer.mouseY(click.y());

        for (BuildHitbox hitbox : buildHitboxes) {
            if (hitbox.bounds().contains(mx, my)) {
                draft = draft.withBuildToggled(hitbox.buildKey());
                edited = true;
                statusFocused = false;
                return true;
            }
        }
        for (RegionHitbox hitbox : regionHitboxes) {
            if (hitbox.bounds().contains(mx, my)) {
                // Clicking the selected region clears it, so "no region" stays reachable.
                draft = draft.withRegion(draft.region() == hitbox.region() ? null : hitbox.region());
                edited = true;
                statusFocused = false;
                return true;
            }
        }
        if (aurasBounds != null && aurasBounds.contains(mx, my)) {
            draft = draft.withCanBringAuras(!draft.canBringAuras());
            edited = true;
            statusFocused = false;
            return true;
        }
        if (statusBounds != null && statusBounds.contains(mx, my)) {
            statusFocused = true;
            return true;
        }
        if (saveBounds != null && saveBounds.contains(mx, my)) {
            save();
            return true;
        }
        if (skipBounds != null && skipBounds.contains(mx, my)) {
            skip();
            return true;
        }

        statusFocused = false;
        return super.mouseClicked(click, outsideScreen);
    }

    /** Sends the profile and only leaves once it landed, so a failed save is not silent. */
    private void save() {
        if (saving) {
            return;
        }
        if (!profiles().hasLoadedProfiles()) {
            // Before the fetch lands the form may still be the blank one shown on open,
            // and saving that would overwrite the stored profile. The edited flag only
            // covers a form that was touched, so Save needs this guard too.
            profiles().refresh();
            String failure = profiles().lastError();
            error = failure == null || failure.isBlank()
                    ? "Your profile is still loading. Try again in a moment."
                    : failure;
            return;
        }
        saving = true;
        error = null;
        profiles().saveSelfProfile(draft.withStatus(statusInput)).thenAccept(saved -> SeqClient.mc.execute(() -> {
            saving = false;
            if (SeqClient.mc.screen != this) {
                // The player already left; do not pull them back into the panel.
                return;
            }
            if (saved) {
                SeqClient.mc.setScreen(new GuildMembersScreen(parent));
            } else {
                error = profiles().lastError();
            }
        }));
    }

    private void skip() {
        if (firstRun) {
            // Only a first run records a decision; cancelling an edit does not.
            RaidProfileStore.getInstance().dismissSetup();
        }
        SeqClient.mc.setScreen(new GuildMembersScreen(parent));
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent keyEvent) {
        int key = keyEvent.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            skip();
            return true;
        }
        if (statusFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!statusInput.isEmpty()) {
                    statusInput = statusInput.substring(0, statusInput.length() - 1);
                    edited = true;
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                statusFocused = false;
                return true;
            }
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean charTyped(@NotNull CharacterEvent characterEvent) {
        if (!statusFocused) {
            return super.charTyped(characterEvent);
        }
        String typed = com.seqwawa.seq.utils.TextInputHelper.getTypedText(characterEvent);
        if (typed != null
                && typed.length() == 1
                && statusInput.length() < RaidTeamProfile.MAX_STATUS_LENGTH
                && typed.charAt(0) >= ' ') {
            statusInput += typed;
            edited = true;
        }
        return true;
    }

    @Override
    public void onClose() {
        skip();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ══════════════════════════════ HELPERS ══════════════════════════════

    private static void drawText(
            UiCanvas canvas,
            String fontName,
            float fontSize,
            Color textColor,
            float x,
            float y,
            String text,
            UiCanvas.HorizontalAlign horizontalAlign) {
        canvas.drawText(
                text,
                x,
                y,
                new UiCanvas.TextStyle(
                        fontName, fontSize, textColor, horizontalAlign, UiCanvas.VerticalAlign.MIDDLE));
    }

    private record Rect(float x, float y, float width, float height) {
        boolean contains(float pointX, float pointY) {
            return pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height;
        }
    }

    private record BuildHitbox(Rect bounds, String buildKey) {}

    private record RegionHitbox(Rect bounds, PartyRegion region) {}
}
