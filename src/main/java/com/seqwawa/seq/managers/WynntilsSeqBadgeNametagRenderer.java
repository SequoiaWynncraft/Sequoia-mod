package com.seqwawa.seq.managers;

import com.wynntils.core.WynntilsMod;
import com.wynntils.core.components.Models;
import com.wynntils.mc.event.PlayerNametagRenderEvent;
import com.wynntils.mc.extension.EntityRenderStateExtension;
import com.wynntils.services.leaderboard.type.LeaderboardBadge;
import com.wynntils.utils.render.RenderUtils;
import com.wynntils.utils.render.Texture;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.SeqBadge;
import com.seqwawa.seq.network.auth.StoredAuthSession;

public final class WynntilsSeqBadgeNametagRenderer implements SeqBadgeNametagRendererHandle {
    private static final float BADGE_WIDTH = 19f;
    private static final float BADGE_HEIGHT = 18f;
    private static final float TEXTURE_SIZE = 64f;
    private static final float BADGE_STEP = 21f;
    private static final float DEFAULT_BADGE_Y_OFFSET = 25f;
    private static final float CUSTOM_NAMETAG_BADGE_Y_OFFSET = 15f;

    private static volatile WynntilsSeqBadgeNametagRenderer instance;
    private final LeaderboardBadgeService badgeService = LeaderboardBadgeService.getInstance();
    private boolean registered;
    private String status = "waiting for Wynntils event bus";
    private PlayerNametagRenderEvent lastIntegratedEvent;

    WynntilsSeqBadgeNametagRenderer() {
        instance = this;
        ensureRegistered();
    }

    public static boolean renderIntegrated(
            PlayerNametagRenderEvent event,
            float nametagVerticalOffset,
            List<LeaderboardBadge> visibleWynntilsBadges) {
        WynntilsSeqBadgeNametagRenderer renderer = instance;
        if (renderer == null) {
            return false;
        }
        boolean handled = renderer.renderBadges(event, nametagVerticalOffset, visibleWynntilsBadges);
        if (handled) {
            renderer.lastIntegratedEvent = event;
        }
        return handled;
    }

    @Override
    public void tick() {
        ensureRegistered();
    }

    @Override
    public String status() {
        return status;
    }

    private void ensureRegistered() {
        if (registered) {
            return;
        }
        try {
            WynntilsMod.registerEventListener(this);
            registered = true;
            status = "ready";
            SeqClient.LOGGER.info("[LeaderboardBadges] Registered Wynntils nametag badge listener.");
        } catch (Throwable throwable) {
            status = "waiting: " + throwable.getClass().getSimpleName();
            SeqClient.LOGGER.debug(
                    "[LeaderboardBadges] Wynntils nametag badge listener not ready yet: {}",
                    throwable.toString());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onPlayerNametagRender(PlayerNametagRenderEvent event) {
        if (event == lastIntegratedEvent) {
            lastIntegratedEvent = null;
            return;
        }
        renderBadges(event, 0f, List.of());
    }

    private boolean renderBadges(
            PlayerNametagRenderEvent event,
            float nametagVerticalOffset,
            List<LeaderboardBadge> visibleWynntilsBadges) {
        if (event == null || event.getEntityRenderState() == null) {
            return false;
        }
        if (event.getEntityRenderState().nameTagAttachment == null) {
            return false;
        }

        Entity entity = ((EntityRenderStateExtension) event.getEntityRenderState()).getEntity();
        if (!(entity instanceof AbstractClientPlayer player)) {
            return false;
        }
        if (Models.Player.isNpc(player)) {
            return false;
        }

        UUID uuid = Models.Player.getUserUUID(player);
        String profileName = profileName(player);
        String scoreboardName = player.getScoreboardName();
        String displayName = player.getDisplayName() == null ? null : player.getDisplayName().getString();
        String entityName = player.getName() == null ? null : player.getName().getString();
        boolean localCandidate = isLocalBadgeCandidate(player, profileName, scoreboardName, displayName, entityName);
        String localName = localCandidate && SeqClient.mc.player != null && SeqClient.mc.player.getName() != null
                ? SeqClient.mc.player.getName().getString()
                : null;
        String nametagText = event.getEntityRenderState().nameTag == null
                ? null
                : event.getEntityRenderState().nameTag.getString();
        localCandidate = localCandidate || isLocalBadgeCandidate(player, nametagText, localName);
        if (localCandidate && localName == null && SeqClient.mc.player != null && SeqClient.mc.player.getName() != null) {
            localName = SeqClient.mc.player.getName().getString();
        }
        List<SeqBadge> badges = badgesForPlayer(
                player, uuid, localCandidate, profileName, scoreboardName, displayName, entityName, localName, nametagText);
        if (badges.isEmpty()) {
            return false;
        }

        float badgeYOffset =
                nametagVerticalOffset == 0f ? DEFAULT_BADGE_Y_OFFSET : CUSTOM_NAMETAG_BADGE_Y_OFFSET;
        drawCombinedBadges(event, nametagVerticalOffset, badgeYOffset, visibleWynntilsBadges, badges);
        return true;
    }

    private static void drawCombinedBadges(
            PlayerNametagRenderEvent event,
            float nametagVerticalOffset,
            float badgeYOffset,
            List<LeaderboardBadge> wynntilsBadges,
            List<SeqBadge> seqBadges) {
        int totalBadgeCount = wynntilsBadges.size() + seqBadges.size();
        float rowWidth = BADGE_WIDTH * totalBadgeCount + 2f * (totalBadgeCount - 1);
        float badgeXOffset = -rowWidth / 2f + BADGE_WIDTH / 2f;

        for (LeaderboardBadge badge : wynntilsBadges) {
            RenderUtils.renderLeaderboardBadge(
                    event.getPoseStack(),
                    event.getSubmitNodeCollector(),
                    event.getEntityRenderState(),
                    event.getCameraRenderState(),
                    Texture.LEADERBOARD_BADGES.identifier(),
                    BADGE_WIDTH,
                    BADGE_HEIGHT,
                    badge.uOffset(),
                    badge.vOffset(),
                    BADGE_WIDTH,
                    BADGE_HEIGHT,
                    Texture.LEADERBOARD_BADGES.width(),
                    Texture.LEADERBOARD_BADGES.height(),
                    nametagVerticalOffset,
                    badgeXOffset,
                    badgeYOffset);
            badgeXOffset += BADGE_STEP;
        }

        for (SeqBadge badge : seqBadges) {
            RenderUtils.renderLeaderboardBadge(
                    event.getPoseStack(),
                    event.getSubmitNodeCollector(),
                    event.getEntityRenderState(),
                    event.getCameraRenderState(),
                    badge.textureId(),
                    BADGE_WIDTH,
                    BADGE_HEIGHT,
                    0f,
                    0f,
                    TEXTURE_SIZE,
                    TEXTURE_SIZE,
                    TEXTURE_SIZE,
                    TEXTURE_SIZE,
                    nametagVerticalOffset,
                    badgeXOffset,
                    badgeYOffset);
            badgeXOffset += BADGE_STEP;
        }
    }

    private List<SeqBadge> badgesForPlayer(
            AbstractClientPlayer player,
            UUID wynntilsUuid,
            boolean actualLocalPlayer,
            String... nameCandidates) {
        List<SeqBadge> badges = badgeService.badgesFor(wynntilsUuid, nameCandidates);
        if (!badges.isEmpty()) {
            return badges;
        }

        UUID rawUuid = player.getUUID();
        if (rawUuid != null && !rawUuid.equals(wynntilsUuid)) {
            badges = badgeService.badgesFor(rawUuid, nameCandidates);
            if (!badges.isEmpty()) {
                return badges;
            }
        }

        if (actualLocalPlayer) {
            StoredAuthSession session = SeqClient.getConfigManager().getStoredAuthSession();
            UUID authUuid = parseUuid(session == null ? null : session.minecraftUuid());
            badges = badgeService.badgesFor(authUuid, nameCandidates);
            if (!badges.isEmpty()) {
                return badges;
            }

            UUID launcherUuid = SeqClient.mc != null && SeqClient.mc.getUser() != null
                    ? SeqClient.mc.getUser().getProfileId()
                    : null;
            badges = badgeService.badgesFor(launcherUuid, nameCandidates);
            if (!badges.isEmpty()) {
                return badges;
            }
        }

        return List.of();
    }

    private static boolean isActualLocalPlayer(AbstractClientPlayer player) {
        if (player == null || SeqClient.mc == null || SeqClient.mc.player == null) {
            return false;
        }
        UUID playerUuid = player.getUUID();
        UUID localUuid = SeqClient.mc.player.getUUID();
        return player == SeqClient.mc.player || (playerUuid != null && playerUuid.equals(localUuid));
    }

    private static boolean isLocalBadgeCandidate(AbstractClientPlayer player, String... nameCandidates) {
        if (isActualLocalPlayer(player)) {
            return true;
        }
        String localName = SeqClient.mc != null && SeqClient.mc.getUser() != null ? SeqClient.mc.getUser().getName() : null;
        if (localName != null && !localName.isBlank() && nameCandidates != null) {
            String normalizedLocalName = localName.trim();
            for (String candidate : nameCandidates) {
                if (candidate != null && candidate.toLowerCase().contains(normalizedLocalName.toLowerCase())) {
                    return true;
                }
            }
        }
        return player != null
                && SeqClient.mc != null
                && SeqClient.mc.player != null
                && player.distanceToSqr(SeqClient.mc.player) <= 0.04d;
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String profileName(AbstractClientPlayer player) {
        GameProfile profile = player.getGameProfile();
        return profile == null ? null : profile.name();
    }
}
