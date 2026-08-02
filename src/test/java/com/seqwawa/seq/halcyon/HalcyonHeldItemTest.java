package com.seqwawa.seq.halcyon;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HalcyonHeldItemTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void resetCache() {
        HalcyonHeldItem.reset();
    }

    @Test
    void updatesCachedResultWhenHeldItemComponentsChange() {
        ItemStack mainHand = new ItemStack(Items.STICK);
        HalcyonHeldItem.updateHeldItems(mainHand, ItemStack.EMPTY);
        assertFalse(HalcyonHeldItem.isHoldingHalcyon());

        mainHand.set(DataComponents.CUSTOM_NAME, Component.literal("Crafted Halcyon"));
        HalcyonHeldItem.updateHeldItems(mainHand, ItemStack.EMPTY);
        assertTrue(HalcyonHeldItem.isHoldingHalcyon());

        mainHand.set(DataComponents.CUSTOM_NAME, Component.literal("Ordinary Relik"));
        HalcyonHeldItem.updateHeldItems(mainHand, ItemStack.EMPTY);
        assertFalse(HalcyonHeldItem.isHoldingHalcyon());
    }

    @Test
    void detectsHalcyonInEitherHand() {
        ItemStack offhand = new ItemStack(Items.STICK);
        offhand.set(DataComponents.ITEM_NAME, Component.literal("HALCYON"));

        HalcyonHeldItem.updateHeldItems(ItemStack.EMPTY, offhand);

        assertTrue(HalcyonHeldItem.isHoldingHalcyon());
    }
}
