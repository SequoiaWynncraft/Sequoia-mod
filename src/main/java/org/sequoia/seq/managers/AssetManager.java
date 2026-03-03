package org.sequoia.seq.managers;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.utils.rendering.nvg.NVGContext;
import org.sequoia.seq.utils.rendering.nvg.NVGWrapper;

public class AssetManager {

    private static final String[] ASSET_FILES = new String[] {
            "icon.png",
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
            "annihilation.png",
            "starup.png",
            "cross.png",
    };

    @Getter
    public static ConcurrentHashMap<String, Asset> assetsMap = new ConcurrentHashMap<>();

    public AssetManager() {
        getAssets();
    }

    public void getAssets() {
        try {
            for (String file : ASSET_FILES) {
                String assetName = file.split("\\.")[0];

                String path = "assets/seq/" + file;
                URL resource = AssetManager.class.getClassLoader().getResource(
                        path);
                if (resource == null)
                    continue;

                BufferedImage bufferedImage = ImageIO.read(
                        resource.openStream());
                Identifier identifier = SeqClient.getFileLocation(
                        "textures/icons/" + assetName);
                Asset asset = new Asset(
                        identifier,
                        bufferedImage,
                        bufferedImage.getWidth(),
                        bufferedImage.getHeight());
                assetsMap.put(assetName, asset);
            }
            linkAssetsToNanoVG();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void linkAssetsToNanoVG() {
        assetsMap.forEach((s, asset) -> {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            try {
                ImageIO.write(asset.bufferedImage, "png", os);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            byte[] imageBytes = os.toByteArray();
            ByteBuffer byteBuffer = MemoryUtil.memAlloc(imageBytes.length);
            byteBuffer.put(imageBytes);
            byteBuffer.flip();

            asset.image = NVGWrapper.loadImageFromInputStream(
                    NVGContext.getContext(),
                    byteBuffer);
            MemoryUtil.memFree(byteBuffer);
        });
    }

    public Identifier getAssetLocation(String assetName) {
        return assetsMap.get(assetName).getIdentifier();
    }

    public Asset getAsset(String assetName) {
        if (assetName == null)
            return null;
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
        int image;
        int width;
        int height;

        public Asset(
                Identifier identifier,
                BufferedImage bufferedImage,
                int width,
                int height) {
            this.identifier = identifier;
            this.bufferedImage = bufferedImage;
            this.width = width;
            this.height = height;
        }
    }
}
