package com.chunkfinder;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashMap;
import java.util.Map;

/**
 * Chunk Data Check: a diagnostic. It samples the chunks the server has sent you and shows, per distance ring and height
 * band, how many sections are mixed (air and blocks, like real terrain), completely solid, or completely air.
 * If the deep layers far away are all "solid" while the ones near you are mixed, the server is not sending the real
 * underground data from that far, and no finder can see it from there.
 */
public final class ChunkDataCheck {
    private ChunkDataCheck() {}

    // per chunk: 3 bands x 3 kinds (mixed, solid, air) = 9 counters of sections
    private static final Map<ChunkPos, int[]> DATA = new HashMap<>();
    private static final Map<ChunkPos, Long> AT = new HashMap<>();
    private static long ticks = 0;
    private static final String[] BAND = {"y<0", "0-47", "48+"};
    private static final String[] RING = {"near 0-2", "mid 3-6", "far 7+"};
    private static volatile int[][] SUM = new int[3][9];
    private static volatile int[] CHUNKS = new int[3];

    public static void clear() {
        DATA.clear();
        AT.clear();
        ticks = 0;
        SUM = new int[3][9];
        CHUNKS = new int[3];
    }

    private static int band(int sectionY) {
        int y = sectionY * 16 + 8;
        return y < 0 ? 0 : y < 48 ? 1 : 2;
    }

    private static int[] scan(WorldChunk chunk) {
        int[] out = new int[9];
        ChunkSection[] secs = chunk.getSectionArray();
        for (int i = 0; i < secs.length; i++) {
            ChunkSection sec = secs[i];
            int b = band(chunk.getBottomSectionCoord() + i);
            if (sec == null || sec.isEmpty()) { out[b * 3 + 2]++; continue; }
            int air = 0, total = 0;
            BlockState first = null;
            boolean same = true;
            for (int y = 1; y < 16; y += 4) {
                for (int z = 1; z < 16; z += 4) {
                    for (int x = 1; x < 16; x += 4) {
                        BlockState s = sec.getBlockState(x, y, z);
                        total++;
                        if (s.isAir()) air++;
                        if (first == null) first = s;
                        else if (s != first) same = false;
                    }
                }
            }
            if (air == total) out[b * 3 + 2]++;
            else if (same) out[b * 3 + 1]++;
            else out[b * 3]++;
        }
        return out;
    }

    public static void tick(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.checkEnabled || mc.world == null || mc.player == null) return;
        ticks++;
        int radius = mc.options.getViewDistance().getValue();
        int pcx = mc.player.getChunkPos().x, pcz = mc.player.getChunkPos().z;

        int budget = FastScan.maxChunks(mc, 2);
        int done = 0;
        // nearest rings first, so the readout fills in quickly
        for (int r = 0; r <= radius && done < budget; r++) {
            for (int cx = pcx - r; cx <= pcx + r && done < budget; cx++) {
                for (int cz = pcz - r; cz <= pcz + r && done < budget; cz++) {
                    if (Math.max(Math.abs(cx - pcx), Math.abs(cz - pcz)) != r) continue;
                    ChunkPos pos = new ChunkPos(cx, cz);
                    Long at = AT.get(pos);
                    if (at != null && ticks - at < 200) continue;
                    WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                    if (chunk == null) continue;
                    DATA.put(pos, scan(chunk));
                    AT.put(pos, ticks);
                    done++;
                }
            }
        }

        if (ticks % 20 == 0) {
            int[][] sum = new int[3][9];
            int[] n = new int[3];
            java.util.Iterator<Map.Entry<ChunkPos, int[]>> it = DATA.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<ChunkPos, int[]> e = it.next();
                int dist = Math.max(Math.abs(e.getKey().x - pcx), Math.abs(e.getKey().z - pcz));
                if (dist > radius + 2) { AT.remove(e.getKey()); it.remove(); continue; }
                int ring = dist <= 2 ? 0 : dist <= 6 ? 1 : 2;
                n[ring]++;
                for (int i = 0; i < 9; i++) sum[ring][i] += e.getValue()[i];
            }
            SUM = sum;
            CHUNKS = n;
        }
    }

    public static void render(DrawContext ctx, MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.checkEnabled || mc.player == null) return;
        String[] rows = new String[4];
        rows[0] = "CHUNK DATA  mixed / solid / air %";
        int[][] sum = SUM;
        int[] n = CHUNKS;
        for (int r = 0; r < 3; r++) {
            StringBuilder sb = new StringBuilder(RING[r]).append(" (").append(n[r]).append(")");
            for (int b = 0; b < 3; b++) {
                int m = sum[r][b * 3], s = sum[r][b * 3 + 1], a = sum[r][b * 3 + 2];
                int t = Math.max(1, m + s + a);
                sb.append("   ").append(BAND[b]).append(' ').append(m * 100 / t).append('/').append(s * 100 / t).append('/').append(a * 100 / t);
            }
            rows[r + 1] = sb.toString();
        }
        int w = 0;
        for (String s : rows) w = Math.max(w, mc.textRenderer.getWidth(s));
        w += 12;
        int h = 14 + 3 * 10 + 2;
        int x = Math.max(0, Math.min(cfg.checkX, mc.getWindow().getScaledWidth() - w));
        int y = Math.max(0, Math.min(cfg.checkY, mc.getWindow().getScaledHeight() - h));
        HudPanel.draw(ctx, x, y, w, h);
        ctx.drawTextWithShadow(mc.textRenderer, Text.literal(rows[0]), x + 6, y + 3, 0xFFF4ECD6);
        for (int i = 1; i < 4; i++) {
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal(rows[i]), x + 6, y + 4 + i * 10, 0xFFE8C170);
        }
    }
}
