package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class GameManager {
    private static final String DEFAULT_RESOURCE_FONT = "mc";

    public void loadFont() {
        //Minecraft font for when people dont want custom font. AKA fuck minecrafts renderer
        loadFontFromResources("/assets/seq/fonts/mc.ttf", DEFAULT_RESOURCE_FONT);


        SeqClient.getFontManager().setSelectedFont(DEFAULT_RESOURCE_FONT);

    }

    public static void loadFontFromResources(String resourcePath, String fontName) {
        try (InputStream input = GameManager.class.getResourceAsStream(resourcePath)) {
            // Load font from the resources folder
            if (input == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }

            SeqClient.getFontManager().addFont(ByteBuffer.wrap(input.readAllBytes()), fontName);
        } catch (IOException e) {
            throw new FontLoadException("Error loading font from resources: " + resourcePath, e);
        }
    }

}
