package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Small bar with the Pharaoh logo and your FPS. Can be dragged around while the menu is open. */
public final class FpsBar {
    private FpsBar() {}

    /** Where it was last drawn: x, y, width, height (used for dragging it). */
    public static final int[] BOUNDS = new int[4];

    public static void render(DrawContext ctx, MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        BOUNDS[2] = 0;
        if (!cfg.fpsEnabled && !cfg.timerEnabled) return;

        java.util.List<String> parts = new java.util.ArrayList<>();
        java.util.List<Integer> cols = new java.util.ArrayList<>();
        if (cfg.fpsEnabled) {
            parts.add(mc.getCurrentFps() + " FPS");
            cols.add(0xFFF4ECD6);
        }
        SessionTimer.segments(parts, cols);

        int sep = PFont.w(mc, " | ");
        int textW = 0;
        for (int i = 0; i < parts.size(); i++) textW += PFont.w(mc, parts.get(i)) + (i > 0 ? sep : 0);
        int w = 4 + 16 + 5 + textW + 6;
        int h = 20;
        int x = Math.max(0, Math.min(cfg.fpsX, mc.getWindow().getScaledWidth() - w));
        int y = Math.max(0, Math.min(cfg.fpsY, mc.getWindow().getScaledHeight() - h));
        BOUNDS[0] = x;
        BOUNDS[1] = y;
        BOUNDS[2] = w;
        BOUNDS[3] = h;

        HudPanel.draw(ctx, x, y, w, h);

        // the logo at half size (16 x 16)
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + 6, y + 2, 0f);
        ctx.getMatrices().scale(0.5f, 0.5f, 1f);
        LogoRenderer.draw(ctx, 0, 0);
        ctx.getMatrices().pop();

        int tx = x + 27;
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                ctx.drawTextWithShadow(mc.textRenderer, PFont.t(" | "), tx, y + 6, 0xFFE0A526);
                tx += sep;
            }
            ctx.drawTextWithShadow(mc.textRenderer, PFont.t(parts.get(i)), tx, y + 6, cols.get(i));
            tx += PFont.w(mc, parts.get(i));
        }
    }
}
