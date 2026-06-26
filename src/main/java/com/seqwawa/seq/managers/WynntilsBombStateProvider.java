package com.seqwawa.seq.managers;

import com.wynntils.core.components.Models;
import com.wynntils.models.worlds.type.BombInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.BombShareType;

final class WynntilsBombStateProvider implements BombShareManager.BombStateProvider {

    @Override
    public Collection<ActiveBomb> activeBombs() {
        List<ActiveBomb> activeBombs = new ArrayList<>();
        try {
            for (BombInfo bombInfo : Models.Bomb.getBombBells()) {
                if (bombInfo == null || !bombInfo.isActive()) {
                    continue;
                }
                BombShareType type = mapType(bombInfo);
                if (type == null || bombInfo.server() == null || bombInfo.server().isBlank()) {
                    continue;
                }
                activeBombs.add(new ActiveBomb(type, bombInfo.server()));
            }
        } catch (Throwable throwable) {
            SeqClient.LOGGER.warn("[BombShare] Failed to read Wynntils bomb state: {}", throwable.toString());
            return List.of();
        }
        return activeBombs;
    }

    private BombShareType mapType(BombInfo bombInfo) {
        if (bombInfo.bomb() == null) {
            return null;
        }
        return switch (bombInfo.bomb()) {
            case COMBAT_XP -> BombShareType.COMBAT_XP;
            case PROFESSION_XP -> BombShareType.PROFESSION_XP;
            case PROFESSION_SPEED -> BombShareType.PROFESSION_SPEED;
            case LOOT -> BombShareType.LOOT;
            case LOOT_CHEST -> BombShareType.LOOT_CHEST;
            default -> null;
        };
    }
}
