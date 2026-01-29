package op.legends.seq.managers;

import com.collarmc.pounce.Subscribe;
import op.legends.seq.accessors.EventBusAccessor;
import op.legends.seq.client.SeqClient;
import op.legends.seq.events.GameStartEvent;
import op.legends.seq.utils.rendering.nvg.NVGContext;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class GameManager implements EventBusAccessor {

    public GameManager() {
        subscribe(this);
    }

    @Subscribe
    public void gameStart(GameStartEvent event) {
        SeqClient.isGameLaunched = true;
    }

    public void loadFonts() {

        String defaultFont = "";
        if (SeqClient.os.equals(SeqClient.OS.WINDOWS)) {
            loadWindowsFonts();
            String fontName = "verdana";
            FontManager.font = NanoVG.nvgCreateFont(NVGContext.context, fontName, "C:\\Windows\\Fonts\\verdana.ttf");
            SeqClient.fontManager.setSelectedFont(fontName);
            defaultFont = fontName;
        } else if (SeqClient.os.equals(SeqClient.OS.MAC)) {
            loadMacFonts();
            String fontName = "keyboard";
            FontManager.font = NanoVG.nvgCreateFont(NVGContext.context, fontName, "/System/Library/Fonts/Geneva.ttf");
            SeqClient.fontManager.setSelectedFont(fontName);
            defaultFont = fontName;

        } else if (SeqClient.os.equals(SeqClient.OS.LINUX)) {
            loadLinuxFonts();
            String fontName = "verdana";
            FontManager.font = NanoVG.nvgCreateFont(NVGContext.context, "verdana", "/home/" + System.getProperty("user.name") + "/Documents/fonts/Poppins-Medium.ttf");
            SeqClient.fontManager.setSelectedFont(fontName);
            defaultFont = fontName;
        }
        //Minecraft font for when people dont want custom font. AKA fuck minecrafts renderer
        loadFontFromResources("/assets/seq/fonts/minecraft.ttf", "minecraft");
        // if font is not found we load the default which is included in the jar
//        if (Core.customFont.getValue() && !SeqClient.fontManager.getLoadedFontNames().contains(defaultFont) && !SeqClient.fontManager.altFont.isBlank() && SeqClient.fontManager.altFont.length() < 2)
//            SeqClient.fontManager.setSelectedFont("jetbrainsmono");
//        else if (Core.customFont.getValue() && SeqClient.fontManager.altFont.length() > 2) {
//            SeqClient.fontManager.setSelectedFont(SeqClient.fontManager.altFont);
//        } else if (!Core.customFont.getValue()) {
        SeqClient.fontManager.setSelectedFont("minecraft");
//        }

    }


    public void loadWindowsFonts() {
        try {
            File directoryPath = new File("C:\\Windows\\Fonts\\");
            //List of all files and directories
            File[] filesList = directoryPath.listFiles();
            if (filesList == null || filesList.length == 0) throw new RuntimeException("No Fonts Found: Windows User!");

            for (File file : filesList) {
                // if its a ttf and doesnt end with __ or start with samsung we will use it as a useable font
                if (!file.getName().substring(file.getName().length() - 3).equalsIgnoreCase("ttf") || file.getName().toLowerCase().startsWith("samsung") || file.getName().toLowerCase().endsWith("___.ttf"))
                    continue;

                SeqClient.fontManager.addFont(file.getAbsolutePath(), file.getName().toLowerCase());

            }
        } catch (Exception e) {

            System.out.println(e.getMessage());
        }
    }

    public void loadMacFonts() {
        try {
            File directoryPath = new File("/System/Library/Fonts/");
            //List of all files and directories
            File[] filesList = directoryPath.listFiles();
            if (filesList == null || filesList.length == 0) throw new RuntimeException("No Fonts Found: Mac User!");

            for (File file : filesList) {
                // if its a ttf and doesnt end with __ or start with samsung we will use it as a useable font
                SeqClient.fontManager.addFont(file.getAbsolutePath(), file.getName().toLowerCase());

            }
        } catch (Exception e) {

            System.out.println(e.getMessage());
        }
    }

    public void loadLinuxFonts() {
        try {
            File directoryPath = new File("/home/" + System.getProperty("user.name") + "/Documents/fonts/");
            //List of all files and directories
            File[] filesList = directoryPath.listFiles();
            if (filesList == null || filesList.length == 0) throw new RuntimeException("No Fonts Found: Linux User!");

            for (File file : filesList) {
                // if its a ttf and doesnt end with __ or start with samsung we will use it as a useable font
                SeqClient.fontManager.addFont(file.getAbsolutePath(), file.getName().toLowerCase());

            }
        } catch (Exception e) {

            System.out.println(e.getMessage());
        }
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
            //Also MemoryUtil should be used for created byte buffers NanoVG "REQUIRES" it
            ByteBuffer byteBuffer = MemoryUtil.memAlloc(fontBytes.length);
            byteBuffer.put(fontBytes);
            //why do we flip i have no idea someone on stack over flow said to
            byteBuffer.flip();


            // Load font with NanoVG
            SeqClient.fontManager.addFont(byteBuffer, fontName);


        } catch (IOException e) {
            throw new RuntimeException("Error loading font from resources", e);
        }
    }

}
