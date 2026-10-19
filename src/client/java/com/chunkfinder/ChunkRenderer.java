package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;

/** Collects the chunk boxes for Chunk Finder (geodes, player activity and sus chunks). */
public class ChunkRenderer {
    public static void collect(List<EspRenderer.Box> boxes, Vec3d cam) {
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

        if (cfg.detectSus) {
            int[] s = cfg.susColor;
            for (ChunkPos pos : SusScanner.FOUND.keySet()) {
                double x1 = pos.getStartX() + 1.0;
                double z1 = pos.getStartZ() + 1.0;
                boxes.add(new EspRenderer.Box(x1, yb, z1, x1 + 14, yt, z1 + 14, s[0] / 255f, s[1] / 255f, s[2] / 255f, fill));
            }
        }
    }
}
