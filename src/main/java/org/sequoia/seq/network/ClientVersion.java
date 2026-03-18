package org.sequoia.seq.network;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Resolves the installed mod version used for backend compatibility checks.
 */
public final class ClientVersion {

    public static final String MOD_ID = "seq";
    public static final String MOD_VERSION_HEADER = "X-Sequoia-Mod-Version";

    private ClientVersion() {}

    public static String resolveInstalledVersion() {
        return FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
