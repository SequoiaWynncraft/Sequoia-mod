package com.seqwawa.seq.halcyon;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.Locale;

public final class HalcyonHeldItem {
	private static final String HALCYON_NAME = "halcyon";

	private HalcyonHeldItem() {
	}

	public static boolean isHoldingHalcyon(Minecraft client) {
		if (client.player == null) return false;

		return isHalcyon(client.player.getMainHandItem()) || isHalcyon(client.player.getOffhandItem());
	}

	private static boolean isHalcyon(ItemStack stack) {
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
