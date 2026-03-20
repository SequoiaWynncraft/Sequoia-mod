package org.sequoia.seq.network;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/**
 * Restricts Seq network traffic to the main Wynncraft network and blocks beta.
 */
public final class WynncraftServerPolicy {

    public static final String MAIN_SERVER_ONLY_MESSAGE =
            "Seq network features are only available on the main Wynncraft server.";

    private WynncraftServerPolicy() {}

    public static boolean isCurrentServerAllowed() {
        return classifyAddress(currentServerAddress()) == Scope.MAIN;
    }

    static Scope classifyAddress(String serverAddress) {
        String normalizedHost = normalizeHost(serverAddress);
        if (normalizedHost == null) {
            return Scope.BLOCKED;
        }
        if ("wynncraft.com".equals(normalizedHost)) {
            return Scope.MAIN;
        }
        if ("beta.wynncraft.com".equals(normalizedHost) || normalizedHost.endsWith(".beta.wynncraft.com")) {
            return Scope.BLOCKED;
        }
        if (normalizedHost.endsWith(".wynncraft.com")) {
            return Scope.MAIN;
        }
        return Scope.BLOCKED;
    }

    static String normalizeHost(String serverAddress) {
        if (serverAddress == null) {
            return null;
        }

        String normalized = serverAddress.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }

        int schemeSeparator = normalized.indexOf("://");
        if (schemeSeparator >= 0) {
            normalized = normalized.substring(schemeSeparator + 3);
        }

        int slashIndex = normalized.indexOf('/');
        if (slashIndex >= 0) {
            normalized = normalized.substring(0, slashIndex);
        }

        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (normalized.startsWith("[")) {
            return null;
        }

        int colonIndex = normalized.indexOf(':');
        if (colonIndex >= 0) {
            normalized = normalized.substring(0, colonIndex);
        }

        return normalized.isEmpty() ? null : normalized;
    }

    private static String currentServerAddress() {
        ServerData serverData = Minecraft.getInstance().getCurrentServer();
        return serverData != null ? serverData.ip : null;
    }

    enum Scope {
        MAIN,
        BLOCKED
    }
}
