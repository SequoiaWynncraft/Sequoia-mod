package com.seqwawa.seq.managers;

import com.wynntils.core.WynntilsMod;
import com.wynntils.core.components.Models;
import com.wynntils.core.components.Managers;
import com.wynntils.core.persisted.config.Config;
import com.wynntils.features.players.CustomNametagRendererFeature;
import com.wynntils.mc.event.GetCameraEntityEvent;
import com.wynntils.mc.event.PlayerNametagRenderEvent;
import com.wynntils.mc.extension.EntityRenderStateExtension;
import com.wynntils.services.leaderboard.type.LeaderboardBadge;
import com.wynntils.utils.render.RenderUtils;
import com.wynntils.utils.render.Texture;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.SeqBadge;
import com.seqwawa.seq.render.SeqAvatarRenderStateExtension;

public final class WynntilsSeqBadgeNametagRenderer implements SeqBadgeNametagRendererHandle {
    private static final Pattern USERNAME_TOKEN = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final float TEXTURE_SIZE = 64f;
    private static volatile WynntilsSeqBadgeNametagRenderer instance;
    private static volatile Config<Boolean> showOwnNametagConfig;
    private final LeaderboardBadgeService badgeService = LeaderboardBadgeService.getInstance();
    private final Set<UUID> wynntilsEventPlayers = ConcurrentHashMap.newKeySet();
    private boolean registered;
    private String status = "waiting for Wynntils event bus";
    private PlayerNametagRenderEvent lastIntegratedEvent;
    private boolean nametagFeatureEnabled = true;
    private boolean showOwnNametag;
    private long wynntilsEventPlayersTick = Long.MIN_VALUE;

    WynntilsSeqBadgeNametagRenderer() {
        instance = this;
        new VanillaSeqBadgeNametagRenderer(this::shouldRenderVanillaFallback, true);
        ensureRegistered();
    }

    public static void registerNametagFeatureConfig(Config<Boolean> showOwnNametag) {
        showOwnNametagConfig = showOwnNametag;
    }

    public static boolean renderIntegrated(
            PlayerNametagRenderEvent event,
            float nametagVerticalOffset,
            List<LeaderboardBadge> visibleWynntilsBadges) {
        return renderFromWynntilsHook(event, nametagVerticalOffset, visibleWynntilsBadges, false);
    }

    public static boolean renderHiddenPlayerNametag(PlayerNametagRenderEvent event) {
        return renderFromWynntilsHook(event, 0f, List.of(), true);
    }

    private static boolean renderFromWynntilsHook(
            PlayerNametagRenderEvent event,
            float nametagVerticalOffset,
            List<LeaderboardBadge> visibleWynntilsBadges,
            boolean seqOnlyLowerPlacement) {
        WynntilsSeqBadgeNametagRenderer renderer = instance;
        if (renderer == null) {
            return false;
        }
        boolean handled = renderer.renderBadges(
                event, nametagVerticalOffset, visibleWynntilsBadges, seqOnlyLowerPlacement);
        if (handled) {
            renderer.lastIntegratedEvent = event;
        }
        renderer.markWynntilsEvent(event);
        return handled;
    }

    @Override
    public void tick() {
        ensureRegistered();
        refreshNametagFeatureState();
        resetWynntilsEventPlayersIfTickChanged();
    }

    @Override
    public String status() {
        return "wynntils (" + status + ")";
    }

    private boolean ensureRegistered() {
        if (registered) {
            return true;
        }
        try {
            WynntilsMod.registerEventListener(this);
            registered = true;
            status = "ready";
            SeqClient.LOGGER.info("[LeaderboardBadges] Registered Wynntils nametag badge listener.");
            return true;
        } catch (Throwable throwable) {
            status = "waiting: " + throwable.getClass().getSimpleName();
            SeqClient.LOGGER.debug(
                    "[LeaderboardBadges] Wynntils nametag badge listener not ready yet: {}",
                    throwable.toString());
            return false;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onPlayerNametagRender(PlayerNametagRenderEvent event) {
        markWynntilsEvent(event);
        if (event == lastIntegratedEvent) {
            lastIntegratedEvent = null;
            return;
        }
        if (!shouldRenderStandaloneEvent(event.isCanceled())) {
            return;
        }
        renderBadges(event, 0f, List.of(), false);
    }

    static boolean shouldRenderStandaloneEvent(boolean eventCanceled) {
        return !eventCanceled;
    }

    static boolean shouldRenderVanillaFallback(
            boolean showOwnNametag,
            boolean nametagFeatureEnabled,
            boolean wynntilsEventThisTick,
            boolean localPlayer) {
        if (wynntilsEventThisTick) {
            return false;
        }
        return !nametagFeatureEnabled || (localPlayer && !showOwnNametag);
    }

    static boolean shouldForceOwnAvatarRendering(boolean showOwnNametag, boolean nametagFeatureEnabled) {
        return !nametagFeatureEnabled || !showOwnNametag;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onGetCameraEntity(GetCameraEntityEvent event) {
        refreshNametagFeatureState();
        if (!SeqBadgeNametagRenderSupport.showLeaderboardBadges()
                || !SeqBadgeNametagRenderSupport.showOwnLeaderboardBadge()
                || !shouldForceOwnAvatarRendering(showOwnNametag, nametagFeatureEnabled)
                || !isGameplayOrInventoryScreen()) {
            return;
        }
        event.setEntity(null);
    }

    private boolean shouldRenderVanillaFallback(AvatarRenderState state, Boolean localPlayer) {
        refreshNametagFeatureState();
        UUID playerUuid = state instanceof SeqAvatarRenderStateExtension extension ? extension.seq$getPlayerUuid() : null;
        return shouldRenderVanillaFallback(
                showOwnNametag,
                nametagFeatureEnabled,
                playerUuid != null && wasSeenInWynntilsEventThisTick(playerUuid),
                Boolean.TRUE.equals(localPlayer));
    }

    private void refreshNametagFeatureState() {
        Config<Boolean> showOwnNametag = showOwnNametagConfig;
        if (showOwnNametag != null) {
            this.showOwnNametag = Boolean.TRUE.equals(showOwnNametag.get());
        }

        try {
            CustomNametagRendererFeature feature =
                    Managers.Feature.getFeatureInstance(CustomNametagRendererFeature.class);
            nametagFeatureEnabled = feature == null || feature.isEnabled();
        } catch (Throwable throwable) {
            nametagFeatureEnabled = true;
        }
    }

    private void markWynntilsEvent(PlayerNametagRenderEvent event) {
        AbstractClientPlayer player = playerFromEvent(event);
        if (player == null) {
            return;
        }
        resetWynntilsEventPlayersIfTickChanged();
        wynntilsEventPlayers.add(player.getUUID());
    }

    private boolean wasSeenInWynntilsEventThisTick(UUID playerUuid) {
        resetWynntilsEventPlayersIfTickChanged();
        return wynntilsEventPlayers.contains(playerUuid);
    }

    private void resetWynntilsEventPlayersIfTickChanged() {
        long currentTick = currentGameTick();
        if (wynntilsEventPlayersTick == currentTick) {
            return;
        }
        wynntilsEventPlayers.clear();
        wynntilsEventPlayersTick = currentTick;
    }

    private static long currentGameTick() {
        return SeqClient.mc == null || SeqClient.mc.level == null ? Long.MIN_VALUE : SeqClient.mc.level.getGameTime();
    }

    private static boolean isGameplayOrInventoryScreen() {
        return SeqClient.mc != null
                && (SeqClient.mc.screen == null || SeqClient.mc.screen instanceof InventoryScreen);
    }

    private boolean renderBadges(
            PlayerNametagRenderEvent event,
            float nametagVerticalOffset,
            List<LeaderboardBadge> visibleWynntilsBadges,
            boolean seqOnlyLowerPlacement) {
        if (!SeqBadgeNametagRenderSupport.showLeaderboardBadges()) {
            return false;
        }
        if (event == null || event.getEntityRenderState() == null) {
            return false;
        }
        if (event.getEntityRenderState().nameTagAttachment == null) {
            return false;
        }

        AbstractClientPlayer player = playerFromEvent(event);
        if (player == null) {
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
        String nametagText = event.getEntityRenderState().nameTag == null
                ? null
                : event.getEntityRenderState().nameTag.getString();
        localCandidate = localCandidate || isLocalBadgeCandidate(player, nametagText);
        if (localCandidate && !SeqBadgeNametagRenderSupport.showOwnLeaderboardBadge()) {
            return false;
        }
        List<SeqBadge> badges = badgesForPlayer(player, uuid, localCandidate);
        if (badges.isEmpty()) {
            return false;
        }

        float badgeYOffset = badgeYOffset(nametagVerticalOffset, seqOnlyLowerPlacement);
        drawCombinedBadges(event, nametagVerticalOffset, badgeYOffset, visibleWynntilsBadges, badges);
        return true;
    }

    static float badgeYOffset(float nametagVerticalOffset, boolean seqOnlyLowerPlacement) {
        return nametagVerticalOffset == 0f && !seqOnlyLowerPlacement
                ? SeqBadgeNametagRenderSupport.WYNNTILS_DEFAULT_BADGE_Y_OFFSET
                : SeqBadgeNametagRenderSupport.LOWER_BADGE_Y_OFFSET;
    }

    private static AbstractClientPlayer playerFromEvent(PlayerNametagRenderEvent event) {
        if (event == null || event.getEntityRenderState() == null) {
            return null;
        }

        Entity entity = ((EntityRenderStateExtension) event.getEntityRenderState()).getEntity();
        return entity instanceof AbstractClientPlayer player ? player : null;
    }

    private static void drawCombinedBadges(
            PlayerNametagRenderEvent event,
            float nametagVerticalOffset,
            float badgeYOffset,
            List<LeaderboardBadge> wynntilsBadges,
            List<SeqBadge> seqBadges) {
        int totalBadgeCount = wynntilsBadges.size() + seqBadges.size();
        float rowWidth =
                SeqBadgeNametagRenderSupport.BADGE_WIDTH * totalBadgeCount + 2f * (totalBadgeCount - 1);
        float badgeXOffset = -rowWidth / 2f + SeqBadgeNametagRenderSupport.BADGE_WIDTH / 2f;

        for (LeaderboardBadge badge : wynntilsBadges) {
            RenderUtils.renderLeaderboardBadge(
                    event.getPoseStack(),
                    event.getSubmitNodeCollector(),
                    event.getEntityRenderState(),
                    event.getCameraRenderState(),
                    Texture.LEADERBOARD_BADGES.identifier(),
                    SeqBadgeNametagRenderSupport.BADGE_WIDTH,
                    SeqBadgeNametagRenderSupport.BADGE_HEIGHT,
                    badge.uOffset(),
                    badge.vOffset(),
                    SeqBadgeNametagRenderSupport.BADGE_WIDTH,
                    SeqBadgeNametagRenderSupport.BADGE_HEIGHT,
                    Texture.LEADERBOARD_BADGES.width(),
                    Texture.LEADERBOARD_BADGES.height(),
                    nametagVerticalOffset,
                    badgeXOffset,
                    badgeYOffset);
            badgeXOffset += SeqBadgeNametagRenderSupport.BADGE_STEP;
        }

        for (SeqBadge badge : seqBadges) {
            RenderUtils.renderLeaderboardBadge(
                    event.getPoseStack(),
                    event.getSubmitNodeCollector(),
                    event.getEntityRenderState(),
                    event.getCameraRenderState(),
                    badge.textureId(),
                    SeqBadgeNametagRenderSupport.BADGE_WIDTH,
                    SeqBadgeNametagRenderSupport.BADGE_HEIGHT,
                    0f,
                    0f,
                    TEXTURE_SIZE,
                    TEXTURE_SIZE,
                    TEXTURE_SIZE,
                    TEXTURE_SIZE,
                    nametagVerticalOffset,
                    badgeXOffset,
                    badgeYOffset);
            badgeXOffset += SeqBadgeNametagRenderSupport.BADGE_STEP;
        }
    }

    private List<SeqBadge> badgesForPlayer(
            AbstractClientPlayer player,
            UUID wynntilsUuid,
            boolean actualLocalPlayer) {
        UUID rawUuid = player.getUUID();
        return SeqBadgePlayerResolver.resolve(
                badgeService, wynntilsUuid, rawUuid, actualLocalPlayer);
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
        if (matchesLocalUsername(localName, nameCandidates)) {
            return true;
        }
        return player != null
                && SeqClient.mc != null
                && SeqClient.mc.player != null
                && player.distanceToSqr(SeqClient.mc.player) <= 0.04d;
    }

    static boolean matchesLocalUsername(String localName, String... nameCandidates) {
        String normalizedLocalName = normalizeMinecraftUsername(localName);
        if (normalizedLocalName == null || nameCandidates == null) {
            return false;
        }

        for (String candidate : nameCandidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            String cleaned = stripFormatting(candidate);
            String exact = normalizeMinecraftUsername(cleaned);
            if (normalizedLocalName.equals(exact)) {
                return true;
            }

            Matcher matcher = USERNAME_TOKEN.matcher(cleaned);
            while (matcher.find()) {
                if (normalizedLocalName.equals(matcher.group().toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String normalizeMinecraftUsername(String username) {
        if (username == null) {
            return null;
        }
        String trimmed = stripFormatting(username).trim();
        if (!trimmed.matches("[A-Za-z0-9_]{3,16}")) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static String stripFormatting(String value) {
        return value.replaceAll("(?i)§[0-9A-FK-OR]", "");
    }

    private static String profileName(AbstractClientPlayer player) {
        GameProfile profile = player.getGameProfile();
        return profile == null ? null : profile.name();
    }

}
