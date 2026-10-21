package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Trigger Bot: when your crosshair is on a target, it attacks for you once your weapon is charged.
 * You still aim yourself.
 */
public class TriggerBot {
    public static void tick(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.triggerBotEnabled || mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null || mc.player.isUsingItem()) return;
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.ENTITY) return;

        Entity target = ((EntityHitResult) mc.crosshairTarget).getEntity();
        if (!(target instanceof LivingEntity) || !target.isAlive() || target == mc.player) return;
        if (target instanceof PlayerEntity pl && pl.isSpectator()) return;
        if (cfg.triggerPlayersOnly && !(target instanceof PlayerEntity)) return;

        if (mc.player.getAttackCooldownProgress(0.5f) < cfg.triggerCharge / 100f) return;

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }
}
