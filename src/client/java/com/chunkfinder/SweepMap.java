package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.block.BlockState;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashMap;
import java.util.Map;

/**
 * Sweep Map: a small map of the chunks around you that remembers where you have already been. A chunk is "checked"
 * when your game holds real blocks for its deep layers (caves and ores, not plain filler), so you can see which ground
 * you have covered and where to dig next. Flagged chunks (sus, farms, redstone activity) are drawn on top.
 */
public final class SweepMap {
    private SweepMap() {}

    public static final int[] BOUNDS = new int[4];
    // 1 = only filler seen so far, 2 = real deep blocks seen
    private static final Map<Long, Byte> STATE = new HashMap<>();
    private static long ticks = 0;
    private static Object lastWorld = null;

    public static void clear() {
        STATE.clear();
        ticks = 0;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    /** True when at least one deep section (below y 0) holds a mix of air and blocks, like real terrain. */
    static boolean realDeep(WorldChunk chunk) {
        ChunkSection[] secs = chunk.getSectionArray();
        boolean any = false;
        for (int i = 0; i < secs.length; i++) {
            int sy = chunk.getBottomSectionCoord() + i;
            if (sy >= 0) break;
            ChunkSection sec = secs[i];
            if (sec == null) continue;
            any = true;
            if (sec.isEmpty()) return true;
            int air = 0, total = 0;
            BlockState first = null;
            boolean same = true;
            for (int y = 1; y < 16; y += 4) {
                for (int z = 1; z < 16; z += 4) {
                    for (int x = 1; x < 16; x += 4) {
                        BlockState s = sec.getBlockState(x, y, z);
                        total++;
                        if (s.isAir()) air++;
                        if (first == null) first = s;
                        else if (s != first) same = false;
                    }
                }
            }
            if (air > 0 || !same) return true;
        }
        return !any;
    }

    public static void tick(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.sweepEnabled || mc.world == null || mc.player == null) return;
        if (mc.world != lastWorld) { lastWorld = mc.world; STATE.clear(); }
        ticks++;
        int pcx = mc.player.getChunkPos().x, pcz = mc.player.getChunkPos().z;
        int radius = Math.min(mc.options.getViewDistance().getValue(), 24);
        int budget = FastScan.maxChunks(mc, 4);
        int done = 0;
        for (int r = 0; r <= radius && done < budget; r++) {
            for (int cx = pcx - r; cx <= pcx + r && done < budget; cx++) {
                for (int cz = pcz - r; cz <= pcz + r && done < budget; cz++) {
                    if (Math.max(Math.abs(cx - pcx), Math.abs(cz - pcz)) != r) continue;
                    long k = key(cx, cz);
                    Byte st = STATE.get(k);
                    if (st != null && st == 2) continue;                 // already known to be real
                    if (st != null && (r > 4 || ticks % 20 != 0)) continue; // filler chunks are re-checked only when you are close
                    WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                    if (chunk == null) continue;
                    STATE.put(k, (byte) (realDeep(chunk) ? 2 : 1));
                    done++;
                }
            }
        }
        if (STATE.size() > 60000) STATE.clear();
    }

    public static void render(DrawContext ctx, MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.sweepEnabled || mc.player == null || mc.world == null) return;
        int R = cfg.sweepRadius;
        int cell = 4;
        int side = (2 * R + 1) * cell;
        int w = Math.max(side + 12, 128);
        int h = side + 8 + 22;
        int x = Math.max(0, Math.min(cfg.sweepX, mc.getWindow().getScaledWidth() - w));
        int y = Math.max(0, Math.min(cfg.sweepY, mc.getWindow().getScaledHeight() - h));
        BOUNDS[0] = x; BOUNDS[1] = y; BOUNDS[2] = w; BOUNDS[3] = h;
        HudPanel.draw(ctx, x, y, w, h);

        int pcx = mc.player.getChunkPos().x, pcz = mc.player.getChunkPos().z;
        int ox = x + 6, oy = y + 6;
        for (int dz = -R; dz <= R; dz++) {
            for (int dx = -R; dx <= R; dx++) {
                int cx = pcx + dx, cz = pcz + dz;
                Byte st = STATE.get(key(cx, cz));
                int col = st == null ? 0xFF1A1710 : st == 2 ? 0xFF2F7A3A : 0xFF4A4A4A;
                ChunkPos p = new ChunkPos(cx, cz);
                if (cfg.sweepFlags) {
                    Integer sus = SusScanner.FOUND.get(p);
                    if (sus != null) col = 0xFFE23B3B;
                    else if (FarmFinder.FOUND.containsKey(p)) col = 0xFFF0B020;
                }
                int px = ox + (dx + R) * cell, py = oy + (dz + R) * cell;
                ctx.fill(px, py, px + cell - 1, py + cell - 1, col);
            }
        }
        // you
        int mx = ox + R * cell, my = oy + R * cell;
        ctx.fill(mx - 1, my - 1, mx + cell, my + cell, 0xFFFFFFFF);
        String[] names = {"real", "hidden", "sus", "farm"};
        int[] cols = {0xFF2F7A3A, 0xFF4A4A4A, 0xFFE23B3B, 0xFFF0B020};
        int lx = x + 6;
        int ly = y + side + 12;
        for (int i = 0; i < 4; i++) {
            ctx.fill(lx, ly + 1, lx + 5, ly + 6, cols[i]);
            ctx.drawTextWithShadow(mc.textRenderer, PFont.t(names[i]), lx + 8, ly, 0xFFB8AD90);
            lx += 8 + PFont.w(mc, names[i]) + 6;
        }
    }
}
