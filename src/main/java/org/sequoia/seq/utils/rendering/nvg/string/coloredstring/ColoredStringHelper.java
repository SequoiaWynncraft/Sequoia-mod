package org.sequoia.seq.utils.rendering.nvg.string.coloredstring;

public class ColoredStringHelper {

    private ColoredStringHelper() {
    }

    public static float getWidth(ColoredString[] coloredStrings) {
        float messageWidth = 0;
        for (ColoredString coloredString : coloredStrings) {
            messageWidth += coloredString.getTextWidth();
        }
        return messageWidth;
    }
}
