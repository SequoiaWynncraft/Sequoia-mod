package com.seqwawa.seq.utils;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks the pictures a bridged Discord message can show in chat, and decides what its
 * links are shown as.
 * <p>
 * The backend forwards Discord's media-proxy URLs for a message's attachments and
 * embeds: {@code media.discordapp.net/attachments/...} for an uploaded file, and
 * {@code images-ext-N.discordapp.net/external/<hash>/https/host/path} for an embed of
 * a file hosted elsewhere. A picture is only ever fetched, or opened when clicked, from
 * those Discord hosts or the GIF sites Discord's own GIF picker uses, and only as an
 * image file; see {@link #isSafeMediaLink}. A message cannot make every client that
 * reads it request, or its reader open, an arbitrary address.
 * <p>
 * GIF, PNG, JPEG and WebP, animated or not, are fetched as they are. AVIF, which cannot
 * be read here, is fetched through the proxy converted to WebP, animation and all, as
 * Discord advises apps to fetch any animated picture. A GIF from Discord's GIF picker
 * reaches Discord as a video: from Klipy, the picker's site since Tenor closed, the
 * animated WebP beside it is shown instead; from Tenor or Giphy, the GIF it was made
 * from, fetched from its site. Other videos are not shown.
 * <p>
 * No address is ever shown in a message: each link reads as a short label, clickable
 * only when it is itself a safe image link; see {@link #linkKind}.
 */
public final class BridgeMedia {

    /** Whether a picture animates in chat. */
    public enum Kind {
        ANIMATED,
        STILL
    }

    /** What a link in a message is shown as, since its address never is. */
    public enum LinkKind {
        GIF("[GIF]"),
        IMAGE("[Image]"),
        OTHER("[Link]");

        private final String label;

        LinkKind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * A picture to show under a bridged message.
     *
     * @param fetch where it is downloaded from, and what clicking it opens: always a
     *              {@linkplain #isSafeMediaLink safe image link}
     * @param kind  whether it animates
     * @param name  its file name, for text shown in its place
     */
    public record Picture(URI fetch, Kind kind, String name) {

        /** What identifies the picture: the file it is fetched from. */
        public String key() {
            return fetch.toString();
        }

        /** The short label a link to this picture is replaced with in the message text. */
        public String label() {
            return kind == Kind.ANIMATED ? LinkKind.GIF.label() : LinkKind.IMAGE.label();
        }
    }

    /**
     * What a message shows: its pictures, and the links in its text that stand for one
     * of them.
     */
    public record Presentation(List<Picture> pictures, Map<String, Picture> linkLabels) {
        public static final Presentation NONE = new Presentation(List.of(), Map.of());

        public Presentation {
            pictures = List.copyOf(pictures);
            linkLabels = Map.copyOf(linkLabels);
        }
    }

    /** A link in a message, spanning {@code [start, end)} including any angle brackets. */
    public record Link(int start, int end, String url) {}

    /** Discord caps a message at ten attachments; a chat line has room for far fewer. */
    static final int MAX_PICTURES = 3;

    /** A link, optionally in the angle brackets Discord uses to suppress its embed. */
    private static final Pattern LINK = Pattern.compile("<?(https?://[^\\s<>]+)>?");
    private static final Pattern EXTERNAL_PROXY_HOST = Pattern.compile("images-ext-\\d+\\.discordapp\\.net");
    private static final Set<String> ATTACHMENT_HOSTS = Set.of("media.discordapp.net", "cdn.discordapp.com");

    private static final String TENOR_HOST = "media.tenor.com";
    /**
     * A Tenor file: its media id, then five characters naming the rendition, such as
     * {@code AAAPo} for the MP4 Discord plays, then the file name.
     */
    private static final Pattern TENOR_FILE = Pattern.compile("^/([A-Za-z0-9_-]{6,})[A-Za-z0-9_-]{5}/([^/]+)\\.[A-Za-z0-9]+$");
    /** Tenor's rendition code for the full-size GIF. */
    private static final String TENOR_FULL_GIF = "AAAAC";
    private static final Pattern GIPHY_HOST = Pattern.compile("(media\\d?|i)\\.giphy\\.com");
    /** A Giphy file: the directory naming the GIF, then a rendition such as {@code giphy.mp4}. */
    private static final Pattern GIPHY_FILE = Pattern.compile("^(/media/(?:[^/]+/)?[A-Za-z0-9]+)/[^/]+$");

    /**
     * Klipy, the GIF site behind Discord's GIF picker. Its GIFs reach Discord as a video
     * with an animated WebP beside it, each file of an item named at random within the
     * item's own folder: {@code /ii/<hash>/<xx>/<yy>/<name>.<ext>}.
     */
    private static final String KLIPY_HOST = "static.klipy.com";
    private static final Pattern KLIPY_FILE = Pattern.compile("^(/ii/[^/]+/[^/]+/[^/]+)/[^/]+$");
    private static final Set<String> KLIPY_ANIMATED_EXTENSIONS = Set.of("gif", "webp");

    /** Pages of the GIF sites, which stand for a GIF although they are no image file. */
    private static final Map<String, String> GIF_PAGES = Map.of(
            "tenor.com", "/view/",
            "klipy.com", "/gifs/",
            "giphy.com", "/gifs/");

    private static final Set<String> ANIMATED_EXTENSIONS = Set.of("gif");
    private static final Set<String> READABLE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");
    private static final Set<String> CONVERTED_EXTENSIONS = Set.of("avif");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("gif", "png", "jpg", "jpeg", "webp", "avif");

    private BridgeMedia() {}

    /**
     * The pictures among {@code mediaUrls}, at most {@link #MAX_PICTURES}, and which of
     * {@code message}'s links they stand for.
     * <p>
     * One file reached through several URLs, such as a GIF site's video and its still
     * preview, is one picture, the animated form winning. A link stands for a picture
     * when it names the same file: the same attachment on Discord's CDN, or the address
     * an external embed was proxied from. A message made of nothing but one link also
     * gives that link to its first picture, as Discord does when it hides a GIF picker
     * link behind the GIF itself.
     */
    public static Presentation present(String message, List<String> mediaUrls) {
        if (mediaUrls == null || mediaUrls.isEmpty()) {
            return Presentation.NONE;
        }

        Map<String, Candidate> candidates = new LinkedHashMap<>();
        for (String mediaUrl : mediaUrls) {
            Candidate candidate = candidate(mediaUrl);
            if (candidate == null) {
                continue;
            }
            Candidate held = candidates.get(candidate.fileKey());
            if (held == null && candidates.size() < MAX_PICTURES) {
                candidates.put(candidate.fileKey(), candidate);
            } else if (held != null && held.picture().kind() == Kind.STILL && candidate.picture().kind() == Kind.ANIMATED) {
                candidates.put(candidate.fileKey(), candidate);
            }
        }
        if (candidates.isEmpty()) {
            return Presentation.NONE;
        }

        List<Link> links = links(message);
        Map<String, Picture> linkLabels = new LinkedHashMap<>();
        for (Link link : links) {
            Candidate candidate = candidates.get(fileKey(link.url()));
            if (candidate != null) {
                linkLabels.putIfAbsent(link.url(), candidate.picture());
            }
        }
        if (linkLabels.isEmpty() && links.size() == 1 && isWholeMessage(message, links.getFirst())) {
            linkLabels.put(links.getFirst().url(), candidates.values().iterator().next().picture());
        }

        List<Picture> pictures = candidates.values().stream().map(Candidate::picture).toList();
        return new Presentation(pictures, linkLabels);
    }

    /** Every link in {@code text}, in order. */
    public static List<Link> links(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<Link> links = new ArrayList<>();
        Matcher matcher = LINK.matcher(text);
        while (matcher.find()) {
            links.add(new Link(matcher.start(), matcher.end(), matcher.group(1)));
        }
        return links;
    }

    /**
     * What {@code url} is shown as in a message: a GIF, whether a GIF file or a GIF
     * site's page, an image file, or any other link.
     */
    public static LinkKind linkKind(String url) {
        URI uri = parse(url);
        if (uri == null || uri.getRawPath() == null) {
            return LinkKind.OTHER;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String site = host.startsWith("www.") ? host.substring(4) : host;
        String page = GIF_PAGES.get(site);
        if (page != null && uri.getRawPath().startsWith(page)) {
            return LinkKind.GIF;
        }
        String extension = extension(fileName(uri.getRawPath()));
        if (ANIMATED_EXTENSIONS.contains(extension)) {
            return LinkKind.GIF;
        }
        return IMAGE_EXTENSIONS.contains(extension) ? LinkKind.IMAGE : LinkKind.OTHER;
    }

    /** {@code text} with each link replaced by what it is shown as, for text that cannot be clicked. */
    public static String hideLinks(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder hidden = new StringBuilder();
        int cursor = 0;
        for (Link link : links(text)) {
            hidden.append(text, cursor, link.start()).append(linkKind(link.url()).label());
            cursor = link.end();
        }
        return hidden.append(text.substring(cursor)).toString();
    }

    /** {@link #isSafeMediaLink(URI)} for a link as written. */
    public static boolean isSafeMediaLink(String url) {
        URI uri = parse(url);
        return uri != null && isSafeMediaLink(uri);
    }

    /**
     * Whether {@code uri} may be fetched, or opened when clicked: an image file, over
     * HTTPS on its standard port, on a {@linkplain #isTrustedMediaHost trusted host},
     * and on Discord only among attachments and proxied embeds.
     */
    public static boolean isSafeMediaLink(URI uri) {
        if (!isTrustedMediaHost(uri) || uri.getRawPath() == null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getRawPath();
        if (ATTACHMENT_HOSTS.contains(host) && !path.startsWith("/attachments/")) {
            return false;
        }
        if (EXTERNAL_PROXY_HOST.matcher(host).matches() && !path.startsWith("/external/")) {
            return false;
        }
        return IMAGE_EXTENSIONS.contains(extension(fileName(path)));
    }

    /**
     * Whether {@code uri} is on a host pictures come from: Discord's CDN and media
     * proxies, and the GIF sites of Discord's GIF picker. Only these are ever fetched
     * from, redirects included.
     */
    public static boolean isTrustedMediaHost(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return ATTACHMENT_HOSTS.contains(host)
                || EXTERNAL_PROXY_HOST.matcher(host).matches()
                || host.equals(TENOR_HOST)
                || GIPHY_HOST.matcher(host).matches()
                || host.equals(KLIPY_HOST);
    }

    private static boolean isWholeMessage(String message, Link link) {
        return message.substring(0, link.start()).isBlank() && message.substring(link.end()).isBlank();
    }

    /** A picture worth showing, with the key of the file it serves. */
    private record Candidate(Picture picture, String fileKey) {}

    private static Candidate candidate(String mediaUrl) {
        URI uri = parse(mediaUrl);
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawPath() == null) {
            return null;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getRawPath();
        boolean attachment = ATTACHMENT_HOSTS.contains(host) && path.startsWith("/attachments/");
        boolean external = EXTERNAL_PROXY_HOST.matcher(host).matches() && path.startsWith("/external/");
        URI source = external ? unwrapExternal(uri) : uri;
        if ((!attachment && !external) || source == null) {
            return null;
        }

        String name = fileName(path);
        String extension = extension(name);
        URI fetch;
        Kind kind;
        URI gifSite = gifSiteGif(source);
        if (gifSite != null) {
            fetch = gifSite;
            kind = Kind.ANIMATED;
            name = fileName(gifSite.getRawPath());
        } else if (isKlipy(source)) {
            // The WebP beside Klipy's video is the animation itself; the video cannot
            // be played here. It is fetched from Klipy, as Klipy made it, rather than
            // however Discord's proxy might re-encode it.
            fetch = KLIPY_ANIMATED_EXTENSIONS.contains(extension) ? source : null;
            kind = Kind.ANIMATED;
        } else if (ANIMATED_EXTENSIONS.contains(extension)) {
            fetch = uri;
            kind = Kind.ANIMATED;
        } else if (READABLE_EXTENSIONS.contains(extension)) {
            fetch = uri;
            kind = Kind.STILL;
        } else if (CONVERTED_EXTENSIONS.contains(extension)) {
            fetch = convertedByDiscord(uri, "format=webp&animated=true");
            kind = Kind.STILL;
        } else {
            return null;
        }
        // Whatever the route, what is fetched and opened must be a safe image link.
        return fetch == null || !isSafeMediaLink(fetch)
                ? null
                : new Candidate(new Picture(fetch, kind, name), fileKey(mediaUrl));
    }

    /**
     * {@code uri} as Discord's media proxy serves it converted to {@code format}, any
     * format asked for before replaced: {@code format=webp&animated=true} keeps an
     * animation, as Discord advises for every animated picture, whatever it was
     * uploaded as. An attachment on the plain CDN is asked of the proxy, which serves
     * the same signed paths. {@code null} for a host that does not convert.
     */
    public static URI convertedByDiscord(URI uri, String format) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String proxyHost = host.equals("cdn.discordapp.com") ? "media.discordapp.net" : host;
        if (!proxyHost.equals("media.discordapp.net") && !EXTERNAL_PROXY_HOST.matcher(proxyHost).matches()) {
            return null;
        }
        String query = uri.getRawQuery() == null ? "" : uri.getRawQuery();
        StringBuilder kept = new StringBuilder();
        for (String parameter : query.split("&")) {
            if (!parameter.isEmpty() && !parameter.startsWith("format=") && !parameter.startsWith("animated=")) {
                kept.append(parameter).append('&');
            }
        }
        return parse("https://" + proxyHost + uri.getRawPath() + "?" + kept + format);
    }

    /**
     * Where the GIF behind a Tenor or Giphy file is served from, or {@code null} for
     * any other file. Discord embeds these GIFs as videos, with a still preview beside.
     */
    static URI gifSiteGif(URI source) {
        String host = source.getHost() == null ? "" : source.getHost().toLowerCase(Locale.ROOT);
        String path = source.getRawPath() == null ? "" : source.getRawPath();
        if (TENOR_HOST.equals(host)) {
            Matcher tenor = TENOR_FILE.matcher(path);
            return tenor.matches()
                    ? parse("https://" + TENOR_HOST + "/" + tenor.group(1) + TENOR_FULL_GIF + "/" + tenor.group(2) + ".gif")
                    : null;
        }
        if (GIPHY_HOST.matcher(host).matches()) {
            Matcher giphy = GIPHY_FILE.matcher(path);
            return giphy.matches() ? parse("https://" + host + giphy.group(1) + "/giphy.gif") : null;
        }
        return null;
    }

    /**
     * What identifies the file a URL serves, whichever of Discord's hosts it goes
     * through: an attachment's path, a GIF site's media id, or the address an external
     * proxy URL wraps. An attachment linked in a message and then embedded through the
     * external proxy is still the same attachment.
     */
    static String fileKey(String url) {
        URI uri = parse(url);
        if (uri == null || uri.getRawPath() == null) {
            return url;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getRawPath();
        URI source = EXTERNAL_PROXY_HOST.matcher(host).matches() && path.startsWith("/external/")
                ? unwrapExternal(uri)
                : uri;
        if (source == null) {
            return "file:" + host + path;
        }
        String sourceHost = source.getHost().toLowerCase(Locale.ROOT);
        String sourcePath = source.getRawPath() == null ? "" : source.getRawPath();
        if (ATTACHMENT_HOSTS.contains(sourceHost) && sourcePath.startsWith("/attachments/")) {
            return "attachment:" + sourcePath;
        }
        if (TENOR_HOST.equals(sourceHost)) {
            Matcher tenor = TENOR_FILE.matcher(sourcePath);
            if (tenor.matches()) {
                return "tenor:" + tenor.group(1);
            }
        }
        if (GIPHY_HOST.matcher(sourceHost).matches()) {
            Matcher giphy = GIPHY_FILE.matcher(sourcePath);
            if (giphy.matches()) {
                return "giphy:" + giphy.group(1).substring(giphy.group(1).lastIndexOf('/') + 1);
            }
        }
        if (KLIPY_HOST.equals(sourceHost)) {
            Matcher klipy = KLIPY_FILE.matcher(sourcePath);
            if (klipy.matches()) {
                return "klipy:" + klipy.group(1);
            }
        }
        return "file:" + sourceHost + sourcePath;
    }

    private static boolean isKlipy(URI source) {
        return source.getHost() != null && KLIPY_HOST.equalsIgnoreCase(source.getHost());
    }

    /** The address an external proxy URL, {@code /external/<hash>/<scheme>/<host>/<path>}, wraps. */
    private static URI unwrapExternal(URI proxy) {
        String[] parts = proxy.getRawPath().split("/", 6);
        if (parts.length < 6 || !parts[3].toLowerCase(Locale.ROOT).startsWith("http")) {
            return null;
        }
        return parse(parts[3].toLowerCase(Locale.ROOT) + "://" + parts[4] + "/" + parts[5]);
    }

    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static URI parse(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            return uri.getHost() == null ? null : uri;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
