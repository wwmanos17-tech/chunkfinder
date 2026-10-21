package com.chunkfinder;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Chest ESP: boxes through walls around storage, spawners, beacons, beds and chest minecarts.
 * Block entities are scanned every half second and cached; minecarts are checked every frame.
 */
public class ChestEsp {
    public enum Kind {
        CHEST("Chest", 255, 170, 0),
        TRAPPED("Trapped Chest", 255, 60, 60),
        BARREL("Barrel", 190, 125, 60),
        ENDER("Ender Chest", 170, 70, 255),
        SHULKER("Shulker Box", 255, 120, 220),
        SPAWNER("Spawner", 80, 255, 80),
        BEACON("Beacon", 120, 255, 255),
        BED("Bed", 255, 90, 120),
        MINECART("Chest Minecart", 255, 140, 0);

        public final String label;
        public final float r, g, b;

        Kind(String label, int r, int g, int b) {
            this.label = label;
            this.r = r / 255f;
            this.g = g / 255f;
            this.b = b / 255f;
        }
    }

    public record Target(Kind kind, double x1, double y1, double z1, double x2, double y2, double z2) {
        public Vec3d center() {
            return new Vec3d((x1 + x2) / 2, (y1 + y2) / 2, (z1 + z2) / 2);
        }
    }

    public static final List<Target> TARGETS = new ArrayList<>();
    public static final Map<Kind, Integer> COUNTS = new EnumMap<>(Kind.class);
    private static int timer = 0;

    /** Everything seen so far, so a chest the server stops sending later stays marked. */
    private static final Map<Long, Target> MEMORY = new java.util.LinkedHashMap<>();

    private static final java.util.Set<Long> SPAWNERS_SEEN = new java.util.HashSet<>();
    private static final java.util.Set<Long> BASES_SEEN = new java.util.HashSet<>();

    private static void say(MinecraftClient mc, String text) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(text), false);
    }

    public static void clear() {
        SPAWNERS_SEEN.clear();
        BASES_SEEN.clear();
        TARGETS.clear();
        COUNTS.clear();
        MEMORY.clear();
        timer = 0;
    }

    private static boolean enabled(Kind k) {
        ChunkFinderConfig c = ChunkFinderConfig.INSTANCE;
        return switch (k) {
            case CHEST -> c.espChest;
            case TRAPPED -> c.espTrapped;
            case BARREL -> c.espBarrel;
            case ENDER -> c.espEnder;
            case SHULKER -> c.espShulker;
            case SPAWNER -> c.espSpawner;
            case BEACON -> c.espBeacon;
            case BED -> c.espBed;
            case MINECART -> c.espMinecart;
        };
    }

    private static Kind classify(BlockEntity be) {
        if (be instanceof TrappedChestBlockEntity) return Kind.TRAPPED;
        if (be instanceof ChestBlockEntity) return Kind.CHEST;
        if (be instanceof BarrelBlockEntity) return Kind.BARREL;
        if (be instanceof EnderChestBlockEntity) return Kind.ENDER;
        if (be instanceof ShulkerBoxBlockEntity) return Kind.SHULKER;
        if (be instanceof MobSpawnerBlockEntity) return Kind.SPAWNER;
        if (be instanceof BeaconBlockEntity) return Kind.BEACON;
        if (be instanceof BedBlockEntity) return Kind.BED;
        return null;
    }

    public static void tick(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.chestEspEnabled || mc.world == null || mc.player == null) return;
        if (timer-- > 0) return;
        timer = 3;

        java.util.Set<Long> seenNow = new java.util.HashSet<>();
        Map<Kind, Integer> counts = new EnumMap<>(Kind.class);

        int radius = Math.min((cfg.chestEspRange + 15) / 16, 128);
        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;

        outer:
        for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
            for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;
                int stored = 0;
                int yTotal = 0;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    Kind k = classify(be);
                    if (k == null) continue;
                    BlockPos p = be.getPos();
                    if (k == Kind.SPAWNER && cfg.alertSpawners && SPAWNERS_SEEN.add(p.asLong())) {
                        say(mc, "[Pharaoh] Spawner found at X " + p.getX() + ", Y " + p.getY() + ", Z " + p.getZ());
                    }
                    if (k == Kind.CHEST || k == Kind.TRAPPED || k == Kind.BARREL || k == Kind.SHULKER || k == Kind.BED || k == Kind.SPAWNER) {
                        stored++;
                        yTotal += p.getY();
                    }
                    BlockState st = be.getCachedState();
                    VoxelShape shape = st.getOutlineShape(mc.world, p);
                    Box b = shape.isEmpty() ? new Box(p) : shape.getBoundingBox().offset(p);
                    seenNow.add(p.asLong());
                    MEMORY.put(p.asLong(), new Target(k, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ));
                    if (MEMORY.size() >= 6000) break outer;
                }
                if (cfg.alertBases && stored >= 4 && BASES_SEEN.add(chunk.getPos().toLong())) {
                    say(mc, "[Pharaoh] Base found at X " + (chunk.getPos().getStartX() + 8)
                        + ", Y " + (yTotal / stored) + ", Z " + (chunk.getPos().getStartZ() + 8)
                        + " (" + stored + " storage/beds/spawners)");
                }
            }
        }

        // forget remembered ones only when their chunk is loaded and the spot is now air
        java.util.Iterator<Map.Entry<Long, Target>> it = MEMORY.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Target> en = it.next();
            if (seenNow.contains(en.getKey())) continue;
            BlockPos bp = BlockPos.fromLong(en.getKey());
            if (mc.world.getChunkManager().getWorldChunk(bp.getX() >> 4, bp.getZ() >> 4) != null
                && mc.world.getBlockState(bp).isAir()) {
                it.remove();
            }
        }
        List<Target> list = new ArrayList<>();
        for (Target t : MEMORY.values()) {
            if (!enabled(t.kind())) continue;
            list.add(t);
            counts.merge(t.kind(), 1, Integer::sum);
            if (list.size() >= 4000) break;
        }

        TARGETS.clear();
        TARGETS.addAll(list);
        COUNTS.clear();
        COUNTS.putAll(counts);
    }

    /** Distance ignoring height, so chests deep underground or high up still count. */
    private static double flatDist(Vec3d a, Vec3d b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** All targets right now, including chest minecarts, within range of the camera. */
    public static List<Target> current(MinecraftClient mc, Vec3d cam) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        List<Target> out = new ArrayList<>();
        double range = cfg.chestEspRange;
        for (Target t : TARGETS) {
            if (flatDist(t.center(), cam) <= range) out.add(t);
        }
        if (cfg.espMinecart && mc.world != null) {
            for (Entity e : mc.world.getEntities()) {
                if (e instanceof ChestMinecartEntity) {
                    Box b = e.getBoundingBox();
                    Target t = new Target(Kind.MINECART, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
                    if (flatDist(t.center(), cam) <= range) out.add(t);
                }
            }
        }
        return out;
    }

    public static void collect(List<EspRenderer.Box> boxes, List<EspRenderer.Line> lines, Vec3d cam) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.chestEspEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        Vec3d start = Proj.tracerStart(mc);
        int tracerCount = 0;
        for (Target t : current(mc, cam)) {
            // far away chests get a bigger box and a tall line above them so they are easy to spot
            Vec3d ctr = t.center();
            double dist = Math.sqrt(flatDist(ctr, cam) * flatDist(ctr, cam) + (ctr.y - cam.y) * (ctr.y - cam.y));
            double grow = Math.min(1.5, Math.max(0, (dist - 40) / 100.0));
            boxes.add(new EspRenderer.Box(t.x1() - grow, t.y1() - grow, t.z1() - grow, t.x2() + grow, t.y2() + grow, t.z2() + grow,
                t.kind().r, t.kind().g, t.kind().b, cfg.chestEspFill ? 0.22f : 0f));
            if (dist > 40 && tracerCount < 150) {
                lines.add(new EspRenderer.Line(ctr.x, t.y2() + grow, ctr.z, ctr.x, t.y2() + grow + 12, ctr.z,
                    t.kind().r, t.kind().g, t.kind().b));
            }
            if (cfg.chestEspTracers && tracerCount < 150) {
                Vec3d c = t.center();
                lines.add(new EspRenderer.Line(start.x, start.y, start.z, c.x, c.y, c.z,
                    t.kind().r, t.kind().g, t.kind().b));
                tracerCount++;
            }
        }
    }
}
