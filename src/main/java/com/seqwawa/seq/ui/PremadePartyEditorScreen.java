package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.PlayerHeadCache;
import com.seqwawa.seq.managers.GuildPresenceManager;
import com.seqwawa.seq.managers.RaidProfileStore;
import com.seqwawa.seq.model.KnownGuildMember;
import com.seqwawa.seq.model.PremadeParty;
import com.seqwawa.seq.utils.TextInputFilters;
import com.seqwawa.seq.utils.TextInputHelper;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiImage;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/** Builds or edits one saved party composition. */
public class PremadePartyEditorScreen extends Screen {

    private static final float PANEL_WIDTH = 420;
    private static final float PANEL_PADDING = 20;
    private static final float INPUT_HEIGHT = 22;
    private static final float MEMBER_ROW_HEIGHT = 22;
    private static final float SECTION_GAP = 14;
    private static final float FOOTER_BUTTON_H = 26;
    private static final float HEAD_SIZE = 14;

    private static final float TITLE_FONT_SIZE = 18;
    private static final float SECTION_FONT_SIZE = 10;
    private static final float BODY_FONT_SIZE = 12;
    private static final float SMALL_FONT_SIZE = 10;

    private final Screen parent;
    /** The name this party had when the editor opened, so a rename replaces it. */
    private final String previousName;

    private PremadeParty draft;
    private String nameInput;
    private String memberInput = "";
    private Field focused = Field.NAME;
    private String error;

    private float uiMouseX;
    private float uiMouseY;

    private final List<MemberHitbox> memberHitboxes = new ArrayList<>();
    private Rect nameBounds;
    private Rect memberInputBounds;
    private Rect addBounds;
    private Rect addPartyBounds;
    private Rect saveBounds;
    private Rect cancelBounds;
    private Rect deleteBounds;

    public PremadePartyEditorScreen(Screen parent, PremadeParty existing) {
        super(Component.literal("Premade party"));
        this.parent = parent;
        this.draft = existing == null ? PremadeParty.named("") : existing;
        this.previousName = existing == null ? null : existing.name();
        this.nameInput = draft.name();
    }

    private boolean isNew() {
        return previousName == null;
    }

    // ══════════════════════════════ RENDER ══════════════════════════════

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        uiMouseX = MinecraftUiRenderer.mouseX(mouseX);
        uiMouseY = MinecraftUiRenderer.mouseY(mouseY);
        memberHitboxes.clear();

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
                    isNew() ? "New premade party" : "Edit premade party",
                    UiCanvas.HorizontalAlign.LEFT);
            cursorY += 28;

            nameBounds = new Rect(contentX, cursorY, contentWidth, INPUT_HEIGHT);
            renderInput(canvas, fontName, nameBounds, nameInput, "Party name, like \"TNA core\"", focused == Field.NAME);
            cursorY += INPUT_HEIGHT + SECTION_GAP;

            drawText(
                    canvas,
                    fontName,
                    SECTION_FONT_SIZE,
                    color(ACCENT_PRIMARY),
                    contentX,
                    cursorY + 6,
                    "MEMBERS " + draft.members().size() + "/" + PremadeParty.MAX_MEMBERS,
                    UiCanvas.HorizontalAlign.LEFT);
            cursorY += 16;

            if (draft.members().isEmpty()) {
                drawText(
                        canvas,
                        fontName,
                        SMALL_FONT_SIZE,
                        color(TEXT_DISABLED),
                        contentX,
                        cursorY + MEMBER_ROW_HEIGHT / 2f,
                        "Nobody added yet.",
                        UiCanvas.HorizontalAlign.LEFT);
                cursorY += MEMBER_ROW_HEIGHT;
            } else {
                for (String member : draft.members()) {
                    renderMemberRow(canvas, fontName, contentX, cursorY, contentWidth, member);
                    cursorY += MEMBER_ROW_HEIGHT;
                }
            }
            cursorY += 6;

            float addButtonWidth = 54;
            memberInputBounds = new Rect(contentX, cursorY, contentWidth - addButtonWidth - 6, INPUT_HEIGHT);
            renderInput(
                    canvas,
                    fontName,
                    memberInputBounds,
                    memberInput,
                    draft.isFull() ? "Party is full" : "Add a username",
                    focused == Field.MEMBER);
            addBounds = new Rect(contentX + contentWidth - addButtonWidth, cursorY, addButtonWidth, INPUT_HEIGHT);
            renderButton(canvas, fontName, addBounds, "Add", !draft.isFull() && !memberInput.isBlank(), false);
            cursorY += INPUT_HEIGHT + 6;

            addPartyBounds = new Rect(contentX, cursorY, 128, INPUT_HEIGHT);
            renderButton(canvas, fontName, addPartyBounds, "Add my party", !draft.isFull(), false);
            if (error != null) {
                drawText(
                        canvas,
                        fontName,
                        SMALL_FONT_SIZE,
                        color(CONTROL_DANGER),
                        contentX + 136,
                        cursorY + INPUT_HEIGHT / 2f,
                        error,
                        UiCanvas.HorizontalAlign.LEFT);
            }
            cursorY += INPUT_HEIGHT + SECTION_GAP;

            renderFooter(canvas, fontName, contentX, cursorY, contentWidth);
        });
    }

    private float panelHeight() {
        int memberRows = Math.max(1, draft.members().size());
        return PANEL_PADDING * 2
                + 28
                + INPUT_HEIGHT
                + SECTION_GAP
                + 16
                + memberRows * MEMBER_ROW_HEIGHT
                + 6
                + INPUT_HEIGHT
                + 6
                + INPUT_HEIGHT
                + SECTION_GAP
                + FOOTER_BUTTON_H;
    }

    private void renderMemberRow(
            UiCanvas canvas, String fontName, float x, float y, float width, String member) {
        boolean hovered = uiMouseX >= x && uiMouseX <= x + width && uiMouseY >= y && uiMouseY <= y + MEMBER_ROW_HEIGHT;
        if (hovered) {
            canvas.fillRect(x - 4, y, width + 8, MEMBER_ROW_HEIGHT, color(CONTROL_INPUT));
        }

        UiImage head = PlayerHeadCache.headFor(uuidFor(member));
        float headY = y + (MEMBER_ROW_HEIGHT - HEAD_SIZE) / 2f;
        if (head != null) {
            canvas.drawImage(head, x, headY, HEAD_SIZE, HEAD_SIZE, 1f);
        } else {
            canvas.fillRect(x, headY, HEAD_SIZE, HEAD_SIZE, color(CONTROL_INPUT_SECONDARY));
        }

        drawText(
                canvas,
                fontName,
                BODY_FONT_SIZE,
                color(TEXT_PRIMARY),
                x + HEAD_SIZE + 8,
                y + MEMBER_ROW_HEIGHT / 2f,
                member,
                UiCanvas.HorizontalAlign.LEFT);

        Rect removeBounds = new Rect(x + width - 18, y + (MEMBER_ROW_HEIGHT - 16) / 2f, 16, 16);
        drawText(
                canvas,
                fontName,
                BODY_FONT_SIZE,
                removeBounds.contains(uiMouseX, uiMouseY) ? color(CONTROL_DANGER) : color(TEXT_MUTED),
                removeBounds.x() + 8,
                removeBounds.y() + 8,
                "x",
                UiCanvas.HorizontalAlign.CENTER);
        memberHitboxes.add(new MemberHitbox(removeBounds, member));
    }

    /** The roster knows UUIDs, so a head shows for anyone in the guild, online or not. */
    private static String uuidFor(String username) {
        KnownGuildMember known = GuildPresenceManager.getInstance().knownMember(username);
        return known == null ? null : known.uuid();
    }

    private void renderFooter(UiCanvas canvas, String fontName, float x, float y, float width) {
        float buttonWidth = 84;
        saveBounds = new Rect(x + width - buttonWidth, y, buttonWidth, FOOTER_BUTTON_H);
        renderButton(canvas, fontName, saveBounds, "Save", canSave(), true);

        cancelBounds = new Rect(saveBounds.x() - buttonWidth - 6, y, buttonWidth, FOOTER_BUTTON_H);
        renderButton(canvas, fontName, cancelBounds, "Cancel", true, false);

        if (!isNew()) {
            deleteBounds = new Rect(x, y, buttonWidth, FOOTER_BUTTON_H);
            boolean hovered = deleteBounds.contains(uiMouseX, uiMouseY);
            canvas.fillRect(
                    deleteBounds.x(),
                    deleteBounds.y(),
                    deleteBounds.width(),
                    deleteBounds.height(),
                    hovered ? color(CONTROL_DANGER_HOVER) : color(CONTROL_DANGER));
            drawText(
                    canvas,
                    fontName,
                    BODY_FONT_SIZE,
                    color(TEXT_PRIMARY),
                    deleteBounds.x() + deleteBounds.width() / 2f,
                    deleteBounds.y() + deleteBounds.height() / 2f,
                    "Delete",
                    UiCanvas.HorizontalAlign.CENTER);
        } else {
            deleteBounds = null;
        }
    }

    private boolean canSave() {
        return !nameInput.isBlank() && !draft.members().isEmpty();
    }

    private void renderInput(
            UiCanvas canvas, String fontName, Rect bounds, String value, String placeholder, boolean isFocused) {
        canvas.fillRect(
                bounds.x(),
                bounds.y(),
                bounds.width(),
                bounds.height(),
                isFocused ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
        if (isFocused) {
            canvas.strokeRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 1, color(ACCENT_PRIMARY));
        }
        boolean empty = value.isEmpty();
        drawText(
                canvas,
                fontName,
                SMALL_FONT_SIZE,
                empty ? color(TEXT_DISABLED) : color(TEXT_PRIMARY),
                bounds.x() + 8,
                bounds.y() + bounds.height() / 2f,
                empty ? placeholder : (isFocused ? value + "_" : value),
                UiCanvas.HorizontalAlign.LEFT);
    }

    private void renderButton(
            UiCanvas canvas, String fontName, Rect bounds, String label, boolean enabled, boolean accent) {
        boolean hovered = enabled && bounds.contains(uiMouseX, uiMouseY);
        Color background;
        if (!enabled) {
            background = color(ACCENT_DISABLED);
        } else if (accent) {
            background = hovered ? color(ACCENT_PRIMARY_HOVER) : color(ACCENT_PRIMARY);
        } else {
            background = hovered ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT);
        }
        canvas.fillRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), background);
        drawText(
                canvas,
                fontName,
                BODY_FONT_SIZE,
                enabled ? color(TEXT_PRIMARY) : color(TEXT_DISABLED),
                bounds.x() + bounds.width() / 2f,
                bounds.y() + bounds.height() / 2f,
                label,
                UiCanvas.HorizontalAlign.CENTER);
    }

    // ══════════════════════════════ INPUT ══════════════════════════════

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() != 0) {
            return super.mouseClicked(click, outsideScreen);
        }
        float mx = MinecraftUiRenderer.mouseX(click.x());
        float my = MinecraftUiRenderer.mouseY(click.y());
        error = null;

        for (MemberHitbox hitbox : memberHitboxes) {
            if (hitbox.bounds().contains(mx, my)) {
                draft = draft.withMemberToggled(hitbox.member());
                return true;
            }
        }
        if (nameBounds != null && nameBounds.contains(mx, my)) {
            focused = Field.NAME;
            return true;
        }
        if (memberInputBounds != null && memberInputBounds.contains(mx, my)) {
            focused = Field.MEMBER;
            return true;
        }
        if (addBounds != null && addBounds.contains(mx, my)) {
            commitMemberInput();
            return true;
        }
        if (addPartyBounds != null && addPartyBounds.contains(mx, my)) {
            addCurrentParty();
            return true;
        }
        if (saveBounds != null && saveBounds.contains(mx, my)) {
            save();
            return true;
        }
        if (cancelBounds != null && cancelBounds.contains(mx, my)) {
            onClose();
            return true;
        }
        if (deleteBounds != null && deleteBounds.contains(mx, my)) {
            RaidProfileStore.getInstance().deletePremade(previousName);
            onClose();
            return true;
        }
        focused = Field.NONE;
        return super.mouseClicked(click, outsideScreen);
    }

    private void commitMemberInput() {
        String candidate = memberInput.trim();
        if (candidate.isEmpty()) {
            return;
        }
        if (draft.isFull()) {
            error = "A party holds " + PremadeParty.MAX_MEMBERS + " at most.";
            return;
        }
        if (draft.contains(candidate)) {
            error = candidate + " is already in.";
            memberInput = "";
            return;
        }
        draft = draft.withMemberToggled(candidate);
        memberInput = "";
    }

    /** Pulls whoever is in the player's Wynncraft party right now. */
    private void addCurrentParty() {
        List<String> current = SeqClient.wynnPartySyncManager == null
                ? List.of()
                : SeqClient.wynnPartySyncManager.getObservedMemberUsernames();
        String localUsername = SeqClient.mc != null && SeqClient.mc.getUser() != null
                ? SeqClient.mc.getUser().getName()
                : null;

        List<String> additions = current.stream()
                .filter(name -> name != null && !name.isBlank())
                .filter(name -> localUsername == null
                        || !name.trim().toLowerCase(Locale.ROOT).equals(localUsername.toLowerCase(Locale.ROOT)))
                .toList();

        if (additions.isEmpty()) {
            error = "You are not in a party right now.";
            return;
        }
        int before = draft.members().size();
        draft = draft.withMembersAdded(additions);
        if (draft.members().size() == before) {
            error = "They are all in already.";
        }
    }

    private void save() {
        if (!canSave()) {
            error = nameInput.isBlank() ? "Give it a name first." : "Add at least one member.";
            return;
        }
        RaidProfileStore.getInstance().savePremade(draft.withName(nameInput), previousName);
        onClose();
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent keyEvent) {
        int key = keyEvent.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (focused == Field.NAME && !nameInput.isEmpty()) {
                nameInput = nameInput.substring(0, nameInput.length() - 1);
            } else if (focused == Field.MEMBER && !memberInput.isEmpty()) {
                memberInput = memberInput.substring(0, memberInput.length() - 1);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (focused == Field.MEMBER) {
                commitMemberInput();
            } else if (canSave()) {
                save();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            focused = focused == Field.NAME ? Field.MEMBER : Field.NAME;
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean charTyped(@NotNull CharacterEvent characterEvent) {
        String typed = TextInputHelper.getTypedText(characterEvent);
        if (typed == null || typed.length() != 1 || typed.charAt(0) < ' ') {
            return super.charTyped(characterEvent);
        }
        char character = typed.charAt(0);
        if (focused == Field.NAME && nameInput.length() < PremadeParty.MAX_NAME_LENGTH) {
            nameInput += character;
            return true;
        }
        // Member entries are Minecraft usernames, so only what one can contain.
        if (focused == Field.MEMBER
                && memberInput.length() < 16
                && TextInputFilters.isMinecraftUsernameCharacter(character)) {
            memberInput += character;
            return true;
        }
        return super.charTyped(characterEvent);
    }

    @Override
    public void onClose() {
        SeqClient.mc.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

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

    private enum Field {
        NONE,
        NAME,
        MEMBER
    }

    private record Rect(float x, float y, float width, float height) {
        boolean contains(float pointX, float pointY) {
            return pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height;
        }
    }

    private record MemberHitbox(Rect bounds, String member) {}
}
