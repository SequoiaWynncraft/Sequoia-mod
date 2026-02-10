package org.sequoia.seq.ui.widget;

import lombok.Getter;
import net.minecraft.client.input.KeyEvent;
import org.sequoia.seq.config.Setting;

public abstract class SettingWidget<T extends Setting<?>> {
    protected final T setting;
    protected float x;
    protected float y;
    protected float width;
    @Getter
    protected float height;

    protected SettingWidget(T setting) {
        this.setting = setting;
    }

    public void setPosition(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public abstract void render(long nvg, float mouseX, float mouseY);

    public abstract boolean mouseClicked(float mouseX, float mouseY, int button);

    public boolean mouseReleased(float mouseX, float mouseY, int button) {
        return false;
    }

    public boolean mouseDragged(float mouseX, float mouseY) {
        return false;
    }

    public boolean keyPressed(KeyEvent keyEvent) {
        return false;
    }

    public Setting<?> getSetting() {
        return setting;
    }

    protected boolean isHovered(float mouseX, float mouseY, float bx, float by, float bw, float bh) {
        return mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
    }
}
