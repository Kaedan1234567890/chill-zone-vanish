package com.chillzone.vanish;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class LegacyText {
    private LegacyText() {}

    public static Component parse(String input) {
        MutableComponent out = Component.empty();
        StringBuilder text = new StringBuilder();
        ChatFormatting active = null;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < input.length()) {
                ChatFormatting next = code(input.charAt(i + 1));
                if (next != null) {
                    flush(out, text, active);
                    active = next;
                    i++;
                    continue;
                }
            }
            text.append(c);
        }
        flush(out, text, active);
        return out;
    }

    private static void flush(MutableComponent out, StringBuilder text, ChatFormatting format) {
        if (text.length() == 0) return;
        MutableComponent part = Component.literal(text.toString());
        if (format != null) part.withStyle(format);
        out.append(part);
        text.setLength(0);
    }

    private static ChatFormatting code(char c) {
        return switch (Character.toLowerCase(c)) {
            case '0' -> ChatFormatting.BLACK;
            case '1' -> ChatFormatting.DARK_BLUE;
            case '2' -> ChatFormatting.DARK_GREEN;
            case '3' -> ChatFormatting.DARK_AQUA;
            case '4' -> ChatFormatting.DARK_RED;
            case '5' -> ChatFormatting.DARK_PURPLE;
            case '6' -> ChatFormatting.GOLD;
            case '7' -> ChatFormatting.GRAY;
            case '8' -> ChatFormatting.DARK_GRAY;
            case '9' -> ChatFormatting.BLUE;
            case 'a' -> ChatFormatting.GREEN;
            case 'b' -> ChatFormatting.AQUA;
            case 'c' -> ChatFormatting.RED;
            case 'd' -> ChatFormatting.LIGHT_PURPLE;
            case 'e' -> ChatFormatting.YELLOW;
            case 'f' -> ChatFormatting.WHITE;
            case 'l' -> ChatFormatting.BOLD;
            case 'o' -> ChatFormatting.ITALIC;
            case 'n' -> ChatFormatting.UNDERLINE;
            case 'm' -> ChatFormatting.STRIKETHROUGH;
            case 'r' -> ChatFormatting.RESET;
            default -> null;
        };
    }
}
