package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * When a player in spectator mode comes near you, their head pops up like the elder guardian curse,
 * then "Good luck, Pharaoh player" shows (the text is editable in the config). Each spectator can only
 * trigger it once per cooldown (30 minutes by default).
 */
public class SpectatorAlert {
    private static final float MESSAGE_SECONDS = 3.0f;
    private static final float WATCH_SECONDS = 2.5f;

    private static final Map<UUID, Long> LAST = new HashMap<>();
    private static boolean active = false;
    private static String shownName = "";
    private static SkinTextures shownSkin = null;
    private static long startMillis = 0;

    /** The sound and the elder guardian that jumps at your face, like the real curse. */
    private static void effects(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (cfg.spectatorSound) {
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f));
        }
        if (cfg.spectatorGuardian && mc.world != null && mc.player != null) {
            mc.world.addParticle(ParticleTypes.ELDER_GUARDIAN, mc.player.getX(), mc.player.getY(), mc.player.getZ(), 0.0, 0.0, 0.0);
        }
    }

    public static void clear() {
        LAST.clear();
        active = false;
    }

    /** Shows the alert for yourself, so you can check it works without a spectator around. */
    public static void test(MinecraftClient mc) {
        if (mc.player == null) return;
        shownName = mc.player.getName().getString();
        shownSkin = mc.player.getSkinTextures();
        startMillis = System.currentTimeMillis();
        active = true;
        effects(mc);
    }

    public static void tick(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.spectatorEnabled || active) return;
        if (mc.world == null || mc.player == null) return;
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        if (handler == null) return;

        long now = System.currentTimeMillis();
        long cooldown = cfg.spectatorCooldownMinutes * 60_000L;

        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            PlayerListEntry entry = handler.getPlayerListEntry(p.getUuid());
            if (entry == null || entry.getGameMode() != GameMode.SPECTATOR) continue;
            if (p.distanceTo(mc.player) > cfg.spectatorRange) continue;

            Long last = LAST.get(p.getUuid());
            if (last != null && now - last < cooldown) continue;

            LAST.put(p.getUuid(), now);
            shownName = p.getName().getString();
            shownSkin = entry.getSkinTextures();
            startMillis = now;
            active = true;
            effects(mc);
            return;
        }

        // Fallback: spectators that are in the player list but not loaded as entities near you.
        if (!cfg.spectatorTabList) return;
        for (PlayerListEntry entry : handler.getPlayerList()) {
            if (entry.getGameMode() != GameMode.SPECTATOR) continue;
            UUID id = entry.getProfile().getId();
            if (id.equals(mc.player.getUuid())) continue;
            Long last = LAST.get(id);
            if (last != null && now - last < cooldown) continue;

            LAST.put(id, now);
            shownName = entry.getProfile().getName();
            shownSkin = entry.getSkinTextures();
            startMillis = now;
            active = true;
            effects(mc);
            return;
        }
    }

    public static void render(DrawContext ctx, MinecraftClient mc) {
        if (!active) return;
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        float t = (System.currentTimeMillis() - startMillis) / 1000f;
        float popup = cfg.spectatorPopupSeconds;
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();

        if (t < popup) {
            // elder-guardian style: the see-through head rises from below, grows toward you with a slight
            // shake, and fades out. No dark screen behind it.
            float p = Math.min(1f, t / popup);
            float in = Math.min(1f, t / 0.35f);
            float out = Math.min(1f, (popup - t) / 0.6f);
            float alpha = 0.6f * Math.min(in, out);
            // like the Anubis effect: a big see-through head drops in from the top, stops in the middle, then sinks down and out
            int size = Math.max(2, (int) (h * 1.1f * (cfg.spectatorHeadPercent / 100f)));
            float cy;
            if (p < 0.2f) {
                float q = p / 0.2f;
                cy = h * (0.02f + 0.48f * (1f - (1f - q) * (1f - q)));
            } else {
                float q = (p - 0.2f) / 0.8f;
                cy = h * (0.5f + 0.30f * q * q);
            }
            int x = (int) ((w - size) / 2f + 1.5f * Math.sin(t * 40f));
            int y = (int) (cy - size / 2f);

            if (shownSkin != null) {
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.15f, 1.0f, 1.0f, alpha);
                PlayerSkinDrawer.draw(ctx, shownSkin, x, y, size);
                ctx.draw();
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }
        } else if (t < popup + WATCH_SECONDS) {
            // the staff name in big red letters with "WATCHING YOU" under it
            float m = t - popup;
            float alpha = Math.min(Math.min(1f, m / 0.3f), Math.min(1f, (WATCH_SECONDS - m) / 0.5f));
            int a = (int) (255 * alpha);
            if (a > 8) {
                // glitchy name: pink ghost copies jump sideways in short bursts, plus small pink bits flickering around
                java.util.Random rnd = new java.util.Random((long) (t * 14f) * 7919L + 13);
                boolean burst = rnd.nextFloat() < 0.35f;
                float ns = Math.max(4f, h / 90f);
                float ws = Math.max(2f, h / 180f);
                float flick = rnd.nextFloat() < 0.12f ? 0.45f : 1f;
                int an = (int) (a * flick);
                float baseY = h / 2f - h * 0.08f;
                int jx = burst ? rnd.nextInt(9) - 4 : 0;
                for (int pass = 0; pass < 3; pass++) {
                    int color = pass == 0 ? ((an / 2) << 24) | 0xFF3CFF : pass == 1 ? ((an / 2) << 24) | 0x3CD7FF : (an << 24) | 0xFF6A3D;
                    int ox = pass == 0 ? -2 - jx : pass == 1 ? 2 + jx : 0;
                    ctx.getMatrices().push();
                    ctx.getMatrices().translate(w / 2f + ox, baseY, 0f);
                    ctx.getMatrices().scale(ns, ns, 1f);
                    ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(shownName), 0, 0, color);
                    ctx.getMatrices().pop();
                }
                ctx.getMatrices().push();
                ctx.getMatrices().translate(w / 2f + (burst ? rnd.nextInt(5) - 2 : 0), h / 2f + h * 0.04f, 0f);
                ctx.getMatrices().scale(ws, ws, 1f);
                ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("WATCHING YOU"), 0, 0, (an << 24) | 0xD8372D);
                ctx.getMatrices().pop();
                int bits = burst ? 22 : 10;
                for (int i = 0; i < bits; i++) {
                    int bx = w / 2 + rnd.nextInt((int) (w * 0.5f)) - (int) (w * 0.25f);
                    int by = (int) baseY - (int) (h * 0.08f) + rnd.nextInt((int) (h * 0.3f));
                    int bw = 2 + rnd.nextInt(Math.max(3, (int) (h / 70f)) * 2);
                    int bh = 1 + rnd.nextInt(Math.max(2, (int) (h / 120f)));
                    int col = rnd.nextBoolean() ? 0xFF55FF : 0xB02CFF;
                    ctx.fill(bx, by, bx + bw, by + bh, ((int) (a * 0.7f) << 24) | col);
                }
            }
        } else if (t < popup + WATCH_SECONDS + MESSAGE_SECONDS) {
            float m = t - popup - WATCH_SECONDS;
            float alpha = Math.min(Math.min(1f, m / 0.4f), Math.min(1f, (MESSAGE_SECONDS - m) / 0.6f));
            int a = (int) (255 * alpha);
            if (a > 8) {
                ctx.getMatrices().push();
                ctx.getMatrices().translate(w / 2f, h / 2f - 10, 0f);
                ctx.getMatrices().scale(2f, 2f, 1f);
                ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(cfg.spectatorMessage), 0, 0,
                    (a << 24) | 0xFFFF55);
                ctx.getMatrices().pop();
            }
        } else {
            active = false;
        }
    }
}
