package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Farm Finder: flags chunks that are packed with entities, such as villager halls, animal pens, mob grinders, minecart
 * lines and item piles. It uses the entities the server has already sent to your game (about 48 blocks around you), and
 * remembers what it found so the marker stays after you walk away.
 */
public final class FarmFinder {
    private FarmFinder() {}

    public record Found(ChunkPos pos, double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                        int score, String text, long seen) {}

    public static final Map<ChunkPos, Found> FOUND = new HashMap<>();
    private static final Set<ChunkPos> ALERTED = new HashSet<>();
    public static volatile List<Found> SHOWN = new ArrayList<>();
    private static long ticks = 0;
    private static Object lastWorld = null;

    public static void clear() {
        FOUND.clear();
        ALERTED.clear();
        SHOWN = new ArrayList<>();
        ticks = 0;
    }

    private static final class Acc {
        double score;
        int villagers, animals, mobs, items, carts, stands;
        double minX = 1e9, minY = 1e9, minZ = 1e9, maxX = -1e9, maxY = -1e9, maxZ = -1e9;
        void add(Entity e) {
            minX = Math.min(minX, e.getX() - 0.5); maxX = Math.max(maxX, e.getX() + 0.5);
            minY = Math.min(minY, e.getY()); maxY = Math.max(maxY, e.getY() + 1.5);
            minZ = Math.min(minZ, e.getZ() - 0.5); maxZ = Math.max(maxZ, e.getZ() + 0.5);
        }
    }

    public static void tick(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.farmEnabled || mc.world == null || mc.player == null) return;
        if (mc.world != lastWorld) { lastWorld = mc.world; FOUND.clear(); ALERTED.clear(); SHOWN = new ArrayList<>(); }
        ticks++;
        if (ticks % 10 != 0) return;

        Map<ChunkPos, Acc> per = new HashMap<>();
        int n = 0;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof PlayerEntity) continue;
            if (++n > 1500) break;
            double w;
            if (e instanceof VillagerEntity || e instanceof WanderingTraderEntity) w = 2.0;
            else if (e instanceof AnimalEntity) w = 1.0;
            else if (e instanceof MobEntity) w = 1.0;
            else if (e instanceof AbstractMinecartEntity) w = 1.5;
            else if (e instanceof ArmorStandEntity) w = 0.7;
            else if (e instanceof ItemEntity) w = 0.15;
            else continue;
            if (cfg.farmUnderground && mc.world.isSkyVisible(e.getBlockPos()) && e.getY() >= 40) continue;
            Acc a = per.computeIfAbsent(e.getChunkPos(), k -> new Acc());
            a.score += w;
            if (e instanceof VillagerEntity || e instanceof WanderingTraderEntity) a.villagers++;
            else if (e instanceof AnimalEntity) a.animals++;
            else if (e instanceof MobEntity) a.mobs++;
            else if (e instanceof AbstractMinecartEntity) a.carts++;
            else if (e instanceof ArmorStandEntity) a.stands++;
            else a.items++;
            a.add(e);
        }

        int pcx = mc.player.getChunkPos().x, pcz = mc.player.getChunkPos().z;
        for (Map.Entry<ChunkPos, Acc> en : per.entrySet()) {
            Acc a = en.getValue();
            if (a.score < cfg.farmMin) continue;
            StringBuilder sb = new StringBuilder();
            if (a.villagers > 0) sb.append(a.villagers).append(" villagers ");
            if (a.animals > 0) sb.append(a.animals).append(" animals ");
            if (a.mobs > 0) sb.append(a.mobs).append(" mobs ");
            if (a.carts > 0) sb.append(a.carts).append(" minecarts ");
            if (a.stands > 0) sb.append(a.stands).append(" stands ");
            if (a.items > 0) sb.append(a.items).append(" items ");
            Found f = new Found(en.getKey(), a.minX, a.minY, a.minZ, a.maxX, a.maxY, a.maxZ, (int) a.score,
                sb.toString().trim(), ticks);
            FOUND.put(en.getKey(), f);
            if (cfg.farmChat && ALERTED.add(en.getKey())) {
                mc.player.sendMessage(Text.literal("[Pharaoh] Farm / entity pile at X " + (int) ((a.minX + a.maxX) / 2)
                    + ", Y " + (int) a.minY + ", Z " + (int) ((a.minZ + a.maxZ) / 2) + ": " + f.text()), false);
            }
        }

        // forget old finds: close chunks that no longer qualify are dropped, others after 10 minutes
        FOUND.entrySet().removeIf(en -> {
            ChunkPos p = en.getKey();
            boolean near = Math.abs(p.x - pcx) <= 2 && Math.abs(p.z - pcz) <= 2;
            Found f = en.getValue();
            if (f.seen() == ticks) return false;
            return near || ticks - f.seen() > 12000;
        });
        SHOWN = new ArrayList<>(FOUND.values());
    }

    public static void collect(List<EspRenderer.Box> boxes, List<EspRenderer.Line> lines, Vec3d cam) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.farmEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d start = mc.player == null ? null : Proj.tracerStart(mc);
        for (Found f : SHOWN) {
            double e = 0.4;
            boxes.add(new EspRenderer.Box(f.minX() - e, f.minY() - e, f.minZ() - e, f.maxX() + e, f.maxY() + e, f.maxZ() + e,
                1f, 0.7f, 0.1f, 0.14f));
            if (cfg.farmTracer && start != null) {
                lines.add(new EspRenderer.Line(start.x, start.y, start.z, (f.minX() + f.maxX()) / 2,
                    (f.minY() + f.maxY()) / 2, (f.minZ() + f.maxZ()) / 2, 1f, 0.7f, 0.1f));
            }
        }
    }
}
