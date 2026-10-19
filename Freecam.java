package com.chunkfinder;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Freecam: detaches the camera from your player. Your real player stays still while the
 * camera flies around (client-side only). The camera itself is moved by CameraMixin and
 * the mouse is redirected by MouseMixin.
 */
public class Freecam {
    public static boolean active = false;
    public static float yaw;
    public static float pitch;

    private static Vec3d prev = Vec3d.ZERO;
    private static Vec3d cur = Vec3d.ZERO;

    public static boolean isActive() {
        return active;
    }

    /** Camera position, smoothed between ticks. */
    public static Vec3d getPos(float tickDelta) {
        return prev.lerp(cur, tickDelta);
    }

    public static void toggle(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        active = !active;
        if (active) {
            yaw = mc.player.getYaw();
            pitch = mc.player.getPitch();
            Vec3d start = mc.gameRenderer.getCamera().getPos();
            prev = start;
            cur = start;
        } else {
            // Give the movement keys back to the real player.
            KeyBinding.updatePressedStates();
        }
        mc.player.sendMessage(Text.literal("Freecam: " + (active ? "ON" : "OFF")), true);
    }

    /** Runs at the start of every client tick. */
    public static void tick(MinecraftClient mc) {
        if (!active) return;
        if (mc.player == null || mc.world == null) {
            active = false;
            return;
        }

        boolean canMove = mc.currentScreen == null;

        boolean forward = canMove && down(mc, mc.options.forwardKey);
        boolean back = canMove && down(mc, mc.options.backKey);
        boolean left = canMove && down(mc, mc.options.leftKey);
        boolean right = canMove && down(mc, mc.options.rightKey);
        boolean up = canMove && down(mc, mc.options.jumpKey);
        boolean downKey = canMove && down(mc, mc.options.sneakKey);
        boolean fast = canMove && down(mc, mc.options.sprintKey);

        // Stop the real player from moving while the camera flies.
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);

        double speed = ChunkFinderConfig.INSTANCE.freecamSpeed * 0.1;
        if (fast) speed *= 2.0;

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        // Direction you are looking, and the direction to your right.
        Vec3d look = new Vec3d(
            -Math.sin(yawRad) * Math.cos(pitchRad),
            -Math.sin(pitchRad),
            Math.cos(yawRad) * Math.cos(pitchRad));
        Vec3d rightVec = new Vec3d(-Math.cos(yawRad), 0, -Math.sin(yawRad));

        Vec3d move = Vec3d.ZERO;
        if (forward) move = move.add(look);
        if (back) move = move.subtract(look);
        if (right) move = move.add(rightVec);
        if (left) move = move.subtract(rightVec);
        if (up) move = move.add(0, 1, 0);
        if (downKey) move = move.subtract(0, 1, 0);

        prev = cur;
        if (move.lengthSquared() > 0) {
            cur = cur.add(move.normalize().multiply(speed));
        }
    }

    /** Called by MouseMixin: mouse movement turns the camera instead of the player. */
    public static void look(double dx, double dy) {
        yaw += (float) (dx * 0.15);
        pitch = MathHelper.clamp(pitch + (float) (dy * 0.15), -90f, 90f);
    }

    private static boolean down(MinecraftClient mc, KeyBinding binding) {
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(binding);
        if (key.getCategory() != InputUtil.Type.KEYSYM) return false;
        return InputUtil.isKeyPressed(mc.getWindow().getHandle(), key.getCode());
    }
}
