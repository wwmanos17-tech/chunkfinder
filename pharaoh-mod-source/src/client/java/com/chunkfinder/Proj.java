package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/** Small math helpers: where the camera looks, and where a point in the world lands on your screen. */
public class Proj {
    public static Vec3d camPos(MinecraftClient mc) {
        return mc.gameRenderer.getCamera().getPos();
    }

    public static Vec3d forward(MinecraftClient mc) {
        double yaw = Math.toRadians(mc.gameRenderer.getCamera().getYaw());
        double pitch = Math.toRadians(mc.gameRenderer.getCamera().getPitch());
        return new Vec3d(
            -Math.sin(yaw) * Math.cos(pitch),
            -Math.sin(pitch),
            Math.cos(yaw) * Math.cos(pitch));
    }

    /** Start point for tracer lines: a little in front of the camera. */
    public static Vec3d tracerStart(MinecraftClient mc) {
        return camPos(mc).add(forward(mc).multiply(0.3));
    }

    /** Returns {x, y} in scaled GUI coordinates, or null if the point is behind you. */
    public static float[] toScreen(MinecraftClient mc, Vec3d point) {
        Vec3d cam = camPos(mc);
        double yaw = Math.toRadians(mc.gameRenderer.getCamera().getYaw());
        double pitch = Math.toRadians(mc.gameRenderer.getCamera().getPitch());

        Vec3d f = forward(mc);
        Vec3d r = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
        // up = right x forward
        Vec3d u = new Vec3d(
            r.y * f.z - r.z * f.y,
            r.z * f.x - r.x * f.z,
            r.x * f.y - r.y * f.x);

        Vec3d d = point.subtract(cam);
        double zc = d.dotProduct(f);
        if (zc <= 0.05) return null;
        double xc = d.dotProduct(r);
        double yc = d.dotProduct(u);

        double fov = Math.toRadians(mc.options.getFov().getValue());
        double tan = Math.tan(fov / 2.0);
        double aspect = (double) mc.getWindow().getFramebufferWidth() / (double) mc.getWindow().getFramebufferHeight();

        double ndcX = xc / (zc * tan * aspect);
        double ndcY = yc / (zc * tan);
        if (Math.abs(ndcX) > 1.3 || Math.abs(ndcY) > 1.3) return null;

        float sx = (float) ((ndcX * 0.5 + 0.5) * mc.getWindow().getScaledWidth());
        float sy = (float) ((0.5 - ndcY * 0.5) * mc.getWindow().getScaledHeight());
        return new float[]{sx, sy};
    }
}
