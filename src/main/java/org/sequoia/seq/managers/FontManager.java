package org.sequoia.seq.managers;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.utils.rendering.nvg.NVGContext;
import org.sequoia.seq.utils.rendering.nvg.string.coloredstring.ColoredString;
import org.lwjgl.nanovg.NVGColor;

import java.awt.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.sequoia.seq.client.SeqClient.mc;
import static org.lwjgl.nanovg.NanoVG.*;

@Getter
public class FontManager {

    private static int font;
    @Getter
    @Setter
    private static float fontSize = 9f;
    @Setter
    public String altFont = "";
    private String selectedFont = "mc";
    @Setter
    private boolean shadowsEnabled = true;
    @Setter
    private float shadowOffset = 0.5f;
    @Setter
    private boolean pixelSnap = false;
    private final List<String> loadedFontNames = new ArrayList<>();


    public FontManager() {}

    public void addLoadedFont(String fontName) {
        this.loadedFontNames.add(fontName);
    }

    public static int getFontId() {
        return font;
    }

    public static void setFontId(int fontId) {
        font = fontId;
    }


    public int getWidth(String text) {
        return (int) getStringWidth(text, selectedFont);
    }

    public void addFont(String fileLocation, String name) {
        nvgCreateFont(NVGContext.getContext(), name.split("\\.")[0], fileLocation);
        addLoadedFont(name.split("\\.")[0]);
    }

    public void addFont(ByteBuffer byteBuffer, String name) {
        nvgCreateFontMem(NVGContext.getContext(), name.split("\\.")[0], byteBuffer, false);
        addLoadedFont(name.split("\\.")[0]);
    }

    public void renderTextMultiColorCenteredString(int x, int y, boolean shadow, ColoredString... strings) {
        renderTextMultiColorCustom((int) (x - (getStringsWidth(strings) / 2)), y, shadow, strings);
    }

    public void renderTextMultiColorLeftString(int x, int y, boolean shadow, ColoredString... strings) {
        renderTextMultiColorCustom((int) (x - getStringsWidth(strings)), y, shadow, strings);
    }

    public void renderTextMultiColor(int x, int y, boolean shadow, ColoredString... strings) {
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
        long vg = NVGContext.getContext();
        float xOffset = 0;


        for (int i = 0; i < strings.length; i++) {
            ColoredString data = strings[i];
            float baseX = snapCoord(x + xOffset);
            float baseY = snapCoord(y);

            //shadows
            if (shadow && shadowsEnabled && shadowOffset > 0.0f) {
                nvgFontFace(vg, selectedFont);
                NVGColor shadowColor = NVGContext.nvgColor(Color.BLACK);
                nvgFillColor(vg, shadowColor);
                nvgFontSize(vg, fontSize);
                nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
                nvgText(vg, baseX + shadowOffset, baseY + shadowOffset, data.getText());

            }

            //useful stuff
            nvgFontFace(vg, selectedFont);
            NVGColor textColor = NVGContext.nvgColor(data.getColor());
            nvgFillColor(vg, textColor);
            nvgFontSize(vg, fontSize);
            nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
            nvgText(vg, baseX, baseY, data.getText());

            String s = data.getText().endsWith(" ") ? " " : "";
            xOffset += getStringWidth(data.getText() + s, selectedFont);


        }
    }

    public void drawLeftStringFont(String font, String text, int x, int y, Color color, boolean shadow) {
        renderTextWithFont(font, text, (int) (x - SeqClient.getFontManager().getStringWidth(text, font)), y, color, shadow);
    }


    public void drawLeftStringText(String font, String text, int x, int y, Color color, boolean shadow) {
        drawText(text, (int) (x - SeqClient.getFontManager().getStringWidth(text, font)), y, color, shadow);
    }

    public void drawText(String text, float x, float y, Color color, boolean shadow) {
        drawTextCustom(text, x, y, color, shadow);
    }

    public void setSelectedFont(String s) {
        if (loadedFontNames.contains(s)) {
            this.selectedFont = s;
        }
    }

    public float getStringsWidth(ColoredString... strings) {
        // Use NanoVG for custom font measurement
        float size = 0;
        float[] bounds = new float[4]; // Left, top, width, height
        for (ColoredString s : strings) {
            nvgFontSize(NVGContext.getContext(), fontSize);
            nvgFontFace(NVGContext.getContext(), selectedFont);
            int startIndex = s.getText().length() - 1;
            String val = s.getText().substring(Math.max(startIndex, 0)).equalsIgnoreCase(" ") ? " " : "";
            nvgTextBounds(NVGContext.getContext(), 0, 0, s.getText() + val, bounds);
            size += bounds[2];
        }

        return size;
    }

    public float getStringWidth(String s, String selectedFont) {
        float[] bounds = new float[4]; // minx, miny, maxx, maxy
        nvgFontSize(NVGContext.getContext(), fontSize);
        nvgFontFace(NVGContext.getContext(), selectedFont);
        nvgTextBounds(NVGContext.getContext(), 0, 0, s, bounds);

        return bounds[2] - bounds[0];
    }

    public float getFontHeight(String selectedFont) {
        float[] bounds = new float[4]; // minx, miny, maxx, maxy
        nvgFontSize(NVGContext.getContext(), fontSize);
        nvgFontFace(NVGContext.getContext(), selectedFont);
        nvgTextBounds(NVGContext.getContext(), 0, 0, "Meow", bounds);

        return bounds[3] - bounds[1];
    }

    public void renderTextWithFont(String font, String text, int x, int y, Color color, boolean shadow) {
        long vg = NVGContext.getContext();
        float baseX = snapCoord(x);
        float baseY = snapCoord(y);

        if (shadow && shadowsEnabled && shadowOffset > 0.0f) {
            nvgFontFace(vg, font);
            NVGColor nvgColor1 = NVGContext.nvgColor(Color.BLACK);
            nvgFillColor(vg, nvgColor1);
            nvgFontSize(vg, fontSize);
            nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
            nvgText(vg, baseX + shadowOffset, baseY + shadowOffset, text);
        }

        nvgFontFace(vg, font);
        NVGColor nvgColor = NVGContext.nvgColor(color);
        nvgFillColor(vg, nvgColor);
        nvgFontSize(vg, fontSize);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        nvgText(vg, baseX, baseY, text);


    }

    protected void drawTextCustom(String text, float x, float y, Color color, boolean shadow) {
        long vg = NVGContext.getContext();
        float baseX = snapCoord(x);
        float baseY = snapCoord(y);

        nvgGlobalAlpha(vg, 1.0f);
        nvgFontBlur(vg, 0.0f);

        if (shadow && shadowsEnabled && shadowOffset > 0.0f) {
            nvgFontFace(vg, selectedFont);
            NVGColor nvgColor1 = NVGContext.nvgColor(new Color(0,0,0, color.getAlpha()));
            nvgFillColor(vg, nvgColor1);
            nvgFontSize(vg, fontSize);
            nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
            nvgText(vg, baseX + shadowOffset, baseY + shadowOffset, text);
        }

        nvgFontFace(vg, selectedFont);
        NVGColor nvgColor = NVGContext.nvgColor(color);
        nvgFillColor(vg, nvgColor);
        nvgFontSize(vg, fontSize);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        nvgText(vg, baseX, baseY, text);
    }

    public void drawCenteredText(String font, String text, int centerX, int y, Color color, boolean shadow) {
        renderTextWithFont(font, text, (int) (centerX - getStringWidth(text, font) / 2), y, color, shadow);
    }

    private float snapCoord(float value) {
        if (!pixelSnap) {
            // Return as-is for smooth sub-pixel positioning
            return value;
        }
        // NVG is rendered at 2x; snap to 0.5 so we align to physical pixels.
        return Math.round(value * 2.0f) / 2.0f;
    }

}
