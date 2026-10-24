package com.chunkfinder;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Activity Detector: counts the redstone parts in every loaded chunk (wire, torches, repeaters, comparators, observers,
 * pistons, droppers, dispensers, hoppers, lamps, levers, buttons, plates, rails, note blocks, sculk sensors, TNT ...)
 * and how many of them are switched on right now. Chunks are re-checked often, nearest first, and when something
 * changes (redstone switching on or off, parts built or removed, chests added or removed) the chunk is pinged.
 * Only uses chunks the server has already sent to your game.
 */
public final class ActivityScanner {
    private ActivityScanner() {}

    public static final class State {
        public int parts, active, storage;
        public double ySum;
        public long[] spots = new long[0];
        public long scannedAt;
        public long eventAt = -1000000;
        public String eventText = "";
        public int avgY() { return parts > 0 ? (int) (ySum / parts) : 40; }
    }

    private static final Map<ChunkPos, State> STATES = new HashMap<>();
    /** Chunks worth showing right now (redstone chunks, active ones and fresh pings). Rebuilt a few times a second. */
    public static volatile List<Map.Entry<ChunkPos, State>> HOT = new ArrayList<>();
    private static List<ChunkPos> due = new ArrayList<>();
    private static long ticks = 0;

    public static long now() { return ticks; }

    public static void clear() {
        STATES.clear();
        HOT = new ArrayList<>();
        due = new ArrayList<>();
        ticks = 0;
    }

    private static boolean isPart(BlockState s) {
        return s.isOf(Blocks.REDSTONE_WIRE) || s.isOf(Blocks.REDSTONE_TORCH) || s.isOf(Blocks.REDSTONE_WALL_TORCH)
            || s.isOf(Blocks.REPEATER) || s.isOf(Blocks.COMPARATOR) || s.isOf(Blocks.OBSERVER)
            || s.isOf(Blocks.PISTON) || s.isOf(Blocks.STICKY_PISTON) || s.isOf(Blocks.DISPENSER) || s.isOf(Blocks.DROPPER)
            || s.isOf(Blocks.HOPPER) || s.isOf(Blocks.LEVER) || s.isOf(Blocks.TRIPWIRE_HOOK)
            || s.isOf(Blocks.REDSTONE_LAMP) || s.isOf(Blocks.DAYLIGHT_DETECTOR) || s.isOf(Blocks.TARGET)
            || s.isOf(Blocks.NOTE_BLOCK) || s.isOf(Blocks.POWERED_RAIL) || s.isOf(Blocks.DETECTOR_RAIL)
            || s.isOf(Blocks.ACTIVATOR_RAIL) || s.isOf(Blocks.REDSTONE_BLOCK) || s.isOf(Blocks.SCULK_SENSOR)
            || s.isOf(Blocks.CALIBRATED_SCULK_SENSOR) || s.isOf(Blocks.TNT) || s.isOf(Blocks.LIGHTNING_ROD)
            || s.isOf(Blocks.CRAFTER) || s.isOf(Blocks.SLIME_BLOCK) || s.isOf(Blocks.HONEY_BLOCK)
            || s.isIn(BlockTags.BUTTONS) || s.isIn(BlockTags.PRESSURE_PLATES);
    }

    private static boolean isActive(BlockState s) {
        if (s.isOf(Blocks.REDSTONE_WIRE)) return s.get(RedstoneWireBlock.POWER) > 0;
        if (s.contains(Properties.LIT) && s.get(Properties.LIT)) return true;
        if (s.contains(Properties.POWERED) && s.get(Properties.POWERED)) return true;
        if (s.contains(Properties.EXTENDED) && s.get(Properties.EXTENDED)) return true;
        if (s.contains(Properties.TRIGGERED) && s.get(Properties.TRIGGERED)) return true;
        return false;
    }

    /** Parts worth marking one by one (wire, torches, buttons and plates are everywhere and would only clutter). */
    private static boolean isKey(BlockState s) {
        return s.isOf(Blocks.PISTON) || s.isOf(Blocks.STICKY_PISTON) || s.isOf(Blocks.OBSERVER) || s.isOf(Blocks.DROPPER)
            || s.isOf(Blocks.DISPENSER) || s.isOf(Blocks.HOPPER) || s.isOf(Blocks.COMPARATOR) || s.isOf(Blocks.REPEATER)
            || s.isOf(Blocks.REDSTONE_LAMP) || s.isOf(Blocks.NOTE_BLOCK) || s.isOf(Blocks.REDSTONE_BLOCK)
            || s.isOf(Blocks.SCULK_SENSOR) || s.isOf(Blocks.CALIBRATED_SCULK_SENSOR) || s.isOf(Blocks.CRAFTER)
            || s.isOf(Blocks.TNT) || s.isOf(Blocks.DAYLIGHT_DETECTOR) || s.isOf(Blocks.TRIPWIRE_HOOK);
    }

    private static State scan(WorldChunk chunk) {
        State st = new State();
        java.util.List<Long> sp = new java.util.ArrayList<>();
        int csx = chunk.getPos().getStartX(), csz = chunk.getPos().getStartZ();
        ChunkSection[] secs = chunk.getSectionArray();
        for (int i = 0; i < secs.length; i++) {
            ChunkSection sec = secs[i];
            if (sec == null || sec.isEmpty() || !sec.hasAny(ActivityScanner::isPart)) continue;
            int y0 = (chunk.getBottomSectionCoord() + i) * 16;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState bs = sec.getBlockState(x, y, z);
                        if (isPart(bs)) {
                            st.parts++;
                            st.ySum += y0 + y;
                            if (isActive(bs)) st.active++;
                            if (sp.size() < 40 && isKey(bs)) sp.add(net.minecraft.util.math.BlockPos.asLong(csx + x, y0 + y, csz + z));
                        }
                    }
                }
            }
        }
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity
                || be instanceof EnderChestBlockEntity) st.storage++;
        }
        st.spots = new long[sp.size()];
        for (int i = 0; i < st.spots.length; i++) st.spots[i] = sp.get(i);
        return st;
    }

    public static void tick(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.activityEnabled || mc.world == null || mc.player == null) return;
        ticks++;
        int radius = mc.options.getViewDistance().getValue();
        int pcx = mc.player.getChunkPos().x, pcz = mc.player.getChunkPos().z;

        // every half second: which chunks are due for a check (never checked first, then nearest first)
        if (ticks % 10 == 1) {
            List<ChunkPos> list = new ArrayList<>();
            for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
                for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                    ChunkPos pos = new ChunkPos(cx, cz);
                    State st = STATES.get(pos);
                    int dist = Math.max(Math.abs(cx - pcx), Math.abs(cz - pcz));
                    int interval = dist <= 6 ? 30 : dist <= 12 ? 80 : 240;
                    if (st == null || ticks - st.scannedAt >= interval) list.add(pos);
                }
            }
            list.sort((a, b) -> {
                boolean na = !STATES.containsKey(a), nb = !STATES.containsKey(b);
                if (na != nb) return na ? -1 : 1;
                return Integer.compare((a.x - pcx) * (a.x - pcx) + (a.z - pcz) * (a.z - pcz),
                    (b.x - pcx) * (b.x - pcx) + (b.z - pcz) * (b.z - pcz));
            });
            due = list;
        }

        // check chunks: a few per tick normally, many more (inside a small time limit) with Fast Scan
        int budget = FastScan.maxChunks(mc, 2);
        long start = System.nanoTime();
        long limit = FastScan.limitNs(mc);
        int done = 0;
        while (done < budget && !due.isEmpty()) {
            if (done > 0 && System.nanoTime() - start > limit) break;
            ChunkPos pos = due.remove(0);
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(pos.x, pos.z);
            if (chunk == null) continue;
            State prev = STATES.get(pos);
            State now = scan(chunk);
            now.scannedAt = ticks;
            if (prev != null) {
                now.eventAt = prev.eventAt;
                now.eventText = prev.eventText;
                List<String> ch = new ArrayList<>();
                if (cfg.activityRedstone) {
                    if (now.active != prev.active) ch.add("redstone " + (now.active > prev.active ? "switched on" : "switched off"));
                    if (now.parts > prev.parts) ch.add("+" + (now.parts - prev.parts) + " redstone parts");
                    else if (now.parts < prev.parts) ch.add((prev.parts - now.parts) + " redstone parts removed");
                }
                if (cfg.activityStorage && now.storage != prev.storage) {
                    ch.add((now.storage > prev.storage ? "+" : "-") + Math.abs(now.storage - prev.storage) + " chests");
                }
                if (!ch.isEmpty() && ticks - prev.eventAt > 200) {
                    now.eventAt = ticks;
                    now.eventText = String.join(", ", ch);
                    if (cfg.activityChat) {
                        mc.player.sendMessage(Text.literal("[Pharaoh] Activity at X " + (pos.getStartX() + 8) + ", Z "
                            + (pos.getStartZ() + 8) + ": " + now.eventText), false);
                    }
                }
            }
            STATES.put(pos, now);
            done++;
        }

        if (ticks % 5 == 0) {
            List<Map.Entry<ChunkPos, State>> hot = new ArrayList<>();
            for (Map.Entry<ChunkPos, State> e : STATES.entrySet()) {
                State s = e.getValue();
                ChunkPos p = e.getKey();
                if (Math.abs(p.x - pcx) > radius || Math.abs(p.z - pcz) > radius) continue;
                boolean recent = ticks - s.eventAt < 600;
                boolean redstone = s.parts >= cfg.activityRedstoneMin;
                if (recent || redstone) hot.add(Map.entry(p, s));
            }
            HOT = hot;
        }
        if (ticks % 200 == 0) {
            STATES.keySet().removeIf(p -> Math.abs(p.x - pcx) > radius + 4 || Math.abs(p.z - pcz) > radius + 4);
        }
    }

    public static void collect(List<EspRenderer.Box> boxes, List<EspRenderer.Line> lines, net.minecraft.util.math.Vec3d cam) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.activityEnabled) return;
        for (Map.Entry<ChunkPos, State> e : HOT) {
            State s = e.getValue();
            ChunkPos p = e.getKey();
            double x1 = p.getStartX(), z1 = p.getStartZ();
            double y = s.avgY();
            boolean recent = ticks - s.eventAt < 600;
            if (s.parts >= cfg.activityRedstoneMin && cfg.activityStatic) {
                boolean on = s.active > 0;
                if (s.spots.length > 0) {
                    for (long bl : s.spots) {
                        net.minecraft.util.math.BlockPos bp = net.minecraft.util.math.BlockPos.fromLong(bl);
                        boxes.add(new EspRenderer.Box(bp.getX() - 0.02, bp.getY() - 0.02, bp.getZ() - 0.02,
                            bp.getX() + 1.02, bp.getY() + 1.02, bp.getZ() + 1.02,
                            1f, on ? 0.9f : 0.55f, on ? 0.3f : 0.1f, on ? 0.40f : 0.22f));
                    }
                } else {
                    boxes.add(new EspRenderer.Box(x1, y, z1, x1 + 16, y + 0.02, z1 + 16,
                        1f, on ? 0.9f : 0.55f, on ? 0.3f : 0.1f, on ? 0.30f : 0.16f));
                }
            }
            if (recent) {
                if (s.spots.length > 0) {
                    net.minecraft.util.math.BlockPos bp = net.minecraft.util.math.BlockPos.fromLong(s.spots[0]);
                    lines.add(new EspRenderer.Line(bp.getX() + 0.5, bp.getY() + 1, bp.getZ() + 0.5, bp.getX() + 0.5, bp.getY() + 60, bp.getZ() + 0.5, 0.3f, 1f, 1f));
                } else {
                    lines.add(new EspRenderer.Line(x1 + 8, y + 6, z1 + 8, x1 + 8, y + 70, z1 + 8, 0.3f, 1f, 1f));
                }
            }
        }
    }
}
