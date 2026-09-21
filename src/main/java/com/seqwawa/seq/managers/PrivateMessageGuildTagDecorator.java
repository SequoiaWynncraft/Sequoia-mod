package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.network.WynncraftServerPolicy;
import com.seqwawa.seq.utils.ChatIdentityResolver;
import com.seqwawa.seq.utils.ComponentTextEditor;
import com.wynntils.core.components.Models;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** Adds the remote participant's guild prefix to private messages at display time. */
public final class PrivateMessageGuildTagDecorator {
    private static final Pattern FORMATTING = Pattern.compile("§(?:#[0-9a-fA-F]{8}|[0-9a-fA-Fk-oK-OrR])");
    private static final Pattern TIMESTAMP = Pattern.compile("\\s*(?:\\[\\d{1,2}:\\d{2}(?::\\d{2})?\\]\\s*)?");
    private static final Pattern HEADER = Pattern.compile("\\s*(.+?)\\s+\uE003\\s+(.+?):\\s");
    private static final Pattern REVEALED_NAME = Pattern.compile(".*\\(([a-zA-Z0-9_]{3,16})\\)");
    private static final Pattern GUILD_TAG = Pattern.compile("[A-Za-z0-9]{1,5}");
    private static final Map<String, CachedTag> TAGS = new LinkedHashMap<>();
    private static final long CACHE_MILLIS = TimeUnit.MINUTES.toMillis(5);

    record CachedTag(long fetchedAt, CompletableFuture<String> value, Set<Runnable> waitingViews) {
        CachedTag(long fetchedAt, CompletableFuture<String> value) {
            this(fetchedAt, value, new LinkedHashSet<>());
        }

        String read(Runnable refreshChat) {
            if (!value.isDone()) waitingViews.add(refreshChat);
            return value.getNow("");
        }

        void refreshWaitingViews() {
            List<Runnable> refreshes = List.copyOf(waitingViews);
            waitingViews.clear();
            refreshes.forEach(Runnable::run);
        }
    }

    private PrivateMessageGuildTagDecorator() {}

    public static Component decorate(Component message, Runnable refreshChat) {
        if (SeqClient.getShowPrivateMessageGuildTagsSetting() != null
                && !SeqClient.getShowPrivateMessageGuildTagsSetting().getValue()) {
            return message;
        }
        Minecraft client = Minecraft.getInstance();
        if (message == null || client.player == null || !WynncraftServerPolicy.isCurrentServerAllowed()) {
            return message;
        }
        return decorate(message, client.getUser().getName(), username -> cachedTag(username, refreshChat));
    }

    /** Lookups never hold up chat. Rewrapping updates the same line, preserving its position and age. */
    private static String cachedTag(String username, Runnable refreshChat) {
        String key = username.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        CachedTag cached = TAGS.get(key);
        if (cached == null || now - cached.fetchedAt() >= CACHE_MILLIS) {
            CompletableFuture<String> value = Models.Player.getPlayer(username)
                    .thenApply(player -> player == null ? "" : player.guildInfo()
                            .map(guild -> guild.guildPrefix()).orElse(""))
                    .completeOnTimeout("", 5, TimeUnit.SECONDS)
                    .exceptionally(error -> "");
            CachedTag pending = new CachedTag(now, value);
            TAGS.put(key, pending);
            if (TAGS.size() > 128) {
                TAGS.remove(TAGS.keySet().iterator().next());
            }
            Minecraft client = Minecraft.getInstance();
            var connection = client.getConnection();
            value.thenAccept(tag -> {
                if (!tag.isBlank()) {
                    // Always queue: an already cached Wynntils response may complete inside splitLines.
                    client.schedule(() -> {
                        if (client.getConnection() == connection) pending.refreshWaitingViews();
                        else pending.waitingViews().clear();
                    });
                } else {
                    client.schedule(() -> pending.waitingViews().clear());
                }
            });
            cached = TAGS.get(key);
        }
        return cached.read(refreshChat);
    }

    static Component decorate(Component message, String localUsername, Function<String, String> guildLookup) {
        List<ComponentTextEditor.Fragment> fragments = ComponentTextEditor.flatten(message);
        String text = ComponentTextEditor.textOf(fragments);
        int marker = text.indexOf('\uE007');
        if (marker < 0) {
            marker = text.indexOf('\uE001');
            if (marker < 0 || !hasPrivateColor(fragments, marker)) return message;
        }
        String masked = maskDecorations(text);
        if (!TIMESTAMP.matcher(masked.substring(0, marker)).matches()) return message;
        Matcher header = HEADER.matcher(masked);
        header.region(marker + 1, masked.length());
        if (!header.lookingAt()) return message;

        String from = username(fragments, header.start(1), header.end(1));
        String to = username(fragments, header.start(2), header.end(2));
        boolean fromLocal = isLocal(from, localUsername);
        boolean toLocal = isLocal(to, localUsername);
        if (fromLocal == toLocal) return message;
        int group = fromLocal ? 2 : 1;
        String other = fromLocal ? to : from;
        if (!ChatIdentityResolver.isValidUsername(other)) return message;
        String tag = guildLookup.apply(other);
        if (tag == null || !GUILD_TAG.matcher(tag).matches()) return message;
        Component prefix = Component.literal("[" + tag + "] ")
                .withStyle(styleAt(fragments, header.start(group)));
        return ComponentTextEditor.toComponent(ComponentTextEditor.insertAt(fragments, header.start(group), prefix));
    }

    private static boolean isLocal(String username, String localUsername) {
        return username != null && (username.equalsIgnoreCase(localUsername) || username.equalsIgnoreCase("You"));
    }

    private static String username(List<ComponentTextEditor.Fragment> fragments, int start, int end) {
        var name = Component.empty();
        int offset = 0;
        for (var fragment : fragments) {
            int next = offset + fragment.text().length();
            if (next > start && offset < end) {
                name.append(Component.literal(fragment.text().substring(Math.max(0, start - offset),
                        Math.min(fragment.text().length(), end - offset))).withStyle(fragment.style()));
            }
            offset = next;
        }
        String visible = maskDecorations(name.getString()).trim();
        if (visible.startsWith("[")) return null; // Already tagged by another decorator.
        String canonical = ChatIdentityResolver.resolveCanonicalUsername(name, visible);
        if (canonical != null) return canonical;
        Matcher revealed = REVEALED_NAME.matcher(visible);
        return revealed.matches() ? revealed.group(1) : null;
    }

    private static Style styleAt(List<ComponentTextEditor.Fragment> fragments, int index) {
        int offset = 0;
        for (var fragment : fragments) {
            offset += fragment.text().length();
            if (index < offset) return fragment.style();
        }
        return Style.EMPTY;
    }

    private static boolean hasPrivateColor(List<ComponentTextEditor.Fragment> fragments, int marker) {
        var color = styleAt(fragments, marker).getColor();
        return color != null && color.getValue() == 0xDDCC99;
    }

    /** Keep UTF-16 offsets while ignoring rank glyphs, padding and embedded colour codes. */
    private static String maskDecorations(String text) {
        char[] masked = text.toCharArray();
        Matcher formatting = FORMATTING.matcher(text);
        while (formatting.find()) {
            java.util.Arrays.fill(masked, formatting.start(), formatting.end(), ' ');
        }
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int width = Character.charCount(codePoint);
            int type = Character.getType(codePoint);
            if (codePoint != '\uE003' && (type == Character.PRIVATE_USE
                    || type == Character.UNASSIGNED || type == Character.FORMAT)) {
                java.util.Arrays.fill(masked, i, i + width, ' ');
            }
            i += width;
        }
        return new String(masked);
    }
}
