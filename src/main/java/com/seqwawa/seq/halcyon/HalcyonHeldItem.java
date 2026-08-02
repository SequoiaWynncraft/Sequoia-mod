package com.seqwawa.seq.halcyon;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.Locale;

public final class HalcyonHeldItem {
	private static final String HALCYON_NAME = "halcyon";
	private static ItemStack cachedMainHand = ItemStack.EMPTY;
	private static ItemStack cachedOffhand = ItemStack.EMPTY;
	private static boolean holdingHalcyon;

	private HalcyonHeldItem() {
	}

	public static void tick(Minecraft client) {
		if (client.player == null) {
			reset();
			return;
		}
		updateHeldItems(client.player.getMainHandItem(), client.player.getOffhandItem());
	}

	public static boolean isHoldingHalcyon() {
		return holdingHalcyon;
	}

	public static void reset() {
		cachedMainHand = ItemStack.EMPTY;
		cachedOffhand = ItemStack.EMPTY;
		holdingHalcyon = false;
	}

	static void updateHeldItems(ItemStack mainHand, ItemStack offhand) {
		if (ItemStack.isSameItemSameComponents(mainHand, cachedMainHand)
				&& ItemStack.isSameItemSameComponents(offhand, cachedOffhand)) {
			return;
		}
		cachedMainHand = mainHand.copy();
		cachedOffhand = offhand.copy();
		holdingHalcyon = isHalcyon(mainHand) || isHalcyon(offhand);
	}

	static boolean isHalcyon(ItemStack stack) {
		if (stack.isEmpty()) return false;

		if (containsHalcyon(stack.getHoverName())) return true;
		if (containsHalcyon(stack.get(DataComponents.CUSTOM_NAME))) return true;
		if (containsHalcyon(stack.get(DataComponents.ITEM_NAME))) return true;

		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) return false;

		for (Component line : lore.lines()) {
			if (containsHalcyon(line)) return true;
		}

		for (Component line : lore.styledLines()) {
			if (containsHalcyon(line)) return true;
		}

		return false;
	}

	private static boolean containsHalcyon(Component component) {
		return component != null && component.getString().toLowerCase(Locale.ROOT).contains(HALCYON_NAME);
	}
}
