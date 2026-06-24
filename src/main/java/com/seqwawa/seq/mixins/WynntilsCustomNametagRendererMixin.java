package com.seqwawa.seq.mixins;

import com.wynntils.core.components.Models;
import com.wynntils.core.components.Services;
import com.wynntils.core.persisted.config.Config;
import com.wynntils.mc.event.PlayerNametagRenderEvent;
import com.wynntils.mc.extension.EntityRenderStateExtension;
import com.wynntils.services.leaderboard.type.LeaderboardBadge;
import com.wynntils.utils.mc.McUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import com.seqwawa.seq.managers.WynntilsSeqBadgeNametagRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.wynntils.features.players.CustomNametagRendererFeature", remap = false)
public abstract class WynntilsCustomNametagRendererMixin {
    @Shadow @Final private Config<Boolean> showLeaderboardBadges;

    @Shadow @Final private Config<Integer> badgeCount;

    @Inject(method = "drawBadges", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void seq$renderLeaderboardBadge(
            PlayerNametagRenderEvent event, float nametagVerticalOffset, CallbackInfo callbackInfo) {
        if (WynntilsSeqBadgeNametagRenderer.renderIntegrated(
                event, nametagVerticalOffset, seq$visibleWynntilsBadges(event))) {
            callbackInfo.cancel();
        }
    }

    @Unique
    private List<LeaderboardBadge> seq$visibleWynntilsBadges(PlayerNametagRenderEvent event) {
        if (!Boolean.TRUE.equals(showLeaderboardBadges.get()) || badgeCount.get() <= 0) {
            return List.of();
        }
        if (event == null || event.getEntityRenderState() == null) {
            return List.of();
        }

        Entity entity = ((EntityRenderStateExtension) event.getEntityRenderState()).getEntity();
        if (!(entity instanceof AbstractClientPlayer player)) {
            return List.of();
        }

        List<LeaderboardBadge> badges = Services.Leaderboard.getPlayerBadges(Models.Player.getUserUUID(player));
        if (badges == null || badges.isEmpty()) {
            return List.of();
        }

        int visibleCount = Math.min(badgeCount.get(), badges.size());
        if (visibleCount >= badges.size()) {
            return List.copyOf(badges);
        }

        int startIndex = Math.floorMod(McUtils.player().tickCount / 40, badges.size());
        List<LeaderboardBadge> visible = new ArrayList<>(visibleCount);
        for (int index = 0; index < visibleCount; index++) {
            visible.add(badges.get((startIndex + index) % badges.size()));
        }
        return List.copyOf(visible);
    }
}
