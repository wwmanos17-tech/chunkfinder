package com.chunkfinder;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

/**
 * Draws one outlined box per flagged chunk. Every box is snapped to the 16x16 chunk grid
 * and sits at the same configured height, so all highlights line up on one plane.
 */
public class ChunkRenderer {
    public static void render(WorldRenderContext context) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.enabled || ChunkScanner.FOUND.isEmpty()) return;
        if (context.consumers() == null) return;

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;
        Vec3d cam = context.camera().getPos();
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());

        double y1 = cfg.renderY - cam.y;
        double y2 = y1 + cfg.boxHeight;

        matrices.push();
        for (Map.Entry<ChunkPos, ChunkScanner.Result> entry : ChunkScanner.FOUND.entrySet()) {
            ChunkPos pos = entry.getKey();
            double x1 = pos.getStartX() - cam.x;
            double z1 = pos.getStartZ() - cam.z;
            double x2 = x1 + 16;
            double z2 = z1 + 16;

            ChunkScanner.Result r = entry.getValue();
            int[] c = (r == ChunkScanner.Result.GEODE) ? cfg.geodeColor : cfg.activityColor;

            VertexRendering.drawBox(matrices, lines, x1, y1, z1, x2, y2, z2,
                c[0] / 255f, c[1] / 255f, c[2] / 255f, 1f);

            // Chunks with both signals get an extra inner outline in the geode color.
            if (r == ChunkScanner.Result.BOTH) {
                int[] g = cfg.geodeColor;
                VertexRendering.drawBox(matrices, lines, x1 + 0.25, y1, z1 + 0.25, x2 - 0.25, y2, z2 - 0.25,
                    g[0] / 255f, g[1] / 255f, g[2] / 255f, 1f);
            }
        }
        matrices.pop();
    }
}
