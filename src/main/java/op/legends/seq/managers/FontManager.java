package op.legends.seq.managers;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import op.legends.seq.client.SeqClient;
import op.legends.seq.utils.rendering.nvg.NVGContext;
import op.legends.seq.utils.rendering.nvg.string.coloredstring.ColoredString;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.pattern.TextRenderer;
import org.lwjgl.nanovg.NVGColor;

import java.awt.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import static op.legends.seq.client.SeqClient.mc;
import static org.lwjgl.nanovg.NanoVG.*;

@Getter
public class FontManager {

    public static int font;
    @Getter
    public static float fontSize = 9f;
    @Setter
    public String altFont = "";
    public String selectedFont = "verdana";
    private final ArrayList<String> loadedFontNames = new ArrayList<>();
    NVGColor nvgColor;


    public FontManager() {
    }

    public void addLoadedFont(String fontName) {
        this.loadedFontNames.add(fontName);
    }

    public ArrayList<String> getLoadedFontNames() {
        return this.loadedFontNames;
    }


    @Deprecated
    public int getWidth(String text) {
        Font font = mc.font; //Core.customFont.getValue() ? SeqClient.tr[0] :
        return font.width(text);
    }

    public void addFont(String fileLocation, String name) {
        nvgCreateFont(NVGContext.context, name.split("\\.")[0], fileLocation);
        addLoadedFont(name.split("\\.")[0]);
    }

    public void addFont(ByteBuffer byteBuffer, String name) {
        nvgCreateFontMem(NVGContext.context, name.split("\\.")[0], byteBuffer, false);
        addLoadedFont(name.split("\\.")[0]);
    }

    public Font getFont() {
        return mc.font; //Core.customFont.getValue() ? SeqClient.tr[0] :
    }

    public void renderTextMultiColorCenteredString(GuiGraphics context, int x, int y, boolean shadow, ColoredString... strings) {
        renderTextMultiColorCustom((int) (x - (getStringsWidth(strings) / 2)), y, shadow, strings);
    }

    public void renderTextMultiColorLeftString(GuiGraphics context, int x, int y, boolean shadow, ColoredString... strings) {
        renderTextMultiColorCustom((int) (x - getStringsWidth(strings)), y, shadow, strings);
    }

    public void renderTextMultiColor(GuiGraphics context, int x, int y, boolean shadow, ColoredString... strings) {
        renderTextMultiColorCustom(x, y, shadow, strings);
    }

    public void renderTextMultiColorMc(GuiGraphics context, int x, int y, ColoredString... strings) {
        String message = "";

        float xOffset = 0;

        for (ColoredString data : strings) {

            context.drawCenteredString(mc.font, data.getText(), (int) (x + xOffset), y, data.getColor().getRGB());
            xOffset += mc.font.width(data.getText());
        }

        context.drawCenteredString(mc.font, message, x, y, Color.WHITE.getRGB());

    }


    public void renderTextMultiColorCustom(int x, int y, boolean shadow, ColoredString... strings) {
        long vg = NVGContext.context;
        float xOffset = 0;


        for (int i = 0; i < strings.length; i++) {
            ColoredString data = strings[i];

            //shadows
            if (shadow) {
                nvgFontFace(vg, selectedFont);
                NVGColor nvgColor = NVGContext.nvgColor(Color.BLACK);
                nvgFillColor(vg, nvgColor);
                nvgFontSize(vg, fontSize);
                nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
                nvgText(vg, x + xOffset + .55f, y + .55f, data.getText());

            }

            //useful stuff


            nvgFontFace(vg, selectedFont);
            NVGColor nvgColor = NVGContext.nvgColor(data.getColor());
            nvgFillColor(vg, nvgColor);
            nvgFontSize(vg, fontSize);
            nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
            nvgText(vg, x + xOffset, y, data.getText());

            String s = data.getText().endsWith(" ") ? " " : "";
            xOffset += getStringWidth(data.getText() + s, selectedFont);


        }
    }

    public void drawLeftStringFont(GuiGraphics context, String font, String text, int x, int y, Color color, boolean shadow) {
        renderTextWithFont(font, text, (int) (x - SeqClient.fontManager.getStringWidth(text, font)), y, color, shadow);
    }


    public void drawLeftStringText(GuiGraphics context, String font, String text, int x, int y, Color color, boolean shadow) {
        drawText(context, text, (int) (x - SeqClient.fontManager.getStringWidth(text, font)), y, color, shadow);
    }

    public void drawText(GuiGraphics context, String text, float x, float y, Color color, boolean shadow) {
        drawTextCustom(text, x, y, color, shadow);

    }

    public void setSelectedFont(String s) {
        if (loadedFontNames.contains(s)) {
            this.selectedFont = s;
//            Command.sendClientSideMessage("Changed Font to " + s, true);
        } else {
//            Command.sendClientSideMessage("Not a Font!", false);
        }
    }

    public float getStringsWidth(ColoredString... strings) {
        if (true) //!Core.customFont.getValue()
            return mc.font.width(ColoredString.getString(strings));
        float size = 0;
        float[] bounds = new float[4]; // Left, top, width, height
        for (ColoredString s : strings) {
            nvgFontSize(NVGContext.context, fontSize);
            nvgFontFace(NVGContext.context, selectedFont);
            int startIndex = s.getText().length() - 1;
            String val = s.getText().substring(Math.max(startIndex, 0)).equalsIgnoreCase(" ") ? " " : "";
            nvgTextBounds(NVGContext.context, 0, 0, s.getText() + val, bounds);
            size += bounds[2];
        }

        return size;
    }

    public float getStringWidth(String s, String selectedFont) {
        float[] bounds = new float[4]; // Left, top, width, height
        nvgFontSize(NVGContext.context, fontSize);
        nvgFontFace(NVGContext.context, selectedFont);
        nvgTextBounds(NVGContext.context, 0, 0, s, bounds);

        return bounds[2];
    }

    public float getFontHeight(String selectedFont) {
        float[] bounds = new float[4]; // Left, top, width, height
        nvgFontSize(NVGContext.context, fontSize);
        nvgFontFace(NVGContext.context, selectedFont);
        nvgTextBounds(NVGContext.context, 0, 0, "Meow", bounds);

        return bounds[3] + 1;
    }

    public void renderTextWithFont(String font, String text, int x, int y, Color color, boolean shadow) {
        long vg = NVGContext.context;


        if (shadow) {
            nvgFontFace(vg, font);
            NVGColor nvgColor1 = NVGContext.nvgColor(Color.BLACK);
            nvgFillColor(vg, nvgColor1);
            nvgFontSize(vg, fontSize);
            nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
            nvgText(vg, x + .55f, y + .55f, text);
        }

        nvgFontFace(vg, font);
        NVGColor nvgColor = NVGContext.nvgColor(color);
        nvgFillColor(vg, nvgColor);
        nvgFontSize(vg, fontSize);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        nvgText(vg, x, y, text);


    }

    protected void drawTextCustom(String text, float x, float y, Color color, boolean shadow) {
        long vg = NVGContext.context;

        if (shadow) {
            nvgFontFace(vg, selectedFont);
            NVGColor nvgColor1 = NVGContext.nvgColor(Color.BLACK);
            nvgFillColor(vg, nvgColor1);
            nvgFontSize(vg, fontSize);
            nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
            nvgText(vg, x + 1, y + 1, text);
        }

        nvgFontFace(vg, selectedFont);
        NVGColor nvgColor = NVGContext.nvgColor(color);
        nvgFillColor(vg, nvgColor);
        nvgFontSize(vg, fontSize);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        nvgText(vg, x, y, text);


    }
    //The below method will stop the rendering process this would usually go at the end of your master render loop

    public void close() {
        long vg = NVGContext.context;
        nvgEndFrame(vg);
        NVGContext.restoreState();


    }

    //The below method will start the rendering process this would usually go at the beginning of your master render loop
    public void start() {
        try {
            if (!loadedFontNames.contains(selectedFont)) selectedFont = loadedFontNames.getFirst();
        } catch (Exception e) {
            SeqClient.LOGGER.debug("Failed to get first loaded font name");
        }

        long vg = NVGContext.context;
        nvgBeginFrame(vg, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), (float) mc.getWindow().getGuiScale());
        nvgFontFace(vg, selectedFont);
        nvgFontSize(vg, fontSize);

        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);

    }

    public void drawCenteredText(GuiGraphics guiGraphics, String font, String text, int centerX, int y, Color color, boolean shadow) {
        renderTextWithFont(font, text, (int) (centerX - getStringWidth(text, font) / 2), y, color, shadow);
    }


    private void renderTextMc(GuiGraphics context, String text, int x, int y, Color color) {

        context.drawCenteredString(mc.font, text, x, y, color.getRGB());
    }

}