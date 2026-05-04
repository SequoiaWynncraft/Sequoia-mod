package org.sequoia.seq.integrations;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import org.sequoia.seq.client.SeqClient;

public final class WynntilsGuildRankAccess {
    private static final String WYNNTILS_MOD_ID = "wynntils";
    private static final String MODELS_CLASS = "com.wynntils.core.components.Models";
    private static final String GUILD_FIELD = "Guild";
    private static final String GET_GUILD_RANK_METHOD = "getGuildRank";

    private static Boolean available;
    private static Object guildModel;
    private static Method getGuildRankMethod;

    private WynntilsGuildRankAccess() {}

    public static boolean isChiefOrOwner() {
        GuildRank rank = currentRank();
        return rank == GuildRank.CHIEF || rank == GuildRank.OWNER;
    }

    private static GuildRank currentRank() {
        if (!ensureAvailable()) {
            return GuildRank.UNKNOWN;
        }

        try {
            Object rank = getGuildRankMethod.invoke(guildModel);
            if (rank == null) {
                return GuildRank.UNKNOWN;
            }

            return GuildRank.fromWynntilsName(String.valueOf(rank));
        } catch (ReflectiveOperationException | RuntimeException e) {
            SeqClient.LOGGER.debug("[Wynntils] Could not read guild rank", e);
            return GuildRank.UNKNOWN;
        }
    }

    private static boolean ensureAvailable() {
        if (available != null) {
            return available;
        }

        if (!FabricLoader.getInstance().isModLoaded(WYNNTILS_MOD_ID)) {
            available = false;
            return false;
        }

        try {
            Class<?> modelsClass = Class.forName(MODELS_CLASS);
            Field guildField = modelsClass.getField(GUILD_FIELD);
            guildModel = guildField.get(null);
            getGuildRankMethod = guildModel.getClass().getMethod(GET_GUILD_RANK_METHOD);
            available = true;
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            SeqClient.LOGGER.warn("[Wynntils] Guild rank integration is unavailable", e);
            available = false;
            return false;
        }
    }

    private enum GuildRank {
        CHIEF,
        OWNER,
        UNKNOWN;

        static GuildRank fromWynntilsName(String name) {
            if (name == null) {
                return UNKNOWN;
            }
            return switch (name) {
                case "CHIEF" -> CHIEF;
                case "OWNER" -> OWNER;
                default -> UNKNOWN;
            };
        }
    }
}
