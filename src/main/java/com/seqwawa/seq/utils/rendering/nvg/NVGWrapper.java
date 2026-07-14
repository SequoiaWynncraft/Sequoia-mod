package com.seqwawa.seq.utils.rendering.nvg;

import lombok.experimental.UtilityClass;
import com.seqwawa.seq.managers.AssetManager;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.nanovg.NanoVG;

import java.awt.*;

import static org.lwjgl.nanovg.NanoVG.*;

@UtilityClass
public class NVGWrapper {
    public void drawRect(long context, float x, float y, float w, float h, Color color) {
        nvgBeginPath(context);
        nvgRect(context, x, y, w, h);
        NVGColor nvgColor = nvgColor(color);
        nvgFillColor(context, nvgColor);
        nvgFill(context);
        nvgClosePath(context);
        nvgColor.free();
    }
    
    public static void drawRectOutline(long context, float x, float y, float w, float h, float thickness, Color color) {
        nvgBeginPath(context);
        nvgRect(context, x, y, w, h);
        NVGColor nvgColor = nvgColor(color);
        nvgStrokeWidth(context, thickness);
        nvgStrokeColor(context, nvgColor);
        nvgStroke(context);
        nvgClosePath(context);
        nvgColor.free();
    }

    public static void drawRoundedRect(long context, float x, float y, float w, float h, float radius, Color color) {
        nvgBeginPath(context);
        nvgRoundedRect(context, x, y, w, h, radius);
        NVGColor nvgColor = nvgColor(color);
        nvgFillColor(context, nvgColor);
        nvgFill(context);
        nvgClosePath(context);
        nvgColor.free();
    }

    public static void drawHorizontalGradient(long context, float x, float y, float width, float height, Color color1, Color color2) {
        nvgBeginPath(context);
        nvgRect(context, x, y, width, height);
        NVGColor nvgColor1 = nvgColor(color1);
        NVGColor nvgColor2 = nvgColor(color2);
        NVGPaint paint2 = NVGPaint.calloc();
        NVGPaint paint = NanoVG.nvgLinearGradient(context, x, y, x + width, y,
                nvgColor1,
                nvgColor2, paint2);
        nvgFillPaint(context, paint);
        nvgFill(context);
        nvgClosePath(context);
        nvgColor1.free();
        nvgColor2.free();
        paint2.free();
    }
    public void drawImage(long context, AssetManager.Asset asset, float x, float y, float w, float h, float alpha) {
        if (asset != null && asset.getImage() != null) {
            UiRenderer.currentCanvas().drawImage(asset.getImage(), x, y, w, h, alpha / 255f);
        }
    }

    public static NVGColor nvgColor(Color color) {
        NVGColor nvgColor = NVGColor.create();
        nvgColor.r(color.getRed() / 255.0f);
        nvgColor.g(color.getGreen() / 255.0f);
        nvgColor.b(color.getBlue() / 255.0f);
        nvgColor.a(color.getAlpha() / 255.0f);
        return nvgColor;
    }
}
