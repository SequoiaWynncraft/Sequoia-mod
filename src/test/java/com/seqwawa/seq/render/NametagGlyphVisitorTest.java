package com.seqwawa.seq.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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
        ColorRamp solid = ColorRamp.of(0x2ECC71);
        TextRenderable.Styled fill = glyph(Style.EMPTY.withColor(
                RankGradientAnimation.axis(solid, solid, RankGradientAnimation.Target.RANK_BADGE, 6f)
                        .colorAt(0f, 6f, null)));
        TextRenderable.Styled name = glyph(Style.EMPTY.withColor(
                RankGradientAnimation.axis(solid, solid, RankGradientAnimation.Target.USERNAME, 6f)
                        .colorAt(0f, 6f, null)));
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
