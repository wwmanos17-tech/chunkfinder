package com.chunkfinder;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Light Finder: caves are dark, so torches, lanterns, glowstone and lamps deep underground
 * (below about y 40) usually mean someone built or mined there. Chunks with enough of them are flagged.
 * Overworld only. Chunks are scored a couple per tick and cached, so it stays light on FPS.
 */
public class LightFinder {
    public static final Map<ChunkPos, Integer> FOUND = new ConcurrentHashMap<>();
    /** Average height of the lights in each chunk. */
    public static final Map<ChunkPos, Integer> LEVELS = new ConcurrentHashMap<>();
    private static final Map<ChunkPos, Integer> SCORES = new HashMap<>();
    /** Exact block positions of the lights in each chunk. */
    public static final Map<ChunkPos, long[]> SPOTS = new ConcurrentHashMap<>();
    private static final Set<ChunkPos> STALE = new HashSet<>();
    private static final Set<ChunkPos> NOTIFIED = new HashSet<>();
    private static int age = 0;

    public static void clear() {
        FOUND.clear();
        SCORES.clear();
        SPOTS.clear();
        LEVELS.clear();
        STALE.clear();
        NOTIFIED.clear();
        age = 0;
    }

    private static boolean isLight(BlockState s) {
        // plain torches are left out on purpose: every miner leaves them along ordinary tunnels
        return s.isOf(Blocks.LANTERN) || s.isOf(Blocks.SOUL_LANTERN)
            || s.isOf(Blocks.GLOWSTONE) || s.isOf(Blocks.SEA_LANTERN) || s.isOf(Blocks.SHROOMLIGHT)
            || s.isOf(Blocks.JACK_O_LANTERN) || s.isOf(Blocks.CAMPFIRE) || s.isOf(Blocks.SOUL_CAMPFIRE)
            || s.isOf(Blocks.OCHRE_FROGLIGHT) || s.isOf(Blocks.VERDANT_FROGLIGHT) || s.isOf(Blocks.PEARLESCENT_FROGLIGHT)
            || s.isOf(Blocks.END_ROD) || s.isOf(Blocks.REDSTONE_LAMP) || s.isOf(Blocks.BEACON) || s.isOf(Blocks.CONDUIT);
    }

    private static int score(WorldChunk chunk) {
        int count = 0;
        long ySum = 0;
        java.util.List<Long> spots = new java.util.ArrayList<>();
        ChunkSection[] sections = chunk.getSectionArray();
        for (int i = 0; i < sections.length; i++) {
            int sectionY = chunk.getBottomSectionCoord() + i;
            if (sectionY > 2) break;                       // only below about y 48
            ChunkSection sec = sections[i];
            if (sec == null || sec.isEmpty() || !sec.hasAny(LightFinder::isLight)) continue;
            for (int ly = 0; ly < 16; ly++) {
                if (sectionY * 16 + ly > 40) break;
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        if (isLight(sec.getBlockState(lx, ly, lz))) {
                            count++;
                            ySum += sectionY * 16 + ly;
                            if (spots.size() < 40) spots.add(net.minecraft.util.math.BlockPos.asLong(chunk.getPos().getStartX() + lx, sectionY * 16 + ly, chunk.getPos().getStartZ() + lz));
                        }
                    }
                }
            }
        }
        if (count > 0) LEVELS.put(chunk.getPos(), (int) (ySum / count));
        long[] arr = new long[spots.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = spots.get(i);
        SPOTS.put(chunk.getPos(), arr);
        return count;
    }

    public static void tick(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.enabled || !cfg.detectLight || mc.world == null || mc.player == null) return;
        if (mc.world.getRegistryKey() != World.OVERWORLD) return;

        if (++age >= 1200) {
            age = 0;
            STALE.addAll(SCORES.keySet());
        }

        int radius = mc.options.getViewDistance().getValue(); // every chunk the game has loaded, so flagged chunks show from anywhere
        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;

        int budget = FastScan.maxChunks(mc, 2);
        long startNs = System.nanoTime();
        long limitNs = FastScan.limitNs(mc);
        boolean fast = FastScan.on();
        int done = 0;
        for (int cx = pcx - radius; cx <= pcx + radius && done < budget; cx++) {
            for (int cz = pcz - radius; cz <= pcz + radius && done < budget; cz++) {
                if (fast && done > 0 && System.nanoTime() - startNs > limitNs) break;
                ChunkPos pos = new ChunkPos(cx, cz);
                if (SCORES.containsKey(pos) && !STALE.contains(pos)) continue;
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;
                SCORES.put(pos, score(chunk));
                STALE.remove(pos);
                done++;
            }
        }

        FOUND.clear();
        for (Map.Entry<ChunkPos, Integer> e : SCORES.entrySet()) {
            ChunkPos p = e.getKey();
            if (Math.abs(p.x - pcx) > radius || Math.abs(p.z - pcz) > radius) continue;
            if (e.getValue() >= cfg.lightMin) {
                FOUND.put(p, e.getValue());
                if (cfg.notify && NOTIFIED.add(p)) {
                    mc.player.sendMessage(Text.literal("[Pharaoh] Light finder: " + e.getValue() + " lights underground at X "
                        + (p.getStartX() + 8) + ", Z " + (p.getStartZ() + 8)), false);
                }
            }
        }
    }
}
