package com.chunkfinder;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Everything drawn flat on the screen: player names, chest labels, the chest summary and the spectator alert. */
public class PharaohHud {
    public static void register() {
        HudRenderCallback.EVENT.register(PharaohHud::render);
        HudRenderCallback.EVENT.register((ctx, tc) -> renderSpectator(ctx));
        HudRenderCallback.EVENT.register((ctx, tc) -> renderModuleList(ctx));
        HudRenderCallback.EVENT.register((ctx, tc) -> {
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.world == null || mc.player == null || mc.options.hudHidden) return;
                StaffTracker.render(ctx, mc);
            } catch (Throwable t) {
                ChunkFinderClient.LOGGER.error("Staff list drawing failed", t);
            }
        });
        HudRenderCallback.EVENT.register((ctx, tc) -> {
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.world == null || mc.player == null || mc.options.hudHidden) return;
                SusScanner.renderList(ctx, mc);
            } catch (Throwable t) {
                ChunkFinderClient.LOGGER.error("Sus list drawing failed", t);
            }
        });
    }

    private static long pausedUntil = 0;

    private static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        if (System.currentTimeMillis() < pausedUntil) return;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null || mc.player == null) return;
            playerLabels(ctx, mc);
            chestLabels(ctx, mc);
            chestSummary(ctx, mc);
        } catch (Throwable t) {
            pausedUntil = System.currentTimeMillis() + 2000;
            ChunkFinderClient.LOGGER.error("HUD drawing failed, trying again in 2 seconds", t);
        }
    }

    /** The list at the top right with the names of every tool that is switched on. */
    private static void renderModuleList(DrawContext ctx) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            ChunkFinderConfig c = ChunkFinderConfig.INSTANCE;
            if (!c.showModuleList || mc.world == null || mc.player == null || mc.options.hudHidden) return;

            List<String> names = new ArrayList<>();
            if (c.enabled) names.add("CHUNK FINDER");
            if (Freecam.isActive()) names.add("FREECAM");
            if (c.playerEspEnabled) names.add("PLAYER ESP");
            if (c.chestEspEnabled) names.add("CHEST ESP");
            if (c.oreEspEnabled) names.add("ORE ESP");
            if (c.spectatorEnabled) names.add("SPECTATOR ALERT");
            if (names.isEmpty()) return;

            names.sort((a, b) -> Integer.compare(mc.textRenderer.getWidth(b), mc.textRenderer.getWidth(a)));
            int sw = mc.getWindow().getScaledWidth();
            int y = 4;
            for (String n : names) {
                int w = mc.textRenderer.getWidth(n);
                int right = sw - 3;
                ctx.fill(right - w - 6, y, right, y + 11, 0xB0101018);
                ctx.fill(right - 2, y, right, y + 11, 0xFFE0A526);
                ctx.drawTextWithShadow(mc.textRenderer, Text.literal(n), right - w - 3, y + 2, 0xFFE6E6E6);
                y += 12;
            }
        } catch (Throwable t) {
            ChunkFinderClient.LOGGER.error("Module list drawing failed", t);
        }
    }

    private static void renderSpectator(DrawContext ctx) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null || mc.player == null) return;
            SpectatorAlert.render(ctx, mc);
        } catch (Throwable t) {
            ChunkFinderClient.LOGGER.error("Spectator alert drawing failed", t);
        }
    }

    private static void playerLabels(DrawContext ctx, MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.playerEspEnabled || !(cfg.playerEspNames || cfg.playerEspDistance)) return;
        Vec3d cam = Proj.camPos(mc);
        int color = 0xFF000000 | (cfg.playerColor[0] << 16) | (cfg.playerColor[1] << 8) | cfg.playerColor[2];

        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (!PlayerEsp.shouldShow(mc, p)) continue;
            double dist = p.getPos().distanceTo(cam);
            if (dist > cfg.playerEspRange) continue;

            StringBuilder sb = new StringBuilder();
            if (cfg.playerEspNames) sb.append(p.getName().getString());
            if (cfg.playerEspDistance) {
                if (sb.length() > 0) sb.append(' ');
                sb.append('[').append((int) dist).append("m]");
            }
            Vec3d top = new Vec3d(p.getX(), p.getBoundingBox().maxY + 0.35, p.getZ());
            label(ctx, mc, top, sb.toString(), color);
        }
    }

    private static void chestLabels(DrawContext ctx, MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.chestEspEnabled || !cfg.chestEspLabels) return;
        Vec3d cam = Proj.camPos(mc);

        ChestEsp.View view = ChestEsp.view(mc, cam);
        for (ChestEsp.Group g : view.groups()) {
            Vec3d gc = g.center();
            label(ctx, mc, new Vec3d(gc.x, g.y2() + 1.0, gc.z), g.text() + " [" + (int) gc.distanceTo(cam) + "m]", 0xFFFFD24D);
        }
        List<ChestEsp.Target> list = new ArrayList<>(view.singles());
        list.sort((a, b) -> Double.compare(a.center().distanceTo(cam), b.center().distanceTo(cam)));
        int shown = 0;
        for (ChestEsp.Target t : list) {
            if (shown >= 40) break;
            Vec3d c = t.center();
            int rgb = ((int) (t.r() * 255) << 16) | ((int) (t.g() * 255) << 8) | (int) (t.b() * 255);
            label(ctx, mc, new Vec3d(c.x, t.y2() + 0.4, c.z),
                t.kind().label + " [" + (int) c.distanceTo(cam) + "m]", 0xFF000000 | rgb);
            shown++;
        }
    }

    private static void chestSummary(DrawContext ctx, MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.chestEspEnabled || !cfg.chestEspSummary || ChestEsp.COUNTS.isEmpty()) return;

        int y = 8;
        ctx.drawTextWithShadow(mc.textRenderer, Text.literal("Chest ESP"), 8, y, 0xFFFFE680);
        y += 11;
        for (Map.Entry<ChestEsp.Kind, Integer> e : ChestEsp.COUNTS.entrySet()) {
            ChestEsp.Kind k = e.getKey();
            int rgb = ((int) (k.r * 255) << 16) | ((int) (k.g * 255) << 8) | (int) (k.b * 255);
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal(k.label + ": " + e.getValue()), 8, y, 0xFF000000 | rgb);
            y += 10;
        }
    }

    private static void label(DrawContext ctx, MinecraftClient mc, Vec3d pos, String text, int color) {
        if (text.isEmpty()) return;
        float[] s = Proj.toScreen(mc, pos);
        if (s == null) return;
        ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(text), (int) s[0], (int) s[1], color);
    }
}
