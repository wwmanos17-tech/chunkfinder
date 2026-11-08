package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Smooth rounded shapes. Minecraft's GUI units are 2 to 4 real screen pixels big, so corners drawn in GUI units look
 * like stairs. These helpers draw in real screen pixels instead and soften the edge pixels, so corners and circles
 * look round. Only colours given by the caller are used.
 */
public final class Smooth {
    private Smooth() {}

    /** Real screen pixels per drawing unit, using the current matrix. 1 means no finer drawing is possible. */
    public static float scale(DrawContext c) {
        try {
            float gui = (float) MinecraftClient.getInstance().getWindow().getScaleFactor();
            float k = gui * c.getMatrices().peek().getPositionMatrix().m00();
            return Math.max(1f, Math.min(4f, k));
        } catch (Throwable t) {
            return 1f;
        }
    }

    private static int withAlpha(int color, float f) {
        int a = Math.round(((color >>> 24) & 255) * Math.max(0f, Math.min(1f, f)));
        return (a << 24) | (color & 0xFFFFFF);
    }

    /** Rounded rectangle. */
    public static void round(DrawContext c, int x1, int y1, int x2, int y2, int color, int r) {
        r = Math.max(0, Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
        float k = scale(c);
        if (r == 0) {
            c.fill(x1, y1, x2, y2, color);
            return;
        }
        if (k <= 1.01f) {
            roundRaw(c, x1, y1, x2, y2, color, r, false);
            return;
        }
        c.getMatrices().push();
        c.getMatrices().scale(1f / k, 1f / k, 1f);
        roundRaw(c, Math.round(x1 * k), Math.round(y1 * k), Math.round(x2 * k), Math.round(y2 * k), color,
            Math.max(1, Math.round(r * k)), true);
        c.getMatrices().pop();
    }

    private static void roundRaw(DrawContext c, int x1, int y1, int x2, int y2, int color, int r, boolean soft) {
        r = Math.max(0, Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
        c.fill(x1, y1 + r, x2, y2 - r, color);
        for (int dy = 0; dy < r; dy++) {
            double d = r - dy - 0.5;
            double inset = r - Math.sqrt(Math.max(0, r * r - d * d));
            int whole = (int) Math.ceil(inset - 1e-6);
            c.fill(x1 + whole, y1 + dy, x2 - whole, y1 + dy + 1, color);
            c.fill(x1 + whole, y2 - 1 - dy, x2 - whole, y2 - dy, color);
            if (soft && whole > 0) {
                int edge = whole - 1;
                int part = withAlpha(color, (float) (whole - inset));
                c.fill(x1 + edge, y1 + dy, x1 + edge + 1, y1 + dy + 1, part);
                c.fill(x2 - edge - 1, y1 + dy, x2 - edge, y1 + dy + 1, part);
                c.fill(x1 + edge, y2 - 1 - dy, x1 + edge + 1, y2 - dy, part);
                c.fill(x2 - edge - 1, y2 - 1 - dy, x2 - edge, y2 - dy, part);
            }
        }
    }

    /** A round disc. */
    public static void disc(DrawContext c, float cx, float cy, float r, int color) {
        float k = scale(c);
        if (k > 1.01f) {
            c.getMatrices().push();
            c.getMatrices().scale(1f / k, 1f / k, 1f);
            discRaw(c, cx * k, cy * k, r * k, color, true);
            c.getMatrices().pop();
        } else {
            discRaw(c, cx, cy, r, color, false);
        }
    }

    /** Draws a disc using the current matrix as it is (no extra scaling). */
    public static void discRaw(DrawContext c, float cx, float cy, float r, int color, boolean soft) {
        int top = (int) Math.floor(cy - r), bottom = (int) Math.ceil(cy + r);
        for (int row = top; row < bottom; row++) {
            float yy = row + 0.5f - cy;
            if (Math.abs(yy) > r) continue;
            float half = (float) Math.sqrt(r * r - yy * yy);
            float xa = cx - half, xb = cx + half;
            if (!soft) {
                int a = Math.round(xa), b = Math.round(xb);
                if (b > a) c.fill(a, row, b, row + 1, color);
                continue;
            }
            int fa = (int) Math.floor(xa), fb = (int) Math.floor(xb);
            if (fa == fb) {
                c.fill(fa, row, fa + 1, row + 1, withAlpha(color, xb - xa));
                continue;
            }
            int full1 = fa + 1, full2 = fb;
            if (full2 > full1) c.fill(full1, row, full2, row + 1, color);
            c.fill(fa, row, fa + 1, row + 1, withAlpha(color, full1 - xa));
            if (xb > fb) c.fill(fb, row, fb + 1, row + 1, withAlpha(color, xb - fb));
        }
    }
}
