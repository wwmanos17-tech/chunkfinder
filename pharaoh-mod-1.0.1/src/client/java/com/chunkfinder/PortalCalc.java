package com.chunkfinder;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Portal Calc: works out where a Nether portal has to be so that it links to the one on the other side.
 * Overworld to Nether divides X and Z by 8, Nether to Overworld multiplies by 8. When you step through a portal,
 * the game looks for the nearest existing portal inside a square around the converted spot (16 blocks wide each way in
 * the Nether, 128 in the Overworld); only if it finds none does it build a new one.
 * This remembers the portals you walk through (or save with a key), tells you which ones link, and draws the link
 * zone in the world. It only does arithmetic on your own coordinates and block positions your game already has.
 */
public final class PortalCalc {
    private PortalCalc() {}

    public static final int[] BOUNDS = new int[4];

    private static final int OW = 0, NETHER = 1;
    private static final int NETHER_RADIUS = 16, OVERWORLD_RADIUS = 128;

    // tracking for Auto Learn
    private static int lastDim = -1;
    private static BlockPos lastPortalBlock = null;
    private static long lastPortalTick = -1000;
    private static long tickNo = 0;
    private static int pendingArrival = 0;
    private static int arrivalDim = -1;

    // ---- the place you typed in with /pgoto ----
    private static boolean targetOn = false;
    private static int targetOwX, targetOwZ;      // always kept as Overworld coordinates

    public static void clearTarget() { targetOn = false; }

    /** Points the guide at an Overworld X, Z (used by RTP Hunter). */
    public static void guideTo(int owX, int owZ) { setTarget(owX, owZ); }

    private static void setTarget(int owX, int owZ) {
        targetOwX = owX;
        targetOwZ = owZ;
        targetOn = true;
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.portalEnabled) {
            cfg.portalEnabled = true;
            ChunkFinderConfig.save();
        }
    }

    /** /pgoto X Z (Overworld coordinates), /pgoto nether X Z (Nether coordinates), /pgoto off. Handled on your own computer only. */
    public static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("pgoto")
                .then(ClientCommandManager.literal("off").executes(ctx -> {
                    clearTarget();
                    ctx.getSource().sendFeedback(Text.literal("[Pharaoh] Target cleared"));
                    return 1;
                }))
                .then(ClientCommandManager.literal("nether")
                    .then(ClientCommandManager.argument("x", IntegerArgumentType.integer(-3750000, 3750000))
                        .then(ClientCommandManager.argument("z", IntegerArgumentType.integer(-3750000, 3750000)).executes(ctx -> {
                            int x = IntegerArgumentType.getInteger(ctx, "x") * 8, z = IntegerArgumentType.getInteger(ctx, "z") * 8;
                            setTarget(x, z);
                            ctx.getSource().sendFeedback(Text.literal("[Pharaoh] Target set: Nether " + (x / 8) + ", " + (z / 8)
                                + " = Overworld " + x + ", " + z));
                            return 1;
                        }))))
                .then(ClientCommandManager.argument("x", IntegerArgumentType.integer(-30000000, 30000000))
                    .then(ClientCommandManager.argument("z", IntegerArgumentType.integer(-30000000, 30000000)).executes(ctx -> {
                        int x = IntegerArgumentType.getInteger(ctx, "x"), z = IntegerArgumentType.getInteger(ctx, "z");
                        setTarget(x, z);
                        ctx.getSource().sendFeedback(Text.literal("[Pharaoh] Target set: Overworld " + x + ", " + z
                            + " = Nether " + Math.floorDiv(x, 8) + ", " + Math.floorDiv(z, 8)));
                        return 1;
                    }))));
        });
    }

    private static String turnText(MinecraftClient mc, double dx, double dz) {
        double rel = MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(-dx, dz)) - mc.player.getYaw());
        long a = Math.round(Math.abs(rel));
        if (a < 8) return "straight ahead";
        if (a > 172) return "behind you";
        return "turn " + (rel > 0 ? "right " : "left ") + a + " deg";
    }

    private static int dimOf(MinecraftClient mc) {
        if (mc.world == null) return -1;
        if (mc.world.getRegistryKey() == World.OVERWORLD) return OW;
        if (mc.world.getRegistryKey() == World.NETHER) return NETHER;
        return -1;
    }

    private static String serverKey(MinecraftClient mc) {
        if (mc.getCurrentServerEntry() != null && mc.getCurrentServerEntry().address != null) {
            return mc.getCurrentServerEntry().address.toLowerCase();
        }
        return "singleplayer";
    }

    private static List<ChunkFinderConfig.PortalSave> mine(MinecraftClient mc, int dim) {
        List<ChunkFinderConfig.PortalSave> out = new ArrayList<>();
        String key = serverKey(mc);
        for (ChunkFinderConfig.PortalSave p : ChunkFinderConfig.INSTANCE.portalSaved) {
            if (p.dim == dim && key.equals(p.server)) out.add(p);
        }
        return out;
    }

    private static int radiusFor(int toDim) {
        return toDim == NETHER ? NETHER_RADIUS : OVERWORLD_RADIUS;
    }

    private static double convert(double v, int fromDim) {
        return fromDim == OW ? Math.floor(v / 8.0) : Math.floor(v * 8.0);
    }

    /** The saved portal the game would pick for a trip from (x, z) in fromDim, or null. */
    private static ChunkFinderConfig.PortalSave linkFor(MinecraftClient mc, int fromDim, double x, double z) {
        int to = 1 - fromDim;
        double tx = convert(x, fromDim), tz = convert(z, fromDim);
        int r = radiusFor(to);
        ChunkFinderConfig.PortalSave best = null;
        double bestD = Double.MAX_VALUE;
        for (ChunkFinderConfig.PortalSave p : mine(mc, to)) {
            if (Math.max(Math.abs(p.x - tx), Math.abs(p.z - tz)) > r) continue;
            double d = (p.x - tx) * (p.x - tx) + (p.z - tz) * (p.z - tz);
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    private static ChunkFinderConfig.PortalSave nearestAny(MinecraftClient mc, int dim, double tx, double tz) {
        ChunkFinderConfig.PortalSave best = null;
        double bestD = Double.MAX_VALUE;
        for (ChunkFinderConfig.PortalSave p : mine(mc, dim)) {
            double d = (p.x - tx) * (p.x - tx) + (p.z - tz) * (p.z - tz);
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    private static void add(MinecraftClient mc, int dim, BlockPos pos) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        for (ChunkFinderConfig.PortalSave p : mine(mc, dim)) {
            if (Math.abs(p.x - pos.getX()) <= 6 && Math.abs(p.z - pos.getZ()) <= 6 && Math.abs(p.y - pos.getY()) <= 8) {
                p.x = pos.getX(); p.y = pos.getY(); p.z = pos.getZ();   // same portal, refresh its position
                ChunkFinderConfig.save();
                return;
            }
        }
        ChunkFinderConfig.PortalSave s = new ChunkFinderConfig.PortalSave();
        s.dim = dim; s.x = pos.getX(); s.y = pos.getY(); s.z = pos.getZ(); s.server = serverKey(mc);
        cfg.portalSaved.add(s);
        // keep at most 30 per server
        List<ChunkFinderConfig.PortalSave> all = mine(mc, 0);
        all.addAll(mine(mc, 1));
        while (all.size() > 30) {
            cfg.portalSaved.remove(all.remove(0));
        }
        ChunkFinderConfig.save();
    }

    public static void clearServer() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        String key = serverKey(mc);
        ChunkFinderConfig.INSTANCE.portalSaved.removeIf(p -> key.equals(p.server));
        ChunkFinderConfig.save();
    }

    /** Key: save the portal you stand in (or your own position). */
    public static void saveHere(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        int dim = dimOf(mc);
        if (dim < 0) {
            mc.player.sendMessage(Text.literal("Portal Calc works in the Overworld and the Nether"), true);
            return;
        }
        BlockPos pos = portalBlockNear(mc);
        if (pos == null) pos = mc.player.getBlockPos();
        add(mc, dim, pos);
        mc.player.sendMessage(Text.literal("Portal saved: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
            + (dim == OW ? " (Overworld)" : " (Nether)")), true);
    }

    /** Key: forget the saved portal nearest to you in this dimension. */
    public static void deleteNearest(MinecraftClient mc) {
        if (mc.player == null) return;
        int dim = dimOf(mc);
        if (dim < 0) return;
        ChunkFinderConfig.PortalSave p = nearestAny(mc, dim, mc.player.getX(), mc.player.getZ());
        if (p == null) {
            mc.player.sendMessage(Text.literal("No saved portal here to remove"), true);
            return;
        }
        ChunkFinderConfig.INSTANCE.portalSaved.remove(p);
        ChunkFinderConfig.save();
        mc.player.sendMessage(Text.literal("Removed saved portal at " + p.x + ", " + p.y + ", " + p.z), true);
    }

    private static BlockPos portalBlockNear(MinecraftClient mc) {
        BlockPos feet = mc.player.getBlockPos();
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos p = feet.up(dy);
            if (mc.world.getBlockState(p).isOf(Blocks.NETHER_PORTAL)) return p;
        }
        return null;
    }

    public static void tick(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.portalEnabled || mc.world == null || mc.player == null) {
            lastDim = -1;
            return;
        }
        tickNo++;
        int dim = dimOf(mc);
        if (dim != lastDim) {
            // you went through something: if you were standing in a portal a moment ago, that is a portal pair
            if (cfg.portalAuto && lastDim >= 0 && dim >= 0 && lastDim != dim
                && lastPortalBlock != null && tickNo - lastPortalTick < 160) {
                add(mc, lastDim, lastPortalBlock);
                pendingArrival = 60;       // look for the portal you arrive in over the next 3 seconds
                arrivalDim = dim;
            } else {
                pendingArrival = 0;
            }
            lastPortalBlock = null;
            lastDim = dim;
        }
        if (dim < 0) return;
        BlockPos in = portalBlockNear(mc);
        if (in != null) {
            lastPortalBlock = in;
            lastPortalTick = tickNo;
            if (pendingArrival > 0 && arrivalDim == dim) {
                add(mc, dim, in);
                pendingArrival = 0;
            }
        }
        if (pendingArrival > 0) pendingArrival--;
    }

    private static String xz(double x, double z) {
        return (long) x + ", " + (long) z;
    }

    public static void render(DrawContext ctx, MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        BOUNDS[2] = 0;
        if (!cfg.portalEnabled || mc.player == null || mc.world == null) return;
        int dim = dimOf(mc);
        if (dim < 0) return;
        int to = 1 - dim;
        String here = dim == OW ? "Overworld" : "Nether";
        String there = to == OW ? "Overworld" : "Nether";
        String op = dim == OW ? "/ 8" : "x 8";
        int r = radiusFor(to);

        List<String> lines = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        lines.add("PORTAL CALC"); cols.add(0xFFE0A526);

        double px = mc.player.getX(), pz = mc.player.getZ();
        if (targetOn) {
            int nx = Math.floorDiv(targetOwX, 8), nz = Math.floorDiv(targetOwZ, 8);
            lines.add("TARGET: Overworld " + targetOwX + ", " + targetOwZ + "  (Nether " + nx + ", " + nz + ")"); cols.add(0xFF7CFF8A);
            if (dim == NETHER) {
                double dx = nx + 0.5 - px, dz = nz + 0.5 - pz;
                lines.add("Go to " + nx + ", " + nz + " in the Nether: " + Math.round(Math.sqrt(dx * dx + dz * dz)) + " blocks, " + turnText(mc, dx, dz));
                cols.add(0xFFF4ECD6);
                lines.add("Build a portal there, go through, and you arrive near the target"); cols.add(0xFF9A9A9A);
            } else {
                double dx = targetOwX + 0.5 - px, dz = targetOwZ + 0.5 - pz;
                lines.add("Walk: " + Math.round(Math.sqrt(dx * dx + dz * dz)) + " blocks, " + turnText(mc, dx, dz)); cols.add(0xFFF4ECD6);
                lines.add("Faster: Nether " + nx + ", " + nz + " (build a portal here, travel 1/8 of the way)"); cols.add(0xFF9A9A9A);
            }
            addStatus(mc, lines, cols, NETHER, nx + 0.5, nz + 0.5, OVERWORLD_RADIUS, "Overworld");
            lines.add("  (/pgoto off to clear)"); cols.add(0xFF6A6A6A);
        }
        lines.add("You: " + xz(Math.floor(px), Math.floor(pz)) + "  (" + here + ")"); cols.add(0xFFF4ECD6);
        lines.add(there + " side: " + xz(convert(px, dim), convert(pz, dim)) + "  (" + op + ")"); cols.add(0xFFF4ECD6);
        addStatus(mc, lines, cols, dim, px, pz, r, there);

        if (cfg.portalLook) {
            HitResult hit = mc.player.raycast(80.0, 1.0f, false);
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos bp = ((BlockHitResult) hit).getBlockPos();
                lines.add("Looking at: " + bp.getX() + ", " + bp.getZ() + " -> " + there + " "
                    + xz(convert(bp.getX() + 0.5, dim), convert(bp.getZ() + 0.5, dim))); cols.add(0xFF8FD3FF);
                addStatus(mc, lines, cols, dim, bp.getX() + 0.5, bp.getZ() + 0.5, r, there);
            }
        }

        List<ChunkFinderConfig.PortalSave> saved = mine(mc, dim);
        saved.sort((a, b) -> Double.compare(d2(a, px, pz), d2(b, px, pz)));
        lines.add("Saved: " + saved.size() + " " + here + ", " + mine(mc, to).size() + " " + there); cols.add(0xFF9A9A9A);
        int shown = 0;
        for (ChunkFinderConfig.PortalSave p : saved) {
            if (shown++ >= 3) break;
            ChunkFinderConfig.PortalSave l = linkFor(mc, dim, p.x + 0.5, p.z + 0.5);
            String t = p.x + ", " + p.y + ", " + p.z + "  ->  ";
            if (l != null) { lines.add(t + l.x + ", " + l.z + "  OK"); cols.add(0xFF7CFF8A); }
            else { lines.add(t + "no match"); cols.add(0xFFFF8A5C); }
        }
        if (saved.isEmpty() && mine(mc, to).isEmpty()) {
            lines.add("Walk through a portal and it learns both ends"); cols.add(0xFF9A9A9A);
        }

        int w = 0;
        for (String s : lines) w = Math.max(w, PFont.w(mc, s));
        w += 14;
        int h = 6 + lines.size() * 10 + 4;
        int x = Math.max(0, Math.min(cfg.portalX, mc.getWindow().getScaledWidth() - w));
        int y = Math.max(0, Math.min(cfg.portalY, mc.getWindow().getScaledHeight() - h));
        BOUNDS[0] = x; BOUNDS[1] = y; BOUNDS[2] = w; BOUNDS[3] = h;
        HudPanel.draw(ctx, x, y, w, h);
        for (int i = 0; i < lines.size(); i++) {
            ctx.drawTextWithShadow(mc.textRenderer, PFont.t(lines.get(i)), x + 7, y + 4 + i * 10, cols.get(i));
        }
    }

    private static double d2(ChunkFinderConfig.PortalSave p, double x, double z) {
        return (p.x - x) * (p.x - x) + (p.z - z) * (p.z - z);
    }

    private static void addStatus(MinecraftClient mc, List<String> lines, List<Integer> cols, int dim, double x, double z, int r, String there) {
        int to = 1 - dim;
        ChunkFinderConfig.PortalSave link = linkFor(mc, dim, x, z);
        double tx = convert(x, dim), tz = convert(z, dim);
        if (link != null) {
            long d = Math.round(Math.sqrt(d2(link, tx, tz)));
            lines.add("Links to " + there + " portal " + link.x + ", " + link.y + ", " + link.z + "  (off by " + d + ")");
            cols.add(0xFF7CFF8A);
        } else {
            ChunkFinderConfig.PortalSave near = nearestAny(mc, to, tx, tz);
            if (near != null) {
                long d = Math.round(Math.sqrt(d2(near, tx, tz)));
                lines.add("No link: nearest saved is " + d + " away (max " + r + "). It would build a new one"); cols.add(0xFFFF8A5C);
            } else {
                lines.add("Nothing saved there: it builds a new portal near " + (long) tx + ", " + (long) tz); cols.add(0xFFFFC94D);
            }
        }
    }

    public static void collect(List<EspRenderer.Box> boxes, List<EspRenderer.Line> lines, Vec3d cam) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!cfg.portalEnabled || !cfg.portalMarks || mc == null || mc.player == null || mc.world == null) return;
        int dim = dimOf(mc);
        if (dim < 0) return;
        int to = 1 - dim;
        // the place you typed in with /pgoto: a tall beam, a box at your height and a line from you
        if (targetOn) {
            double gx, gz;
            if (dim == NETHER) { gx = Math.floorDiv(targetOwX, 8) + 0.5; gz = Math.floorDiv(targetOwZ, 8) + 0.5; }
            else { gx = targetOwX + 0.5; gz = targetOwZ + 0.5; }
            double py = mc.player.getY();
            double bot = Math.max(mc.world.getBottomY(), py - 80), top = Math.min(mc.world.getBottomY() + mc.world.getHeight(), py + 160);
            lines.add(new EspRenderer.Line(gx, bot, gz, gx, top, gz, 0.30f, 1f, 0.45f));
            boxes.add(new EspRenderer.Box(gx - 0.5, py - 1, gz - 0.5, gx + 0.5, py + 2, gz + 0.5, 0.30f, 1f, 0.45f, 0.30f));
            Vec3d from = Proj.tracerStart(mc);
            lines.add(new EspRenderer.Line(from.x, from.y, from.z, gx, py + 1, gz, 0.30f, 1f, 0.45f));
        }
        // saved portals in this dimension
        for (ChunkFinderConfig.PortalSave p : mine(mc, dim)) {
            if (Math.abs(p.x - cam.x) > 400 || Math.abs(p.z - cam.z) > 400) continue;
            boxes.add(new EspRenderer.Box(p.x, p.y, p.z, p.x + 1, p.y + 3, p.z + 1, 0.70f, 0.40f, 1f, 0.20f));
        }
        // for every portal saved on the other side: the exact spot it converts to here, and the zone that links to it
        int r = radiusFor(dim);   // a portal built in this dimension links when it is inside this zone
        double y = Math.floor(mc.player.getY()) + 0.05;
        for (ChunkFinderConfig.PortalSave p : mine(mc, to)) {
            double cx = convert(p.x + 0.5, to), cz = convert(p.z + 0.5, to);
            if (Math.abs(cx - cam.x) > 400 || Math.abs(cz - cam.z) > 400) continue;
            boolean linked = false;
            for (ChunkFinderConfig.PortalSave q : mine(mc, dim)) {
                if (Math.max(Math.abs(q.x - cx), Math.abs(q.z - cz)) <= r) { linked = true; break; }
            }
            float rr = linked ? 0.30f : 1f, gg = linked ? 1f : 0.70f, bb = linked ? 0.45f : 0.20f;
            boxes.add(new EspRenderer.Box(cx, y - 1, cz, cx + 1, y + 2, cz + 1, rr, gg, bb, 0.25f));
            double a = cx - r, b = cx + r + 1, c = cz - r, d = cz + r + 1;
            lines.add(new EspRenderer.Line(a, y, c, b, y, c, rr, gg, bb));
            lines.add(new EspRenderer.Line(b, y, c, b, y, d, rr, gg, bb));
            lines.add(new EspRenderer.Line(b, y, d, a, y, d, rr, gg, bb));
            lines.add(new EspRenderer.Line(a, y, d, a, y, c, rr, gg, bb));
        }
    }
}
