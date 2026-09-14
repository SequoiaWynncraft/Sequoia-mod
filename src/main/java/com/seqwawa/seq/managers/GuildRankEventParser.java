package com.seqwawa.seq.managers;

import com.seqwawa.seq.utils.ChatIdentityResolver;
import com.seqwawa.seq.utils.PacketTextNormalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

/** Parses successful rank assignments without mistaking a nickname for an account name. */
public final class GuildRankEventParser {
    private static final String RANK = "(Recruit|Recruiter|Captain|Strategist|Chief|Owner)";
    private static final Pattern MESSAGE = Pattern.compile(
            "^(?:\\[guild] )?([A-Za-z0-9_][A-Za-z0-9_ ]{0,63}?) has set "
                    + "([A-Za-z0-9_][A-Za-z0-9_ ]{0,63}?) guild rank from " + RANK + " to " + RANK + "[.!]?$",
            Pattern.CASE_INSENSITIVE);

    private GuildRankEventParser() {}

    public static Event parse(Component message, String localUsername) {
        if (message == null || ChatManager.parseGuildMessage(message) != null) return null;
        String text = PacketTextNormalizer.normalizeForParsing(message.getString());
        var matcher = MESSAGE.matcher(text);
        if (!matcher.matches()) return null;
        return new Event(identity(message, matcher.group(1), localUsername),
                identity(message, matcher.group(2), localUsername), rank(matcher.group(3)), rank(matcher.group(4)), text);
    }

    private static Identity identity(Component message, String display, String localUsername) {
        if ("you".equalsIgnoreCase(display) && ChatIdentityResolver.isValidUsername(localUsername)) {
            return new Identity(display, localUsername, "local_player");
        }
        Set<String> candidates = new LinkedHashSet<>();
        for (Component fragment : message.toFlatList()) {
            if (!display.equalsIgnoreCase(PacketTextNormalizer.normalizeForParsing(fragment.getString()))) continue;
            String hover = ChatManager.extractHoverRealUsername(fragment.getStyle());
            String insertion = ChatManager.extractInsertionUsername(fragment.getStyle());
            if (ChatIdentityResolver.isValidUsername(hover)) candidates.add(hover.toLowerCase(Locale.ROOT));
            if (ChatIdentityResolver.isValidUsername(insertion)) candidates.add(insertion.toLowerCase(Locale.ROOT));
        }
        if (candidates.size() == 1) return new Identity(display, candidates.iterator().next(), "component");
        // Never use username syntax or a nickname prefix match as proof. Conflicting metadata stays unresolved.
        return new Identity(display, null, "unresolved");
    }

    private static String rank(String value) {
        return value.substring(0, 1).toUpperCase(Locale.ROOT)
                + value.substring(1).toLowerCase(Locale.ROOT);
    }

    public record Identity(String displayName, String username, String source) {}
    public record Event(Identity actor, Identity target, String oldRank, String newRank, String text) {}
}
