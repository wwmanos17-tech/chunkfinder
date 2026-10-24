package com.chunkfinder;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds "sus" chunks: chunks that look like someone built or dug there.
 * Clues (Overworld only, underground only so villages are ignored):
 *  - blocks that never generate in caves (glass, ladders, doors, pistons, TNT ...)
 *  - villagers underground (trading halls), not villagers standing on the surface
 * Chunks are scored once and cached, a couple per tick, so it does not cause lag.
 */
public class SusScanner {
    public static final Map<ChunkPos, Integer> FOUND = new ConcurrentHashMap<>();
    private static final Map<ChunkPos, Integer> SCORES = new HashMap<>();
    /** Heights where the clues were found, per chunk, so the chunk can be drawn as flat slabs at those heights. */
    public static final Map<ChunkPos, int[]> LEVELS = new ConcurrentHashMap<>();
    private static final Set<ChunkPos> NOTIFIED = new HashSet<>();
    /** Exact block positions (packed BlockPos longs) of the clues in each chunk. The first HI entries are the important ones (storage, villagers). */
    public static final Map<ChunkPos, long[]> SPOTS = new ConcurrentHashMap<>();
    public static final Map<ChunkPos, Integer> HI = new ConcurrentHashMap<>();

    private record Cand(ChunkPos pos, boolean scanned, int dist2) {}

    private static int age = 0;
    private static final Set<ChunkPos> STALE = new HashSet<>();

    public static void clear() {
        age = 0;
        STALE.clear();
        FOUND.clear();
        SCORES.clear();
        SPOTS.clear();
        HI.clear();
        NOTIFIED.clear();
    }

    private static boolean isSusBlock(BlockState s) {
        return s.isOf(Blocks.GLASS) || s.isOf(Blocks.GLASS_PANE)
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

    /** Blocks that only exist in natural underground structures (ancient cities, trial chambers, strongholds). */
    private static boolean isNaturalMarker(BlockState s) {
        return s.isOf(Blocks.SCULK_SHRIEKER) || s.isOf(Blocks.SCULK_CATALYST) || s.isOf(Blocks.REINFORCED_DEEPSLATE)
            || s.isOf(Blocks.TRIAL_SPAWNER) || s.isOf(Blocks.VAULT) || s.isOf(Blocks.END_PORTAL_FRAME)
            || s.isOf(Blocks.CHISELED_TUFF_BRICKS) || s.isOf(Blocks.HEAVY_CORE);
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

        int radius = mc.options.getViewDistance().getValue(); // every chunk the game has loaded, so flagged chunks show from anywhere
        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;

        // Fast scan: nearest chunks first, never scanned chunks before old ones, and as many chunks per tick as fit in a
        // small time budget (more while you fly fast). Without it only 2 chunks are checked per tick.
        boolean fast = cfg.fastScan;
        long limitNs = !fast ? 0 : FastScan.limitNs(mc) + 1_500_000L;
        int maxChunks = FastScan.maxChunks(mc, 2) * (fast ? 2 : 1);

        List<Cand> todo = new ArrayList<>();
        for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
            for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                ChunkPos pos = new ChunkPos(cx, cz);
                boolean scanned = SCORES.containsKey(pos);
                if (scanned && !STALE.contains(pos)) continue;
                int dx = cx - pcx, dz = cz - pcz;
                todo.add(new Cand(pos, scanned, dx * dx + dz * dz));
            }
        }
        if (fast) {
            todo.sort((a1, b1) -> a1.scanned() != b1.scanned() ? (a1.scanned() ? 1 : -1) : Integer.compare(a1.dist2(), b1.dist2()));
        }
        long start = System.nanoTime();
        int done = 0;
        for (Cand cd : todo) {
            if (done >= maxChunks) break;
            if (done > 0 && fast && System.nanoTime() - start > limitNs) break;
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cd.pos().x, cd.pos().z);
            if (chunk == null) continue;
            SCORES.put(cd.pos(), score(mc, chunk));
            STALE.remove(cd.pos());
            done++;
        }

        // a base usually spreads over several chunks, so a chunk also gets a share of the score of its neighbours.
        // A chunk still needs some evidence of its own, so an empty chunk next to a base is not flagged.
        int need = cfg.requiredSusScore();
        FOUND.clear();
        for (Map.Entry<ChunkPos, Integer> e : SCORES.entrySet()) {
            ChunkPos p = e.getKey();
            if (Math.abs(p.x - pcx) > radius || Math.abs(p.z - pcz) > radius) continue;
            int own = e.getValue();
            if (own <= 0) continue;
            int near = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    Integer v = SCORES.get(new ChunkPos(p.x + dx, p.z + dz));
                    if (v != null) near += v;
                }
            }
            int eff = own + near / 3;
            if (own >= Math.min(2, need) && eff >= need) {
                FOUND.put(p, eff);
                if (cfg.notify && NOTIFIED.add(p)) {
                    mc.player.sendMessage(Text.literal("[Pharaoh] Sus chunk at X " + (p.getStartX() + 8)
                        + ", Z " + (p.getStartZ() + 8)), false);
                }
            }
        }
    }

    private static int score(MinecraftClient mc, WorldChunk chunk) {
        int score = 0;
        java.util.List<Integer> ys = new java.util.ArrayList<>();
        java.util.List<Long> hi = new java.util.ArrayList<>();
        java.util.List<Long> lo = new java.util.ArrayList<>();
        int csx = chunk.getPos().getStartX(), csz = chunk.getPos().getStartZ();

        // natural structures (ancient city, trial chamber, stronghold portal room) look like bases, so they get discounted
        boolean natural = false;
        for (ChunkSection ns : chunk.getSectionArray()) {
            if (ns == null || ns.isEmpty()) continue;
            if (ns.hasAny(SusScanner::isNaturalMarker)) { natural = true; break; }
        }

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
                // note the height of the clues in this section
                long sum = 0;
                int n = 0;
                for (int ly = 0; ly < 16; ly++) {
                    if (sectionY * 16 + ly > 47) break;
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            if (isSusBlock(s.getBlockState(lx, ly, lz))) {
                                sum += sectionY * 16 + ly;
                                n++;
                                if (lo.size() < 60) lo.add(BlockPos.asLong(csx + lx, sectionY * 16 + ly, csz + lz));
                            }
                        }
                    }
                }
                if (n > 0) ys.add((int) (sum / n));
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
                ys.add(be.getPos().getY());
                if (hi.size() < 40) hi.add(be.getPos().asLong());
            }
        }
        score += Math.min(6, storage * 2);

        // 1c) villagers sitting underground: trading halls are bases now,
        //     while village villagers stand on the surface and are ignored
        int bx = chunk.getPos().getStartX();
        int bz = chunk.getPos().getStartZ();
        Box area = new Box(bx, mc.world.getBottomY(), bz, bx + 16, mc.world.getTopYInclusive() + 1, bz + 16);
        int villagers = 0;
        java.util.List<net.minecraft.entity.LivingEntity> folk = new java.util.ArrayList<>();
        folk.addAll(mc.world.getEntitiesByClass(VillagerEntity.class, area, e -> true));
        for (net.minecraft.entity.LivingEntity v : folk) {
            int top = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, v.getBlockX(), v.getBlockZ());
            if (v.getY() < top - 6) {
                villagers++;
                ys.add((int) v.getY());
                if (hi.size() < 40) hi.add(BlockPos.asLong(v.getBlockX(), v.getBlockY(), v.getBlockZ()));
            }
        }
        if (villagers >= 1) score += 4;
        if (villagers >= 3) score += 3;
        if (villagers >= 6) score += 3;

        // 2) tunnels are ignored on purpose
        // (tunnels no longer count: ordinary strip mining and caves made too many false alarms)

        if (natural) score = Math.max(0, score - 8);

        LEVELS.put(chunk.getPos(), cluster(ys));
        long[] all = new long[hi.size() + lo.size()];
        for (int i = 0; i < hi.size(); i++) all[i] = hi.get(i);
        for (int i = 0; i < lo.size(); i++) all[hi.size() + i] = lo.get(i);
        SPOTS.put(chunk.getPos(), all);
        HI.put(chunk.getPos(), hi.size());
        return score;
    }

    /** Merges heights that are close together and returns up to 5 slab heights. */
    private static int[] cluster(java.util.List<Integer> ys) {
        java.util.Collections.sort(ys);
        java.util.List<int[]> groups = new java.util.ArrayList<>(); // {sum, count}
        int lastY = Integer.MIN_VALUE;
        for (int y : ys) {
            if (groups.isEmpty() || y - lastY > 6) groups.add(new int[]{0, 0});
            int[] g = groups.get(groups.size() - 1);
            g[0] += y;
            g[1]++;
            lastY = y;
        }
        int n = Math.min(5, groups.size());
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = groups.get(i)[0] / groups.get(i)[1];
        return out;
    }

    private static int tunnels(MinecraftClient mc, WorldChunk chunk, java.util.List<Integer> ys) {
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
                                ys.add(y);
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

    /** Strongest sus chunks first, nearest first when equal. */
    public static java.util.List<Map.Entry<ChunkPos, Integer>> ranked(double px, double pz) {
        java.util.List<Map.Entry<ChunkPos, Integer>> list = new java.util.ArrayList<>(FOUND.entrySet());
        list.sort((a, b) -> {
            int c = Integer.compare(b.getValue(), a.getValue());
            if (c != 0) return c;
            double da = Math.hypot(a.getKey().getStartX() + 8 - px, a.getKey().getStartZ() + 8 - pz);
            double db = Math.hypot(b.getKey().getStartX() + 8 - px, b.getKey().getStartZ() + 8 - pz);
            return Double.compare(da, db);
        });
        return list;
    }

    /** Colour for a sus chunk: yellow = weak, orange = medium, red = strong. */
    public static int[] strengthColor(int score, int need, int[] strong) {
        float r = score / (float) Math.max(1, need);
        if (r < 1.5f) return new int[]{255, 214, 60};
        if (r < 2.2f) return new int[]{255, 140, 30};
        return strong;
    }

    /** Small on-screen list of the strongest sus chunks, nearest to your position, with distance and coordinates. */
    public static void renderList(DrawContext ctx, MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.enabled || !cfg.detectSus || !cfg.susList || FOUND.isEmpty()) return;
        double px = mc.player.getX();
        double pz = mc.player.getZ();

        java.util.List<Map.Entry<ChunkPos, Integer>> list = new java.util.ArrayList<>(FOUND.entrySet());
        // strongest first, nearest first when equal
        list.sort((a, b) -> {
            int c = Integer.compare(b.getValue(), a.getValue());
            if (c != 0) return c;
            double da = Math.hypot(a.getKey().getStartX() + 8 - px, a.getKey().getStartZ() + 8 - pz);
            double db = Math.hypot(b.getKey().getStartX() + 8 - px, b.getKey().getStartZ() + 8 - pz);
            return Double.compare(da, db);
        });

        int shown = Math.min(list.size(), Math.max(1, cfg.susListCount));
        String[] lines = new String[shown];
        int[] colors = new int[shown];
        int width = mc.textRenderer.getWidth("SUS CHUNKS " + list.size());
        for (int i = 0; i < shown; i++) {
            ChunkPos p = list.get(i).getKey();
            int cx = p.getStartX() + 8;
            int cz = p.getStartZ() + 8;
            int dist = (int) Math.hypot(cx - px, cz - pz);
            lines[i] = dist + "m  X " + cx + "  Z " + cz;
            int score = list.get(i).getValue();
            colors[i] = score >= 10 ? 0xFFFF5555 : score >= 7 ? 0xFFFFAA33 : 0xFFE6E6E6;
            width = Math.max(width, mc.textRenderer.getWidth(lines[i]));
        }
        width += 12;

        int rowH = 11;
        int h = 14 + shown * rowH + 3;
        int x = 4;
        int y = mc.getWindow().getScaledHeight() / 2 - h / 2;
        ctx.fill(x, y, x + width, y + h, 0xB0101018);
        ctx.fill(x, y, x + 2, y + h, 0xFFFF5555);
        ctx.drawTextWithShadow(mc.textRenderer, Text.literal("SUS CHUNKS " + list.size()), x + 6, y + 3, 0xFF8A8A96);
        for (int i = 0; i < shown; i++) {
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal(lines[i]), x + 6, y + 14 + i * rowH, colors[i]);
        }
    }
}
