package com.chunkfinder;

import net.minecraft.block.Blocks;
import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Scans loaded chunks around the player and caches which ones are interesting. */
public class ChunkScanner {
    public enum Result { GEODE, ACTIVITY, BOTH }

    public static final Map<ChunkPos, Result> FOUND = new ConcurrentHashMap<>();
    private static int timer = 0;

    public static void clear() {
        FOUND.clear();
        timer = 0;
    }

    public static void tick(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.enabled || mc.world == null || mc.player == null) return;
        if (timer-- > 0) return;
        timer = cfg.scanInterval;

        FOUND.clear();

        // Never scan further than the client actually has loaded.
        int radius = Math.min(cfg.simulationDistance, mc.options.getViewDistance().getValue());
        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;
        int threshold = cfg.requiredActivityBlocks();

        for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
            for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;

                boolean geode = cfg.detectGeodes && hasGeode(chunk);
                boolean activity = cfg.detectActivity && activityScore(chunk) >= threshold;

                if (geode && activity) FOUND.put(chunk.getPos(), Result.BOTH);
                else if (geode) FOUND.put(chunk.getPos(), Result.GEODE);
                else if (activity) FOUND.put(chunk.getPos(), Result.ACTIVITY);
            }
        }
    }

    private static boolean hasGeode(WorldChunk chunk) {
        for (ChunkSection section : chunk.getSectionArray()) {
            if (section == null || section.isEmpty()) continue;
            // Palette check: only walks the section if the block is present in it.
            if (section.hasAny(state -> state.isOf(Blocks.BUDDING_AMETHYST))) return true;
        }
        return false;
    }

    private static int activityScore(WorldChunk chunk) {
        int score = 0;
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof ChestBlockEntity
                || be instanceof BarrelBlockEntity
                || be instanceof ShulkerBoxBlockEntity
                || be instanceof EnderChestBlockEntity
                || be instanceof AbstractFurnaceBlockEntity
                || be instanceof HopperBlockEntity
                || be instanceof BedBlockEntity
                || be instanceof DispenserBlockEntity
                || be instanceof BrewingStandBlockEntity) {
                score++;
            }
        }
        return score;
    }
}
