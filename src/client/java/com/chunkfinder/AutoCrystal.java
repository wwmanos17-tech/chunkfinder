package com.chunkfinder;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Auto Crystal for anarchy servers (like 2b2t). Near an enemy player it places obsidian under them,
 * puts an end crystal on it, and breaks crystals next to them. Needs end crystals (and obsidian) in the hotbar.
 */
public class AutoCrystal {
    private static int timer = 0;

    public static void clear() {
        timer = 0;
    }

    private static int findSlot(MinecraftClient mc, net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        }
        return -1;
    }

    private static AbstractClientPlayerEntity findTarget(MinecraftClient mc, double range) {
        AbstractClientPlayerEntity best = null;
        double bestD = range * range;
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isSpectator() || p.isDead()) continue;
            double d = p.squaredDistanceTo(mc.player);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private static boolean free(MinecraftClient mc, Box box) {
        return mc.world.getOtherEntities(null, box).isEmpty();
    }

    public static void tick(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.autoCrystalEnabled || mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;
        if (timer-- > 0) return;
        timer = cfg.autoCrystalDelay;

        AbstractClientPlayerEntity target = findTarget(mc, cfg.autoCrystalRange);
        if (target == null) return;
        Vec3d eye = mc.player.getEyePos();
        double reach = 4.5;

        // 1) break a crystal next to the target (never one right next to you)
        if (cfg.autoCrystalBreak) {
            Entity best = null;
            double bestD = 1e9;
            for (Entity e : mc.world.getEntitiesByClass(EndCrystalEntity.class,
                    target.getBoundingBox().expand(6), c -> true)) {
                if (e.getPos().distanceTo(eye) > reach) continue;
                if (e.getPos().distanceTo(mc.player.getPos()) < cfg.autoCrystalSafeDistance) continue;
                double d = e.getPos().distanceTo(target.getPos());
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
            if (best != null) {
                mc.interactionManager.attackEntity(mc.player, best);
                mc.player.swingHand(Hand.MAIN_HAND);
                return;
            }
        }

        int crystalSlot = findSlot(mc, Items.END_CRYSTAL);
        BlockPos tp = target.getBlockPos();

        // 2) put a crystal on obsidian or bedrock near the target
        if (cfg.autoCrystalPlace && crystalSlot >= 0) {
            BlockPos bestBase = null;
            double bestD = 1e9;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = -2; dy <= 0; dy++) {
                        BlockPos base = tp.add(dx, dy, dz);
                        if (!(mc.world.getBlockState(base).isOf(Blocks.OBSIDIAN)
                            || mc.world.getBlockState(base).isOf(Blocks.BEDROCK))) continue;
                        if (!mc.world.getBlockState(base.up()).isAir() || !mc.world.getBlockState(base.up(2)).isAir()) continue;
                        if (Vec3d.ofCenter(base).distanceTo(eye) > reach) continue;
                        if (Vec3d.ofCenter(base).distanceTo(mc.player.getPos()) < cfg.autoCrystalSafeDistance) continue;
                        Box box = new Box(base.getX(), base.getY() + 1, base.getZ(), base.getX() + 1, base.getY() + 3, base.getZ() + 1);
                        if (!free(mc, box)) continue;
                        double d = Vec3d.ofCenter(base).distanceTo(target.getPos());
                        if (d < bestD) {
                            bestD = d;
                            bestBase = base;
                        }
                    }
                }
            }
            if (bestBase != null) {
                mc.player.getInventory().selectedSlot = crystalSlot;
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(bestBase).add(0, 0.5, 0), Direction.UP, bestBase, false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                mc.player.swingHand(Hand.MAIN_HAND);
                return;
            }
        }

        // 3) no base yet: put obsidian on the ground right next to the target
        if (cfg.autoCrystalObsidian) {
            int obsSlot = findSlot(mc, Items.OBSIDIAN);
            if (obsSlot < 0) return;
            List<BlockPos> spots = List.of(tp.add(1, -1, 0), tp.add(-1, -1, 0), tp.add(0, -1, 1), tp.add(0, -1, -1));
            for (BlockPos spot : spots) {
                if (!mc.world.getBlockState(spot).isAir()) continue;
                BlockPos support = spot.down();
                if (!mc.world.getBlockState(support).isSolidBlock(mc.world, support)) continue;
                if (Vec3d.ofCenter(spot).distanceTo(eye) > reach) continue;
                if (Vec3d.ofCenter(spot).distanceTo(mc.player.getPos()) < cfg.autoCrystalSafeDistance) continue;
                if (!free(mc, new Box(spot))) continue;
                mc.player.getInventory().selectedSlot = obsSlot;
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(support).add(0, 0.5, 0), Direction.UP, support, false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                mc.player.swingHand(Hand.MAIN_HAND);
                return;
            }
        }
    }
}
