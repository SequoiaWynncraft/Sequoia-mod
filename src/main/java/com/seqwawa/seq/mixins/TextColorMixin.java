package com.seqwawa.seq.mixins;

import com.seqwawa.seq.accessors.GradientTagHolder;
import net.minecraft.network.chat.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Gives every text colour room for the rank decoration it was minted for. Only colours
 * this mod mints ever carry one; the field plays no part in a colour's equality or
 * serialisation.
 */
@Mixin(TextColor.class)
public abstract class TextColorMixin implements GradientTagHolder {
    @Unique
    private Object seq$gradientTag;

    @Override
    public Object seq$gradientTag() {
        return seq$gradientTag;
    }

    @Override
    public void seq$setGradientTag(Object tag) {
        seq$gradientTag = tag;
    }
}
