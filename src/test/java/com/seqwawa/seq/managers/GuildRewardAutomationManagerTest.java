package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class GuildRewardAutomationManagerTest {

    @Test
    void parseRewardActionsExtractsHotbarButtonsAmountsAndTypes() {
        List<GuildRewardAutomationManager.RewardAction> actions =
                GuildRewardAutomationManager.parseRewardActions(List.of(
                        "Press 1 to send 1 Aspect",
                        "Press 2 to send 1 Guild Tome",
                        "Press 3 to send 1,024 Emeralds"));

        assertEquals(3, actions.size());
        assertEquals(new GuildRewardAutomationManager.RewardAction(
                GuildRewardAutomationManager.RewardType.ASPECT, 0, 1), actions.get(0));
        assertEquals(new GuildRewardAutomationManager.RewardAction(
                GuildRewardAutomationManager.RewardType.TOME, 1, 1), actions.get(1));
        assertEquals(new GuildRewardAutomationManager.RewardAction(
                GuildRewardAutomationManager.RewardType.EMERALDS, 2, 1024), actions.get(2));
    }

    @Test
    void parseRewardActionsAcceptsArticleAmountsAndReorderedLore() {
        List<GuildRewardAutomationManager.RewardAction> actions =
                GuildRewardAutomationManager.parseRewardActions(List.of(
                        "Rank: Chief",
                        "Press 3 to send an Aspect",
                        "Joined: 2024/01/19",
                        "Press 1 to send one Guild Tome"));

        assertEquals(2, actions.size());
        assertEquals(new GuildRewardAutomationManager.RewardAction(
                GuildRewardAutomationManager.RewardType.ASPECT, 2, 1), actions.get(0));
        assertEquals(new GuildRewardAutomationManager.RewardAction(
                GuildRewardAutomationManager.RewardType.TOME, 0, 1), actions.get(1));
    }
}
