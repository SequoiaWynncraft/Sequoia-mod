package com.seqwawa.seq.managers;

import lombok.Getter;
import lombok.Setter;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;

import java.awt.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

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
        String fontName = name.split("\\.")[0];
        if (UiRenderer.registerFont(fontName, fileLocation)) {
            addLoadedFont(fontName);
        }
    }

    public void addFont(ByteBuffer byteBuffer, String name) {
        String fontName = name.split("\\.")[0];
        if (UiRenderer.registerFont(fontName, byteBuffer)) {
            addLoadedFont(fontName);
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

    public float getStringWidth(String s, String selectedFont) {
        return UiRenderer.measureText(s, selectedFont, fontSize).width();
    }

    public float getFontHeight(String selectedFont) {
        return UiRenderer.measureText("Meow", selectedFont, fontSize).height();
    }

    public void renderTextWithFont(String font, String text, int x, int y, Color color, boolean shadow) {
        float baseX = snapCoord(x);
        float baseY = snapCoord(y);

        if (shadow && shadowsEnabled && shadowOffset > 0.0f) {
            drawCanvasText(font, text, baseX + shadowOffset, baseY + shadowOffset, Color.BLACK);
        }

        drawCanvasText(font, text, baseX, baseY, color);
    }

    protected void drawTextCustom(String text, float x, float y, Color color, boolean shadow) {
        float baseX = snapCoord(x);
        float baseY = snapCoord(y);

        if (shadow && shadowsEnabled && shadowOffset > 0.0f) {
            drawCanvasText(selectedFont, text, baseX + shadowOffset, baseY + shadowOffset,
                    new Color(0, 0, 0, color.getAlpha()));
        }

        drawCanvasText(selectedFont, text, baseX, baseY, color);
    }

    public void drawCenteredText(String font, String text, int centerX, int y, Color color, boolean shadow) {
        renderTextWithFont(font, text, (int) (centerX - getStringWidth(text, font) / 2), y, color, shadow);
    }

    private void drawCanvasText(String font, String text, float x, float y, Color color) {
        UiRenderer.currentCanvas().drawText(text, x, y, new UiCanvas.TextStyle(
                font,
                fontSize,
                color,
                UiCanvas.HorizontalAlign.LEFT,
                UiCanvas.VerticalAlign.TOP));
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
