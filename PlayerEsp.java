package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** Player ESP: a box around every other player, through walls. Names are drawn by PharaohHud. */
public class PlayerEsp {
    public static boolean shouldShow(MinecraftClient mc, AbstractClientPlayerEntity p) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (p == mc.player) return Freecam.isActive() && cfg.playerEspSelfInFreecam;
        return true;
    }

    public static void collect(List<EspRenderer.Box> boxes, List<EspRenderer.Line> lines, Vec3d cam) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.playerEspEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        float r = cfg.playerColor[0] / 255f;
        float g = cfg.playerColor[1] / 255f;
        float b = cfg.playerColor[2] / 255f;
        Vec3d start = Proj.tracerStart(mc);

        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (!shouldShow(mc, p)) continue;
            if (p.getPos().distanceTo(cam) > cfg.playerEspRange) continue;
            Box bb = p.getBoundingBox();
            boxes.add(new EspRenderer.Box(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ,
                r, g, b, cfg.playerEspFill ? 0.18f : 0f));
            if (cfg.playerEspTracers && p != mc.player) {
                Vec3d c = bb.getCenter();
                lines.add(new EspRenderer.Line(start.x, start.y, start.z, c.x, c.y, c.z, r, g, b));
            }
        }
    }
}
