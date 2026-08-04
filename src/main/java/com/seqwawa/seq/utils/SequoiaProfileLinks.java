package com.seqwawa.seq.utils;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Links into the Sequoia site for a player. */
public final class SequoiaProfileLinks {

    private static final String PLAYER_CARD = "https://seqwawa.com/statistics/player/playercard?player=";
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-zA-Z0-9_]{3,16}");

    private SequoiaProfileLinks() {}

    /**
     * Profile card of {@code username}, or {@code null} when it is not a Minecraft
     * username.
     * <p>
     * The check matters because the caller feeds this whatever a chat component
     * carries as its shift-click insertion, which is not always a player name.
     */
    public static URI playerCard(String username) {
        if (username == null || !USERNAME_PATTERN.matcher(username.trim()).matches()) {
            return null;
        }
        return URI.create(PLAYER_CARD + URLEncoder.encode(username.trim(), StandardCharsets.UTF_8));
    }
}
