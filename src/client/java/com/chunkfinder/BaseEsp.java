package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base ESP: puts the clues the other tools already collect together and draws one box around each place
 * that looks like a player base. Purely visual, it only uses what the server has already sent to your game.
 *
 * Clues and their weight: chests/barrels 1, ender chests 1, shulker boxes 3, spawners 2, beacons 6,
 * beds 0.4, droppers 0.5, pistons 0.5, redstone blocks 0.5, hoppers 0.7, villagers living underground 1.5, sus chunks 2 (from the Sus Chunk scanner).
 * Clues that are closer than 28 blocks to each other are joined into one cluster.
 */
public class BaseEsp {
    public record Base(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                       double cx, double cz, double score, String level, int rgb, String summary) {}

    public static volatile List<Base> BASES = new ArrayList<>();
    private static final Set<Long> ALERTED = new HashSet<>();
    private static int timer = 0;

    private static final double JOIN = 28.0;

    public static void clear() {
        BASES = new ArrayList<>();
        ALERTED.clear();
        timer = 0;
    }

    private record Pt(double x, double y, double z, double w, int type) {}
    // types: 9 piston, 10 redstone block, 11 hopper, 0 chest-like, 1 ender, 2 shulker, 3 spawner, 4 beacon, 5 bed, 6 dropper, 7 villager, 8 sus chunk

    public static void tick(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.baseEspEnabled || mc.world == null || mc.player == null) {
            if (!BASES.isEmpty()) BASES = new ArrayList<>();
            return;
        }
        if (timer-- > 0) return;
        timer = 30;

        List<Pt> pts = new ArrayList<>();
        for (ChestEsp.Target t : ChestEsp.memory()) {
            Vec3d c = t.center();
            switch (t.kind()) {
                case CHEST, TRAPPED, BARREL, MINECART -> pts.add(new Pt(c.x, c.y, c.z, 1.0, 0));
                case ENDER -> pts.add(new Pt(c.x, c.y, c.z, 1.0, 1));
                case SHULKER -> pts.add(new Pt(c.x, c.y, c.z, 3.0, 2));
                case SPAWNER -> pts.add(new Pt(c.x, c.y, c.z, 2.0, 3));
                case BEACON -> pts.add(new Pt(c.x, c.y, c.z, 6.0, 4));
                case BED -> pts.add(new Pt(c.x, c.y, c.z, 0.4, 5));
            }
        }
        for (long l : ChestEsp.dropperPositions()) {
            BlockPos p = BlockPos.fromLong(l);
            pts.add(new Pt(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 0.5, 6));
        }
        for (Map.Entry<Long, Integer> e : ChestEsp.techBlocks().entrySet()) {
            BlockPos p = BlockPos.fromLong(e.getKey());
            int t = e.getValue();
            pts.add(new Pt(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, t == 2 ? 0.7 : 0.5, 9 + t));
        }
        if (cfg.baseEspUseVillagers) {
            for (net.minecraft.entity.Entity e : mc.world.getEntities()) {
                if (!(e instanceof VillagerEntity)) continue;
                BlockPos bp = e.getBlockPos();
                // underground = no open sky above (not standing in a village)
                if (mc.world.isSkyVisible(bp)) continue;
                pts.add(new Pt(e.getX(), e.getY(), e.getZ(), 1.5, 7));
            }
        }
        if (cfg.baseEspUseSus) {
            for (Map.Entry<ChunkPos, Integer> e : SusScanner.FOUND.entrySet()) {
                int[] lv = SusScanner.LEVELS.get(e.getKey());
                double y = (lv != null && lv.length > 0) ? lv[0] : 30;
                pts.add(new Pt(e.getKey().getStartX() + 8, y, e.getKey().getStartZ() + 8, 2.0, 8));
            }
        }
        BASES = build(pts, cfg, mc);
    }

    private static int find(int[] p, int i) {
        while (p[i] != i) { p[i] = p[p[i]]; i = p[i]; }
        return i;
    }

    private static List<Base> build(List<Pt> pts, ChunkFinderConfig cfg, MinecraftClient mc) {
        int n = pts.size();
        List<Base> out = new ArrayList<>();
        if (n == 0) return out;
        int[] par = new int[n];
        for (int i = 0; i < n; i++) par[i] = i;

        // grid so we only compare nearby points
        Map<Long, List<Integer>> grid = new HashMap<>();
        for (int i = 0; i < n; i++) {
            Pt p = pts.get(i);
            long key = cell((int) Math.floor(p.x() / JOIN), (int) Math.floor(p.z() / JOIN));
            grid.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }
        for (int i = 0; i < n; i++) {
            Pt p = pts.get(i);
            int gx = (int) Math.floor(p.x() / JOIN), gz = (int) Math.floor(p.z() / JOIN);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    List<Integer> l = grid.get(cell(gx + dx, gz + dz));
                    if (l == null) continue;
                    for (int j : l) {
                        if (j <= i) continue;
                        Pt q = pts.get(j);
                        double ddx = p.x() - q.x(), ddz = p.z() - q.z(), ddy = p.y() - q.y();
                        if (ddx * ddx + ddz * ddz <= JOIN * JOIN && Math.abs(ddy) <= 40) {
                            int a = find(par, i), b = find(par, j);
                            if (a != b) par[a] = b;
                        }
                    }
                }
            }
        }

        Map<Integer, List<Pt>> groups = new HashMap<>();
        for (int i = 0; i < n; i++) groups.computeIfAbsent(find(par, i), k -> new ArrayList<>()).add(pts.get(i));

        int need = cfg.baseEspSensitivity;
        for (List<Pt> g : groups.values()) {
            double score = 0;
            int[] cnt = new int[12];
            double x1 = 1e18, y1 = 1e18, z1 = 1e18, x2 = -1e18, y2 = -1e18, z2 = -1e18;
            double sx = 0, sz = 0;
            for (Pt p : g) {
                score += p.w();
                cnt[p.type()]++;
                x1 = Math.min(x1, p.x()); x2 = Math.max(x2, p.x());
                y1 = Math.min(y1, p.y()); y2 = Math.max(y2, p.y());
                z1 = Math.min(z1, p.z()); z2 = Math.max(z2, p.z());
                sx += p.x(); sz += p.z();
            }
            // variety bonus: a real base has several kinds of things, a lone dungeon does not
            int kinds = 0;
            for (int k = 0; k < 12; k++) if (cnt[k] > 0) kinds++;
            score += Math.max(0, kinds - 1) * 1.5;

            // villages and single natural structures: need real storage, not just beds or one villager
            int storage = cnt[0] + cnt[1] + cnt[2] + cnt[4] + cnt[6];
            if (storage < 2 && score < 12) continue;
            if (score < need) continue;

            String level;
            int rgb;
            if (score >= 4 * need) { level = "MAJOR"; rgb = 0xFF3C3C; }
            else if (score >= 2 * need) { level = "LIKELY"; rgb = 0xFF9A1A; }
            else { level = "POSSIBLE"; rgb = 0xFFD24D; }

            List<String> parts = new ArrayList<>();
            if (cnt[0] > 0) parts.add(cnt[0] + (cnt[0] == 1 ? " chest" : " chests"));
            if (cnt[1] > 0) parts.add(cnt[1] + " ender");
            if (cnt[2] > 0) parts.add(cnt[2] + (cnt[2] == 1 ? " shulker" : " shulkers"));
            if (cnt[3] > 0) parts.add(cnt[3] + (cnt[3] == 1 ? " spawner" : " spawners"));
            if (cnt[4] > 0) parts.add(cnt[4] + " beacon");
            if (cnt[6] > 0) parts.add(cnt[6] + " droppers");
            if (cnt[9] > 0) parts.add(cnt[9] + " pistons");
            if (cnt[10] > 0) parts.add(cnt[10] + " redstone blocks");
            if (cnt[11] > 0) parts.add(cnt[11] + " hoppers");
            if (cnt[7] > 0) parts.add(cnt[7] + " villagers");
            if (cnt[8] > 0) parts.add(cnt[8] + " sus chunks");
            if (cnt[5] > 0) parts.add(cnt[5] + " beds");
            String summary = String.join(", ", parts.subList(0, Math.min(4, parts.size())));

            double pad = 3.0;
            double cx = sx / g.size(), cz = sz / g.size();
            Base b = new Base(x1 - pad, y1 - 1, z1 - pad, x2 + pad + 1, y2 + 2, z2 + pad + 1, cx, cz,
                score, level, rgb, summary);
            out.add(b);

            if (cfg.baseEspAlert && !level.equals("POSSIBLE")) {
                long id = cell((int) Math.floor(cx / 64.0), (int) Math.floor(cz / 64.0));
                if (ALERTED.add(id) && mc.player != null) {
                    mc.player.sendMessage(Text.literal("[Pharaoh] Base (" + level.toLowerCase() + ") near X " + (int) cx
                        + ", Y " + (int) ((y1 + y2) / 2) + ", Z " + (int) cz + ": " + summary), false);
                }
            }
        }
        out.sort((a, b) -> Double.compare(b.score(), a.score()));
        return out;
    }

    private static long cell(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    public static void collect(List<EspRenderer.Box> boxes, List<EspRenderer.Line> lines, Vec3d cam) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.baseEspEnabled) return;
        boolean first = true;
        MinecraftClient mc = MinecraftClient.getInstance();
        for (Base b : BASES) {
            float r = ((b.rgb() >> 16) & 255) / 255f, g = ((b.rgb() >> 8) & 255) / 255f, bl = (b.rgb() & 255) / 255f;
            boxes.add(new EspRenderer.Box(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ(),
                r, g, bl, cfg.baseEspFill ? 0.07f : 0f));
            // second, slightly bigger outline so it is easy to spot from far away
            boxes.add(new EspRenderer.Box(b.minX() - 0.15, b.minY() - 0.15, b.minZ() - 0.15,
                b.maxX() + 0.15, b.maxY() + 0.15, b.maxZ() + 0.15, r, g, bl, 0f));
            lines.add(new EspRenderer.Line(b.cx(), b.maxY(), b.cz(), b.cx(), b.maxY() + 30, b.cz(), r, g, bl));
            if (first && cfg.baseEspTracer && mc.player != null) {
                Vec3d s = Proj.tracerStart(mc);
                lines.add(new EspRenderer.Line(s.x, s.y, s.z, b.cx(), (b.minY() + b.maxY()) / 2, b.cz(), r, g, bl));
            }
            first = false;
        }
    }
}
