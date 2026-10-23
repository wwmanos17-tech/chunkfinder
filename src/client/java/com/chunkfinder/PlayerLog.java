package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.text.Text;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Player Logger: remembers every player that comes into (or leaves) your view, with the time, distance and
 * coordinates. Shows a short list on screen and can send a chat message for each join / leave.
 * It only uses players the server has already sent to your game.
 */
public final class PlayerLog {
    private PlayerLog() {}

    public record Entry(String time, String name, boolean entered, int x, int y, int z, int dist) {}

    /** Where the list was last drawn: x, y, width, height (used for dragging it). */
    public static final int[] BOUNDS = new int[4];

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final List<Entry> LOG = new ArrayList<>();

    private record Seen(String name, double x, double y, double z, long lastTick) {}
    private static final Map<UUID, Seen> NOW = new HashMap<>();
    private static int timer = 0;
    private static long tickCount = 0;

    public static void clear() {
        LOG.clear();
        NOW.clear();
        timer = 0;
        tickCount = 0;
    }

    public static void tick(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.playerLogEnabled || mc.world == null || mc.player == null) return;
        tickCount++;
        if (timer-- > 0) return;
        timer = 4;

        Map<UUID, Boolean> present = new HashMap<>();
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            UUID id = p.getUuid();
            present.put(id, true);
            Seen old = NOW.get(id);
            String name = p.getName().getString();
            NOW.put(id, new Seen(name, p.getX(), p.getY(), p.getZ(), tickCount));
            if (old == null) {
                add(mc, cfg, name, true, p.getX(), p.getY(), p.getZ());
            }
        }
        // a player counts as gone after about 2 seconds without being seen (so a short flicker is not a "leave")
        List<UUID> gone = new ArrayList<>();
        for (Map.Entry<UUID, Seen> e : NOW.entrySet()) {
            if (!present.containsKey(e.getKey()) && tickCount - e.getValue().lastTick() > 40) gone.add(e.getKey());
        }
        for (UUID id : gone) {
            Seen s = NOW.remove(id);
            add(mc, cfg, s.name(), false, s.x(), s.y(), s.z());
        }
    }

    private static void add(MinecraftClient mc, ChunkFinderConfig cfg, String name, boolean entered, double x, double y, double z) {
        int dist = (int) Math.sqrt(mc.player.squaredDistanceTo(x, y, z));
        Entry en = new Entry(LocalTime.now().format(FMT), name, entered, (int) x, (int) y, (int) z, dist);
        LOG.add(en);
        while (LOG.size() > 100) LOG.remove(0);
        boolean chat = entered ? cfg.playerLogChatEnter : cfg.playerLogChatLeave;
        if (chat) {
            mc.player.sendMessage(Text.literal("[Pharaoh] " + name + (entered ? " came into range" : " left range")
                + " at X " + en.x() + ", Y " + en.y() + ", Z " + en.z() + " (" + dist + "m)"), false);
        }
    }

    public static void render(DrawContext ctx, MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        BOUNDS[2] = 0;
        if (!cfg.playerLogEnabled || !cfg.playerLogShowList) return;

        int lines = Math.min(cfg.playerLogLines, LOG.size());
        List<String> rows = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        for (int i = LOG.size() - lines; i < LOG.size(); i++) {
            Entry e = LOG.get(i);
            rows.add(e.time() + (e.entered() ? "  + " : "  - ") + e.name() + "  " + e.dist() + "m  "
                + e.x() + " " + e.z());
            cols.add(e.entered() ? 0xFFE8C170 : 0xFF9A9078);
        }
        String title = "PLAYERS " + NOW.size() + " in range";
        int w = mc.textRenderer.getWidth(title);
        for (String r : rows) w = Math.max(w, mc.textRenderer.getWidth(r));
        w += 12;
        int h = 14 + Math.max(1, rows.size()) * 10 + 2;
        int x = Math.max(0, Math.min(cfg.playerLogX, mc.getWindow().getScaledWidth() - w));
        int y = Math.max(0, Math.min(cfg.playerLogY, mc.getWindow().getScaledHeight() - h));
        BOUNDS[0] = x; BOUNDS[1] = y; BOUNDS[2] = w; BOUNDS[3] = h;

        ctx.fill(x, y, x + w, y + h, 0xB0100E08);
        ctx.fill(x, y, x + 2, y + h, 0xFFE0A526);
        ctx.drawTextWithShadow(mc.textRenderer, Text.literal(title), x + 6, y + 3, 0xFFF4ECD6);
        if (rows.isEmpty()) {
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal("nobody yet"), x + 6, y + 14, 0xFF9A9078);
        }
        for (int i = 0; i < rows.size(); i++) {
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal(rows.get(i)), x + 6, y + 14 + i * 10, cols.get(i));
        }
    }
}
