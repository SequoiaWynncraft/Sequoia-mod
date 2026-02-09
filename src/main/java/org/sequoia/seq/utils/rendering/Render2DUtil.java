package org.sequoia.seq.utils.rendering;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.*;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.utils.rendering.nvg.NVGContext;
import org.sequoia.seq.utils.rendering.nvg.NVGWrapper;

import java.awt.*;
import java.lang.Math;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.sequoia.seq.client.SeqClient.mc;

public class Render2DUtil {

    private final Matrix4f cachedViewMatrix = new Matrix4f();
    private final Matrix4f cachedProjectionMatrix = new Matrix4f();
    private int cachedScreenWidth;
    private int cachedScreenHeight;
    private boolean projectionReady;

    public Vector2f worldToScreen(Vector3f worldPos, Matrix4f viewMatrix, Matrix4f projectionMatrix, int screenWidth, int screenHeight, boolean allowBehind) {
        Vector4f clipSpacePos = new Vector4f(worldPos, 1.0f);
        viewMatrix.transform(clipSpacePos);
        projectionMatrix.transform(clipSpacePos);
        if (clipSpacePos.w == 0.0f) return null;
        boolean behind = clipSpacePos.w < 0.0f;
        float ndcX = clipSpacePos.x / clipSpacePos.w;
        float ndcY = clipSpacePos.y / clipSpacePos.w;
        if (behind && allowBehind) { ndcX = -ndcX; ndcY = -ndcY; }
        if (behind && !allowBehind) return null;
        float screenX = ((ndcX + 1.0f) / 2.0f) * screenWidth;
        float screenY = ((1.0f - ndcY) / 2.0f) * screenHeight;
        return new Vector2f(screenX, screenY);
    }

    public static double lerp(double previous, double current, double tickDelta) { return previous + (current - previous) * tickDelta; }
    public static float lerp(float previous, float current, float tickDelta) { return previous + (current - previous) * tickDelta; }

    public void render2DAtWorldPos(double worldX, double worldY, double worldZ, float tickdelta, float scale, boolean behind, RenderCallback renderAction) {
        if (!beginWorldProjection(tickdelta)) {
            return;
        }

        // Use absolute world position with lookAt view matrix
        Vector2f screenPos = worldToScreen(
                new Vector3f((float) worldX, (float) worldY, (float) worldZ),
                cachedViewMatrix,
                cachedProjectionMatrix,
                cachedScreenWidth,
                cachedScreenHeight,
                behind
        );
        if (screenPos == null) return;

        pushTransform();
        scale(scale, scale);
        renderAction.handleRender((screenPos.x() / scale), (screenPos.y() / scale));
        popTransform();
    }

    public boolean beginWorldProjection(float tickdelta) {
        projectionReady = false;
        if (mc.getCameraEntity() == null) {
            return false;
        }

        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camPos = cam.position();

        // Get camera orientation vectors
        Vector3fc forward = cam.forwardVector();
        Vector3fc up = cam.upVector();

        // Calculate target position in world space
        Vector3f target = new Vector3f(
                (float) (camPos.x + forward.x()),
                (float) (camPos.y + forward.y()),
                (float) (camPos.z + forward.z())
        );

        // Build proper view matrix using lookAt
        cachedViewMatrix.setLookAt(
                (float) camPos.x, (float) camPos.y, (float) camPos.z,  // eye position
                target.x, target.y, target.z,                           // target position
                up.x(), up.y(), up.z()                                  // up vector
        );

        // Set up projection matrix with proper FOV
        float fov = mc.gameRenderer.getFov(cam, tickdelta, true);

        // Use scaled window dimensions
        cachedScreenWidth = mc.getWindow().getGuiScaledWidth();
        cachedScreenHeight = mc.getWindow().getGuiScaledHeight();

        cachedProjectionMatrix.identity().setPerspective(
                (float) Math.toRadians(fov),
                (float) cachedScreenWidth / (float) cachedScreenHeight,
                0.05f,
                4096f
        );

        projectionReady = true;
        return true;
    }

    public ScreenBounds projectAabbToScreen(AABB aabb, boolean allowBehind) {
        if (!projectionReady) {
            return null;
        }
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        double minBoxX = aabb.minX;
        double minBoxY = aabb.minY;
        double minBoxZ = aabb.minZ;
        double maxBoxX = aabb.maxX;
        double maxBoxY = aabb.maxY;
        double maxBoxZ = aabb.maxZ;

        Vector3f[] corners = new Vector3f[] {
                new Vector3f((float) minBoxX, (float) minBoxY, (float) minBoxZ),
                new Vector3f((float) minBoxX, (float) minBoxY, (float) maxBoxZ),
                new Vector3f((float) minBoxX, (float) maxBoxY, (float) minBoxZ),
                new Vector3f((float) minBoxX, (float) maxBoxY, (float) maxBoxZ),
                new Vector3f((float) maxBoxX, (float) minBoxY, (float) minBoxZ),
                new Vector3f((float) maxBoxX, (float) minBoxY, (float) maxBoxZ),
                new Vector3f((float) maxBoxX, (float) maxBoxY, (float) minBoxZ),
                new Vector3f((float) maxBoxX, (float) maxBoxY, (float) maxBoxZ)
        };

        for (Vector3f corner : corners) {
            Vector2f screenPos = worldToScreen(
                    corner,
                    cachedViewMatrix,
                    cachedProjectionMatrix,
                    cachedScreenWidth,
                    cachedScreenHeight,
                    allowBehind
            );
            if (screenPos == null) {
                return null;
            }
            minX = Math.min(minX, screenPos.x);
            minY = Math.min(minY, screenPos.y);
            maxX = Math.max(maxX, screenPos.x);
            maxY = Math.max(maxY, screenPos.y);
        }

        if (minX == Float.MAX_VALUE || minY == Float.MAX_VALUE) {
            return null;
        }
        return new ScreenBounds(minX, minY, maxX, maxY);
    }

    /**
     * Draws text using NanoVG and FontManager
     */
    public void drawText(String text, float x, float y, Color color, boolean shadow) {
        translate(x, y);
        SeqClient.getFontManager().drawText(text, 0, 0, color, shadow);
        translate(-x, -y);
    }

    /**
     * Draws text with a specific font using FontManager
     */
    public void drawTextWithFont(String font, String text, float x, float y, Color color, boolean shadow) {
        SeqClient.getFontManager().renderTextWithFont(font, text, (int) x, (int) y, color, shadow);
    }

    /**
     * Draws a filled rectangle using NanoVG
     */
    public void fillRect(float x, float y, float width, float height, Color color) {
        NVGWrapper.drawRect(NVGContext.getContext(), x, y, width, height, color);
    }

    /**
     * Saves the current NanoVG transformation state
     */
    public void pushTransform() {
        nvgSave(NVGContext.getContext());
    }

    /**
     * Restores the previous NanoVG transformation state
     */
    public void popTransform() {
        nvgRestore(NVGContext.getContext());
    }

    /**
     * Translates the NanoVG coordinate system
     */
    public void translate(float x, float y) {
        nvgTranslate(NVGContext.getContext(), x, y);
    }

    /**
     * Rotates the NanoVG coordinate system
     * @param angleDegrees Angle in degrees
     */
    public void rotate(float angleDegrees) {
        nvgRotate(NVGContext.getContext(), (float) Math.toRadians(angleDegrees));
    }

    /**
     * Scales the NanoVG coordinate system
     */
    public void scale(float x, float y) {
        nvgScale(NVGContext.getContext(), x, y);
    }

    /**
     * Draws a rectangle outline using NanoVG
     */
    public void drawRectOutline(float x, float y, float width, float height, float thickness, Color color) {
        NVGWrapper.drawRectOutline(NVGContext.getContext(), x, y, width, height, thickness, color);
    }

    /**
     * Draws a rounded rectangle using NanoVG
     */
    public void drawRoundedRect(float x, float y, float width, float height, float radius, Color color) {
        NVGWrapper.drawRoundedRect(NVGContext.getContext(), x, y, width, height, radius, color);
    }

    /**
     * Draws a horizontal line using NanoVG
     */
    public void drawHorizontalLine(float x, float width, float y, Color color) {
        NVGWrapper.drawHorizontalLine(NVGContext.getContext(), x, width, y, color);
    }

    /**
     * Draws a vertical line using NanoVG
     */
    public void drawVerticalLine(float y, float height, float x, Color color) {
        NVGWrapper.drawVerticalLine(NVGContext.getContext(), y, height, x, color);
    }

    /**
     * Draws a diagonal line using NanoVG
     */
    public void drawDiagonalLine(float x1, float y1, float x2, float y2, int thickness, Color color) {
        NVGWrapper.drawDiagonalLine(NVGContext.getContext(), x1, y1, x2, y2, thickness, color);
    }

    /**
     * Draws a horizontal gradient using NanoVG
     */
    public void drawHorizontalGradient(float x, float y, float width, float height, Color color1, Color color2) {
        NVGWrapper.drawHorizontalGradient(NVGContext.getContext(), x, y, width, height, color1, color2);
    }

    /**
     * Gets the width of text using FontManager
     */
    public float getTextWidth(String text) {
        return SeqClient.getFontManager().getStringWidth(text, SeqClient.getFontManager().getSelectedFont());
    }

    /**
     * Gets the width of text with a specific font
     */
    public float getTextWidth(String text, String font) {
        return SeqClient.getFontManager().getStringWidth(text, font);
    }

    /**
     * Gets the height of the current font
     */
    public float getFontHeight() {
        return SeqClient.getFontManager().getFontHeight(SeqClient.getFontManager().getSelectedFont());
    }

    /**
     * Functional interface for rendering callbacks
     */
    public interface RenderCallback {
        void handleRender(float x, float y);
    }

    public record ScreenBounds(float minX, float minY, float maxX, float maxY) {

        public float getWidth() {
                return Math.max(0.0f, maxX - minX);
            }

        public float getHeight() {
                return Math.max(0.0f, maxY - minY);
            }
    }
}
