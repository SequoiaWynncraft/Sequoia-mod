package com.seqwawa.seq.managers;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Provider-specific URL negotiation for media services used in chat. */
final class ChatMediaUrlResolver {
    private static final String DEFAULT_USER_AGENT = "Sequoia-Mod-Chat-Preview/1";
    private static final String DISCORD_CRAWLER_USER_AGENT = "Discordbot/2.0";

    private ChatMediaUrlResolver() {
    }

    static URI preferredMediaUri(URI uri) {
        if (uri == null || uri.getHost() == null || uri.getPath() == null) {
            return uri;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath();
        if (!host.equals("cdn.7tv.app") || !path.toLowerCase(Locale.ROOT).endsWith(".avif")) {
            return uri;
        }

        String webpPath = path.substring(0, path.length() - ".avif".length()) + ".webp";
        try {
            return new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    uri.getHost(),
                    uri.getPort(),
                    webpPath,
                    uri.getQuery(),
                    uri.getFragment());
        } catch (URISyntaxException ignored) {
            return uri;
        }
    }

    static String userAgent(URI uri) {
        if (uri != null && uri.getHost() != null) {
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.equals("klipy.com") || host.endsWith(".klipy.com")) {
                return DISCORD_CRAWLER_USER_AGENT;
            }
        }
        return DEFAULT_USER_AGENT;
    }
}
