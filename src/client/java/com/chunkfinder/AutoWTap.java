package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Auto W-Tap: when you hit someone while sprinting, forward is released for a tick or two
 * and then pressed again, which resets your sprint so the next hit knocks back more.
 */
public class AutoWTap {
    private static int tapTicks = 0;
    private static boolean wasSwinging = false;

    public static void clear() {
        tapTicks = 0;
        wasSwinging = false;
    }

    /** Runs at the start of every tick, before the player moves. */
    public static void pre(MinecraftClient mc) {
        if (tapTicks <= 0 || mc.player == null) return;
        mc.options.forwardKey.setPressed(false);
        tapTicks--;
        if (tapTicks == 0) {
            long window = mc.getWindow().getHandle();
            int code = InputUtil.fromTranslationKey(mc.options.forwardKey.getBoundKeyTranslationKey()).getCode();
            mc.options.forwardKey.setPressed(InputUtil.isKeyPressed(window, code));
        }
    }

    /** Runs at the end of every tick: spots a fresh hit. */
    public static void post(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.autoWTapEnabled || mc.player == null || mc.currentScreen != null) {
            wasSwinging = false;
            return;
        }
        boolean swinging = mc.player.handSwinging && mc.player.handSwingTicks == 0;
        if (swinging && !wasSwinging && tapTicks == 0 && mc.player.isSprinting()
            && mc.options.forwardKey.isPressed()
            && mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY
            && ((EntityHitResult) mc.crosshairTarget).getEntity() instanceof LivingEntity) {
            tapTicks = cfg.autoWTapTicks;
        }
        wasSwinging = swinging;
    }
}
