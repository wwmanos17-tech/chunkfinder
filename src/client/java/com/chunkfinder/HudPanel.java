package com.chunkfinder;

import net.minecraft.client.gui.DrawContext;

/** One shared look for the on-screen boxes: rounded corners, a thin soft glowing edge and a short accent bar. */
public final class HudPanel {
    private HudPanel() {}

    public static final int ACCENT = 0xFFE0A526;

    /** Draws the panel background. Text should start about 6 pixels in from the left edge. */
    public static void draw(DrawContext ctx, int x, int y, int w, int h) {
        draw(ctx, x, y, w, h, ACCENT);
    }

    public static void draw(DrawContext ctx, int x, int y, int w, int h, int accent) {
        int rgb = accent & 0xFFFFFF;
        round(ctx, x - 2, y - 2, x + w + 2, y + h + 2, 0x14000000 | rgb, 6);   // soft glow
        round(ctx, x - 1, y - 1, x + w + 1, y + h + 1, 0x70000000 | rgb, 5);   // thin edge
        round(ctx, x, y, x + w, y + h, 0xD0100E08, 4);                           // body
        ctx.fill(x + 4, y + 1, x + w - 4, y + 2, 0x1CFFFFFF);                    // faint top highlight
        if (h > 12) ctx.fill(x + 2, y + 4, x + 3, y + h - 4, 0xFF000000 | rgb);  // accent bar
    }

    /** Filled rectangle with rounded corners, drawn row by row. */
    public static void round(DrawContext c, int x1, int y1, int x2, int y2, int color, int r) {
        r = Math.max(0, Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
        if (r == 0) {
            c.fill(x1, y1, x2, y2, color);
            return;
        }
        c.fill(x1, y1 + r, x2, y2 - r, color);
        for (int dy = 0; dy < r; dy++) {
            double d = r - dy - 0.5;
            int inset = r - (int) Math.floor(Math.sqrt(r * r - d * d));
            c.fill(x1 + inset, y1 + dy, x2 - inset, y1 + dy + 1, color);
            c.fill(x1 + inset, y2 - 1 - dy, x2 - inset, y2 - dy, color);
        }
    }
}
