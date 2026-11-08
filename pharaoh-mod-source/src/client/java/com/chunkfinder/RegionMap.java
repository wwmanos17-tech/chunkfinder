package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Donut region map: a 9 x 9 grid of 50,000 block squares from -225k to +225k on both axes.
 * Draws the grid on screen with your position and names the region you are standing in.
 */
public class RegionMap {
    private static final int[][] NUM = {
        {82, 100, 101, 102, 103, 104, 105, 106, 91},
        {83, 44, 75, 42, 41, 40, 39, 38, 92},
        {84, 45, 14, 13, 12, 11, 10, 37, 93},
        {85, 46, 74, 3, 2, 1, 25, 36, 94},
        {86, 47, 72, 71, 5, 4, 24, 35, 95},
        {87, 51, 17, 9, 8, 7, 23, 34, 96},
        {88, 54, 18, 61, 62, 21, 22, 33, 97},
        {89, 26, 27, 28, 29, 30, 59, 32, 98},
        {90, 107, 108, 109, 110, 111, 112, 113, 99}
    };

    // O = Oceania, W = NA West, E = NA East, A = Asia, D = EU West, C = EU Central
    private static final String[] AREA = {
        "OWWWEEEEE",
        "OWWWEEEEE",
        "OWWWEEEEE",
        "OOOWEEEEE",
        "AAAAEEEEE",
        "ADDCCCCCE",
        "ADDCCCCCC",
        "CDCCCCCCC",
        "CDDDDDDDC"
    };

    private static final int SIZE = 50_000;
    private static final int HALF = 225_000;

    /** Where the map was last drawn: x, y, width, height (used for dragging it). */
    public static final int[] BOUNDS = new int[4];

    private static int color(char a) {
        return switch (a) {
            case 'O' -> 0xFFFF8A1F;
            case 'W' -> 0xFF2F63B5;
            case 'E' -> 0xFF4BA3E3;
            case 'A' -> 0xFFF2C14E;
            case 'D' -> 0xFF1FA060;
            default -> 0xFF9ACD5A;
        };
    }

    private static String name(char a) {
        return switch (a) {
            case 'O' -> "Oceania";
            case 'W' -> "NA West";
            case 'E' -> "NA East";
            case 'A' -> "Asia";
            case 'D' -> "EU West";
            default -> "EU Central";
        };
    }

    public static void render(DrawContext ctx, MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.regionMapEnabled || mc.player == null) return;

        int cell = Math.max(6, Math.min(30, cfg.regionMapCell));
        int mapSize = cell * 9;
        int w = Math.max(mapSize, 120) + 8;
        int h = mapSize + 8 + 24;
        int x = Math.max(0, Math.min(cfg.regionMapX, mc.getWindow().getScaledWidth() - w));
        int y = Math.max(0, Math.min(cfg.regionMapY, mc.getWindow().getScaledHeight() - h));
        BOUNDS[0] = x;
        BOUNDS[1] = y;
        BOUNDS[2] = w;
        BOUNDS[3] = h;

        double px = mc.player.getX();
        double pz = mc.player.getZ();
        int col = (int) Math.floor((px + HALF) / SIZE);
        int row = (int) Math.floor((pz + HALF) / SIZE);
        boolean inside = col >= 0 && col < 9 && row >= 0 && row < 9;

        ctx.fill(x, y, x + w, y + h, 0xB0101018);
        ctx.fill(x, y, x + 2, y + h, 0xFFE0A526);
        int mx0 = x + 4;
        int my0 = y + 4;
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                int cx = mx0 + c * cell;
                int cy = my0 + r * cell;
                ctx.fill(cx, cy, cx + cell - 1, cy + cell - 1, color(AREA[r].charAt(c)));
                if (cell >= 20) {
                    String n = String.valueOf(NUM[r][c]);
                    ctx.drawText(mc.textRenderer, PFont.t(n), cx + (cell - 1 - PFont.w(mc, n)) / 2, cy + (cell - 8) / 2, 0xFFFFFFFF, true);
                }
            }
        }

        if (inside) {
            // outline of the region you are in
            int cx = mx0 + col * cell;
            int cy = my0 + row * cell;
            ctx.fill(cx - 1, cy - 1, cx + cell, cy, 0xFFFFFFFF);
            ctx.fill(cx - 1, cy + cell - 1, cx + cell, cy + cell, 0xFFFFFFFF);
            ctx.fill(cx - 1, cy, cx, cy + cell - 1, 0xFFFFFFFF);
            ctx.fill(cx + cell - 1, cy, cx + cell, cy + cell - 1, 0xFFFFFFFF);
        }

        // you: a red dot, kept on the map edge if you are outside it
        double fx = Math.max(0, Math.min(9, (px + HALF) / SIZE));
        double fz = Math.max(0, Math.min(9, (pz + HALF) / SIZE));
        int dx = mx0 + (int) (fx * cell);
        int dy = my0 + (int) (fz * cell);
        ctx.fill(dx - 2, dy - 2, dx + 3, dy + 3, 0xFFFFFFFF);
        ctx.fill(dx - 1, dy - 1, dx + 2, dy + 2, 0xFFFF2222);

        int ty = my0 + mapSize + 3;
        ctx.drawTextWithShadow(mc.textRenderer, PFont.t("X " + (int) px + "  Z " + (int) pz), x + 6, ty, 0xFFE6E6E6);
        if (inside) {
            char a = AREA[row].charAt(col);
            ctx.drawTextWithShadow(mc.textRenderer, PFont.t("Region " + NUM[row][col] + " (" + name(a) + ")"), x + 6, ty + 10, color(a));
        } else {
            ctx.drawTextWithShadow(mc.textRenderer, PFont.t("Outside the map"), x + 6, ty + 10, 0xFF8A8A96);
        }
    }
}
