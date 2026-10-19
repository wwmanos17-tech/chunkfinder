package com.chunkfinder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Ore ESP: boxes through walls around ores. Chunks are scanned two at a time, nearest first,
 * and the results are cached, so it stays light on FPS.
 */
public class OreEsp {
    private record Hit(int x, int y, int z, int ore) {}

    public static final String[] NAMES = {
        "Diamond", "Ancient Debris", "Emerald", "Gold", "Iron", "Coal", "Redstone", "Lapis", "Copper"
    };
    private static final float[][] COLORS = {
        {80, 235, 255}, {255, 150, 80}, {60, 255, 100}, {255, 215, 0}, {235, 205, 185},
        {170, 170, 170}, {255, 40, 40}, {60, 100, 255}, {255, 130, 60}
    };

    private static final Map<Block, Integer> ORE_INDEX = new HashMap<>();

    static {
        reg(0, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);
        reg(1, Blocks.ANCIENT_DEBRIS);
        reg(2, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE);
        reg(3, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE);
        reg(4, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE);
        reg(5, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
        reg(6, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE);
        reg(7, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE);
        reg(8, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE);
    }

    private static void reg(int idx, Block... blocks) {
        for (Block b : blocks) ORE_INDEX.put(b, idx);
    }

    private static final Map<ChunkPos, List<Hit>> CACHE = new HashMap<>();
    private static final Map<ChunkPos, Integer> SCANNED_AT = new HashMap<>();
    private static List<ChunkPos> pending = new ArrayList<>();
    private static int ticks = 0;

    public static void clear() {
        CACHE.clear();
        SCANNED_AT.clear();
        pending = new ArrayList<>();
        ticks = 0;
    }

    private static boolean oreEnabled(int idx) {
        ChunkFinderConfig c = ChunkFinderConfig.INSTANCE;
        return switch (idx) {
            case 0 -> c.oreDiamond;
            case 1 -> c.oreDebris;
            case 2 -> c.oreEmerald;
            case 3 -> c.oreGold;
            case 4 -> c.oreIron;
            case 5 -> c.oreCoal;
            case 6 -> c.oreRedstone;
            case 7 -> c.oreLapis;
            case 8 -> c.oreCopper;
            default -> false;
        };
    }

    private static boolean wanted(BlockState st) {
        Integer idx = ORE_INDEX.get(st.getBlock());
        return idx != null && oreEnabled(idx);
    }

    public static void tick(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.oreEspEnabled || mc.world == null || mc.player == null) return;
        ticks++;

        int radius = Math.min(cfg.oreEspRange, mc.options.getViewDistance().getValue());
        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;

        // every second: work out which chunks still need scanning, nearest first
        if (ticks % 20 == 1) {
            List<ChunkPos> list = new ArrayList<>();
            for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
                for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                    ChunkPos pos = new ChunkPos(cx, cz);
                    Integer at = SCANNED_AT.get(pos);
                    if (at == null || ticks - at > 600) list.add(pos);
                }
            }
            list.sort((a, b) -> Integer.compare(
                (a.x - pcx) * (a.x - pcx) + (a.z - pcz) * (a.z - pcz),
                (b.x - pcx) * (b.x - pcx) + (b.z - pcz) * (b.z - pcz)));
            pending = list;
        }

        // scan up to two chunks per tick
        int budget = 2;
        while (budget > 0 && !pending.isEmpty()) {
            ChunkPos pos = pending.remove(0);
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(pos.x, pos.z);
            if (chunk == null) continue;
            CACHE.put(pos, scan(chunk));
            SCANNED_AT.put(pos, ticks);
            budget--;
        }

        // forget chunks far away
        if (ticks % 100 == 0) {
            Iterator<ChunkPos> it = CACHE.keySet().iterator();
            while (it.hasNext()) {
                ChunkPos p = it.next();
                if (Math.abs(p.x - pcx) > radius + 2 || Math.abs(p.z - pcz) > radius + 2) {
                    it.remove();
                    SCANNED_AT.remove(p);
                }
            }
        }
    }

    private static List<Hit> scan(WorldChunk chunk) {
        List<Hit> hits = new ArrayList<>();
        ChunkSection[] sections = chunk.getSectionArray();
        int cx = chunk.getPos().x * 16;
        int cz = chunk.getPos().z * 16;
        for (int i = 0; i < sections.length; i++) {
            ChunkSection s = sections[i];
            if (s == null || s.isEmpty()) continue;
            if (!s.hasAny(OreEsp::wanted)) continue;
            int y0 = (chunk.getBottomSectionCoord() + i) * 16;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState st = s.getBlockState(x, y, z);
                        Integer idx = ORE_INDEX.get(st.getBlock());
                        if (idx != null && oreEnabled(idx)) {
                            hits.add(new Hit(cx + x, y0 + y, cz + z, idx));
                            if (hits.size() >= 1500) return hits;
                        }
                    }
                }
            }
        }
        return hits;
    }

    public static void collect(List<EspRenderer.Box> boxes, List<EspRenderer.Line> lines, Vec3d cam) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.oreEspEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d start = Proj.tracerStart(mc);
        double range = cfg.oreEspRange * 16.0 + 16.0;
        int added = 0;
        int tracers = 0;

        for (List<Hit> hits : CACHE.values()) {
            for (Hit h : hits) {
                if (!oreEnabled(h.ore())) continue;
                double dx = h.x() + 0.5 - cam.x;
                double dy = h.y() + 0.5 - cam.y;
                double dz = h.z() + 0.5 - cam.z;
                if (dx * dx + dy * dy + dz * dz > range * range) continue;

                float[] c = COLORS[h.ore()];
                float r = c[0] / 255f;
                float g = c[1] / 255f;
                float b = c[2] / 255f;
                boxes.add(new EspRenderer.Box(h.x(), h.y(), h.z(), h.x() + 1, h.y() + 1, h.z() + 1,
                    r, g, b, cfg.oreFill ? 0.3f : 0f));
                if (cfg.oreTracers && tracers < 100) {
                    lines.add(new EspRenderer.Line(start.x, start.y, start.z,
                        h.x() + 0.5, h.y() + 0.5, h.z() + 0.5, r, g, b));
                    tracers++;
                }
                if (++added >= 2500) return;
            }
        }
    }
}
