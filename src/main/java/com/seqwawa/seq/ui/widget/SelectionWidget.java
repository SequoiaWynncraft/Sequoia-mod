package com.seqwawa.seq.ui.widget;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.ui.DropdownMenu;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import java.util.List;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

/** Shared selection control for both enum settings and dynamically loaded choices (including themes). */
public abstract class SelectionWidget<T extends Setting<?>> extends SettingWidget<T> {
    private boolean open;
    private int scroll;
    private int highlighted;
    private float viewportTop;
    private float viewportBottom = 1000;

    protected SelectionWidget(T setting) {
        super(setting);
        height = 28;
    }

    protected abstract List<String> options();
    protected abstract int selectedIndex();
    protected abstract void select(int index);

    private float buttonWidth() { return Math.min(160, Math.max(1, (width - labelIndent()) * .55f)); }
    private float buttonX() { return x + width - buttonWidth() - 8; }
    private float buttonY() { return y + (height - 18) / 2; }

    public void setViewport(float top, float bottom) {
        viewportTop = top;
        viewportBottom = bottom;
        if (buttonY() < top || buttonY() + 18 > bottom) onHidden();
    }

    public boolean isOpen() {
        if (!isEnabled() || !setting.isVisible() || options().isEmpty()) onHidden();
        return open;
    }

    private DropdownMenu.Popup popup() {
        return DropdownMenu.fit(buttonX(), buttonY(), buttonWidth(), 18, options().size(), viewportTop, viewportBottom);
    }

    @Override
    public void render(UiCanvas canvas, float mouseX, float mouseY) {
        boolean enabled = isEnabled() && !options().isEmpty();
        drawParentGuide(canvas, enabled);
        DropdownMenu.label(canvas, indentedContentX(8), y + height / 2,
                Math.max(0, buttonX() - indentedContentX(8) - 8), getDisplayName(), color(enabled ? TEXT_PRIMARY : TEXT_DISABLED), 12);
        List<String> values = options();
        int selected = selectedIndex();
        String label = selected >= 0 && selected < values.size() ? toDisplayName(values.get(selected)) : "None";
        DropdownMenu.trigger(canvas, buttonX(), buttonY(), buttonWidth(), 18, label, isOpen(), enabled, mouseX, mouseY);
    }

    /** Draw after the settings list restores its scissor, so the popup overlays subsequent rows. */
    public void renderOverlay(UiCanvas canvas, float mouseX, float mouseY) {
        if (!isOpen()) return;
        var bounds = popup();
        if (bounds.rows() == 0) return;
        scroll = DropdownMenu.clampScroll(scroll, options().size(), bounds.rows());
        DropdownMenu.list(canvas, bounds.x(), bounds.y(), bounds.width(), DropdownMenu.ROW_HEIGHT,
                options().stream().map(SettingWidget::toDisplayName).toList(), i -> i == selectedIndex(),
                scroll, bounds.rows(), mouseX, mouseY);
        if (highlighted >= scroll && highlighted < scroll + bounds.rows()) {
            // Keep keyboard focus distinct from the current selection.
            float rowY = bounds.y() + (highlighted - scroll) * DropdownMenu.ROW_HEIGHT;
            canvas.fillRect(bounds.x(), rowY + 3, 2, DropdownMenu.ROW_HEIGHT - 6, color(ACCENT_PRIMARY));
        }
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (isOpen()) {
            int index = popup().optionAt(mouseX, mouseY, scroll);
            onHidden();
            if (button == 0 && index >= 0 && index < options().size()) select(index);
            return true;
        }
        if (button != 0 || !isEnabled() || options().isEmpty()) return false;
        if (!DropdownMenu.contains(mouseX, mouseY, buttonX(), buttonY(), buttonWidth(), 18)) return false;
        open = true;
        highlighted = Math.max(0, selectedIndex());
        revealHighlighted();
        return true;
    }

    public boolean scroll(double amount) {
        if (!isOpen()) return false;
        scroll = DropdownMenu.clampScroll(scroll - (int) Math.signum(amount), options().size(), popup().rows());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!isOpen()) return false;
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) onHidden();
        else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            select(Math.min(highlighted, options().size() - 1));
            onHidden();
        } else if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) {
            highlighted = key == GLFW.GLFW_KEY_HOME ? 0 : key == GLFW.GLFW_KEY_END ? options().size() - 1
                    : Math.floorMod(highlighted + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1), options().size());
            revealHighlighted();
        }
        return true;
    }

    private void revealHighlighted() {
        int rows = popup().rows();
        if (highlighted < scroll) scroll = highlighted;
        if (highlighted >= scroll + rows) scroll = highlighted - rows + 1;
        scroll = DropdownMenu.clampScroll(scroll, options().size(), rows);
    }

    @Override
    public void onHidden() { open = false; }
}
