package com.seqwawa.seq.managers;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.seqwawa.seq.model.IngredientGuideEntry.Icon;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ResolvableProfile;

public final class IngredientItemIconFactory {
    private static final String SKIN_TEXTURE_BASE = "https://textures.minecraft.net/texture/";

    private IngredientItemIconFactory() {}

    public static ItemStack create(Icon icon) {
        if (icon == null) {
            return ItemStack.EMPTY;
        }
        return switch (icon.format()) {
            case "attribute" -> attributeIcon(icon);
            case "skin" -> skinIcon(icon.textureHash());
            default -> ItemStack.EMPTY;
        };
    }

    private static ItemStack attributeIcon(Icon icon) {
        Identifier itemId = Identifier.tryParse(icon.itemId());
        if (itemId == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack itemStack = new ItemStack(item);
        itemStack.set(
                DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of((float) icon.modelData()), List.of(), List.of(), List.of()));
        return itemStack;
    }

    private static ItemStack skinIcon(String textureHash) {
        GameProfile profile = skinProfile(textureHash);
        if (profile == null) {
            return ItemStack.EMPTY;
        }
        ItemStack itemStack = new ItemStack(Items.PLAYER_HEAD);
        itemStack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        return itemStack;
    }

    public static GameProfile skinProfile(Icon icon) {
        if (icon == null || !"skin".equals(icon.format())) {
            return null;
        }
        return skinProfile(icon.textureHash());
    }

    private static GameProfile skinProfile(String textureHash) {
        if (textureHash == null || !textureHash.matches("[0-9a-f]{32,64}")) {
            return null;
        }
        String textureJson =
                "{\"textures\":{\"SKIN\":{\"url\":\"" + SKIN_TEXTURE_BASE + textureHash + "\"}}}";
        String encodedTexture =
                Base64.getEncoder().encodeToString(textureJson.getBytes(StandardCharsets.UTF_8));
        UUID profileId = UUID.nameUUIDFromBytes(textureHash.getBytes(StandardCharsets.UTF_8));
        PropertyMap properties = new PropertyMap(
                ImmutableMultimap.of("textures", new Property("textures", encodedTexture)));
        return new GameProfile(profileId, "", properties);
    }
}
