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
                RegionMap.render(ctx, mc);
                SweepMap.render(ctx, mc);
                FpsBar.render(ctx, mc);
                PlayerLog.render(ctx, mc);
                ReachTest.render(ctx, mc);
                ArmorHud.render(ctx, mc);
            } catch (Throwable t) {
                ChunkFinderClient.LOGGER.error("Region map drawing failed", t);
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
            baseLabels(ctx, mc);
            susLabels(ctx, mc);
            itemLabels(ctx, mc);
            entityLabels(ctx, mc);
            activityLabels(ctx, mc);
            farmLabels(ctx, mc);
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
            if (c.baseEspEnabled) names.add("BASE ESP");
            if (c.itemEspEnabled) names.add("ITEM ESP");
            if (c.entityEspEnabled) names.add("ENTITY ESP");
            if (c.playerLogEnabled) names.add("PLAYER LOGGER");
            if (c.activityEnabled) names.add("ACTIVITY");
            if (c.farmEnabled) names.add("FARM FINDER");
            if (c.caveEnabled) names.add("CAVE FINDER");
            if (c.sweepEnabled) names.add("SWEEP MAP");
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
    }

    private static void activityLabels(DrawContext ctx, MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.activityEnabled || !cfg.activityLabels) return;
        long now = ActivityScanner.now();
        int shown = 0;
        for (Map.Entry<net.minecraft.util.math.ChunkPos, ActivityScanner.State> e : ActivityScanner.HOT) {
            if (shown >= 12) break;
            ActivityScanner.State s = e.getValue();
            boolean recent = now - s.eventAt < 600;
            boolean red = s.parts >= cfg.activityRedstoneMin;
            if (!recent && !(red && s.active > 0)) continue;
            double cx = e.getKey().getStartX() + 8, cz = e.getKey().getStartZ() + 8;
            float[] sc = Proj.toScreen(mc, new Vec3d(cx, s.avgY() + 8, cz));
            if (sc == null) continue;
            int dist = (int) Math.hypot(cx - mc.player.getX(), cz - mc.player.getZ());
            String text = recent ? "ACTIVITY: " + s.eventText + " (" + ((now - s.eventAt) / 20) + "s ago)"
                : "REDSTONE " + s.parts + " parts, " + s.active + " on";
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(text + " [" + dist + "m]"),
                (int) sc[0], (int) sc[1], recent ? 0xFF4DFFFF : 0xFFFFD24D);
            shown++;
        }
    }

    private static void farmLabels(DrawContext ctx, MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.farmEnabled || !cfg.farmLabels) return;
        int shown = 0;
        for (FarmFinder.Found f : FarmFinder.SHOWN) {
            if (shown >= 10) break;
            double cx = (f.minX() + f.maxX()) / 2, cz = (f.minZ() + f.maxZ()) / 2;
            float[] s = Proj.toScreen(mc, new Vec3d(cx, f.maxY() + 1.0, cz));
            if (s == null) continue;
            int dist = (int) Math.hypot(cx - mc.player.getX(), cz - mc.player.getZ());
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("FARM: " + f.text() + " [" + dist + "m]"),
                (int) s[0], (int) s[1], 0xFFFFB520);
            shown++;
        }
    }

    private static void entityLabels(DrawContext ctx, MinecraftClient mc) {
        if (!ChunkFinderConfig.INSTANCE.entityEspEnabled || !ChunkFinderConfig.INSTANCE.entityEspLabels) return;
        for (EntityEsp.Label l : EntityEsp.LABELS) {
            float[] s = Proj.toScreen(mc, new Vec3d(l.x(), l.y(), l.z()));
            if (s == null) continue;
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(l.text() + " [" + l.dist() + "m]"),
                (int) s[0], (int) s[1], 0xFF000000 | l.rgb());
        }
    }

    private static void itemLabels(DrawContext ctx, MinecraftClient mc) {
        if (!ChunkFinderConfig.INSTANCE.itemEspEnabled || !ChunkFinderConfig.INSTANCE.itemEspLabels) return;
        for (ItemEsp.Label l : ItemEsp.LABELS) {
            float[] s = Proj.toScreen(mc, new Vec3d(l.x(), l.y(), l.z()));
            if (s == null) continue;
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(l.text() + " [" + l.dist() + "m]"),
                (int) s[0], (int) s[1], 0xFF000000 | l.rgb());
        }
    }

    private static void susLabels(DrawContext ctx, MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.enabled || !cfg.detectSus || !cfg.susLabels || SusScanner.FOUND.isEmpty()) return;
        java.util.List<Map.Entry<net.minecraft.util.math.ChunkPos, Integer>> rk = SusScanner.ranked(mc.player.getX(), mc.player.getZ());
        int need = cfg.requiredSusScore();
        int n = Math.min(rk.size(), Math.max(1, cfg.susListCount));
        for (int i = 0; i < n; i++) {
            net.minecraft.util.math.ChunkPos p = rk.get(i).getKey();
            int[] lv = SusScanner.LEVELS.get(p);
            double y = (lv != null && lv.length > 0 ? lv[0] : cfg.renderY) + 1.5;
            double cx = p.getStartX() + 8, cz = p.getStartZ() + 8;
            float[] s = Proj.toScreen(mc, new Vec3d(cx, y, cz));
            if (s == null) continue;
            int[] col = SusScanner.strengthColor(rk.get(i).getValue(), need, cfg.susColor);
            int dist = (int) Math.hypot(cx - mc.player.getX(), cz - mc.player.getZ());
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("SUS " + rk.get(i).getValue() + "  [" + dist + "m]"),
                (int) s[0], (int) s[1], 0xFF000000 | (col[0] << 16) | (col[1] << 8) | col[2]);
        }
    }

    private static void baseLabels(DrawContext ctx, MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.baseEspEnabled || !cfg.baseEspLabels) return;
        Vec3d cam = Proj.camPos(mc);
        for (BaseEsp.Base b : BaseEsp.BASES) {
            Vec3d top = new Vec3d(b.cx(), b.maxY() + 2.5, b.cz());
            int d = (int) Math.sqrt(top.squaredDistanceTo(cam));
            float[] s = Proj.toScreen(mc, top);
            if (s == null) continue;
            int col = 0xFF000000 | b.rgb();
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("BASE: " + b.level() + "  [" + d + "m]"), (int) s[0], (int) s[1] - 5, col);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(b.summary()), (int) s[0], (int) s[1] + 5, 0xFFE6E6E6);
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
