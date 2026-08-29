package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.SeqPointsEffects;
import com.seqwawa.seq.model.SeqPointsPurchase;
import com.seqwawa.seq.model.SeqPointsShop;
import com.seqwawa.seq.model.SeqPointsShopEffect;
import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.network.ConnectionManager;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Owns shop state and the lightweight timed-effect roster. */
public final class SeqPointsShopManager {

    public enum State {
        IDLE,
        LOADING,
        READY,
        UNAVAILABLE
    }

    static final long EFFECT_REFRESH_MS = Duration.ofMinutes(1).toMillis();
    private static SeqPointsShopManager instance;

    private volatile SeqPointsShop shop = SeqPointsShop.EMPTY;
    private volatile State state = State.IDLE;
    private volatile Map<String, SeqPointsShopEffect> effectsByUuid = Map.of();
    private volatile Map<String, SeqPointsShopEffect> effectsByUsername = Map.of();
    private volatile long lastEffectRefresh;
    private volatile boolean effectLoading;
    private volatile int generation;

    private SeqPointsShopManager() {}

    public static synchronized SeqPointsShopManager getInstance() {
        if (instance == null) {
            instance = new SeqPointsShopManager();
        }
        return instance;
    }

    public SeqPointsShop shop() {
        return shop;
    }

    public State state() {
        return state;
    }

    public synchronized CompletableFuture<SeqPointsShop> refreshShop() {
        int requestedGeneration = generation;
        state = State.LOADING;
        return ApiClient.getInstance().getSeqPointsShop().handle((response, error) -> {
            synchronized (this) {
                if (requestedGeneration != generation) {
                    return shop;
                }
                if (error != null || response == null || !response.isSupported()) {
                    state = State.UNAVAILABLE;
                    if (error != null) SeqClient.LOGGER.warn("[SeqPoints] Shop refresh failed", error);
                    return shop;
                }
                accept(response);
                return shop;
            }
        });
    }

    public CompletableFuture<SeqPointsPurchase> purchase(
            String itemKey, String targetPlayerUuid, String value) {
        UUID requestId = UUID.randomUUID();
        return ApiClient.getInstance()
                .purchaseSeqPointsItem(requestId, itemKey, targetPlayerUuid, value)
                .thenApply(result -> {
                    synchronized (this) {
                        if (result != null && result.shop() != null && result.shop().isSupported()) {
                            accept(result.shop());
                        }
                    }
                    return result;
                });
    }

    public void tick() {
        if (!ConnectionManager.isConnected()) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (effectLoading || now - lastEffectRefresh < EFFECT_REFRESH_MS) {
                return;
            }
            effectLoading = true;
            lastEffectRefresh = now;
        }
        int requestedGeneration = generation;
        ApiClient.getInstance().getSeqPointsEffects().whenComplete((response, error) -> {
            synchronized (this) {
                effectLoading = false;
                if (requestedGeneration != generation) return;
                if (error != null || response == null || response.schemaVersion() != 1) {
                    return;
                }
                acceptEffects(response.effects());
            }
        });
    }

    public SeqPointsShopEffect effectForUuid(UUID playerUuid) {
        if (playerUuid == null) return null;
        return active(effectsByUuid.get(playerUuid.toString()));
    }

    public SeqPointsShopEffect effectForUsername(String username) {
        if (username == null) return null;
        return active(effectsByUsername.get(username.trim().toLowerCase(Locale.ROOT)));
    }

    public synchronized void reset() {
        generation++;
        state = State.IDLE;
        shop = SeqPointsShop.EMPTY;
        effectsByUuid = Map.of();
        effectsByUsername = Map.of();
        effectLoading = false;
        lastEffectRefresh = 0;
    }

    private void accept(SeqPointsShop response) {
        shop = response;
        state = State.READY;
        acceptEffects(response.activeEffects());
    }

    private void acceptEffects(List<SeqPointsShopEffect> effects) {
        HashMap<String, SeqPointsShopEffect> byUuid = new HashMap<>();
        HashMap<String, SeqPointsShopEffect> byUsername = new HashMap<>();
        for (SeqPointsShopEffect effect : effects == null ? List.<SeqPointsShopEffect>of() : effects) {
            if (effect == null || active(effect) == null) continue;
            if (effect.targetPlayerUuid() != null) {
                byUuid.put(effect.targetPlayerUuid().toLowerCase(Locale.ROOT), effect);
            }
            if (effect.targetUsername() != null) {
                byUsername.put(effect.targetUsername().toLowerCase(Locale.ROOT), effect);
            }
        }
        effectsByUuid = Map.copyOf(byUuid);
        effectsByUsername = Map.copyOf(byUsername);
    }

    private static SeqPointsShopEffect active(SeqPointsShopEffect effect) {
        return effect != null && effect.endsAt() != null && effect.endsAt().isAfter(Instant.now()) ? effect : null;
    }
}
