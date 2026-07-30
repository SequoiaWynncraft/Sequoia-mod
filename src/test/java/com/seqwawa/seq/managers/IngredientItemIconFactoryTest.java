package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.authlib.GameProfile;
import com.seqwawa.seq.model.IngredientGuideEntry.Icon;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class IngredientItemIconFactoryTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void createsCustomModelDataItemStackForAttributeIcon() {
        ItemStack itemStack =
                IngredientItemIconFactory.create(new Icon("attribute", "minecraft:potion", 1719, null));

        assertFalse(itemStack.isEmpty());
        assertEquals(Items.POTION, itemStack.getItem());
        CustomModelData modelData = itemStack.get(DataComponents.CUSTOM_MODEL_DATA);
        assertNotNull(modelData);
        assertEquals(1719f, modelData.getFloat(0));
    }

    @Test
    void createsResolvedPlayerHeadForSkinIcon() {
        Icon icon = new Icon(
                "skin",
                null,
                0,
                "a17e0633e376d2c6bf4c6d45517eb27b4b918a136b23ee0df469279ff45fe16e");
        ItemStack itemStack = IngredientItemIconFactory.create(icon);

        assertEquals(Items.PLAYER_HEAD, itemStack.getItem());
        assertNotNull(itemStack.get(DataComponents.PROFILE));
        assertNotNull(IngredientItemIconFactory.skinProfile(icon));
    }

    @Test
    void createsDirectSkinLookupProfileForDeadBee() {
        String hash = "947322f831e3c168cfbd3e28fe925144b261e79eb39c771349fac55a8126473";
        GameProfile profile =
                IngredientItemIconFactory.skinProfile(new Icon("skin", null, 0, hash));

        assertNotNull(profile);
        String encodedTexture = profile.properties().get("textures").iterator().next().value();
        String textureJson = new String(Base64.getDecoder().decode(encodedTexture), StandardCharsets.UTF_8);
        assertTrue(textureJson.contains("https://textures.minecraft.net/texture/" + hash));
    }

    @Test
    void rejectsInvalidIcons() {
        assertTrue(IngredientItemIconFactory.create(Icon.unavailable()).isEmpty());
        assertTrue(IngredientItemIconFactory.create(new Icon("skin", null, 0, "bad")).isEmpty());
    }
}
