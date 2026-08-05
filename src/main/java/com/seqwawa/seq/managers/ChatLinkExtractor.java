package com.seqwawa.seq.managers;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts a small, ordered set of web links from untrusted chat text. */
final class ChatLinkExtractor {
    private static final Pattern WEB_LINK = Pattern.compile("(?i)\\bhttps?://[^\\s<>\\\"']+");
    private static final String TRAILING_PUNCTUATION = ".,!?:;";
    private static final int MAX_URL_LENGTH = 2_048;

    private ChatLinkExtractor() {
    }

    static List<URI> extract(String message, int limit) {
        if (message == null || message.isBlank() || limit <= 0) {
            return List.of();
        }

        Set<URI> links = new LinkedHashSet<>();
        Matcher matcher = WEB_LINK.matcher(message);
        while (matcher.find() && links.size() < limit) {
            String candidate = stripTrailingPunctuation(matcher.group());
            if (candidate.length() > MAX_URL_LENGTH) {
                continue;
            }

            try {
                URI uri = new URI(candidate);
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                if ((scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null) {
                    links.add(uri.normalize());
                }
            } catch (URISyntaxException ignored) {
                // Malformed chat links remain ordinary text.
            }
        }
        return List.copyOf(new ArrayList<>(links));
    }

    private static String stripTrailingPunctuation(String input) {
        int end = input.length();
        while (end > 0) {
            char last = input.charAt(end - 1);
            if (TRAILING_PUNCTUATION.indexOf(last) >= 0
                    || (last == ')' && count(input, '(', end) < count(input, ')', end))
                    || (last == ']' && count(input, '[', end) < count(input, ']', end))
                    || (last == '}' && count(input, '{', end) < count(input, '}', end))) {
                end--;
                continue;
            }
            break;
        }
        return input.substring(0, end);
    }

    private static int count(String text, char needle, int end) {
        int count = 0;
        for (int index = 0; index < end; index++) {
            if (text.charAt(index) == needle) {
                count++;
            }
        }
        return count;
    }
}
