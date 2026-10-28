package com.chunkfinder;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * New Chunks: a guess about how old each chunk is, using only what the server openly sends you.
 *  - NEW (red): right after a chunk loads, the server sends updates for water or lava that is still flowing. That only
 *    happens in chunks that were freshly generated, because older chunks have already settled.
 *  - OLD (blue): the chunk has water or lava in it, and no such flowing updates came after it loaded.
 *  - Chunks with no water or lava can not be told apart, so they are left out (no colour).
 * Nobody builds in a new chunk, so a flag in a red chunk is probably natural. It is a guess, not a fact: a water farm
 * can make an old chunk look new. Nothing hidden is read and nothing is sent anywhere.
 */
public final class NewChunks {
    private NewChunks() {}

    /** 1 = new, 2 = old. */
    private static final Map<Long, Integer> STATE = new ConcurrentHashMap<>();
    private static final Map<Long, Long> LOADED = new ConcurrentHashMap<>();
    private static final ArrayDeque<long[]> PENDING = new ArrayDeque<>();   // {key, loadTick, cx, cz}
    private static long ticks = 0;
    private static Object lastWorld = null;
    private static final int WINDOW = 160;   // ticks after loading in which flowing fluid counts as a sign of a new chunk
    private static final int CHECK = 200;    // ticks after loading when a chunk with no such sign is checked for fluid
    private static final int MAX = 30000;

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    public static void clear() {
        STATE.clear();
        LOADED.clear();
        PENDING.clear();
    }

    /** 0 = unknown, 1 = new, 2 = old. */
    public static int state(int cx, int cz) {
        Integer s = STATE.get(key(cx, cz));
        return s == null ? 0 : s;
    }

    public static void onChunkLoad(WorldChunk chunk) {
        if (!ChunkFinderConfig.INSTANCE.newChunksEnabled) return;
        int cx = chunk.getPos().x, cz = chunk.getPos().z;
        long k = key(cx, cz);
        if (STATE.containsKey(k) && STATE.get(k) == 1) return;   // already known to be new
        LOADED.put(k, ticks);
        PENDING.add(new long[]{k, ticks, cx, cz});
    }

    /** Called for every block change the server sends (see the packet hook). */
    public static void fluid(BlockPos pos, BlockState state) {
        if (!ChunkFinderConfig.INSTANCE.newChunksEnabled || state == null) return;
        var f = state.getFluidState();
        if (f.isEmpty() || f.isStill()) return;
        long k = key(Math.floorDiv(pos.getX(), 16), Math.floorDiv(pos.getZ(), 16));
        Long lt = LOADED.get(k);
        if (lt != null && ticks - lt <= WINDOW) {
            if (STATE.size() < MAX || STATE.containsKey(k)) STATE.put(k, 1);
        }
    }

    public static void tick(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.newChunksEnabled || mc.world == null) return;
        if (mc.world != lastWorld) {
            lastWorld = mc.world;
            clear();
        }
        ticks++;
        int done = 0;
        while (!PENDING.isEmpty() && done < 10) {
            long[] p = PENDING.peekFirst();
            if (ticks - p[1] < CHECK) break;
            PENDING.pollFirst();
            done++;
            LOADED.remove(p[0]);
            if (STATE.containsKey(p[0])) continue;   // already marked new
            WorldChunk c = mc.world.getChunkManager().getWorldChunk((int) p[2], (int) p[3]);
            if (c == null) continue;
            boolean fluid = false;
            for (ChunkSection s : c.getSectionArray()) {
                if (s != null && !s.isEmpty() && s.hasAny(st -> !st.getFluidState().isEmpty())) { fluid = true; break; }
            }
            if (fluid && STATE.size() < MAX) STATE.put(p[0], 2);
        }
    }

    public static void collect(List<EspRenderer.Box> boxes, List<EspRenderer.Line> lines, Vec3d cam) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.newChunksEnabled || (!cfg.newChunksShowNew && !cfg.newChunksShowOld)) return;
        int pcx = Math.floorDiv((int) Math.floor(cam.x), 16), pcz = Math.floorDiv((int) Math.floor(cam.z), 16);
        int r = cfg.newChunksRange;
        double y = cfg.newChunksY;   // a fixed height, so the colours stay put when you move up or down
        int n = 0;
        for (int dx = -r; dx <= r && n < 500; dx++) {
            for (int dz = -r; dz <= r && n < 500; dz++) {
                int s = state(pcx + dx, pcz + dz);
                if (s == 0 || (s == 1 && !cfg.newChunksShowNew) || (s == 2 && !cfg.newChunksShowOld)) continue;
                double x0 = (pcx + dx) * 16, z0 = (pcz + dz) * 16;
                if (s == 1) boxes.add(new EspRenderer.Box(x0, y, z0, x0 + 16, y + 0.3, z0 + 16, 1f, 0.30f, 0.25f, 0.22f));
                else boxes.add(new EspRenderer.Box(x0, y, z0, x0 + 16, y + 0.3, z0 + 16, 0.25f, 0.55f, 1f, 0.22f));
                n++;
            }
        }
    }
}
