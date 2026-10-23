package com.chunkfinder;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws all boxes and lines of every tool in one go, with the depth test off so they show through walls.
 */
public class EspRenderer {
    public record Box(double x1, double y1, double z1, double x2, double y2, double z2,
                      float r, float g, float b, float fill) {}

    public record Line(double x1, double y1, double z1, double x2, double y2, double z2,
                       float r, float g, float b) {}

    private static long pausedUntil = 0;
    private static int failCount = 0;

    public static void render(WorldRenderContext ctx) {
        // if drawing ever fails, pause for 2 seconds and try again instead of switching ESP off for good
        if (System.currentTimeMillis() < pausedUntil) return;
        try {
            draw(ctx);
        } catch (Throwable t) {
            failCount++;
            pausedUntil = System.currentTimeMillis() + 2000;
            if (failCount <= 3) {
                ChunkFinderClient.LOGGER.error("ESP rendering failed, trying again in 2 seconds", t);
            }
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private static void draw(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        MatrixStack ms = ctx.matrixStack();
        if (ms == null) return;
        Vec3d cam = ctx.camera().getPos();

        List<Box> boxes = new ArrayList<>();
        List<Line> lines = new ArrayList<>();
        ChunkRenderer.collect(boxes, cam);
        PlayerEsp.collect(boxes, lines, cam);
        ChestEsp.collect(boxes, lines, cam);
        OreEsp.collect(boxes, lines, cam);
        BaseEsp.collect(boxes, lines, cam);
        if (boxes.isEmpty() && lines.isEmpty()) return;

        Matrix4f m = ms.peek().getPositionMatrix();

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        // filled faces
        boolean anyFill = false;
        for (Box b : boxes) {
            if (b.fill() > 0f) { anyFill = true; break; }
        }
        if (anyFill) {
            BufferBuilder qb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (Box b : boxes) {
                if (b.fill() > 0f) quads(qb, m, b, cam);
            }
            BufferRenderer.drawWithGlobalProgram(qb.end());
        }

        // outlines and tracers
        RenderSystem.lineWidth(2.0f);
        BufferBuilder lb = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        for (Box b : boxes) edges(lb, m, b, cam);
        for (Line l : lines) {
            vert(lb, m, l.x1() - cam.x, l.y1() - cam.y, l.z1() - cam.z, l.r(), l.g(), l.b(), 1f);
            vert(lb, m, l.x2() - cam.x, l.y2() - cam.y, l.z2() - cam.z, l.r(), l.g(), l.b(), 1f);
        }
        BufferRenderer.drawWithGlobalProgram(lb.end());

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void vert(BufferBuilder bb, Matrix4f m, double x, double y, double z,
                             float r, float g, float b, float a) {
        bb.vertex(m, (float) x, (float) y, (float) z).color(r, g, b, a);
    }

    private static void quads(BufferBuilder bb, Matrix4f m, Box b, Vec3d cam) {
        double x1 = b.x1() - cam.x, y1 = b.y1() - cam.y, z1 = b.z1() - cam.z;
        double x2 = b.x2() - cam.x, y2 = b.y2() - cam.y, z2 = b.z2() - cam.z;
        float r = b.r(), g = b.g(), bl = b.b(), a = b.fill();
        // bottom
        vert(bb, m, x1, y1, z1, r, g, bl, a); vert(bb, m, x2, y1, z1, r, g, bl, a);
        vert(bb, m, x2, y1, z2, r, g, bl, a); vert(bb, m, x1, y1, z2, r, g, bl, a);
        // top
        vert(bb, m, x1, y2, z1, r, g, bl, a); vert(bb, m, x1, y2, z2, r, g, bl, a);
        vert(bb, m, x2, y2, z2, r, g, bl, a); vert(bb, m, x2, y2, z1, r, g, bl, a);
        // north
        vert(bb, m, x1, y1, z1, r, g, bl, a); vert(bb, m, x1, y2, z1, r, g, bl, a);
        vert(bb, m, x2, y2, z1, r, g, bl, a); vert(bb, m, x2, y1, z1, r, g, bl, a);
        // south
        vert(bb, m, x1, y1, z2, r, g, bl, a); vert(bb, m, x2, y1, z2, r, g, bl, a);
        vert(bb, m, x2, y2, z2, r, g, bl, a); vert(bb, m, x1, y2, z2, r, g, bl, a);
        // west
        vert(bb, m, x1, y1, z1, r, g, bl, a); vert(bb, m, x1, y1, z2, r, g, bl, a);
        vert(bb, m, x1, y2, z2, r, g, bl, a); vert(bb, m, x1, y2, z1, r, g, bl, a);
        // east
        vert(bb, m, x2, y1, z1, r, g, bl, a); vert(bb, m, x2, y2, z1, r, g, bl, a);
        vert(bb, m, x2, y2, z2, r, g, bl, a); vert(bb, m, x2, y1, z2, r, g, bl, a);
    }

    private static void edges(BufferBuilder bb, Matrix4f m, Box b, Vec3d cam) {
        double x1 = b.x1() - cam.x, y1 = b.y1() - cam.y, z1 = b.z1() - cam.z;
        double x2 = b.x2() - cam.x, y2 = b.y2() - cam.y, z2 = b.z2() - cam.z;
        float r = b.r(), g = b.g(), bl = b.b();
        // bottom ring
        line(bb, m, x1, y1, z1, x2, y1, z1, r, g, bl);
        line(bb, m, x2, y1, z1, x2, y1, z2, r, g, bl);
        line(bb, m, x2, y1, z2, x1, y1, z2, r, g, bl);
        line(bb, m, x1, y1, z2, x1, y1, z1, r, g, bl);
        // top ring
        line(bb, m, x1, y2, z1, x2, y2, z1, r, g, bl);
        line(bb, m, x2, y2, z1, x2, y2, z2, r, g, bl);
        line(bb, m, x2, y2, z2, x1, y2, z2, r, g, bl);
        line(bb, m, x1, y2, z2, x1, y2, z1, r, g, bl);
        // verticals
        line(bb, m, x1, y1, z1, x1, y2, z1, r, g, bl);
        line(bb, m, x2, y1, z1, x2, y2, z1, r, g, bl);
        line(bb, m, x2, y1, z2, x2, y2, z2, r, g, bl);
        line(bb, m, x1, y1, z2, x1, y2, z2, r, g, bl);
    }

    private static void line(BufferBuilder bb, Matrix4f m, double ax, double ay, double az,
                             double bx, double by, double bz, float r, float g, float b) {
        vert(bb, m, ax, ay, az, r, g, b, 1f);
        vert(bb, m, bx, by, bz, r, g, b, 1f);
    }
}
