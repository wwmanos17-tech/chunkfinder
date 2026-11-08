package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.entity.vehicle.VehicleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Entity ESP: boxes through walls around things bases like to hide stuff in or on:
 * item frames that hold an item, armor stands, chest boats and storage minecarts, TNT minecarts and end crystals.
 * Only uses entities the server has already sent to your game.
 */
public class EntityEsp {
    public record Label(double x, double y, double z, String text, int rgb, int dist) {}

    public static final List<Label> LABELS = new ArrayList<>();

    public static void clear() {
        LABELS.clear();
    }

    public static void collect(List<EspRenderer.Box> boxes, List<EspRenderer.Line> lines, Vec3d cam) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        LABELS.clear();
        if (!cfg.entityEspEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        double r2 = (double) cfg.entityEspRange * cfg.entityEspRange;
        boolean chestEspHandlesMinecarts = cfg.chestEspEnabled && cfg.espMinecart;
        int count = 0;

        for (Entity e : mc.world.getEntities()) {
            if (e.squaredDistanceTo(cam) > r2) continue;

            String label = null;
            float r = 1f, g = 1f, b = 1f;
            int rgb = 0xFFFFFF;

            if (e instanceof ItemFrameEntity frame) {
                if (!cfg.entityEspFrames) continue;
                ItemStack held = frame.getHeldItemStack();
                if (held.isEmpty()) continue;
                r = 0.3f; g = 0.9f; b = 1f; rgb = 0x4DE6FF;
                label = "Frame: " + held.getName().getString();
            } else if (e instanceof ArmorStandEntity stand) {
                if (!cfg.entityEspStands) continue;
                int pieces = 0;
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    if (!stand.getEquippedStack(slot).isEmpty()) pieces++;
                }
                r = 1f; g = 0.6f; b = 0.15f; rgb = 0xFF9926;
                label = pieces > 0 ? "Armor Stand (" + pieces + " items)" : "Armor Stand";
            } else if (e instanceof TntMinecartEntity) {
                if (!cfg.entityEspVehicles) continue;
                r = 1f; g = 0.2f; b = 0.2f; rgb = 0xFF3333;
                label = "TNT Minecart";
            } else if (e instanceof VehicleInventory) {
                if (!cfg.entityEspVehicles) continue;
                if (e instanceof ChestMinecartEntity && chestEspHandlesMinecarts) continue;
                r = 1f; g = 0.85f; b = 0.2f; rgb = 0xFFD933;
                label = e.getType().getName().getString();
            } else if (e instanceof EndCrystalEntity) {
                if (!cfg.entityEspCrystals) continue;
                r = 1f; g = 0.3f; b = 0.9f; rgb = 0xFF4DE6;
                label = "End Crystal";
            } else {
                continue;
            }

            if (++count > 300) break;
            Box bb = e.getBoundingBox();
            boxes.add(new EspRenderer.Box(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ,
                r, g, b, cfg.entityEspFill ? 0.14f : 0f));
            if (cfg.entityEspLabels && label != null) {
                LABELS.add(new Label((bb.minX + bb.maxX) / 2, bb.maxY + 0.4, (bb.minZ + bb.maxZ) / 2,
                    label, rgb, (int) Math.sqrt(e.squaredDistanceTo(cam))));
            }
        }
    }
}
