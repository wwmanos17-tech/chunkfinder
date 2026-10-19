package com.chunkfinder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Scans loaded chunks around the player and caches which ones are interesting. */
public class ChunkScanner {
    public enum Result { GEODE, ACTIVITY, BOTH }

    public static final Map<ChunkPos, Result> FOUND = new ConcurrentHashMap<>();
    private static final Set<ChunkPos> NOTIFIED = new HashSet<>();
    private static int timer = 0;

    public static void clear() {
        FOUND.clear();
        NOTIFIED.clear();
        timer = 0;
    }

    /** Blocks that players place a lot of, and that rarely or never generate naturally (worth 3 points). */
    private static boolean isStrongBlock(BlockState s) {
        return s.isOf(Blocks.DIAMOND_BLOCK) || s.isOf(Blocks.NETHERITE_BLOCK)
            || s.isOf(Blocks.EMERALD_BLOCK) || s.isOf(Blocks.GOLD_BLOCK)
            || s.isOf(Blocks.IRON_BLOCK) || s.isOf(Blocks.BEACON)
            || s.isOf(Blocks.TNT) || s.isOf(Blocks.ENCHANTING_TABLE)
            || s.isOf(Blocks.ANVIL) || s.isOf(Blocks.OBSERVER)
            || s.isOf(Blocks.PISTON) || s.isOf(Blocks.STICKY_PISTON)
            || s.isOf(Blocks.REDSTONE_LAMP) || s.isOf(Blocks.COMPARATOR)
            || s.isOf(Blocks.REPEATER) || s.isOf(Blocks.NOTE_BLOCK);
    }

    /** Common building blocks (worth 1 point). Villages and mineshafts also have some, so this is a weaker clue. */
    private static boolean isWeakBlock(BlockState s) {
        return s.isOf(Blocks.CRAFTING_TABLE) || s.isOf(Blocks.TORCH) || s.isOf(Blocks.WALL_TORCH)
            || s.isOf(Blocks.SOUL_TORCH) || s.isOf(Blocks.LANTERN) || s.isOf(Blocks.GLASS)
            || s.isOf(Blocks.GLASS_PANE) || s.isOf(Blocks.LADDER) || s.isOf(Blocks.CAMPFIRE)
            || s.isOf(Blocks.SMOKER) || s.isOf(Blocks.BLAST_FURNACE) || s.isOf(Blocks.COBBLESTONE_STAIRS)
            || s.isOf(Blocks.SMOOTH_STONE) || s.isOf(Blocks.STONE_BRICKS) || s.isOf(Blocks.OAK_DOOR)
            || s.isOf(Blocks.OAK_SIGN) || s.isOf(Blocks.OAK_WALL_SIGN);
    }

    private static boolean isGeodeBlock(BlockState s) {
        return s.isOf(Blocks.BUDDING_AMETHYST);
    }

    public static void tick(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.enabled || mc.world == null || mc.player == null) return;
        if (timer-- > 0) return;
        timer = cfg.scanInterval;

        // Never scan further than the client actually has loaded.
        int radius = Math.min(cfg.simulationDistance, mc.options.getViewDistance().getValue());
        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;
        int threshold = cfg.requiredActivityBlocks();

        Map<ChunkPos, Result> fresh = new HashMap<>();

        for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
            for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;

                boolean geode = cfg.detectGeodes && hasGeode(chunk);
                boolean activity = cfg.detectActivity && activityScore(chunk) >= threshold;

                if (geode && activity) fresh.put(chunk.getPos(), Result.BOTH);
                else if (geode) fresh.put(chunk.getPos(), Result.GEODE);
                else if (activity) fresh.put(chunk.getPos(), Result.ACTIVITY);
            }
        }

        FOUND.clear();
        FOUND.putAll(fresh);

        // Chat alerts for chunks we have not reported yet.
        if (cfg.notify) {
            for (Map.Entry<ChunkPos, Result> e : fresh.entrySet()) {
                if (!NOTIFIED.add(e.getKey())) continue;
                int x = e.getKey().getStartX() + 8;
                int z = e.getKey().getStartZ() + 8;
                String what = switch (e.getValue()) {
                    case GEODE -> "Geode";
                    case ACTIVITY -> "Player activity";
                    case BOTH -> "Geode + activity";
                };
                mc.player.sendMessage(Text.literal("[Chunk Finder] " + what + " at X " + x + ", Z " + z), false);
            }
        }
    }

    private static boolean hasGeode(WorldChunk chunk) {
        for (ChunkSection section : chunk.getSectionArray()) {
            if (section == null || section.isEmpty()) continue;
            if (section.hasAny(ChunkScanner::isGeodeBlock)) return true;
        }
        return false;
    }

    private static int activityScore(WorldChunk chunk) {
        int score = 0;

        // Storage, furnaces, beds etc. count once each.
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof ChestBlockEntity
                || be instanceof BarrelBlockEntity
                || be instanceof ShulkerBoxBlockEntity
                || be instanceof EnderChestBlockEntity
                || be instanceof AbstractFurnaceBlockEntity
                || be instanceof HopperBlockEntity
                || be instanceof BedBlockEntity
                || be instanceof DispenserBlockEntity
                || be instanceof BrewingStandBlockEntity
                || be instanceof BeaconBlockEntity
                || be instanceof SignBlockEntity) {
                score++;
            }
        }

        // Player-made blocks: one check per chunk section, cheap because it only reads the palette.
        boolean strong = false;
        boolean weak = false;
        for (ChunkSection section : chunk.getSectionArray()) {
            if (section == null || section.isEmpty()) continue;
            if (!strong && section.hasAny(ChunkScanner::isStrongBlock)) strong = true;
            if (!weak && section.hasAny(ChunkScanner::isWeakBlock)) weak = true;
            if (strong && weak) break;
        }
        if (strong) score += 3;
        if (weak) score += 1;

        return score;
    }
}
