package com.seqwawa.seq.managers;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.resources.Identifier;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.SeqBadge;
import com.seqwawa.seq.model.SeqBadgeTier;
import com.seqwawa.seq.model.SeqBadgeType;
import com.seqwawa.seq.utils.rendering.UiImage;
import com.seqwawa.seq.utils.rendering.UiRenderer;

public class AssetManager {

    private static final String[] ASSET_FILES = new String[] {
        "icon.png",
        "seqmod_bg.png",
        "archer.png",
        "assassin.png",
        "warrior.png",
        "mage.png",
        "shaman.png",
        "star.png",
        "notg.png",
        "nol.png",
        "tcc.png",
        "tna.png",
        "twp.png",
        "annihilation.png",
        "textures/map/world_event_icon.png",
        "starup.png",
        "cross.png",
        "gaz_ears.png",
        "textures/map/HQ_icon.png",
        "textures/leaf/leaf.png",
        "textures/leaf/leaf_glyph.png",
        "textures/unlock/unlock_emblem_nol.png",
        "textures/unlock/unlock_emblem_notg.png",
        "textures/unlock/unlock_emblem_tcc.png",
        "textures/unlock/unlock_emblem_tna.png",
        "textures/unlock/unlock_emblem_twp.png",
        "textures/unlock/unlock_medal_mini_level.png",
        "textures/unlock/unlock_halo_bronze.png",
        "textures/unlock/unlock_halo_diamond.png",
        "textures/unlock/unlock_halo_gold.png",
        "textures/unlock/unlock_halo_level.png",
        "textures/unlock/unlock_halo_mythril.png",
        "textures/unlock/unlock_halo_obsidian.png",
        "textures/unlock/unlock_halo_platinum.png",
        "textures/unlock/unlock_halo_silver.png",
        "textures/unlock/unlock_glow_bronze.png",
        "textures/unlock/unlock_glow_diamond.png",
        "textures/unlock/unlock_glow_gold.png",
        "textures/unlock/unlock_glow_level.png",
        "textures/unlock/unlock_glow_mythril.png",
        "textures/unlock/unlock_glow_obsidian.png",
        "textures/unlock/unlock_glow_platinum.png",
        "textures/unlock/unlock_glow_silver.png",
        "textures/unlock/unlock_icon_nol.png",
        "textures/unlock/unlock_icon_notg.png",
        "textures/unlock/unlock_icon_tcc.png",
        "textures/unlock/unlock_icon_tna.png",
        "textures/unlock/unlock_icon_twp.png",
        "textures/unlock/unlock_leaf_a.png",
        "textures/unlock/unlock_leaf_b.png",
        "textures/unlock/unlock_leaf_c.png",
        "textures/unlock/unlock_medal_bronze.png",
        "textures/unlock/unlock_medal_diamond.png",
        "textures/unlock/unlock_medal_flash.png",
        "textures/unlock/unlock_medal_gold.png",
        "textures/unlock/unlock_medal_level.png",
        "textures/unlock/unlock_medal_mythril.png",
        "textures/unlock/unlock_medal_obsidian.png",
        "textures/unlock/unlock_medal_platinum.png",
        "textures/unlock/unlock_medal_ribbon_bronze.png",
        "textures/unlock/unlock_medal_ribbon_diamond.png",
        "textures/unlock/unlock_medal_ribbon_flash.png",
        "textures/unlock/unlock_medal_ribbon_gold.png",
        "textures/unlock/unlock_medal_ribbon_level.png",
        "textures/unlock/unlock_medal_ribbon_mythril.png",
        "textures/unlock/unlock_medal_ribbon_obsidian.png",
        "textures/unlock/unlock_medal_ribbon_platinum.png",
        "textures/unlock/unlock_medal_ribbon_silver.png",
        "textures/unlock/unlock_medal_ring_bronze.png",
        "textures/unlock/unlock_medal_ring_diamond.png",
        "textures/unlock/unlock_medal_ring_flash.png",
        "textures/unlock/unlock_medal_ring_gold.png",
        "textures/unlock/unlock_medal_ring_level.png",
        "textures/unlock/unlock_medal_ring_mythril.png",
        "textures/unlock/unlock_medal_ring_obsidian.png",
        "textures/unlock/unlock_medal_ring_platinum.png",
        "textures/unlock/unlock_medal_ring_silver.png",
        "textures/unlock/unlock_medal_silver.png",
        "textures/unlock/unlock_raid.png",
        "textures/unlock/unlock_rays_0.png",
        "textures/unlock/unlock_rays_1.png",
        "textures/unlock/unlock_rays_2.png",
        "textures/unlock/unlock_rays_3.png",
        "textures/unlock/unlock_rays_4.png",
        "textures/unlock/unlock_rays_5.png",
        "textures/unlock/unlock_rays_6.png",
        "textures/unlock/unlock_rays_7.png",
        "textures/unlock/unlock_ring_0.png",
        "textures/unlock/unlock_ring_1.png",
        "textures/unlock/unlock_ring_2.png",
        "textures/unlock/unlock_ring_3.png",
        "textures/unlock/unlock_ring_4.png",
        "textures/unlock/unlock_ring_5.png",
        "textures/unlock/unlock_sparkle_0.png",
        "textures/unlock/unlock_sparkle_1.png",
        "textures/unlock/unlock_sparkle_2.png",
        "textures/unlock/unlock_vignette.png",
        "textures/unlock/unlock_war.png",
    };

    @Getter
    public static ConcurrentHashMap<String, Asset> assetsMap = new ConcurrentHashMap<>();

    public AssetManager() {
        getAssets();
    }

    public void getAssets() {
        try {
            List<String> files = new ArrayList<>(List.of(ASSET_FILES));
            for (SeqBadgeType type : SeqBadgeType.values()) {
                for (SeqBadgeTier tier : SeqBadgeTier.values()) {
                    if ((type == SeqBadgeType.TCC) != (tier == SeqBadgeTier.SINGLE)) continue;
                    files.add(new SeqBadge(type, tier).textureId().getPath());
                }
            }
            for (String file : files) {
                int nameStart = file.lastIndexOf('/') + 1;
                int extensionStart = file.lastIndexOf('.');
                String assetName = file.substring(nameStart, extensionStart).toLowerCase(Locale.ROOT);
                boolean badge = file.startsWith("textures/badges/");
                if (badge) assetName = "badge:" + assetName;

                String path = "assets/seq/" + file;
                URL resource = AssetManager.class.getClassLoader().getResource(path);
                if (resource == null) {
                    continue;
                }

                BufferedImage bufferedImage = ImageIO.read(resource);
                Identifier identifier = SeqClient.getFileLocation(badge ? file : "textures/icons/" + assetName);
                Asset asset = new Asset(identifier, bufferedImage, bufferedImage.getWidth(), bufferedImage.getHeight());
                assetsMap.put(assetName, asset);
            }
            linkAssetsToRenderer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void linkAssetsToRenderer() {
        assetsMap.forEach((s, asset) -> {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            try {
                ImageIO.write(asset.bufferedImage, "png", os);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            byte[] imageBytes = os.toByteArray();
            asset.image = UiRenderer.createImage(ByteBuffer.wrap(imageBytes), true);
        });
    }

    public Identifier getAssetLocation(String assetName) {
        return assetsMap.get(assetName).getIdentifier();
    }

    public Asset getAsset(String assetName) {
        if (assetName == null) return null;
        return assetsMap.get(assetName);
    }

    public Enumeration<String> allAssets() {
        return assetsMap.keys();
    }

    @Getter
    @Setter
    public static class Asset {

        Identifier identifier;
        BufferedImage bufferedImage;
        UiImage image;
        int width;
        int height;

        public Asset(Identifier identifier, BufferedImage bufferedImage, int width, int height) {
            this.identifier = identifier;
            this.bufferedImage = bufferedImage;
            this.width = width;
            this.height = height;
        }
    }
}
