package org.sequoia.seq.ui.widget;

import lombok.Getter;
import net.minecraft.client.input.KeyEvent;
import org.sequoia.seq.config.Setting;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public abstract class SettingWidget<T extends Setting<?>> {
    private static final Map<String, String> DISPLAY_NAME_OVERRIDES = new HashMap<>();
    private static final Map<String, String> TOKEN_REPLACEMENTS = new HashMap<>();

    static {
        DISPLAY_NAME_OVERRIDES.put("auto_connect", "Auto connect to Sequoia backend");
        DISPLAY_NAME_OVERRIDES.put("show_discord_bridge", "Show Discord chat");
        DISPLAY_NAME_OVERRIDES.put("auto_announce", "Auto announce raids");
        DISPLAY_NAME_OVERRIDES.put("check_updates", "Check for updates on startup");
        DISPLAY_NAME_OVERRIDES.put("enable_easter_eggs", "Enable easter eggs");

        TOKEN_REPLACEMENTS.put("discord", "Discord");
        TOKEN_REPLACEMENTS.put("bridge", "Chat");
    }

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

    protected String getDisplayName() {
        return toDisplayName(setting.getName());
    }

    public static String toDisplayName(String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            return "";
        }

        String normalized = rawName.toLowerCase(Locale.ROOT);
        String override = DISPLAY_NAME_OVERRIDES.get(normalized);
        if (override != null) {
            return override;
        }

        String[] parts = rawName.split("[_\\s]+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }

            String lower = part.toLowerCase(Locale.ROOT);
            String replacement = TOKEN_REPLACEMENTS.get(lower);
            String word = replacement != null
                    ? replacement
                    : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);

            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(word);
        }

        return out.toString();
    }

    protected boolean isHovered(float mouseX, float mouseY, float bx, float by, float bw, float bh) {
        return mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
    }
}
