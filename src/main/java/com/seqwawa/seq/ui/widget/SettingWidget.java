package com.seqwawa.seq.ui.widget;

import lombok.Getter;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.utils.rendering.UiCanvas;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public abstract class SettingWidget<T extends Setting<?>> {
    private static final Map<String, String> DISPLAY_NAME_OVERRIDES = new HashMap<>();
    private static final Map<String, String> TOKEN_REPLACEMENTS = new HashMap<>();

    static {
        DISPLAY_NAME_OVERRIDES.put("auto_connect", "Auto connect to Sequoia backend");
        DISPLAY_NAME_OVERRIDES.put("show_discord_bridge", "Show Discord chat");
        DISPLAY_NAME_OVERRIDES.put("show_discord_ranks", "Show Discord ranks and colors in guild chat");
        DISPLAY_NAME_OVERRIDES.put("show_chat_insignias", "Show insignias in chat");
        DISPLAY_NAME_OVERRIDES.put("color_discord_bridge", "Color Chatbridge with Discord ranks");
        DISPLAY_NAME_OVERRIDES.put("discord_chat_text_color", "Discord message text color");
        DISPLAY_NAME_OVERRIDES.put("in_game_guild_chat_text_color", "In-game guild message text color");
        DISPLAY_NAME_OVERRIDES.put("show_rank_gradients", "Use Discord gradients");
        DISPLAY_NAME_OVERRIDES.put("animate_rank_gradients", "Animate rank badges");
        DISPLAY_NAME_OVERRIDES.put("animate_username_gradients", "Animate usernames");
        DISPLAY_NAME_OVERRIDES.put("profile_on_shift_click", "Open Sequoia profile on shift-click");
        DISPLAY_NAME_OVERRIDES.put("auto_announce", "Auto announce raids");
        DISPLAY_NAME_OVERRIDES.put("track_guild_wars", "Track guild wars");
        DISPLAY_NAME_OVERRIDES.put("guild_wars", "Wars");
        DISPLAY_NAME_OVERRIDES.put("track_guild_storage", "Track guild storage");
        DISPLAY_NAME_OVERRIDES.put("receive_bomb_share_requests", "Receive bomb share requests");
        DISPLAY_NAME_OVERRIDES.put("guild_storage_emerald_threshold_percent", "Guild storage emerald threshold %");
        DISPLAY_NAME_OVERRIDES.put("guild_storage_aspect_threshold_percent", "Guild storage aspect threshold %");
        DISPLAY_NAME_OVERRIDES.put("check_updates", "Check for updates on startup");
        DISPLAY_NAME_OVERRIDES.put("enable_easter_eggs", "Enable easter eggs");
        DISPLAY_NAME_OVERRIDES.put("startup_video", "Startup video");
        DISPLAY_NAME_OVERRIDES.put("ui_size_percent", "UI size %");
        DISPLAY_NAME_OVERRIDES.put("enable_radiance_visualiser", "Enable Radiance visualiser");
        DISPLAY_NAME_OVERRIDES.put("radiance_marker_color", "Radiance marker color");
        DISPLAY_NAME_OVERRIDES.put("halcyon_ring_color", "Halcyon ring color");
        DISPLAY_NAME_OVERRIDES.put("enable_light_room_visualiser", "Light Range Visualizer in TNA");
        DISPLAY_NAME_OVERRIDES.put("light_room_ring_color", "Light Room ring color");
        DISPLAY_NAME_OVERRIDES.put("leaderboard_badges", "Badges");
        DISPLAY_NAME_OVERRIDES.put("show_raid_badges", "Show raid badges");
        DISPLAY_NAME_OVERRIDES.put("show_insignia_badges", "Show insignia badges");
        DISPLAY_NAME_OVERRIDES.put("show_own_leaderboard_badge", "Show own badges");
        DISPLAY_NAME_OVERRIDES.put("show_party_healthbars", "Show party health bars");
        DISPLAY_NAME_OVERRIDES.put("notify_tracked_world_events", "Notify tracked world events");
        DISPLAY_NAME_OVERRIDES.put("world_events", "World Events");
        DISPLAY_NAME_OVERRIDES.put("economy_resource_alerts_only", "Only filter resource alerts");

        TOKEN_REPLACEMENTS.put("discord", "Discord");
        TOKEN_REPLACEMENTS.put("bridge", "Chat");
        TOKEN_REPLACEMENTS.put("radiance", "Radiance");
        TOKEN_REPLACEMENTS.put("leaderboard", "Leaderboard");
        TOKEN_REPLACEMENTS.put("insignia", "Insignia");
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

    public abstract void render(UiCanvas canvas, float mouseX, float mouseY);

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

    public boolean charTyped(CharacterEvent characterEvent) {
        return false;
    }

    public Setting<?> getSetting() {
        return setting;
    }

    protected String getDisplayName() {
        if (setting.getDisplayName() != null) {
            return setting.getDisplayName();
        }
        return toDisplayName(setting.getName());
    }

    protected String getDescription() {
        return setting.getDescription();
    }

    protected boolean hasDescription() {
        return getDescription() != null;
    }

    protected boolean isEnabled() {
        return setting.isEnabled();
    }

    protected float labelIndent() {
        return setting.getIndentLevel() * 14f;
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
