package com.chunkfinder;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cave Finder: finds hollow spaces (caves, tunnels, rooms) buried under the surface in the chunks your game already
 * holds, and boxes them through the ground. Natural caves are irregular (yellow). Box shaped hollows with straight
 * walls look like rooms and bases, so they get a stronger orange-red colour. It only reads blocks the server has sent
 * you, so layers the server fills with plain stone show nothing.
 */
public final class CaveFinder {
    private CaveFinder() {}

    public record Pocket(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int size, boolean boxy) {}

    private static final Map<ChunkPos, List<Pocket>> POCKETS = new HashMap<>();
    private static final Map<ChunkPos, Long> AT = new HashMap<>();
    private static long ticks = 0;
    /** The server only sends real blocks below y0 within about 2 chunks, so deep mode looks 3 chunks out. */
    private static final int DEEP_RANGE = 3;
    private static Object lastWorld = null;

    public static void clear() {
        POCKETS.clear();
        AT.clear();
        ticks = 0;
    }

    private static boolean isAir(BlockState s) {
        return s.isAir();
    }

    private static List<Pocket> scan(MinecraftClient mc, WorldChunk chunk, int minSize, boolean deep) {
        int yMin = Math.max(chunk.getBottomY(), -64);
        int yTop = Math.min(chunk.getBottomY() + chunk.getHeight(), deep ? 0 : 112);
        int hh = yTop - yMin;
        if (hh <= 4) return new ArrayList<>();
        boolean[] air = new boolean[16 * hh * 16];
        ChunkSection[] secs = chunk.getSectionArray();
        boolean any = false;
        for (int i = 0; i < secs.length; i++) {
            ChunkSection sec = secs[i];
            if (sec == null || sec.isEmpty()) continue;
            int y0 = (chunk.getBottomSectionCoord() + i) * 16;
            if (y0 + 16 <= yMin || y0 >= yTop) continue;
            if (!sec.hasAny(CaveFinder::isAir)) continue;       // fully solid section: nothing to find
            for (int y = 0; y < 16; y++) {
                int wy = y0 + y;
                if (wy < yMin || wy >= yTop) continue;
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (sec.getBlockState(x, y, z).isAir()) {
                            air[((wy - yMin) * 16 + z) * 16 + x] = true;
                            any = true;
                        }
                    }
                }
            }
        }
        List<Pocket> out = new ArrayList<>();
        if (!any) return out;

        // only count hollows well under the surface, so open ground and shallow dips are ignored
        int bx = chunk.getPos().getStartX(), bz = chunk.getPos().getStartZ();
        int[] limit = new int[256];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                limit[z * 16 + x] = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, bx + x, bz + z) - 4;
            }
        }
        for (int y = 0; y < hh; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (yMin + y > limit[z * 16 + x]) air[(y * 16 + z) * 16 + x] = false;
                }
            }
        }

        boolean[] seen = new boolean[air.length];
        int[] stack = new int[air.length];
        for (int start = 0; start < air.length; start++) {
            if (!air[start] || seen[start]) continue;
            int sp = 0, size = 0;
            int mnx = 99, mny = 99999, mnz = 99, mxx = -1, mxy = -99999, mxz = -1;
            stack[sp++] = start;
            seen[start] = true;
            while (sp > 0) {
                int idx = stack[--sp];
                int x = idx & 15, z = (idx >> 4) & 15, y = idx >> 8;
                size++;
                if (x < mnx) mnx = x; if (x > mxx) mxx = x;
                if (z < mnz) mnz = z; if (z > mxz) mxz = z;
                if (y < mny) mny = y; if (y > mxy) mxy = y;
                if (x > 0)  { int n = idx - 1;   if (air[n] && !seen[n]) { seen[n] = true; stack[sp++] = n; } }
                if (x < 15) { int n = idx + 1;   if (air[n] && !seen[n]) { seen[n] = true; stack[sp++] = n; } }
                if (z > 0)  { int n = idx - 16;  if (air[n] && !seen[n]) { seen[n] = true; stack[sp++] = n; } }
                if (z < 15) { int n = idx + 16;  if (air[n] && !seen[n]) { seen[n] = true; stack[sp++] = n; } }
                if (y > 0)  { int n = idx - 256; if (air[n] && !seen[n]) { seen[n] = true; stack[sp++] = n; } }
                if (y < hh - 1) { int n = idx + 256; if (air[n] && !seen[n]) { seen[n] = true; stack[sp++] = n; } }
            }
            if (size < minSize) continue;
            int dx = mxx - mnx + 1, dy = mxy - mny + 1, dz = mxz - mnz + 1;
            double fill = size / (double) (dx * dy * dz);
            boolean boxy = fill >= 0.6 && dx >= 3 && dz >= 3 && dy >= 2 && size >= Math.max(minSize, 24);
            out.add(new Pocket(bx + mnx, yMin + mny, bz + mnz, bx + mxx + 1, yMin + mxy + 1, bz + mxz + 1, size, boxy));
            if (out.size() >= 40) break;
        }
        return out;
    }

    public static void tick(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.caveEnabled || mc.world == null || mc.player == null) return;
        if (mc.world != lastWorld) { lastWorld = mc.world; clear(); }
        ticks++;
        boolean deep = cfg.caveDeep;
        int radius = Math.min(deep ? DEEP_RANGE : cfg.caveRange, mc.options.getViewDistance().getValue());
        int pcx = mc.player.getChunkPos().x, pcz = mc.player.getChunkPos().z;

        int budget = FastScan.maxChunks(mc, 1);
        long limit = FastScan.limitNs(mc);
        long start = System.nanoTime();
        int done = 0;
        for (int r = 0; r <= radius && done < budget; r++) {
            for (int cx = pcx - r; cx <= pcx + r && done < budget; cx++) {
                for (int cz = pcz - r; cz <= pcz + r && done < budget; cz++) {
                    if (Math.max(Math.abs(cx - pcx), Math.abs(cz - pcz)) != r) continue;
                    ChunkPos pos = new ChunkPos(cx, cz);
                    Long at = AT.get(pos);
                    long every = deep ? 20 : (r <= FastScan.NEAR ? 40 : 1200);
                    if (at != null && ticks - at < every) continue;
                    if (done > 0 && System.nanoTime() - start > limit) { done = budget; break; }
                    WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                    if (chunk == null) continue;
                    POCKETS.put(pos, scan(mc, chunk, cfg.caveMin, deep));
                    AT.put(pos, ticks);
                    done++;
                }
            }
        }
        if (ticks % 200 == 0) {
            POCKETS.keySet().removeIf(p -> Math.abs(p.x - pcx) > radius + 3 || Math.abs(p.z - pcz) > radius + 3);
            AT.keySet().removeIf(p -> !POCKETS.containsKey(p));
        }
    }

    public static void collect(List<EspRenderer.Box> boxes, List<EspRenderer.Line> lines, Vec3d cam) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.caveEnabled) return;
        double range = (cfg.caveDeep ? DEEP_RANGE : cfg.caveRange) * 16.0 + 16.0;
        int added = 0;
        for (List<Pocket> list : POCKETS.values()) {
            for (Pocket p : list) {
                if (p.boxy() ? !cfg.caveRooms : !cfg.caveCaves) continue;
                double cx = (p.minX() + p.maxX()) / 2.0, cz = (p.minZ() + p.maxZ()) / 2.0, cy = (p.minY() + p.maxY()) / 2.0;
                double dx = cx - cam.x, dy = cy - cam.y, dz = cz - cam.z;
                if (dx * dx + dy * dy + dz * dz > range * range) continue;
                if (p.boxy()) {
                    boxes.add(new EspRenderer.Box(p.minX(), p.minY(), p.minZ(), p.maxX(), p.maxY(), p.maxZ(), 1f, 0.35f, 0.15f, 0.16f));
                } else {
                    boxes.add(new EspRenderer.Box(p.minX(), p.minY(), p.minZ(), p.maxX(), p.maxY(), p.maxZ(), 1f, 0.85f, 0.15f, cfg.caveFill ? 0.07f : 0f));
                }
                if (++added >= 600) return;
            }
        }
    }
}
