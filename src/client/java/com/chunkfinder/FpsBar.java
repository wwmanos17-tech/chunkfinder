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
        if (!cfg.fpsEnabled) return;

        String text = mc.getCurrentFps() + " FPS";
        int w = 4 + 16 + 5 + mc.textRenderer.getWidth(text) + 6;
        int h = 20;
        int x = Math.max(0, Math.min(cfg.fpsX, mc.getWindow().getScaledWidth() - w));
        int y = Math.max(0, Math.min(cfg.fpsY, mc.getWindow().getScaledHeight() - h));
        BOUNDS[0] = x;
        BOUNDS[1] = y;
        BOUNDS[2] = w;
        BOUNDS[3] = h;

        ctx.fill(x, y, x + w, y + h, 0xB0100E08);
        ctx.fill(x, y, x + 2, y + h, 0xFFE0A526);

        // the logo at half size (16 x 16)
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + 6, y + 2, 0f);
        ctx.getMatrices().scale(0.5f, 0.5f, 1f);
        LogoRenderer.draw(ctx, 0, 0);
        ctx.getMatrices().pop();

        ctx.drawTextWithShadow(mc.textRenderer, Text.literal(text), x + 27, y + 6, 0xFFF4ECD6);
    }
}
