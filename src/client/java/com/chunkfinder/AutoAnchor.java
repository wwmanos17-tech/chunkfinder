package com.chunkfinder;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Auto Anchor for anarchy servers (like 2b2t). Near an enemy it places a respawn anchor next to them,
 * charges it with glowstone, then sets it off. Needs respawn anchors and glowstone in the hotbar.
 * Anchors only explode in the Overworld and the End, so it stays off in the Nether.
 */
public class AutoAnchor {
    private static int timer = 0;

    public static void clear() {
        timer = 0;
    }

    private static int findSlot(MinecraftClient mc, Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        }
        return -1;
    }

    /** A hotbar slot that does not hold glowstone, so right clicking the anchor sets it off. */
    private static int findNonGlowstoneSlot(MinecraftClient mc) {
        int current = mc.player.getInventory().selectedSlot;
        if (!mc.player.getInventory().getStack(current).isOf(Items.GLOWSTONE)) return current;
        for (int i = 0; i < 9; i++) {
            if (!mc.player.getInventory().getStack(i).isOf(Items.GLOWSTONE)) return i;
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

    private static void click(MinecraftClient mc, BlockPos pos, Direction face) {
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos).add(0, 0.5, 0), face, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    public static void tick(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.autoAnchorEnabled || mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null || mc.world.getRegistryKey() == World.NETHER) return;
        if (timer-- > 0) return;
        timer = cfg.autoAnchorDelay;

        AbstractClientPlayerEntity target = findTarget(mc, cfg.autoAnchorRange);
        if (target == null) return;
        Vec3d eye = mc.player.getEyePos();
        double reach = 4.5;
        BlockPos tp = target.getBlockPos();

        // 1) an anchor already next to the target: charge it, then set it off
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos pos = tp.add(dx, dy, dz);
                    BlockState st = mc.world.getBlockState(pos);
                    if (!st.isOf(Blocks.RESPAWN_ANCHOR)) continue;
                    if (Vec3d.ofCenter(pos).distanceTo(eye) > reach) continue;
                    if (Vec3d.ofCenter(pos).distanceTo(mc.player.getPos()) < cfg.autoAnchorSafeDistance) continue;

                    int charges = st.get(RespawnAnchorBlock.CHARGES);
                    if (charges == 0) {
                        int glow = findSlot(mc, Items.GLOWSTONE);
                        if (glow < 0) return;
                        mc.player.getInventory().selectedSlot = glow;
                        click(mc, pos, Direction.UP);
                    } else {
                        int other = findNonGlowstoneSlot(mc);
                        if (other < 0) return;
                        mc.player.getInventory().selectedSlot = other;
                        click(mc, pos, Direction.UP);
                    }
                    return;
                }
            }
        }

        // 2) no anchor yet: place one on the ground right next to the target
        int anchorSlot = findSlot(mc, Items.RESPAWN_ANCHOR);
        if (anchorSlot < 0) return;
        BlockPos[] spots = {tp.add(1, 0, 0), tp.add(-1, 0, 0), tp.add(0, 0, 1), tp.add(0, 0, -1)};
        for (BlockPos spot : spots) {
            if (!mc.world.getBlockState(spot).isAir()) continue;
            BlockPos support = spot.down();
            if (!mc.world.getBlockState(support).isSolidBlock(mc.world, support)) continue;
            if (Vec3d.ofCenter(spot).distanceTo(eye) > reach) continue;
            if (Vec3d.ofCenter(spot).distanceTo(mc.player.getPos()) < cfg.autoAnchorSafeDistance) continue;
            if (!mc.world.getOtherEntities(null, new Box(spot)).isEmpty()) continue;
            mc.player.getInventory().selectedSlot = anchorSlot;
            click(mc, support, Direction.UP);
            return;
        }
    }
}
