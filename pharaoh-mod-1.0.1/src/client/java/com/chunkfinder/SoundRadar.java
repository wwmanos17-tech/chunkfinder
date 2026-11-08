package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Sound Radar: the server sends you sounds that happen close to you (about 16 blocks): doors, chests, digging, pistons,
 * furnaces. This shows each one as a small label around your crosshair that points toward it, with its distance and
 * height difference, and marks the exact block in the world for a few seconds. It only uses sounds your game already
 * received. It does not store anything, send anything or change what the server sends.
 */
public final class SoundRadar {
    private SoundRadar() {}

    private static final int DOOR = 0, STORAGE = 1, DIG = 2, MACHINE = 3;
    private static final String[] NAME = {"Door", "Chest", "Digging", "Machine"};
    private static final int[] COLOR = {0xFF6FD3FF, 0xFFFFC94D, 0xFFFF7A5C, 0xFFB98CFF};
    private static final float[][] RGB = {{0.43f, 0.83f, 1f}, {1f, 0.79f, 0.30f}, {1f, 0.48f, 0.36f}, {0.73f, 0.55f, 1f}};

    private static final class Ev {
        double x, y, z;
        int kind;
        long at;
        int count = 1;
    }

    private static final List<Ev> EVENTS = new ArrayList<>();

    public static void clear() {
        synchronized (EVENTS) { EVENTS.clear(); }
    }

    /** -1 when the sound is not one of the kinds this shows. */
    private static int classify(String p, ChunkFinderConfig cfg) {
        if (p.contains("footstep") || p.contains("step") || p.contains("ambient") || p.contains("entity.")) {
            if (!p.contains("villager") && !p.contains("iron_golem")) return -1;
        }
        if (cfg.soundDoors && (p.contains("_door.") || p.contains("trapdoor") || p.contains("fence_gate"))) return DOOR;
        if (cfg.soundStorage && (p.contains("chest.") || p.contains("barrel.") || p.contains("shulker_box.") || p.contains("ender_chest"))) return STORAGE;
        if (cfg.soundDig && p.startsWith("block.") && (p.endsWith(".break") || p.endsWith(".hit"))
            && !p.contains("grass") && !p.contains("leaves") && !p.contains("wool") && !p.contains("snow") && !p.contains("sand")
            && !p.contains("gravel") && !p.contains("wood") && !p.contains("water")) return DIG;
        if (cfg.soundMachines && (p.contains("piston.") || p.contains("dispenser.") || p.contains("furnace.") || p.contains("hopper")
            || p.contains("anvil.") || p.contains("grindstone") || p.contains("smithing") || p.contains("lever.")
            || p.contains("pressure_plate") || p.contains("button.") || p.contains("tripwire") || p.contains("note_block")
            || p.contains("campfire") || p.contains("brewing_stand") || p.contains("enchantment_table") || p.contains("beacon."))) return MACHINE;
        return -1;
    }

    /** Called from the packet handler mixin for each sound the server sent you. */
    public static void sound(double x, double y, double z, String path) {
        try {
            ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
            if (!cfg.soundEnabled) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null || !mc.isOnThread()) return;
            double dx = x - mc.player.getX(), dy = y - mc.player.getY(), dz = z - mc.player.getZ();
            if (dx * dx + dy * dy + dz * dz < 9.0) return;   // closer than 3 blocks is you
            int kind = classify(path, cfg);
            if (kind < 0) return;
            long now = System.currentTimeMillis();
            synchronized (EVENTS) {
                for (Ev e : EVENTS) {
                    if (e.kind == kind && now - e.at < 1500 && Math.abs(e.x - x) < 3 && Math.abs(e.y - y) < 3 && Math.abs(e.z - z) < 3) {
                        e.at = now;
                        e.count++;
                        e.x = x; e.y = y; e.z = z;
                        return;
                    }
                }
                Ev e = new Ev();
                e.x = x; e.y = y; e.z = z; e.kind = kind; e.at = now;
                EVENTS.add(e);
                if (EVENTS.size() > 40) EVENTS.remove(0);
            }
        } catch (Throwable ignored) { }
    }

    private static List<Ev> live() {
        long keep = ChunkFinderConfig.INSTANCE.soundSeconds * 1000L;
        long now = System.currentTimeMillis();
        List<Ev> out = new ArrayList<>();
        synchronized (EVENTS) {
            EVENTS.removeIf(e -> now - e.at > keep);
            out.addAll(EVENTS);
        }
        return out;
    }

    public static void render(DrawContext ctx, MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.soundEnabled || mc.player == null) return;
        List<Ev> list = live();
        if (list.isEmpty()) return;
        int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
        int cx = w / 2, cy = h / 2;
        double yaw = mc.player.getYaw();
        long now = System.currentTimeMillis();
        long keep = cfg.soundSeconds * 1000L;
        for (Ev e : list) {
            double dx = e.x - mc.player.getX(), dz = e.z - mc.player.getZ(), dy = e.y - mc.player.getY();
            double tyaw = Math.toDegrees(Math.atan2(-dx, dz));
            double rel = Math.toRadians(MathHelper.wrapDegrees(tyaw - yaw));
            int dist = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
            String vert = dy > 3 ? " ^" + Math.round(dy) : dy < -3 ? " v" + Math.round(-dy) : "";
            String label = NAME[e.kind] + " " + dist + "m" + vert;
            int tw = PFont.w(mc, label);
            int ex = cx + (int) Math.round(Math.sin(rel) * 70) - tw / 2;
            int ey = cy - (int) Math.round(Math.cos(rel) * 52) - 4;
            float fade = 1f - Math.max(0f, (now - e.at) / (float) keep - 0.6f) / 0.4f;
            int alpha = (int) (255 * MathHelper.clamp(fade, 0.25f, 1f));
            int bg = (alpha * 150 / 255) << 24;
            ctx.fill(ex - 3, ey - 2, ex + tw + 3, ey + 10, bg);
            ctx.fill(ex - 3, ey - 2, ex - 1, ey + 10, (alpha << 24) | (COLOR[e.kind] & 0xFFFFFF));
            ctx.drawTextWithShadow(mc.textRenderer, PFont.t(label), ex, ey, (alpha << 24) | (COLOR[e.kind] & 0xFFFFFF));
        }
    }

    public static void collect(List<EspRenderer.Box> boxes, List<EspRenderer.Line> lines, Vec3d cam) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.soundEnabled || !cfg.soundMarks) return;
        for (Ev e : live()) {
            double bx = Math.floor(e.x), by = Math.floor(e.y), bz = Math.floor(e.z);
            float[] c = RGB[e.kind];
            boxes.add(new EspRenderer.Box(bx, by, bz, bx + 1, by + 1, bz + 1, c[0], c[1], c[2], 0.18f));
        }
    }
}
