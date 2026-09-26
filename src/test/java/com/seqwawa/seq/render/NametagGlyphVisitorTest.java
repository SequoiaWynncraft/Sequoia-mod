package com.seqwawa.seq.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seqwawa.seq.mixins.GlyphInstanceAccessor;
import com.seqwawa.seq.utils.ColorRamp;
import com.seqwawa.seq.utils.RankGradientAnimation;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.EmptyArea;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class NametagGlyphVisitorTest {
    private static final TextColor LABEL = RankGradientAnimation.markDecorationColor(TextColor.fromRgb(0x1F2126));

    @Test
    void onlyRegisteredPillLettersUseTheForegroundLayer() {
        List<TextRenderable.Styled> ordinary = new ArrayList<>();
        List<TextRenderable.Styled> foreground = new ArrayList<>();
        NametagGlyphVisitor visitor = new NametagGlyphVisitor(collector(ordinary), collector(foreground));
        RankGradientAnimation.Pinned<List<TextColor>> colors = RankGradientAnimation.pin(() -> List.of(
                RankGradientAnimation.colorAt(ColorRamp.of(0x2ECC71), 0d, RankGradientAnimation.Target.RANK_BADGE),
                RankGradientAnimation.colorAt(ColorRamp.of(0x2ECC71), 0d, RankGradientAnimation.Target.USERNAME)));
        try {
            TextRenderable.Styled fill = glyph(Style.EMPTY.withColor(colors.value().get(0)));
            TextRenderable.Styled name = glyph(Style.EMPTY.withColor(colors.value().get(1)));
            TextRenderable.Styled otherMod = glyph(Style.EMPTY.withColor(TextColor.fromRgb(0x1F2126)));
            TextRenderable.Styled label = glyph(Style.EMPTY.withColor(LABEL));
            visitor.acceptGlyph(fill);
            visitor.acceptGlyph(name);
            visitor.acceptGlyph(otherMod);
            visitor.acceptGlyph(label);

            assertEquals(3, ordinary.size());
            assertSame(fill, ordinary.get(0));
            assertSame(name, ordinary.get(1));
            assertSame(otherMod, ordinary.get(2), "matching RGB alone must not affect other mods' icons");
            assertEquals(1, foreground.size());
            assertSame(label, foreground.getFirst(), "forward the original glyph, including its pass alpha");
        } finally {
            RankGradientAnimation.release(colors.colors());
        }
    }

    @Test
    void preservesBackgroundEffectsAndEmptyAreas() {
        int[] calls = new int[2];
        Font.GlyphVisitor ordinary = new Font.GlyphVisitor() {
            @Override public void acceptEffect(TextRenderable effect) { calls[0]++; }
            @Override public void acceptEmptyArea(EmptyArea area) { calls[1]++; }
        };
        NametagGlyphVisitor visitor = new NametagGlyphVisitor(ordinary, new Font.GlyphVisitor() {});
        visitor.acceptEffect(null);
        visitor.acceptEmptyArea(null);
        assertEquals(1, calls[0]);
        assertEquals(1, calls[1]);
    }

    @Test
    void foregroundOffsetDoesNotMoveTheNameOrMutateTheSharedPose() {
        Matrix4f pose = new Matrix4f().translate(4f, 5f, 6f).scale(-0.025f, -0.025f, 0.025f);
        Matrix4f original = new Matrix4f(pose);
        Matrix4f foreground = NametagGlyphVisitor.foregroundPose(pose);
        Vector3f point = new Vector3f(2f, 3f, 0f);
        Vector3f base = pose.transformPosition(new Vector3f(point));
        Vector3f label = foreground.transformPosition(new Vector3f(point));
        assertEquals(original, pose);
        assertEquals(base.x, label.x);
        assertEquals(base.y, label.y);
        assertEquals(base.z + 0.00075f, label.z, 0.000001f);
    }

    @Test
    void gradesAPillColumnAcrossItsMainQuadOnly() {
        ColorRamp gradient = ColorRamp.of(List.of(0x000000, 0xFFFFFF));
        RankGradientAnimation.Pinned<TextColor> column = RankGradientAnimation.pin(() ->
                RankGradientAnimation.colorAt(
                        gradient, gradient, 0.5d, 0.25d, RankGradientAnimation.Target.RANK_BADGE, null));
        try {
            int main = 0x80000000 | column.value().getValue();
            int shadow = 0x80202020;
            List<Integer> drawn = new ArrayList<>();
            TextRenderable.Styled glyph = drawableColumn(Style.EMPTY.withColor(column.value()), main, shadow);

            TextRenderable.Styled graded = NametagGlyphVisitor.graded(glyph, column.value());
            graded.render(new Matrix4f(), recorder(drawn), 0, false);

            RankGradientAnimation.Span span = RankGradientAnimation.animateSpan(column.value());
            assertEquals(
                    List.of(shadow, shadow, shadow, shadow,
                            0x80000000 | span.leftRgb(), 0x80000000 | span.leftRgb(),
                            0x80000000 | span.rightRgb(), 0x80000000 | span.rightRgb()),
                    drawn,
                    "the shadow keeps its colour; the main quad runs from edge to edge, alpha intact");
        } finally {
            RankGradientAnimation.release(column.colors());
        }
    }

    /**
     * A glyph drawn as a 1px column is: a shadow quad, then the main one, each corner
     * by corner as {@code BakedSheetGlyph} emits them, left edge first.
     */
    private static TextRenderable.Styled drawableColumn(Style style, int main, int shadow) {
        return (TextRenderable.Styled) Proxy.newProxyInstance(
                TextRenderable.Styled.class.getClassLoader(),
                new Class<?>[] {TextRenderable.Styled.class, GlyphInstanceAccessor.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "style" -> style;
                    case "seq$color" -> main;
                    case "left" -> 0f;
                    case "right" -> 2f;
                    case "render" -> {
                        VertexConsumer consumer = (VertexConsumer) args[1];
                        quad(consumer, (Matrix4f) args[0], 1f, shadow);
                        quad(consumer, (Matrix4f) args[0], 0f, main);
                        yield null;
                    }
                    default -> throw new AssertionError("unexpected call: " + method.getName());
                });
    }

    private static void quad(VertexConsumer consumer, Matrix4f pose, float left, int color) {
        consumer.addVertex(pose, left, 0f, 0f).setColor(color);
        consumer.addVertex(pose, left, 7f, 0f).setColor(color);
        consumer.addVertex(pose, left + 1f, 7f, 0f).setColor(color);
        consumer.addVertex(pose, left + 1f, 0f, 0f).setColor(color);
    }

    /** Records the colour of every vertex drawn through it. */
    private static VertexConsumer recorder(List<Integer> colors) {
        return (VertexConsumer) Proxy.newProxyInstance(
                VertexConsumer.class.getClassLoader(),
                new Class<?>[] {VertexConsumer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("setColor") && args.length == 1) {
                        colors.add((Integer) args[0]);
                    }
                    return proxy;
                });
    }

    private static Font.GlyphVisitor collector(List<TextRenderable.Styled> target) {
        return new Font.GlyphVisitor() {
            @Override public void acceptGlyph(TextRenderable.Styled glyph) { target.add(glyph); }
        };
    }

    private static TextRenderable.Styled glyph(Style style) {
        return (TextRenderable.Styled) Proxy.newProxyInstance(
                TextRenderable.Styled.class.getClassLoader(), new Class<?>[] {TextRenderable.Styled.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("style")) return style;
                    throw new AssertionError("Glyph must be forwarded unchanged: " + method.getName());
                });
    }
}
