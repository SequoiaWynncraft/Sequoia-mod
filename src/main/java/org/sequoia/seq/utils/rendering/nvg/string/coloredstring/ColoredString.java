package org.sequoia.seq.utils.rendering.nvg.string.coloredstring;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.ChatFormatting;
import org.sequoia.seq.client.SeqClient;

import java.awt.*;

@Setter
@Getter
public class ColoredString {

    Color color;
    String text;
    ChatFormatting formatting;

    public ColoredString(Color color, String text) {
        this.color = color;
        this.text = text;
        this.formatting = colorToFormat(color);
    }


    public static ColoredString of(Color color, String text) {
        return new ColoredString(color, text);
    }


    private static ChatFormatting colorToFormat(Color color) {
        if (Color.RED == color) return ChatFormatting.RED;
        else if (Color.DARK_GRAY == color) return ChatFormatting.DARK_GRAY;
        else if (Color.BLACK == color) return ChatFormatting.BLACK;
        else if (Color.BLUE == color) return ChatFormatting.BLUE;
        else if (Color.PINK == color) return ChatFormatting.LIGHT_PURPLE;
        else if (Color.MAGENTA == color) return ChatFormatting.DARK_PURPLE;
        else if (Color.ORANGE == color) return ChatFormatting.GOLD;
        else if (Color.GREEN == color) return ChatFormatting.GREEN;
        else if (Color.LIGHT_GRAY == color) return ChatFormatting.LIGHT_PURPLE;
        else if (Color.WHITE == color) return ChatFormatting.WHITE;
        else if (Color.YELLOW == color) return ChatFormatting.YELLOW;
        else if (Color.cyan == color) return ChatFormatting.AQUA;
        return ChatFormatting.getById(color.getRGB());


    }

    public static String getString(ColoredString[] coloredStrings) {
        String s = "";

        for (ColoredString string : coloredStrings) {
            s += string.getText();
        }
        return s;
    }

    @Override
    public String toString() {

        return text;
    }

    public float getTextWidth() {
        return SeqClient.getFontManager().getStringWidth(text, SeqClient.getFontManager().getSelectedFont());
    }


}
