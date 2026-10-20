package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.SkinTextures;
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
        if (ChunkFinderConfig.INSTANCE.spectatorSound) {
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f));
        }
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
            if (cfg.spectatorSound) {
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f));
            }
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
            if (cfg.spectatorSound) {
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f));
            }
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
            // fade the screen darker while the head is shown
            float in = Math.min(1f, t / 0.2f);
            float out = Math.min(1f, (popup - t) / 0.4f);
            float fade = Math.min(in, out);
            int overlayAlpha = (int) (170 * fade);
            ctx.fill(0, 0, w, h, (overlayAlpha << 24));

            // big head that zooms in fast, then keeps growing slowly
            float grow = 0.55f + 0.45f * in + 0.12f * Math.min(1f, t / popup);
            int size = (int) (h * 0.9f * (cfg.spectatorHeadPercent / 100f) * Math.min(grow, 1.1f));
            int x = (w - size) / 2;
            int y = (h - size) / 2;

            if (shownSkin != null) {
                PlayerSkinDrawer.draw(ctx, shownSkin, x, y, size);
            }
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(shownName), w / 2, h - 20, 0xFFFFE680);
        } else if (t < popup + WATCH_SECONDS) {
            // the staff name in big red letters with "WATCHING YOU" under it
            float m = t - popup;
            float alpha = Math.min(Math.min(1f, m / 0.3f), Math.min(1f, (WATCH_SECONDS - m) / 0.5f));
            int a = (int) (255 * alpha);
            if (a > 8) {
                ctx.getMatrices().push();
                ctx.getMatrices().translate(w / 2f, h / 2f - 30, 0f);
                ctx.getMatrices().scale(4f, 4f, 1f);
                ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(shownName), 0, 0, (a << 24) | 0xFF5555);
                ctx.getMatrices().pop();
                ctx.getMatrices().push();
                ctx.getMatrices().translate(w / 2f, h / 2f + 10, 0f);
                ctx.getMatrices().scale(2f, 2f, 1f);
                ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("WATCHING YOU"), 0, 0, (a << 24) | 0xAA2222);
                ctx.getMatrices().pop();
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
