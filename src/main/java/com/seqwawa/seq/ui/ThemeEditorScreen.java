package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.ThemeManager;
import com.seqwawa.seq.ui.theme.Theme;
import com.seqwawa.seq.ui.theme.UiColor;
import com.seqwawa.seq.utils.TextInputHelper;
import com.seqwawa.seq.utils.ThemeReader;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

public final class ThemeEditorScreen extends Screen {
    private static final float SIDEBAR_WIDTH = 140;
    private static final float HEADER_HEIGHT = 30;
    private static final float TOOLBAR_HEIGHT = 82;
    private static final float PADDING = 8;
    private static final float FIELD_HEIGHT = 20;
    private static final float GROUP_HEIGHT = 28;
    private static final float COLOR_ROW_HEIGHT = 40;
    private static final float EXPANDED_COLOR_ROW_HEIGHT = 176;
    private static final float PICKER_HEIGHT = 74;
    private static final float BAR_HEIGHT = 12;
    private static final float SCROLL_SPEED = 14;
    private static final float FONT_SIZE = 12;
    private static final float BUTTON_GAP = 6;
    private static final float BUTTON_WIDTH = 70;
    private static final int MAX_STATUS_TICKS = 240;

    private final Screen parent;
    private final Map<String, List<UiColor>> colorGroups = createColorGroups();
    private final Set<String> collapsedGroups = new HashSet<>();
    private List<String> sourceNames;
    private int sourceIndex;
    private String configuredThemeName;
    private String draftName;
    private final EnumMap<UiColor, Color> draftColors = new EnumMap<>(UiColor.class);
    private boolean previewActive;
    private boolean dirty;
    private boolean nameFocused;
    private UiColor editingToken;
    private UiColor expandedToken;
    private UiColor draggingSaturationValue;
    private UiColor draggingHue;
    private UiColor draggingAlpha;
    private String colorEditBuffer = "";
    private float scrollOffset;
    private float maxScroll;
    private boolean scrollbarDragging;
    private float scrollbarDragStart;
    private float scrollOffsetDragStart;
    private float nvgMouseX;
    private float nvgMouseY;
    private int cursorBlink;
    private String status = "";
    private boolean statusError;
    private int statusTicks;

    public ThemeEditorScreen(Screen parent) {
        super(Component.literal("Theme Editor"));
        this.parent = parent;
        this.configuredThemeName = SeqClient.themeSetting.getValue();
        this.sourceNames = new ArrayList<>(ThemeManager.loadedThemeNames());
        this.sourceIndex = Math.max(0, sourceNames.indexOf(configuredThemeName));
        collapsedGroups.addAll(colorGroups.keySet());
        collapsedGroups.remove("accent");
        loadSource();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        nvgMouseX = MinecraftUiRenderer.mouseX(mouseX);
        nvgMouseY = MinecraftUiRenderer.mouseY(mouseY);
        cursorBlink++;
        if (statusTicks > 0) {
            statusTicks--;
        }

        UiRenderer.renderScreen(this, canvas -> {
            float screenWidth = canvas.metrics().width();
            float screenHeight = canvas.metrics().height();
            String font = SeqClient.getFontManager().getSelectedFont();
            Layout layout = layout(screenWidth, screenHeight);

            canvas.fillRect(0, 0, screenWidth, screenHeight, color(BACKGROUND_OVERLAY));
            renderSidebar(canvas, font, screenHeight);
            canvas.fillRect(SIDEBAR_WIDTH, 0, screenWidth - SIDEBAR_WIDTH, screenHeight, color(BACKGROUND_BODY));
            canvas.fillRect(SIDEBAR_WIDTH, 0, screenWidth - SIDEBAR_WIDTH, HEADER_HEIGHT, color(BACKGROUND_HEADER));

            drawButton(canvas, font, layout.backButton(), "Back", false);
            drawText(
                    canvas,
                    font,
                    18,
                    color(ACCENT_PRIMARY),
                    UiCanvas.HorizontalAlign.RIGHT,
                    screenWidth - PADDING,
                    HEADER_HEIGHT / 2f,
                    "Theme editor");

            renderToolbar(canvas, font, layout);
            renderColorGroups(canvas, font, layout);
            renderScrollbar(canvas, layout);
        });
    }

    private void renderSidebar(UiCanvas canvas, String font, float screenHeight) {
        canvas.fillRect(0, 0, SIDEBAR_WIDTH, screenHeight, color(BACKGROUND_SIDEBAR));
        drawText(
                canvas,
                font,
                16,
                color(ACCENT_PRIMARY),
                UiCanvas.HorizontalAlign.CENTER,
                SIDEBAR_WIDTH / 2f,
                22,
                "Sequoia");
        canvas.fillRect(10, 40, SIDEBAR_WIDTH - 20, 1, color(ACCENT_DIVIDER));
        canvas.fillRect(10, 50, SIDEBAR_WIDTH - 20, 22, color(ACCENT_PRIMARY_DARK_HOVER, 120));
        drawText(
                canvas,
                font,
                FONT_SIZE,
                color(TEXT_PRIMARY),
                UiCanvas.HorizontalAlign.CENTER,
                SIDEBAR_WIDTH / 2f,
                61,
                "Theme editor");
        drawText(
                canvas,
                font,
                11,
                color(TEXT_MUTED),
                UiCanvas.HorizontalAlign.CENTER,
                SIDEBAR_WIDTH / 2f,
                90,
                "Live UI preview");
        drawText(
                canvas,
                font,
                11,
                color(TEXT_MUTED),
                UiCanvas.HorizontalAlign.CENTER,
                SIDEBAR_WIDTH / 2f,
                105,
                "RGBA controls");
    }

    private void renderToolbar(UiCanvas canvas, String font, Layout layout) {
        canvas.fillRect(
                SIDEBAR_WIDTH,
                HEADER_HEIGHT,
                layout.screenWidth() - SIDEBAR_WIDTH,
                TOOLBAR_HEIGHT,
                color(BACKGROUND_CONTENT));
        drawText(
                canvas,
                font,
                11,
                color(TEXT_MUTED),
                UiCanvas.HorizontalAlign.LEFT,
                layout.sourceField().x(),
                HEADER_HEIGHT + 10,
                "Source theme");
        drawText(
                canvas,
                font,
                11,
                color(TEXT_MUTED),
                UiCanvas.HorizontalAlign.LEFT,
                layout.nameField().x(),
                HEADER_HEIGHT + 10,
                "Personal theme name");

        drawField(canvas, font, layout.sourceField(), "<  " + sourceNameDisplay() + "  >", false, true);
        drawField(canvas, font, layout.nameField(), draftName, nameFocused, ThemeReader.isValidThemeName(draftName));
        drawButton(canvas, font, layout.previewButton(), previewActive ? "Preview on" : "Preview", previewActive);
        drawButton(canvas, font, layout.saveButton(), "Save", false);
        drawButton(canvas, font, layout.revertButton(), "Revert", false);

        String help = isEditingPersonalTheme()
                ? dirty
                        ? "Unsaved changes. Preview them live or press Save to keep them."
                        : "Editing a personal theme. Changes are only written when you press Save."
                : "Choose a bundled theme as a base, give the copy a new name, then preview and save it.";
        Color helpColor = color(TEXT_MUTED);
        if (statusTicks > 0 && !status.isBlank()) {
            help = status;
            helpColor = statusError ? color(CONTROL_DANGER_HOVER) : color(CONTROL_SUCCESS);
        } else if (!ThemeReader.isValidThemeName(draftName)) {
            help = "Use lowercase letters, numbers, underscores, or hyphens (maximum 64 characters).";
            helpColor = color(CONTROL_WARNING);
        }
        drawText(
                canvas,
                font,
                11,
                helpColor,
                UiCanvas.HorizontalAlign.LEFT,
                layout.sourceField().x(),
                HEADER_HEIGHT + 66,
                help);
    }

    private void renderColorGroups(UiCanvas canvas, String font, Layout layout) {
        canvas.save();
        canvas.scissor(
                SIDEBAR_WIDTH,
                layout.contentTop(),
                layout.screenWidth() - SIDEBAR_WIDTH,
                layout.contentHeight());

        float cursorY = layout.contentTop() - scrollOffset + PADDING;
        int rowIndex = 0;
        for (Map.Entry<String, List<UiColor>> group : colorGroups.entrySet()) {
            boolean collapsed = collapsedGroups.contains(group.getKey());
            boolean hovered = isHovered(
                    nvgMouseX,
                    nvgMouseY,
                    SIDEBAR_WIDTH,
                    cursorY,
                    layout.screenWidth() - SIDEBAR_WIDTH,
                    GROUP_HEIGHT);
            canvas.fillRect(
                    SIDEBAR_WIDTH,
                    cursorY,
                    layout.screenWidth() - SIDEBAR_WIDTH,
                    GROUP_HEIGHT,
                    hovered ? color(BACKGROUND_CONTENT_FOCUSED) : color(BACKGROUND_CONTENT));
            drawText(
                    canvas,
                    font,
                    12,
                    color(ACCENT_SECONDARY),
                    UiCanvas.HorizontalAlign.CENTER,
                    SIDEBAR_WIDTH + 20,
                    cursorY + GROUP_HEIGHT / 2f,
                    collapsed ? "+" : "-");
            drawText(
                    canvas,
                    font,
                    14,
                    color(TEXT_SECONDARY),
                    UiCanvas.HorizontalAlign.LEFT,
                    SIDEBAR_WIDTH + 34,
                    cursorY + GROUP_HEIGHT / 2f,
                    displayName(group.getKey()));
            drawText(
                    canvas,
                    font,
                    11,
                    color(TEXT_DISABLED),
                    UiCanvas.HorizontalAlign.RIGHT,
                    layout.screenWidth() - 12,
                    cursorY + GROUP_HEIGHT / 2f,
                    group.getValue().size() + " colors");
            cursorY += GROUP_HEIGHT;

            if (!collapsed) {
                for (UiColor token : group.getValue()) {
                    float rowHeight = token == expandedToken ? EXPANDED_COLOR_ROW_HEIGHT : COLOR_ROW_HEIGHT;
                    canvas.fillRect(
                            SIDEBAR_WIDTH,
                            cursorY,
                            layout.screenWidth() - SIDEBAR_WIDTH,
                            rowHeight,
                            rowIndex++ % 2 == 0
                                    ? color(BACKGROUND_BODY)
                                    : color(BACKGROUND_CONTENT_FOCUSED, 100));
                    renderColorRow(canvas, font, layout, token, cursorY);
                    cursorY += rowHeight;
                }
            }
            cursorY += 6;
        }

        maxScroll = Math.max(0, cursorY + scrollOffset - layout.contentTop() - layout.contentHeight());
        scrollOffset = clamp(scrollOffset, 0, maxScroll);
        canvas.restore();
    }

    private void renderColorRow(UiCanvas canvas, String font, Layout layout, UiColor token, float rowY) {
        Rect hex = hexField(layout, rowY);
        Rect swatch = swatch(layout, rowY);
        boolean editing = token == editingToken;
        String value = editing ? colorEditBuffer : toHex(draftColors.get(token));

        drawText(
                canvas,
                font,
                FONT_SIZE,
                color(TEXT_SECONDARY),
                UiCanvas.HorizontalAlign.LEFT,
                SIDEBAR_WIDTH + 16,
                rowY + COLOR_ROW_HEIGHT / 2f,
                displayName(token.key().substring(token.key().indexOf('.') + 1)));
        drawField(canvas, font, hex, value, editing, parseColor(value) != null);

        Color selected = draftColors.get(token);
        canvas.fillRect(swatch.x(), swatch.y(), swatch.width(), swatch.height(), color(CONTROL_INPUT_SECONDARY));
        canvas.fillRect(swatch.x() + 2, swatch.y() + 2, swatch.width() - 4, swatch.height() - 4, selected);
        canvas.strokeRect(
                swatch.x(),
                swatch.y(),
                swatch.width(),
                swatch.height(),
                1,
                token == expandedToken ? color(ACCENT_PRIMARY) : color(CONTROL_BORDER));

        if (token != expandedToken) {
            return;
        }

        PickerLayout picker = pickerLayout(layout, rowY);
        float[] hsb = hsb(token);
        Color pureHue = new Color(Color.HSBtoRGB(hsb[0], 1, 1));
        canvas.fillHorizontalGradient(
                picker.x(), picker.y(), picker.width(), PICKER_HEIGHT, Color.WHITE, pureHue);
        canvas.fillVerticalGradient(
                picker.x(),
                picker.y(),
                picker.width(),
                PICKER_HEIGHT,
                new Color(0, 0, 0, 0),
                Color.BLACK);
        canvas.strokeRect(picker.x(), picker.y(), picker.width(), PICKER_HEIGHT, 1, color(CONTROL_BORDER));

        float markerX = picker.x() + hsb[1] * picker.width();
        float markerY = picker.y() + (1 - hsb[2]) * PICKER_HEIGHT;
        canvas.strokeRect(markerX - 3, markerY - 3, 6, 6, 1, Color.BLACK);
        canvas.strokeRect(markerX - 2, markerY - 2, 4, 4, 1, Color.WHITE);

        Color[] hueStops = {
                Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED
        };
        float sectionWidth = picker.width() / (hueStops.length - 1);
        for (int index = 0; index < hueStops.length - 1; index++) {
            canvas.fillHorizontalGradient(
                    picker.x() + sectionWidth * index,
                    picker.hueY(),
                    sectionWidth,
                    BAR_HEIGHT,
                    hueStops[index],
                    hueStops[index + 1]);
        }
        canvas.strokeRect(picker.x(), picker.hueY(), picker.width(), BAR_HEIGHT, 1, color(CONTROL_BORDER));
        float hueMarkerX = picker.x() + hsb[0] * picker.width();
        canvas.strokeRect(hueMarkerX - 2, picker.hueY() - 2, 4, BAR_HEIGHT + 4, 1, Color.WHITE);

        Color opaque = new Color(selected.getRed(), selected.getGreen(), selected.getBlue());
        canvas.fillHorizontalGradient(
                picker.x(),
                picker.alphaY(),
                picker.width(),
                BAR_HEIGHT,
                new Color(opaque.getRed(), opaque.getGreen(), opaque.getBlue(), 0),
                opaque);
        canvas.strokeRect(picker.x(), picker.alphaY(), picker.width(), BAR_HEIGHT, 1, color(CONTROL_BORDER));
        float alphaMarkerX = picker.x() + selected.getAlpha() / 255f * picker.width();
        canvas.strokeRect(alphaMarkerX - 2, picker.alphaY() - 2, 4, BAR_HEIGHT + 4, 1, Color.WHITE);
        drawText(
                canvas,
                font,
                10,
                color(TEXT_MUTED),
                UiCanvas.HorizontalAlign.LEFT,
                picker.x(),
                picker.alphaY() + BAR_HEIGHT + 11,
                "Opacity: " + selected.getAlpha());
    }

    private void renderScrollbar(UiCanvas canvas, Layout layout) {
        if (maxScroll <= 0) {
            return;
        }
        float x = layout.screenWidth() - 5;
        canvas.fillRect(x, layout.contentTop(), 4, layout.contentHeight(), color(CONTROL_TRACK));
        float thumbRatio = layout.contentHeight() / (layout.contentHeight() + maxScroll);
        float thumbHeight = Math.max(20, layout.contentHeight() * thumbRatio);
        float thumbY = layout.contentTop()
                + scrollOffset / maxScroll * (layout.contentHeight() - thumbHeight);
        canvas.fillRect(x, thumbY, 4, thumbHeight, color(CONTROL_THUMB));
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() != 0) {
            return super.mouseClicked(click, outsideScreen);
        }
        float mx = MinecraftUiRenderer.mouseX(click.x());
        float my = MinecraftUiRenderer.mouseY(click.y());
        Layout layout = layout(MinecraftUiRenderer.screenWidth(), MinecraftUiRenderer.screenHeight());

        finishColorEditing();
        if (layout.backButton().contains(mx, my)) {
            onClose();
            return true;
        }
        if (layout.sourceField().contains(mx, my)) {
            cycleSource();
            return true;
        }
        if (layout.nameField().contains(mx, my)) {
            nameFocused = true;
            return true;
        }
        nameFocused = false;
        if (layout.previewButton().contains(mx, my)) {
            togglePreview();
            return true;
        }
        if (layout.saveButton().contains(mx, my)) {
            saveDraft();
            return true;
        }
        if (layout.revertButton().contains(mx, my)) {
            loadSource();
            setStatus("Reverted unsaved changes.", false);
            return true;
        }

        if (my < layout.contentTop() || my > layout.screenHeight()) {
            return super.mouseClicked(click, outsideScreen);
        }
        if (maxScroll > 0 && mx >= layout.screenWidth() - 8) {
            scrollbarDragging = true;
            scrollbarDragStart = my;
            scrollOffsetDragStart = scrollOffset;
            return true;
        }

        float cursorY = layout.contentTop() - scrollOffset + PADDING;
        for (Map.Entry<String, List<UiColor>> group : colorGroups.entrySet()) {
            if (isHovered(mx, my, SIDEBAR_WIDTH, cursorY, layout.screenWidth() - SIDEBAR_WIDTH, GROUP_HEIGHT)) {
                if (!collapsedGroups.add(group.getKey())) {
                    collapsedGroups.remove(group.getKey());
                }
                return true;
            }
            cursorY += GROUP_HEIGHT;
            if (!collapsedGroups.contains(group.getKey())) {
                for (UiColor token : group.getValue()) {
                    float rowHeight = token == expandedToken ? EXPANDED_COLOR_ROW_HEIGHT : COLOR_ROW_HEIGHT;
                    Rect hex = hexField(layout, cursorY);
                    Rect swatch = swatch(layout, cursorY);
                    if (hex.contains(mx, my)) {
                        editingToken = token;
                        colorEditBuffer = toHex(draftColors.get(token));
                        cursorBlink = 0;
                        return true;
                    }
                    if (swatch.contains(mx, my)) {
                        expandedToken = expandedToken == token ? null : token;
                        return true;
                    }
                    if (token == expandedToken) {
                        PickerLayout picker = pickerLayout(layout, cursorY);
                        if (isHovered(mx, my, picker.x(), picker.y(), picker.width(), PICKER_HEIGHT)) {
                            draggingSaturationValue = token;
                            updateSaturationValue(token, mx, my, picker);
                            return true;
                        }
                        if (isHovered(mx, my, picker.x(), picker.hueY(), picker.width(), BAR_HEIGHT)) {
                            draggingHue = token;
                            updateHue(token, mx, picker);
                            return true;
                        }
                        if (isHovered(mx, my, picker.x(), picker.alphaY(), picker.width(), BAR_HEIGHT)) {
                            draggingAlpha = token;
                            updateAlpha(token, mx, picker);
                            return true;
                        }
                    }
                    cursorY += rowHeight;
                }
            }
            cursorY += 6;
        }
        return super.mouseClicked(click, outsideScreen);
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent click) {
        scrollbarDragging = false;
        draggingSaturationValue = null;
        draggingHue = null;
        draggingAlpha = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        float mx = MinecraftUiRenderer.mouseX(click.x());
        float my = MinecraftUiRenderer.mouseY(click.y());
        Layout layout = layout(MinecraftUiRenderer.screenWidth(), MinecraftUiRenderer.screenHeight());
        if (scrollbarDragging && maxScroll > 0) {
            float thumbRatio = layout.contentHeight() / (layout.contentHeight() + maxScroll);
            float thumbHeight = Math.max(20, layout.contentHeight() * thumbRatio);
            float range = layout.contentHeight() - thumbHeight;
            scrollOffset = clamp(scrollOffsetDragStart + (my - scrollbarDragStart) / range * maxScroll, 0, maxScroll);
            return true;
        }

        UiColor token = draggingSaturationValue != null
                ? draggingSaturationValue
                : draggingHue != null ? draggingHue : draggingAlpha;
        if (token == null) {
            return super.mouseDragged(click, deltaX, deltaY);
        }
        float rowY = rowYFor(token, layout);
        PickerLayout picker = pickerLayout(layout, rowY);
        if (draggingSaturationValue != null) {
            updateSaturationValue(token, mx, my, picker);
        } else if (draggingHue != null) {
            updateHue(token, mx, picker);
        } else {
            updateAlpha(token, mx, picker);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = clamp(scrollOffset - (float) scrollY * SCROLL_SPEED, 0, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent keyEvent) {
        int key = keyEvent.key();
        boolean shortcut = (keyEvent.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        if (nameFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                nameFocused = false;
                return true;
            }
            if (shortcut && key == GLFW.GLFW_KEY_A) {
                draftName = "";
                dirty = true;
                return true;
            }
            if (shortcut && key == GLFW.GLFW_KEY_V) {
                draftName = normalizedNameClipboard(SeqClient.mc.keyboardHandler.getClipboard());
                dirty = true;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!draftName.isEmpty()) {
                    draftName = draftName.substring(0, draftName.length() - 1);
                    dirty = true;
                }
                return true;
            }
            Character character = TextInputHelper.getTypedCharacter(keyEvent);
            if (character != null && isThemeNameCharacter(character) && draftName.length() < 64) {
                draftName += Character.toLowerCase(character);
                dirty = true;
            }
            return true;
        }

        if (editingToken != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                colorEditBuffer = toHex(draftColors.get(editingToken));
                editingToken = null;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                finishColorEditing();
                return true;
            }
            if (shortcut && key == GLFW.GLFW_KEY_A) {
                colorEditBuffer = "#";
                return true;
            }
            if (shortcut && key == GLFW.GLFW_KEY_V) {
                Color pasted = parseColor(SeqClient.mc.keyboardHandler.getClipboard());
                if (pasted != null) {
                    updateColor(editingToken, pasted);
                    colorEditBuffer = toHex(pasted);
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (colorEditBuffer.length() > 1) {
                    colorEditBuffer = colorEditBuffer.substring(0, colorEditBuffer.length() - 1);
                }
                applyValidColorBuffer();
                return true;
            }
            Character character = TextInputHelper.getTypedCharacter(keyEvent);
            if (character != null
                    && Character.digit(character, 16) >= 0
                    && colorEditBuffer.length() < 9) {
                colorEditBuffer += Character.toUpperCase(character);
                applyValidColorBuffer();
            }
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public void onClose() {
        SeqClient.mc.setScreen(parent);
    }

    @Override
    public void removed() {
        if (previewActive) {
            ThemeManager.setCurrentTheme(configuredThemeName);
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void cycleSource() {
        if (sourceNames.isEmpty()) {
            return;
        }
        sourceIndex = (sourceIndex + 1) % sourceNames.size();
        loadSource();
    }

    private void loadSource() {
        String source = sourceName();
        Theme theme = ThemeManager.theme(source).orElse(Theme.defaults());
        draftColors.clear();
        draftColors.putAll(theme.colors());
        draftName = ThemeManager.isPersonalTheme(source) ? source : availableCopyName(source);
        expandedToken = null;
        editingToken = null;
        dirty = false;
        if (previewActive) {
            applyPreview();
        }
    }

    private String availableCopyName(String source) {
        String base = source + "_custom";
        if (!ThemeManager.loadedThemeNames().contains(base)) {
            return base;
        }
        for (int suffix = 2; suffix < 1000; suffix++) {
            String candidate = base + "_" + suffix;
            if (!ThemeManager.loadedThemeNames().contains(candidate)) {
                return candidate;
            }
        }
        return "personal_theme";
    }

    private void togglePreview() {
        previewActive = !previewActive;
        if (previewActive) {
            applyPreview();
            setStatus("Live preview enabled. Unsaved changes are temporary.", false);
        } else {
            ThemeManager.setCurrentTheme(configuredThemeName);
            setStatus("Live preview disabled.", false);
        }
    }

    private void applyPreview() {
        ThemeManager.previewTheme(new Theme(
                ThemeReader.isValidThemeName(draftName) ? draftName : "preview",
                draftColors));
    }

    private void saveDraft() {
        finishColorEditing();
        if (!ThemeReader.isValidThemeName(draftName)) {
            setStatus("Enter a valid personal theme name before saving.", true);
            return;
        }
        if (ThemeManager.isPersonalTheme(draftName) && !sourceName().equals(draftName)) {
            setStatus("Select '" + draftName + "' as the source before overwriting it.", true);
            return;
        }

        Theme theme = new Theme(draftName, draftColors);
        try {
            Path saved = ThemeManager.savePersonalTheme(theme);
            SeqClient.themeSetting.addOption(theme.name());
            SeqClient.themeSetting.setValue(theme.name());
            SeqClient.getConfigManager().save();
            configuredThemeName = theme.name();
            previewActive = false;
            dirty = false;
            sourceNames = new ArrayList<>(ThemeManager.loadedThemeNames());
            sourceIndex = sourceNames.indexOf(theme.name());
            setStatus("Saved " + saved.getFileName() + " and selected it.", false);
        } catch (IOException exception) {
            setStatus(exception.getMessage(), true);
        }
    }

    private void finishColorEditing() {
        if (editingToken == null) {
            return;
        }
        Color parsed = parseColor(colorEditBuffer);
        if (parsed != null) {
            updateColor(editingToken, parsed);
        }
        editingToken = null;
    }

    private void applyValidColorBuffer() {
        Color parsed = parseColor(colorEditBuffer);
        if (parsed != null) {
            updateColor(editingToken, parsed);
        }
    }

    private void updateSaturationValue(UiColor token, float mouseX, float mouseY, PickerLayout picker) {
        float[] hsb = hsb(token);
        float saturation = clamp((mouseX - picker.x()) / picker.width(), 0, 1);
        float brightness = 1 - clamp((mouseY - picker.y()) / PICKER_HEIGHT, 0, 1);
        setHsb(token, hsb[0], saturation, brightness);
    }

    private void updateHue(UiColor token, float mouseX, PickerLayout picker) {
        float[] hsb = hsb(token);
        setHsb(token, clamp((mouseX - picker.x()) / picker.width(), 0, 1), hsb[1], hsb[2]);
    }

    private void setHsb(UiColor token, float hue, float saturation, float brightness) {
        Color previous = draftColors.get(token);
        Color rgb = new Color(Color.HSBtoRGB(hue, saturation, brightness));
        updateColor(token, new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), previous.getAlpha()));
    }

    private void updateAlpha(UiColor token, float mouseX, PickerLayout picker) {
        Color previous = draftColors.get(token);
        int alpha = Math.round(clamp((mouseX - picker.x()) / picker.width(), 0, 1) * 255);
        updateColor(token, new Color(previous.getRed(), previous.getGreen(), previous.getBlue(), alpha));
    }

    private void updateColor(UiColor token, Color value) {
        draftColors.put(token, value);
        colorEditBuffer = toHex(value);
        dirty = true;
        if (previewActive) {
            applyPreview();
        }
    }

    private float[] hsb(UiColor token) {
        Color selected = draftColors.get(token);
        return Color.RGBtoHSB(selected.getRed(), selected.getGreen(), selected.getBlue(), null);
    }

    private float rowYFor(UiColor target, Layout layout) {
        float cursorY = layout.contentTop() - scrollOffset + PADDING;
        for (Map.Entry<String, List<UiColor>> group : colorGroups.entrySet()) {
            cursorY += GROUP_HEIGHT;
            if (!collapsedGroups.contains(group.getKey())) {
                for (UiColor token : group.getValue()) {
                    if (token == target) {
                        return cursorY;
                    }
                    cursorY += token == expandedToken ? EXPANDED_COLOR_ROW_HEIGHT : COLOR_ROW_HEIGHT;
                }
            }
            cursorY += 6;
        }
        return cursorY;
    }

    private String sourceName() {
        return sourceNames.isEmpty() ? "default" : sourceNames.get(sourceIndex);
    }

    private String sourceNameDisplay() {
        return displayName(sourceName());
    }

    private boolean isEditingPersonalTheme() {
        return ThemeManager.isPersonalTheme(sourceName()) && sourceName().equals(draftName);
    }

    private void setStatus(String message, boolean error) {
        status = message == null ? "Could not save theme." : message;
        statusError = error;
        statusTicks = MAX_STATUS_TICKS;
    }

    private Layout layout(float screenWidth, float screenHeight) {
        float panelWidth = screenWidth - SIDEBAR_WIDTH;
        float availableFields = Math.max(240, panelWidth - PADDING * 2 - BUTTON_WIDTH * 3 - BUTTON_GAP * 5);
        float sourceWidth = Math.min(180, availableFields * 0.45f);
        float nameWidth = Math.max(120, availableFields - sourceWidth);
        float fieldY = HEADER_HEIGHT + 27;
        Rect source = new Rect(SIDEBAR_WIDTH + PADDING, fieldY, sourceWidth, FIELD_HEIGHT);
        Rect name = new Rect(source.x() + source.width() + BUTTON_GAP, fieldY, nameWidth, FIELD_HEIGHT);
        float buttonX = name.x() + name.width() + BUTTON_GAP;
        Rect preview = new Rect(buttonX, fieldY, BUTTON_WIDTH, FIELD_HEIGHT);
        Rect save = new Rect(buttonX + BUTTON_WIDTH + BUTTON_GAP, fieldY, BUTTON_WIDTH, FIELD_HEIGHT);
        Rect revert = new Rect(buttonX + (BUTTON_WIDTH + BUTTON_GAP) * 2, fieldY, BUTTON_WIDTH, FIELD_HEIGHT);
        Rect back = new Rect(SIDEBAR_WIDTH + PADDING, 5, 58, 20);
        float contentTop = HEADER_HEIGHT + TOOLBAR_HEIGHT;
        return new Layout(
                screenWidth,
                screenHeight,
                contentTop,
                screenHeight - contentTop,
                source,
                name,
                preview,
                save,
                revert,
                back);
    }

    private Rect hexField(Layout layout, float rowY) {
        return new Rect(layout.screenWidth() - 148, rowY + 9, 100, 22);
    }

    private Rect swatch(Layout layout, float rowY) {
        return new Rect(layout.screenWidth() - 40, rowY + 9, 28, 22);
    }

    private PickerLayout pickerLayout(Layout layout, float rowY) {
        float x = SIDEBAR_WIDTH + 16;
        float width = Math.max(120, Math.min(320, layout.screenWidth() - x - 16));
        float y = rowY + 43;
        return new PickerLayout(x, y, width, y + PICKER_HEIGHT + 8, y + PICKER_HEIGHT + 28);
    }

    private void drawField(
            UiCanvas canvas, String font, Rect bounds, String value, boolean focused, boolean valid) {
        canvas.fillRect(
                bounds.x(),
                bounds.y(),
                bounds.width(),
                bounds.height(),
                focused ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
        if (focused || !valid) {
            canvas.strokeRect(
                    bounds.x(),
                    bounds.y(),
                    bounds.width(),
                    bounds.height(),
                    1,
                    valid ? color(CONTROL_BORDER) : color(CONTROL_DANGER_HOVER));
        }
        canvas.save();
        canvas.scissor(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        drawText(
                canvas,
                font,
                FONT_SIZE,
                color(TEXT_PRIMARY),
                UiCanvas.HorizontalAlign.LEFT,
                bounds.x() + 5,
                bounds.y() + bounds.height() / 2f,
                value);
        canvas.restore();
        if (focused && (cursorBlink / 1000) % 2 == 0) {
            float textWidth = UiRenderer.measureText(value, font, FONT_SIZE).width();
            canvas.fillRect(
                    Math.min(bounds.x() + bounds.width() - 3, bounds.x() + 6 + textWidth),
                    bounds.y() + 3,
                    1,
                    bounds.height() - 6,
                    color(TEXT_PRIMARY));
        }
    }

    private void drawButton(UiCanvas canvas, String font, Rect bounds, String label, boolean active) {
        boolean hovered = bounds.contains(nvgMouseX, nvgMouseY);
        canvas.fillRect(
                bounds.x(),
                bounds.y(),
                bounds.width(),
                bounds.height(),
                active
                        ? color(ACCENT_PRIMARY_DARK_HOVER)
                        : hovered ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT_SECONDARY));
        drawText(
                canvas,
                font,
                11,
                active ? color(ACCENT_PRIMARY_HOVER) : color(TEXT_PRIMARY),
                UiCanvas.HorizontalAlign.CENTER,
                bounds.x() + bounds.width() / 2f,
                bounds.y() + bounds.height() / 2f,
                label);
    }

    private static void drawText(
            UiCanvas canvas,
            String font,
            float size,
            Color textColor,
            UiCanvas.HorizontalAlign align,
            float x,
            float y,
            String text) {
        canvas.drawText(
                text,
                x,
                y,
                new UiCanvas.TextStyle(font, size, textColor, align, UiCanvas.VerticalAlign.MIDDLE));
    }

    private static Map<String, List<UiColor>> createColorGroups() {
        Map<String, List<UiColor>> groups = new LinkedHashMap<>();
        for (UiColor token : UiColor.values()) {
            String key = token.key();
            groups.computeIfAbsent(key.substring(0, key.indexOf('.')), ignored -> new ArrayList<>()).add(token);
        }
        groups.replaceAll((group, tokens) -> List.copyOf(tokens));
        return Collections.unmodifiableMap(groups);
    }

    private static String displayName(String raw) {
        String[] words = raw.split("_");
        StringBuilder display = new StringBuilder();
        for (String word : words) {
            if (!display.isEmpty()) {
                display.append(' ');
            }
            display.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return display.toString();
    }

    private static boolean isThemeNameCharacter(char character) {
        char lower = Character.toLowerCase(character);
        return lower >= 'a' && lower <= 'z'
                || lower >= '0' && lower <= '9'
                || lower == '_'
                || lower == '-';
    }

    private static String normalizedNameClipboard(String clipboard) {
        if (clipboard == null) {
            return "";
        }
        StringBuilder normalized = new StringBuilder();
        for (int index = 0; index < clipboard.length() && normalized.length() < 64; index++) {
            char character = clipboard.charAt(index);
            if (isThemeNameCharacter(character)) {
                normalized.append(Character.toLowerCase(character));
            }
        }
        return normalized.toString();
    }

    private static String toHex(Color value) {
        return String.format(
                Locale.ROOT,
                "#%02X%02X%02X%02X",
                value.getRed(),
                value.getGreen(),
                value.getBlue(),
                value.getAlpha());
    }

    private static Color parseColor(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.trim();
        if (digits.startsWith("#")) {
            digits = digits.substring(1);
        }
        if (digits.length() != 8) {
            return null;
        }
        try {
            long rgba = Long.parseLong(digits, 16);
            return new Color(
                    (int) (rgba >> 24) & 0xFF,
                    (int) (rgba >> 16) & 0xFF,
                    (int) (rgba >> 8) & 0xFF,
                    (int) rgba & 0xFF);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isHovered(float mx, float my, float x, float y, float width, float height) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record Rect(float x, float y, float width, float height) {
        private boolean contains(float mouseX, float mouseY) {
            return isHovered(mouseX, mouseY, x, y, width, height);
        }
    }

    private record PickerLayout(float x, float y, float width, float hueY, float alphaY) {}

    private record Layout(
            float screenWidth,
            float screenHeight,
            float contentTop,
            float contentHeight,
            Rect sourceField,
            Rect nameField,
            Rect previewButton,
            Rect saveButton,
            Rect revertButton,
            Rect backButton) {}
}
