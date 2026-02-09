package org.sequoia.seq.managers;

import org.sequoia.seq.accessors.EventBusAccessor;
import org.sequoia.seq.client.SeqClient;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class GameManager implements EventBusAccessor {
    private static final String DEFAULT_RESOURCE_FONT = "arial";

    public GameManager() {
        subscribe(this);
    }

    public void loadFont() {
        //Minecraft font for when people dont want custom font. AKA fuck minecrafts renderer
        loadFontFromResources("/assets/seq/fonts/arial.ttf", DEFAULT_RESOURCE_FONT);


        SeqClient.getFontManager().setSelectedFont(DEFAULT_RESOURCE_FONT);

    }

    public static void loadFontFromResources(String resourcePath, String fontName) {
        try (InputStream input = GameManager.class.getResourceAsStream(resourcePath)) {
            // Load font from the resources folder
            if (input == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            os.write(input.readAllBytes());
            byte[] fontBytes = os.toByteArray();
            ByteBuffer byteBuffer = MemoryUtil.memAlloc(fontBytes.length);
            byteBuffer.put(fontBytes);
            //why do we flip i have no idea someone on stack over flow said to
            byteBuffer.flip();

            // Load font with NanoVG
            SeqClient.getFontManager().addFont(byteBuffer, fontName);
        } catch (IOException e) {
            throw new FontLoadException("Error loading font from resources: " + resourcePath, e);
        }
    }

}
