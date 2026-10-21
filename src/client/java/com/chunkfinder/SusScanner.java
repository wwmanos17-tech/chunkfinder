package com.chunkfinder;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
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
 * Finds "sus" chunks: chunks that look like someone built or dug there.
 * Clues (Overworld only, underground only so villages are ignored):
 *  - blocks that never generate in caves (glass, ladders, doors, pistons, TNT ...)
 *  - perfectly straight 1x2 tunnels, 12 or more blocks long, which look like pickaxe digging
 * Chunks are scored once and cached, a couple per tick, so it does not cause lag.
 */
public class SusScanner {
    public static final Map<ChunkPos, Integer> FOUND = new ConcurrentHashMap<>();
    private static final Map<ChunkPos, Integer> SCORES = new HashMap<>();
    private static final Set<ChunkPos> NOTIFIED = new HashSet<>();

    private static int age = 0;
    private static final Set<ChunkPos> STALE = new HashSet<>();

    public static void clear() {
        age = 0;
        STALE.clear();
        FOUND.clear();
        SCORES.clear();
        NOTIFIED.clear();
    }

    private static boolean isSusBlock(BlockState s) {
        return s.isOf(Blocks.GLASS) || s.isOf(Blocks.GLASS_PANE) || s.isOf(Blocks.LADDER)
            || s.isOf(Blocks.SMOOTH_STONE) || s.isOf(Blocks.COBBLESTONE_STAIRS) || s.isOf(Blocks.COBBLESTONE_SLAB)
            || s.isOf(Blocks.CRAFTING_TABLE) || s.isOf(Blocks.OAK_DOOR) || s.isOf(Blocks.IRON_DOOR)
            || s.isOf(Blocks.REDSTONE_TORCH) || s.isOf(Blocks.REPEATER) || s.isOf(Blocks.PISTON)
            || s.isOf(Blocks.TNT) || s.isOf(Blocks.HOPPER) || s.isOf(Blocks.FURNACE) || s.isOf(Blocks.BLAST_FURNACE)
            || s.isOf(Blocks.SMOKER) || s.isOf(Blocks.DISPENSER) || s.isOf(Blocks.DROPPER) || s.isOf(Blocks.OBSERVER)
            || s.isOf(Blocks.STICKY_PISTON) || s.isOf(Blocks.REDSTONE_LAMP) || s.isOf(Blocks.COMPARATOR)
            || s.isOf(Blocks.ENCHANTING_TABLE) || s.isOf(Blocks.ANVIL) || s.isOf(Blocks.BEACON)
            || s.isOf(Blocks.DIAMOND_BLOCK) || s.isOf(Blocks.IRON_BLOCK) || s.isOf(Blocks.GOLD_BLOCK)
            || s.isOf(Blocks.NETHERITE_BLOCK) || s.isOf(Blocks.EMERALD_BLOCK)
            || s.isOf(Blocks.NOTE_BLOCK) || s.isOf(Blocks.SCAFFOLDING) || s.isOf(Blocks.WHITE_WOOL);
    }

    public static void tick(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.enabled || !cfg.detectSus || mc.world == null || mc.player == null) return;
        if (mc.world.getRegistryKey() != World.OVERWORLD) return;

        // every minute, mark the scores as old so chunks that changed get checked again (old scores stay shown meanwhile)
        if (++age >= 1200) {
            age = 0;
            STALE.addAll(SCORES.keySet());
        }

        int radius = Math.min(cfg.simulationDistance, mc.options.getViewDistance().getValue());
        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;

        int budget = 2;
        for (int cx = pcx - radius; cx <= pcx + radius && budget > 0; cx++) {
            for (int cz = pcz - radius; cz <= pcz + radius && budget > 0; cz++) {
                ChunkPos pos = new ChunkPos(cx, cz);
                if (SCORES.containsKey(pos) && !STALE.contains(pos)) continue;
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;
                SCORES.put(pos, score(mc, chunk));
                STALE.remove(pos);
                budget--;
            }
        }

        int need = cfg.requiredSusScore();
        FOUND.clear();
        for (Map.Entry<ChunkPos, Integer> e : SCORES.entrySet()) {
            ChunkPos p = e.getKey();
            if (Math.abs(p.x - pcx) > radius || Math.abs(p.z - pcz) > radius) continue;
            if (e.getValue() >= need) {
                FOUND.put(p, e.getValue());
                if (cfg.notify && NOTIFIED.add(p)) {
                    mc.player.sendMessage(Text.literal("[Pharaoh] Sus chunk at X " + (p.getStartX() + 8)
                        + ", Z " + (p.getStartZ() + 8)), false);
                }
            }
        }
    }

    private static int score(MinecraftClient mc, WorldChunk chunk) {
        int score = 0;

        // 1) blocks that do not belong in caves (only sections below about y 47)
        ChunkSection[] sections = chunk.getSectionArray();
        boolean found = false;
        for (int i = 0; i < sections.length; i++) {
            int sectionY = chunk.getBottomSectionCoord() + i;
            if (sectionY > 2) break;
            ChunkSection s = sections[i];
            if (s == null || s.isEmpty()) continue;
            if (s.hasAny(SusScanner::isSusBlock)) {
                found = true;
                break;
            }
        }
        if (found) score += 4;

        // 1b) things players keep in bases (chests, furnaces, hoppers, beds ...) below about y 47
        int storage = 0;
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be.getPos().getY() > 47) continue;
            if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity
                || be instanceof EnderChestBlockEntity || be instanceof AbstractFurnaceBlockEntity
                || be instanceof HopperBlockEntity || be instanceof BedBlockEntity
                || be instanceof DispenserBlockEntity || be instanceof BrewingStandBlockEntity) {
                storage++;
            }
        }
        score += Math.min(6, storage * 2);

        // 2) perfectly straight 1x2 tunnels
        score += Math.min(6, tunnels(mc, chunk) * 3);
        return score;
    }

    private static int tunnels(MinecraftClient mc, WorldChunk chunk) {
        int count = 0;
        int minY = Math.max(chunk.getBottomY() + 2, -58);
        int maxY = 40;
        int x0 = chunk.getPos().getStartX();
        int z0 = chunk.getPos().getStartZ();
        BlockPos.Mutable p = new BlockPos.Mutable();

        for (int axis = 0; axis < 2; axis++) {
            for (int lat = 1; lat <= 14; lat++) {
                for (int y = minY; y <= maxY; y++) {
                    int run = 0;
                    boolean counted = false;
                    for (int a = 0; a < 16; a++) {
                        int x = axis == 0 ? x0 + a : x0 + lat;
                        int z = axis == 0 ? z0 + lat : z0 + a;
                        int sx = axis == 0 ? 0 : 1; // sideways step
                        int sz = axis == 0 ? 1 : 0;

                        boolean ok = isAir(chunk, p, x, y, z) && isAir(chunk, p, x, y + 1, z);
                        if (ok) ok = isSolid(mc, chunk, p, x, y - 1, z) && isSolid(mc, chunk, p, x, y + 2, z);
                        if (ok) ok = isSolid(mc, chunk, p, x + sx, y, z + sz) && isSolid(mc, chunk, p, x - sx, y, z - sz);
                        if (ok) ok = isSolid(mc, chunk, p, x + sx, y + 1, z + sz) && isSolid(mc, chunk, p, x - sx, y + 1, z - sz);

                        if (ok) {
                            run++;
                            if (run >= 12 && !counted) {
                                count++;
                                counted = true;
                            }
                        } else {
                            run = 0;
                        }
                    }
                    if (count >= 3) return count;
                }
            }
        }
        return count;
    }

    private static boolean isAir(WorldChunk chunk, BlockPos.Mutable p, int x, int y, int z) {
        return chunk.getBlockState(p.set(x, y, z)).isAir();
    }

    private static boolean isSolid(MinecraftClient mc, WorldChunk chunk, BlockPos.Mutable p, int x, int y, int z) {
        p.set(x, y, z);
        BlockState s = chunk.getBlockState(p);
        return !s.isAir() && s.getFluidState().isEmpty() && s.isSolidBlock(mc.world, p);
    }
}
