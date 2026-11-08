package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;

/** Collects the chunk boxes for Chunk Finder (geodes, player activity and sus chunks). */
public class ChunkRenderer {
    public static void collect(List<EspRenderer.Box> boxes, List<EspRenderer.Line> lines, Vec3d cam) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.enabled) return;
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return;

        double yb;
        double yt;
        float fill;
        if (cfg.tallColumns) {
            yb = world.getBottomY();
            yt = yb + world.getHeight();
            fill = 0.05f;
        } else {
            yb = cfg.renderY;
            yt = yb + cfg.boxHeight;
            fill = 0.18f;
        }

        for (Map.Entry<ChunkPos, ChunkScanner.Result> entry : ChunkScanner.FOUND.entrySet()) {
            ChunkPos pos = entry.getKey();
            double x1 = pos.getStartX();
            double z1 = pos.getStartZ();
            ChunkScanner.Result r = entry.getValue();
            int[] c = (r == ChunkScanner.Result.GEODE) ? cfg.geodeColor : cfg.activityColor;
            boxes.add(new EspRenderer.Box(x1, yb, z1, x1 + 16, yt, z1 + 16, c[0] / 255f, c[1] / 255f, c[2] / 255f, fill));
            if (r == ChunkScanner.Result.BOTH) {
                int[] g = cfg.geodeColor;
                boxes.add(new EspRenderer.Box(x1 + 0.25, yb, z1 + 0.25, x1 + 15.75, yt, z1 + 15.75,
                    g[0] / 255f, g[1] / 255f, g[2] / 255f, 0f));
            }
        }

        // sus chunks and light chunks: flat red (or your color) slabs at the heights where the clues are
        if (cfg.detectLight) {
            int[] l = cfg.lightColor;
            for (ChunkPos pos : LightFinder.FOUND.keySet()) {
                Integer y = LightFinder.LEVELS.get(pos);
                double sy = y == null ? cfg.renderY : y;
                double x1 = pos.getStartX();
                double z1 = pos.getStartZ();
                long[] ls = LightFinder.SPOTS.get(pos);
                if (cfg.exactSpots && ls != null && ls.length > 0) {
                    for (long bl : ls) addSpot(boxes, bl, l[0] / 255f, l[1] / 255f, l[2] / 255f, 0.30f);
                    continue;
                }
                boxes.add(new EspRenderer.Box(x1, sy, z1, x1 + 16, sy + 0.02, z1 + 16, l[0] / 255f, l[1] / 255f, l[2] / 255f, 0.25f));
            }
        }

        if (cfg.detectSus) {
            int need = cfg.requiredSusScore();
            for (Map.Entry<ChunkPos, Integer> se : SusScanner.FOUND.entrySet()) {
                ChunkPos pos = se.getKey();
                int[] s = cfg.susScoreColors ? SusScanner.strengthColor(se.getValue(), need, cfg.susColor) : cfg.susColor;
                int[] levels = SusScanner.LEVELS.get(pos);
                if (levels == null || levels.length == 0) levels = new int[]{cfg.renderY};
                double x1 = pos.getStartX();
                double z1 = pos.getStartZ();
                long[] spots = SusScanner.SPOTS.get(pos);
                if (cfg.exactSpots && spots != null && spots.length > 0) {
                    int hi = SusScanner.HI.getOrDefault(pos, 0);
                    for (int i = 0; i < spots.length; i++) {
                        addSpot(boxes, spots[i], s[0] / 255f, s[1] / 255f, s[2] / 255f, i < hi ? 0.40f : 0.20f);
                    }
                    continue;
                }
                for (int y : levels) {
                    boxes.add(new EspRenderer.Box(x1, y, z1, x1 + 16, y + 0.02, z1 + 16,
                        s[0] / 255f, s[1] / 255f, s[2] / 255f, cfg.susAlpha / 100f));
                }
            }
            if (cfg.susTracer && !SusScanner.FOUND.isEmpty()) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null) {
                    java.util.List<Map.Entry<ChunkPos, Integer>> rk = SusScanner.ranked(mc.player.getX(), mc.player.getZ());
                    ChunkPos best = rk.get(0).getKey();
                    int[] lv = SusScanner.LEVELS.get(best);
                    double by = (lv != null && lv.length > 0) ? lv[0] : cfg.renderY;
                    int[] col = SusScanner.strengthColor(rk.get(0).getValue(), need, cfg.susColor);
                    Vec3d st = Proj.tracerStart(mc);
                    double tx = best.getStartX() + 8, tz = best.getStartZ() + 8;
                    long[] bs = SusScanner.SPOTS.get(best);
                    if (cfg.exactSpots && bs != null && bs.length > 0) {
                        net.minecraft.util.math.BlockPos bp = net.minecraft.util.math.BlockPos.fromLong(bs[0]);
                        tx = bp.getX() + 0.5; by = bp.getY() + 0.5; tz = bp.getZ() + 0.5;
                    }
                    lines.add(new EspRenderer.Line(st.x, st.y, st.z, tx, by, tz,
                        col[0] / 255f, col[1] / 255f, col[2] / 255f));
                }
            }
        }
    }

    private static void addSpot(List<EspRenderer.Box> boxes, long packed, float r, float g, float b, float fill) {
        net.minecraft.util.math.BlockPos p = net.minecraft.util.math.BlockPos.fromLong(packed);
        boxes.add(new EspRenderer.Box(p.getX() - 0.02, p.getY() - 0.02, p.getZ() - 0.02,
            p.getX() + 1.02, p.getY() + 1.02, p.getZ() + 1.02, r, g, b, fill));
    }
}
