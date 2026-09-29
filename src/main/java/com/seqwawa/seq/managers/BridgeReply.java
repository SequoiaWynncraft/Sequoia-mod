package com.seqwawa.seq.managers;

import com.seqwawa.seq.network.ConnectionManager;

/**
 * A bridged Discord message that replies to another: who it answers, an excerpt of
 * what they said when the backend sends one, and what the sender wrote.
 * <p>
 * The backend writes a reply as {@code "Replying to <name>: <text>"}, for clients that
 * show it as it comes. Newer backends also send the replied-to message itself; with
 * older ones that prefix is all there is, so the name is read back from it.
 *
 * @param username the replied-to author, as the backend names bridge senders
 * @param excerpt  the replied-to message on one line, or {@code null} when the backend
 *                 does not send it
 * @param text     the reply itself, without the prefix
 */
record BridgeReply(String username, String excerpt, String text) {

    static final String PREFIX = "Replying to ";
    private static final String SEPARATOR = ": ";

    /** Longest name read back from the prefix: past it, the text is not a reply's. */
    private static final int MAX_PREFIX_NAME_LENGTH = 64;

    /** The reply {@code message} makes, or {@code null} when it answers nobody. */
    static BridgeReply of(ConnectionManager.DiscordChatMessage message) {
        String text = message.message() == null ? "" : message.message();
        ConnectionManager.DiscordChatMessage.Reply reply = message.reply();
        if (reply != null && reply.username() != null && !reply.username().isBlank()) {
            String prefix = PREFIX + reply.username() + SEPARATOR;
            return new BridgeReply(
                    reply.username(),
                    oneLine(reply.message()),
                    text.startsWith(prefix) ? text.substring(prefix.length()) : text);
        }

        if (!text.startsWith(PREFIX)) {
            return null;
        }
        int separator = text.indexOf(SEPARATOR, PREFIX.length());
        String username = separator < 0 ? "" : text.substring(PREFIX.length(), separator);
        if (username.isBlank()
                || username.length() > MAX_PREFIX_NAME_LENGTH
                || username.chars().anyMatch(Character::isISOControl)) {
            return null;
        }
        return new BridgeReply(username, null, text.substring(separator + SEPARATOR.length()));
    }

    /** {@code text} with every run of whitespace, line breaks included, as one space. */
    private static String oneLine(String text) {
        if (text == null) {
            return null;
        }
        String collapsed = text.strip().replaceAll("\\s+", " ");
        return collapsed.isEmpty() ? null : collapsed;
    }
}
