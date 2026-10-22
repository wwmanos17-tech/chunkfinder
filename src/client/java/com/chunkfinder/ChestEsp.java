package com.chunkfinder;

import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.chunk.ChunkSection;
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

    public record Target(Kind kind, double x1, double y1, double z1, double x2, double y2, double z2, int rgb) {
        public Target(Kind kind, double x1, double y1, double z1, double x2, double y2, double z2) {
            this(kind, x1, y1, z1, x2, y2, z2, 0);
        }

        public float r() { return rgb == 0 ? kind.r : ((rgb >> 16) & 255) / 255f; }
        public float g() { return rgb == 0 ? kind.g : ((rgb >> 8) & 255) / 255f; }
        public float b() { return rgb == 0 ? kind.b : (rgb & 255) / 255f; }

        public Vec3d center() {
            return new Vec3d((x1 + x2) / 2, (y1 + y2) / 2, (z1 + z2) / 2);
        }
    }

    public static final List<Target> TARGETS = new ArrayList<>();
    public static final Map<Kind, Integer> COUNTS = new EnumMap<>(Kind.class);
    private static int timer = 0;

    private static final java.util.Set<Long> DROPPER_POS = new java.util.HashSet<>();
    private static final java.util.Set<Long> STASH_SEEN = new java.util.HashSet<>();

    /** One chat message per 4x4 chunk area when it holds a lot of shulkers, ender chests, spawners or droppers. */
    private static void stashAlerts(MinecraftClient mc) {
        Map<Long, int[]> areas = new java.util.HashMap<>();
        for (Target t : MEMORY.values()) {
            int idx = switch (t.kind()) {
                case SHULKER -> 0;
                case ENDER -> 1;
                case SPAWNER -> 2;
                default -> -1;
            };
            if (idx < 0) continue;
            addToArea(areas, (int) Math.floor(t.x1()), (int) Math.floor(t.y1()), (int) Math.floor(t.z1()), idx);
        }
        for (long l : DROPPER_POS) {
            BlockPos p = BlockPos.fromLong(l);
            addToArea(areas, p.getX(), p.getY(), p.getZ(), 3);
        }
        for (Map.Entry<Long, int[]> e : areas.entrySet()) {
            int[] a = e.getValue();
            boolean big = a[0] >= 5 || a[1] >= 3 || a[2] >= 2 || a[3] >= 8;
            if (!big || !STASH_SEEN.add(e.getKey())) continue;
            StringBuilder sb = new StringBuilder();
            String[] names = {"shulker boxes", "ender chests", "spawners", "droppers"};
            for (int i = 0; i < 4; i++) {
                if (a[i] == 0) continue;
                if (sb.length() > 0) sb.append(", ");
                sb.append(a[i]).append(' ').append(names[i]);
            }
            int n = Math.max(1, a[7]);
            say(mc, "[Pharaoh] Stash found near X " + (a[4] / n) + ", Y " + (a[5] / n) + ", Z " + (a[6] / n) + ": " + sb);
        }
    }

    private static void addToArea(Map<Long, int[]> areas, int x, int y, int z, int idx) {
        long key = ((long) (x >> 6) << 32) ^ ((z >> 6) & 0xFFFFFFFFL);
        int[] a = areas.computeIfAbsent(key, k -> new int[8]);
        a[idx]++;
        a[4] += x;
        a[5] += y;
        a[6] += z;
        a[7]++;
    }
    private static int ticks = 0;
    private static final Map<Long, Integer> BLOCK_SCANNED = new java.util.HashMap<>();

    /** Own colors: each shulker color, single vs double chests, single vs double trapped chests. 0 = default color of the kind. */
    private static int tint(BlockState st, Kind k) {
        Block b = st.getBlock();
        if (b instanceof ShulkerBoxBlock) {
            net.minecraft.util.DyeColor dye = ((ShulkerBoxBlock) b).getColor();
            return dye == null ? 0xB478C8 : dye.getFireworkColor();
        }
        if ((k == Kind.CHEST || k == Kind.TRAPPED) && st.contains(net.minecraft.block.ChestBlock.CHEST_TYPE)) {
            boolean dbl = st.get(net.minecraft.block.ChestBlock.CHEST_TYPE) != net.minecraft.block.enums.ChestType.SINGLE;
            if (k == Kind.CHEST) return dbl ? 0xFFE63C : 0xFFAA00;
            return dbl ? 0xFF2ED8 : 0xFF3C3C;
        }
        return 0;
    }

    private static boolean storage(BlockState st) {
        Block b = st.getBlock();
        return b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.ENDER_CHEST || b == Blocks.BARREL
            || b == Blocks.SPAWNER || b == Blocks.BEACON || b == Blocks.DROPPER || b instanceof ShulkerBoxBlock;
    }

    private static Kind blockKind(Block b) {
        if (b == Blocks.CHEST) return Kind.CHEST;
        if (b == Blocks.TRAPPED_CHEST) return Kind.TRAPPED;
        if (b == Blocks.ENDER_CHEST) return Kind.ENDER;
        if (b == Blocks.BARREL) return Kind.BARREL;
        if (b == Blocks.SPAWNER) return Kind.SPAWNER;
        if (b == Blocks.BEACON) return Kind.BEACON;
        if (b instanceof ShulkerBoxBlock) return Kind.SHULKER;
        return null;
    }

    /**
     * Finds chests by looking at the blocks themselves, not only at block entities. The server
     * often does not send block entities for far away or deep chunks, but the blocks are there.
     */
    private static void blockScan(MinecraftClient mc, WorldChunk chunk, ChunkFinderConfig cfg) {
        ChunkSection[] sections = chunk.getSectionArray();
        int bx = chunk.getPos().x * 16;
        int bz = chunk.getPos().z * 16;
        for (int i = 0; i < sections.length; i++) {
            ChunkSection sec = sections[i];
            if (sec == null || sec.isEmpty() || !sec.hasAny(ChestEsp::storage)) continue;
            int y0 = (chunk.getBottomSectionCoord() + i) * 16;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState st = sec.getBlockState(x, y, z);
                        if (st.getBlock() == Blocks.DROPPER) {
                            DROPPER_POS.add(new BlockPos(bx + x, y0 + y, bz + z).asLong());
                            continue;
                        }
                        Kind k = blockKind(st.getBlock());
                        if (k == null) continue;
                        BlockPos p = new BlockPos(bx + x, y0 + y, bz + z);
                        if (k == Kind.SPAWNER && cfg.alertSpawners && SPAWNERS_SEEN.add(p.asLong())) {
                            say(mc, "[Pharaoh] Spawner found at X " + p.getX() + ", Y " + p.getY() + ", Z " + p.getZ());
                        }
                        if (MEMORY.containsKey(p.asLong())) continue;
                        VoxelShape shape = st.getOutlineShape(mc.world, p);
                        Box b = shape.isEmpty() ? new Box(p) : shape.getBoundingBox().offset(p);
                        MEMORY.put(p.asLong(), new Target(k, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, tint(st, k)));
                        if (MEMORY.size() >= 6000) return;
                    }
                }
            }
        }
    }

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
        DROPPER_POS.clear();
        STASH_SEEN.clear();
        VIEW = null;
        BLOCK_SCANNED.clear();
        timer = 0;
        ticks = 0;
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
        ticks += 3;
        int scanBudget = 6;

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
                if (scanBudget > 0) {
                    Integer last = BLOCK_SCANNED.get(chunk.getPos().toLong());
                    if (last == null || ticks - last > 400) {
                        BLOCK_SCANNED.put(chunk.getPos().toLong(), ticks);
                        blockScan(mc, chunk, cfg);
                        scanBudget--;
                    }
                }
                int stored = 0;
                int spawners = 0;
                int yTotal = 0;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof DropperBlockEntity && mc.world.getBlockState(be.getPos()).getBlock() == Blocks.DROPPER) {
                        DROPPER_POS.add(be.getPos().asLong());
                    }
                    Kind k = classify(be);
                    if (k == null) continue;
                    BlockPos p = be.getPos();
                    // leftover data for a block that is gone (looted or broken) is ignored
                    if (mc.world.getBlockState(p).getBlock() != be.getCachedState().getBlock()) continue;
                    if (k == Kind.SPAWNER && cfg.alertSpawners && SPAWNERS_SEEN.add(p.asLong())) {
                        say(mc, "[Pharaoh] Spawner found at X " + p.getX() + ", Y " + p.getY() + ", Z " + p.getZ());
                    }
                    if (k == Kind.CHEST || k == Kind.TRAPPED || k == Kind.BARREL || k == Kind.SHULKER || k == Kind.SPAWNER) {
                        stored++;
                        if (k == Kind.SPAWNER) spawners++;
                        yTotal += p.getY();
                    }
                    BlockState st = be.getCachedState();
                    VoxelShape shape = st.getOutlineShape(mc.world, p);
                    Box b = shape.isEmpty() ? new Box(p) : shape.getBoundingBox().offset(p);
                    seenNow.add(p.asLong());
                    MEMORY.put(p.asLong(), new Target(k, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, tint(st, k)));
                    if (MEMORY.size() >= 6000) break outer;
                }
                // one alert per 4x4 chunk area, and only when there is a real pile of storage
                long region = ((long) (chunk.getPos().x >> 2) << 32) ^ (chunk.getPos().z >> 2 & 0xFFFFFFFFL);
                if (cfg.alertBases && stored >= 6 && BASES_SEEN.add(region)) {
                    say(mc, "[Pharaoh] Base found near X " + (chunk.getPos().getStartX() + 8)
                        + ", Y " + (yTotal / stored) + ", Z " + (chunk.getPos().getStartZ() + 8)
                        + " (" + stored + " containers" + (spawners > 0 ? ", " + spawners + " spawners" : "") + ")");
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
        if (cfg.alertStash) stashAlerts(mc);
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

    public record Group(double x1, double y1, double z1, double x2, double y2, double z2, int count, String text) {
        public Vec3d center() {
            return new Vec3d((x1 + x2) / 2, (y1 + y2) / 2, (z1 + z2) / 2);
        }
    }

    public record View(List<Target> singles, List<Group> groups) {}

    private static View VIEW = null;
    private static long viewAt = 0;

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    /** Targets split into single boxes (close by) and piles (3 or more far targets within 12 blocks of each other). */
    public static View view(MinecraftClient mc, Vec3d cam) {
        long now = System.currentTimeMillis();
        if (VIEW != null && now - viewAt < 150) return VIEW;
        viewAt = now;
        List<Target> all = current(mc, cam);
        if (!ChunkFinderConfig.INSTANCE.chestEspGroup) {
            VIEW = new View(all, new ArrayList<>());
            return VIEW;
        }
        List<Target> singles = new ArrayList<>();
        List<Target> far = new ArrayList<>();
        for (Target t : all) {
            if (t.center().distanceTo(cam) < 30) singles.add(t);
            else far.add(t);
        }
        int n = far.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        Map<Long, List<Integer>> cells = new java.util.HashMap<>();
        for (int i = 0; i < n; i++) {
            Vec3d c = far.get(i).center();
            int cx = (int) Math.floor(c.x / 12), cy = (int) Math.floor(c.y / 12), cz = (int) Math.floor(c.z / 12);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        List<Integer> in = cells.get(BlockPos.asLong(cx + dx, cy + dy, cz + dz));
                        if (in == null) continue;
                        for (int j : in) {
                            if (far.get(j).center().distanceTo(c) < 12) {
                                parent[find(parent, i)] = find(parent, j);
                            }
                        }
                    }
                }
            }
            cells.computeIfAbsent(BlockPos.asLong(cx, cy, cz), k -> new ArrayList<>()).add(i);
        }
        Map<Integer, List<Target>> byRoot = new java.util.HashMap<>();
        for (int i = 0; i < n; i++) byRoot.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(far.get(i));
        List<Group> groups = new ArrayList<>();
        for (List<Target> members : byRoot.values()) {
            if (members.size() < 3) {
                singles.addAll(members);
                continue;
            }
            double x1 = Double.MAX_VALUE, y1 = Double.MAX_VALUE, z1 = Double.MAX_VALUE;
            double x2 = -Double.MAX_VALUE, y2 = -Double.MAX_VALUE, z2 = -Double.MAX_VALUE;
            Map<Kind, Integer> counts = new EnumMap<>(Kind.class);
            for (Target t : members) {
                x1 = Math.min(x1, t.x1()); y1 = Math.min(y1, t.y1()); z1 = Math.min(z1, t.z1());
                x2 = Math.max(x2, t.x2()); y2 = Math.max(y2, t.y2()); z2 = Math.max(z2, t.z2());
                counts.merge(t.kind(), 1, Integer::sum);
            }
            StringBuilder sb = new StringBuilder("Pile: ");
            boolean first = true;
            for (Map.Entry<Kind, Integer> e : counts.entrySet()) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(e.getValue()).append(' ').append(e.getKey().label.toLowerCase(java.util.Locale.ROOT));
                if (e.getValue() > 1) sb.append('s');
            }
            groups.add(new Group(x1, y1, z1, x2, y2, z2, members.size(), sb.toString()));
        }
        VIEW = new View(singles, groups);
        return VIEW;
    }

    /** True when the container is open right now, or another player is standing next to it. */
    private static boolean active(MinecraftClient mc, Target t, Vec3d ctr) {
        Kind k = t.kind();
        if (k != Kind.CHEST && k != Kind.TRAPPED && k != Kind.BARREL && k != Kind.ENDER && k != Kind.SHULKER) return false;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (p.squaredDistanceTo(ctr) < 36) return true;
        }
        if (ctr.squaredDistanceTo(mc.player.getPos()) > 96 * 96) return false;
        BlockPos bp = BlockPos.ofFloored(t.x1() + 0.01, t.y1() + 0.01, t.z1() + 0.01);
        BlockEntity be = mc.world.getBlockEntity(bp);
        if (be instanceof LidOpenable lid) return lid.getAnimationProgress(1f) > 0.05f;
        BlockState st = mc.world.getBlockState(bp);
        if (st.getBlock() == Blocks.BARREL && st.contains(BarrelBlock.OPEN)) return st.get(BarrelBlock.OPEN);
        return false;
    }

    public static void collect(List<EspRenderer.Box> boxes, List<EspRenderer.Line> lines, Vec3d cam) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.chestEspEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        Vec3d start = Proj.tracerStart(mc);
        int tracerCount = 0;
        View view = view(mc, cam);
        for (Group g : view.groups()) {
            Vec3d gc = g.center();
            boxes.add(new EspRenderer.Box(g.x1() - 0.5, g.y1() - 0.5, g.z1() - 0.5, g.x2() + 0.5, g.y2() + 0.5, g.z2() + 0.5,
                1f, 0.84f, 0.3f, 0.2f));
            lines.add(new EspRenderer.Line(gc.x, g.y2() + 0.5, gc.z, gc.x, g.y2() + 0.5 + 16, gc.z, 1f, 0.84f, 0.3f));
            if (cfg.chestEspTracers && tracerCount < 150) {
                lines.add(new EspRenderer.Line(start.x, start.y, start.z, gc.x, gc.y, gc.z, 1f, 0.84f, 0.3f));
                tracerCount++;
            }
        }
        for (Target t : view.singles()) {
            // far away chests get a bigger box and a tall line above them so they are easy to spot
            Vec3d ctr = t.center();
            double dist = Math.sqrt(flatDist(ctr, cam) * flatDist(ctr, cam) + (ctr.y - cam.y) * (ctr.y - cam.y));
            double grow = Math.min(1.5, Math.max(0, (dist - 40) / 100.0));
            boolean live = cfg.chestEspActive && active(mc, t, ctr);
            if (live) {
                // open, or someone is next to it: whiter box, stronger fill, a bit bigger
                float r = t.r() + (1f - t.r()) * 0.55f;
                float g = t.g() + (1f - t.g()) * 0.55f;
                float b = t.b() + (1f - t.b()) * 0.55f;
                boxes.add(new EspRenderer.Box(t.x1() - grow - 0.08, t.y1() - grow - 0.08, t.z1() - grow - 0.08,
                    t.x2() + grow + 0.08, t.y2() + grow + 0.08, t.z2() + grow + 0.08, r, g, b, 0.5f));
            } else {
                boxes.add(new EspRenderer.Box(t.x1() - grow, t.y1() - grow, t.z1() - grow, t.x2() + grow, t.y2() + grow, t.z2() + grow,
                    t.r(), t.g(), t.b(), cfg.chestEspFill ? 0.22f : 0f));
            }
            if (dist > 40 && tracerCount < 150) {
                lines.add(new EspRenderer.Line(ctr.x, t.y2() + grow, ctr.z, ctr.x, t.y2() + grow + 12, ctr.z,
                    t.r(), t.g(), t.b()));
            }
            if (cfg.chestEspTracers && tracerCount < 150) {
                Vec3d c = t.center();
                lines.add(new EspRenderer.Line(start.x, start.y, start.z, c.x, c.y, c.z,
                    t.r(), t.g(), t.b()));
                tracerCount++;
            }
        }
    }
}
