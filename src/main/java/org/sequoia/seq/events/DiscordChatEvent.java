package org.sequoia.seq.events;

/**
 * Fired when a discord_chat WebSocket message is received.
 */
public record DiscordChatEvent(String username, String message) {}
