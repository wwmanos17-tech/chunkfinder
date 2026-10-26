package com.chunkfinder;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

/**
 * Draws the gold pyramids logo from a smooth 128 x 128 image (assets/chunkfinder/textures/gui/logo.png).
 * Icon: "Egyptian Pyramids" by Delapouite, game-icons.net, CC BY 3.0 (https://creativecommons.org/licenses/by/3.0/).
 * The icon was recolored gold.
 */
public class LogoRenderer {
    public static final int SIZE = 32;
    private static final Identifier LOGO = Identifier.of("chunkfinder", "textures/gui/logo.png");

    public static void draw(DrawContext ctx, int x, int y) {
        ctx.drawTexture(RenderLayer::getGuiTextured, LOGO, x, y, 0f, 0f, SIZE, SIZE, 128, 128, 128, 128);
    }
}
