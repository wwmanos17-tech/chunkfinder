package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Reach Test: a diagnostic that only counts. It shows how many block changes, sounds and particles your game receives,
 * and how many entities it knows about, split by distance from you and by underground (below y 48) or above.
 * It stores nothing and sends nothing: just numbers in memory while the panel is on.
 * It answers one question: which kinds of information still reach you from far away?
 */
public final class ReachTest {
    private ReachTest() {}

    private static final String[] KIND = {"Block changes", "Sounds", "Particles"};
    private static final String[] BUCKET = {"0-16", "16-48", "48-96", "96+"};
    // [kind][bucket][0 = underground, 1 = above]
    private static int[][][] cur = new int[3][4][2];
    private static int[][][] last = new int[3][4][2];
    // [0 players, 1 other][bucket] (underground only), refreshed every second
    private static int[][] ents = new int[2][4];
    private static long ticks = 0;

    public static void clear() {
        cur = new int[3][4][2];
        last = new int[3][4][2];
        ents = new int[2][4];
        ticks = 0;
    }

    private static int bucket(double dx, double dz) {
        double d = Math.sqrt(dx * dx + dz * dz);
        return d < 16 ? 0 : d < 48 ? 1 : d < 96 ? 2 : 3;
    }

    private static void add(int kind, double x, double y, double z, int n) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        int b = bucket(x - mc.player.getX(), z - mc.player.getZ());
        cur[kind][b][y < 48 ? 0 : 1] += n;
    }

    private static boolean active() {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.reachEnabled) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        // packets are handled once off the main thread and once on it; only count the second time
        return mc != null && mc.isOnThread();
    }

    // called from the packet handler mixin (receiving only)
    public static void block(BlockPos pos) {
        try { if (active()) add(0, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1); } catch (Throwable ignored) { }
    }

    public static void delta(ChunkDeltaUpdateS2CPacket p) {
        try {
            if (!active()) return;
            p.visitUpdates((pos, state) -> add(0, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1));
        } catch (Throwable ignored) { }
    }

    public static void sound(double x, double y, double z) {
        try { if (active()) add(1, x, y, z, 1); } catch (Throwable ignored) { }
    }

    public static void particle(double x, double y, double z) {
        try { if (active()) add(2, x, y, z, 1); } catch (Throwable ignored) { }
    }

    public static void tick(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.reachEnabled || mc.world == null || mc.player == null) return;
        ticks++;
        if (ticks % 1200 == 0) { last = cur; cur = new int[3][4][2]; }
        if (ticks % 20 == 0) {
            int[][] e = new int[2][4];
            int seen = 0;
            for (Entity en : mc.world.getEntities()) {
                if (en == mc.player || ++seen > 400) continue;
                if (mc.world.isSkyVisible(en.getBlockPos())) continue; // only entities under cover
                int b = bucket(en.getX() - mc.player.getX(), en.getZ() - mc.player.getZ());
                e[en instanceof PlayerEntity ? 0 : 1][b]++;
            }
            ents = e;
        }
    }

    public static void render(DrawContext ctx, MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.reachEnabled || mc.player == null) return;
        java.util.List<String> rows = new java.util.ArrayList<>();
        rows.add("REACH TEST  underground / above, last ~2 min");
        StringBuilder head = new StringBuilder("distance:");
        for (String b : BUCKET) head.append("   ").append(b);
        rows.add(head.toString());
        for (int k = 0; k < 3; k++) {
            StringBuilder sb = new StringBuilder(KIND[k]).append(":");
            for (int b = 0; b < 4; b++) {
                sb.append("   ").append(cur[k][b][0] + last[k][b][0]).append('/').append(cur[k][b][1] + last[k][b][1]);
            }
            rows.add(sb.toString());
        }
        StringBuilder pl = new StringBuilder("Players under cover:");
        StringBuilder ot = new StringBuilder("Other entities under cover:");
        for (int b = 0; b < 4; b++) {
            pl.append("   ").append(ents[0][b]);
            ot.append("   ").append(ents[1][b]);
        }
        rows.add(pl.toString());
        rows.add(ot.toString());
        int w = 0;
        for (String s : rows) w = Math.max(w, PFont.w(mc, s));
        w += 12;
        int h = 6 + rows.size() * 10 + 4;
        int x = Math.max(0, Math.min(cfg.reachX, mc.getWindow().getScaledWidth() - w));
        int y = Math.max(0, Math.min(cfg.reachY, mc.getWindow().getScaledHeight() - h));
        HudPanel.draw(ctx, x, y, w, h);
        for (int i = 0; i < rows.size(); i++) {
            ctx.drawTextWithShadow(mc.textRenderer, PFont.t(rows.get(i)), x + 6, y + 4 + i * 10, i == 0 ? 0xFFF4ECD6 : 0xFFE8C170);
        }
    }
}
