package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Item ESP: boxes through walls around items lying on the ground. Valuable drops get a bright colour and a label,
 * everyday items can be shown too. Only uses items the server has already sent to your game.
 */
public class ItemEsp {
    public record Label(double x, double y, double z, String text, int rgb, int dist) {}

    public static final List<Label> LABELS = new ArrayList<>();
    private static final Set<Integer> ALERTED = new HashSet<>();

    public static void clear() {
        LABELS.clear();
        ALERTED.clear();
    }

    private static final String[] RARE = {
        "netherite", "elytra", "totem", "enchanted_golden_apple", "nether_star", "dragon_egg", "beacon",
        "shulker_box", "ancient_debris", "spawner", "heavy_core", "mace", "trident", "dragon_head", "wither_skeleton_skull"
    };
    private static final String[] GOOD = {
        "diamond", "emerald", "golden_apple", "enchanted_book", "ender_chest", "heart_of_the_sea",
        "experience_bottle", "end_crystal", "respawn_anchor", "gold_block", "ender_pearl", "sponge", "wind_charge"
    };

    /** 2 = rare, 1 = good, 0 = normal. */
    private static int tier(ItemStack s, String path) {
        for (String r : RARE) if (path.contains(r)) return 2;
        for (String g : GOOD) if (path.contains(g)) return 1;
        if (s.hasEnchantments()) return 1;
        return 0;
    }

    public static void collect(List<EspRenderer.Box> boxes, List<EspRenderer.Line> lines, Vec3d cam) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        LABELS.clear();
        if (!cfg.itemEspEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        double range = cfg.itemEspRange;
        double r2 = range * range;
        int count = 0;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof ItemEntity ie)) continue;
            if (e.squaredDistanceTo(cam) > r2) continue;
            ItemStack stack = ie.getStack();
            if (stack.isEmpty()) continue;
            String path = Registries.ITEM.getId(stack.getItem()).getPath();
            int t = tier(stack, path);
            if (t == 0 && !cfg.itemEspAll) continue;
            if (++count > 400) break;

            float r, g, b;
            int rgb;
            if (t == 2) { r = 1f; g = 0.25f; b = 0.45f; rgb = 0xFF4073; }
            else if (t == 1) { r = 1f; g = 0.82f; b = 0.2f; rgb = 0xFFD133; }
            else { r = 0.75f; g = 0.9f; b = 1f; rgb = 0xBFE6FF; }

            Vec3d p = ie.getLerpedPos(mc.getRenderTickCounter().getTickDelta(false));
            Box box = new Box(p.x - 0.28, p.y, p.z - 0.28, p.x + 0.28, p.y + 0.5, p.z + 0.28);
            boxes.add(new EspRenderer.Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                r, g, b, cfg.itemEspFill ? 0.18f : 0f));
            if (t == 2) {
                lines.add(new EspRenderer.Line(p.x, p.y + 0.5, p.z, p.x, p.y + 12, p.z, r, g, b));
            }
            if (cfg.itemEspTracers && t >= 1) {
                Vec3d s = Proj.tracerStart(mc);
                lines.add(new EspRenderer.Line(s.x, s.y, s.z, p.x, p.y + 0.25, p.z, r, g, b));
            }
            if (cfg.itemEspLabels && (t >= 1 || cfg.itemEspAll)) {
                String name = stack.getName().getString();
                if (stack.getCount() > 1) name += " x" + stack.getCount();
                LABELS.add(new Label(p.x, p.y + 0.9, p.z, name, rgb, (int) Math.sqrt(e.squaredDistanceTo(cam))));
            }
            if (t == 2 && cfg.itemEspAlert && ALERTED.add(ie.getId())) {
                String name = stack.getName().getString();
                if (stack.getCount() > 1) name += " x" + stack.getCount();
                mc.player.sendMessage(Text.literal("[Pharaoh] Rare item on the ground: " + name + " at X " + (int) p.x
                    + ", Y " + (int) p.y + ", Z " + (int) p.z), false);
            }
        }
    }
}
