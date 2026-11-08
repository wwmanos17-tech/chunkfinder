package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.ReconfiguringScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.AddServerScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.DirectConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

/**
 * Draws a desert sunset with pyramids behind the multiplayer list and the join / leave screens, instead of the
 * normal blurred background. The picture is inside the mod (assets/chunkfinder/textures/gui/cover.png), so no
 * texture pack is needed. It is only used when you are not inside a world.
 */
public final class CoverBackground {
    private CoverBackground() {}

    private static final Identifier COVER = Identifier.of("chunkfinder", "textures/gui/cover.png");
    private static final int TW = 1280, TH = 720;

    public static boolean applies(Screen screen) {
        if (!ChunkFinderConfig.INSTANCE.joinCover) return false;
        if (MinecraftClient.getInstance().world != null) return false;
        return screen instanceof MultiplayerScreen
            || screen instanceof DirectConnectScreen
            || screen instanceof AddServerScreen
            || screen instanceof ConnectScreen
            || screen instanceof DisconnectedScreen
            || screen instanceof MessageScreen
            || screen instanceof ProgressScreen
            || screen instanceof ReconfiguringScreen;
    }

    /** Fills the whole window with the picture, cropping the edges so it is never stretched. */
    public static void draw(DrawContext ctx) {
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        double screenAspect = (double) sw / sh, texAspect = (double) TW / TH;
        int rw = TW, rh = TH;
        float u = 0f, v = 0f;
        if (screenAspect > texAspect) {
            rh = (int) Math.round(TW / screenAspect);
            v = (TH - rh) / 2f;
        } else {
            rw = (int) Math.round(TH * screenAspect);
            u = (TW - rw) / 2f;
        }
        ctx.drawTexture(RenderLayer::getGuiTextured, COVER, 0, 0, u, v, sw, sh, rw, rh, TW, TH);
    }
}
