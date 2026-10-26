package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * The font Pharaoh draws its own text with (Liberation Sans, or Poppins if you choose it, or Minecraft's normal font
 * when it is switched off). Every Pharaoh menu, panel, card and label goes through here so they all match.
 */
public final class PFont {
    private PFont() {}

    private static final Identifier SANS = Identifier.of("chunkfinder", "sans");
    private static final Identifier SANS_BOLD = Identifier.of("chunkfinder", "sans_bold");
    private static final Identifier POPPINS = Identifier.of("chunkfinder", "ui");
    private static final Identifier POPPINS_BOLD = Identifier.of("chunkfinder", "ui_bold");

    public static boolean on() {
        return ChunkFinderConfig.INSTANCE.pharaohFont;
    }

    private static Identifier regularId() {
        return ChunkFinderConfig.INSTANCE.pharaohFontPoppins ? POPPINS : SANS;
    }

    private static Identifier boldId() {
        return ChunkFinderConfig.INSTANCE.pharaohFontPoppins ? POPPINS_BOLD : SANS_BOLD;
    }

    /** Text in the Pharaoh font. */
    public static MutableText t(String s) {
        MutableText m = Text.literal(s);
        if (on()) m.setStyle(Style.EMPTY.withFont(regularId()));
        return m;
    }

    /** Makes an already styled text bold in the Pharaoh font. */
    public static Style bold(Style st) {
        return on() ? st.withFont(boldId()) : st;
    }

    public static int w(MinecraftClient mc, String s) {
        return mc.textRenderer.getWidth(t(s));
    }
}
