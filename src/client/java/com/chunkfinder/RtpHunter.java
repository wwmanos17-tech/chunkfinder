package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RTP Hunter: for hunting by teleporting to random spots and looking around. It notices each time you land somewhere
 * new, checks how many chunks around you really hold deep blocks (so you know the read was clean), and keeps a list of
 * every flagged chunk or cluster with its coordinates, so a find is not lost when you teleport away. It only uses what
 * the other scanners already found in the data your game received.
 */
public final class RtpHunter {
    private RtpHunter() {}

    public static final int[] BOUNDS = new int[4];

    private static final class Find {
        int x, y, z, score;
        String kind;
        long at;
    }

    private static final Map<Long, Find> LOG = new HashMap<>();
    private static int landings = 0;
    private static long ticks = 0;
    private static long landingTick = -1000;
    private static Vec3d lastPos = null;
    private static Object lastWorld = null;
    private static int realChunks = 0, totalChunks = 0;
    private static boolean readDone = false;

    public static void clear() {
        LOG.clear();
        landings = 0;
        landingTick = -1000;
        lastPos = null;
        lastWorld = null;
        realChunks = 0;
        totalChunks = 0;
        readDone = false;
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    private static void note(MinecraftClient mc, int x, int y, int z, int score, String kind) {
        long k = key(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        Find f = LOG.get(k);
        boolean fresh = f == null;
        if (fresh) {
            f = new Find();
            f.at = ticks;
            LOG.put(k, f);
            if (LOG.size() > 60) {
                long worst = 0; int ws = Integer.MAX_VALUE; boolean any = false;
                for (Map.Entry<Long, Find> e : LOG.entrySet()) {
                    if (e.getKey() == k) continue;
                    if (e.getValue().score < ws) { ws = e.getValue().score; worst = e.getKey(); any = true; }
                }
                if (any) LOG.remove(worst);
            }
        }
        if (fresh || score > f.score) {
            f.x = x; f.y = y; f.z = z; f.score = score; f.kind = kind;
        }
        if (fresh && ChunkFinderConfig.INSTANCE.huntAlert && mc.player != null) {
            mc.player.sendMessage(Text.literal("[Pharaoh] RTP Hunter: " + kind + " at X " + x + ", Z " + z + " (score " + score + ")"), false);
        }
    }

    public static void tick(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.huntEnabled || mc.world == null || mc.player == null) return;
        ticks++;
        Vec3d pos = mc.player.getPos();
        boolean newWorld = mc.world != lastWorld;
        boolean jumped = lastPos != null && Math.hypot(pos.x - lastPos.x, pos.z - lastPos.z) > 100;
        if (lastPos != null && (newWorld || jumped)) {
            landings++;
            landingTick = ticks;
            readDone = false;
            realChunks = 0;
            totalChunks = 0;
        }
        if (lastPos == null) landingTick = ticks;
        lastWorld = mc.world;
        lastPos = pos;

        // read: for the first few seconds after a landing, count how many chunks around you hold real deep blocks
        long since = ticks - landingTick;
        if (!readDone && since >= 20 && since % 20 == 0) {
            int pcx = mc.player.getChunkPos().x, pcz = mc.player.getChunkPos().z;
            int real = 0, total = 0;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    WorldChunk c = mc.world.getChunkManager().getWorldChunk(pcx + dx, pcz + dz);
                    if (c == null) continue;
                    total++;
                    if (SweepMap.realDeep(c)) real++;
                }
            }
            realChunks = real;
            totalChunks = total;
            if (since >= 200) readDone = true;
        }

        // keep every find in the log
        if (ticks % 10 == 0) {
            for (Map.Entry<ChunkPos, Integer> e : SusScanner.FOUND.entrySet()) {
                int[] lv = SusScanner.LEVELS.get(e.getKey());
                int y = lv != null && lv.length > 0 ? lv[0] : 30;
                note(mc, e.getKey().getStartX() + 8, y, e.getKey().getStartZ() + 8, e.getValue(), "sus chunk");
            }
            for (ClusterEsp.Base b : ClusterEsp.BASES) {
                note(mc, (int) b.cx(), (int) ((b.minY() + b.maxY()) / 2), (int) b.cz(), (int) Math.round(b.score()), "cluster (" + b.verdict() + ")");
            }
        }
    }

    private static List<Find> best(int n, double px, double pz) {
        List<Find> l = new ArrayList<>(LOG.values());
        l.sort((a, b) -> Integer.compare(b.score, a.score));
        return l.subList(0, Math.min(n, l.size()));
    }

    /** Key: point the PORTAL guide at the best find. */
    public static void goToBest(MinecraftClient mc) {
        if (mc.player == null) return;
        List<Find> l = best(1, mc.player.getX(), mc.player.getZ());
        if (l.isEmpty()) {
            mc.player.sendMessage(Text.literal("RTP Hunter has no finds yet"), true);
            return;
        }
        Find f = l.get(0);
        PortalCalc.guideTo(f.x, f.z);
        mc.player.sendMessage(Text.literal("Guiding to X " + f.x + ", Z " + f.z), true);
    }

    public static void clearLog() {
        LOG.clear();
    }

    public static void render(DrawContext ctx, MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        BOUNDS[2] = 0;
        if (!cfg.huntEnabled || mc.player == null || mc.world == null) return;
        List<String> lines = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        lines.add("RTP HUNTER"); cols.add(0xFFE0A526);
        lines.add("Landings: " + landings + "    Finds: " + LOG.size()); cols.add(0xFFF4ECD6);
        long since = ticks - landingTick;
        if (totalChunks == 0) {
            lines.add("Reading this landing..."); cols.add(0xFF9A9A9A);
        } else {
            boolean clean = realChunks >= totalChunks * 0.6;
            lines.add("Deep read: " + realChunks + " of " + totalChunks + " chunks real" + (readDone ? "" : " (still loading)"));
            cols.add(clean ? 0xFF7CFF8A : 0xFFFF8A5C);
            if (readDone || since > 100) {
                lines.add(clean ? "Clean read: if nothing is flagged, move on" : "Weak read: go lower or move a bit more");
                cols.add(0xFF9A9A9A);
            }
        }
        double px = mc.player.getX(), pz = mc.player.getZ();
        int shown = 0;
        for (Find f : best(5, px, pz)) {
            long d = Math.round(Math.hypot(f.x - px, f.z - pz));
            lines.add((shown + 1) + ". " + f.kind + "  " + f.x + ", " + f.y + ", " + f.z + "  [" + d + "m]  " + f.score);
            cols.add(shown == 0 ? 0xFF7CFF8A : 0xFFFFC94D);
            shown++;
        }
        if (shown == 0) { lines.add("No finds yet. Land somewhere new."); cols.add(0xFF9A9A9A); }

        int w = 0;
        for (String s : lines) w = Math.max(w, PFont.w(mc, s));
        w += 14;
        int h = 6 + lines.size() * 10 + 4;
        int x = Math.max(0, Math.min(cfg.huntX, mc.getWindow().getScaledWidth() - w));
        int y = Math.max(0, Math.min(cfg.huntY, mc.getWindow().getScaledHeight() - h));
        BOUNDS[0] = x; BOUNDS[1] = y; BOUNDS[2] = w; BOUNDS[3] = h;
        HudPanel.draw(ctx, x, y, w, h);
        for (int i = 0; i < lines.size(); i++) {
            ctx.drawTextWithShadow(mc.textRenderer, PFont.t(lines.get(i)), x + 7, y + 4 + i * 10, cols.get(i));
        }
    }

    public static void collect(List<EspRenderer.Box> boxes, List<EspRenderer.Line> lines, Vec3d cam) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.huntEnabled || !cfg.huntBeams) return;
        for (Find f : LOG.values()) {
            if (Math.hypot(f.x - cam.x, f.z - cam.z) > 3000) continue;
            lines.add(new EspRenderer.Line(f.x + 0.5, f.y - 20, f.z + 0.5, f.x + 0.5, f.y + 120, f.z + 0.5, 1f, 0.55f, 0.2f));
            boxes.add(new EspRenderer.Box(f.x - 7.5, f.y - 3, f.z - 7.5, f.x + 8.5, f.y + 4, f.z + 8.5, 1f, 0.55f, 0.2f, 0.10f));
        }
    }
}
