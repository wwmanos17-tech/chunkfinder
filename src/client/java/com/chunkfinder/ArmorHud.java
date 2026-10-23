package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Armor HUD: your armor (and optionally the items in your hands) with how much durability is left.
 * Drag it anywhere while the menu is open, scroll over it to make it bigger or smaller.
 */
public final class ArmorHud {
    private ArmorHud() {}

    /** Where it was last drawn: x, y, width, height (used for dragging it). */
    public static final int[] BOUNDS = new int[4];

    public static void render(DrawContext ctx, MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        BOUNDS[2] = 0;
        if (!cfg.armorEnabled || mc.player == null) return;

        List<ItemStack> items = new ArrayList<>();
        items.add(mc.player.getEquippedStack(EquipmentSlot.HEAD));
        items.add(mc.player.getEquippedStack(EquipmentSlot.CHEST));
        items.add(mc.player.getEquippedStack(EquipmentSlot.LEGS));
        items.add(mc.player.getEquippedStack(EquipmentSlot.FEET));
        if (cfg.armorHands) {
            items.add(mc.player.getEquippedStack(EquipmentSlot.MAINHAND));
            items.add(mc.player.getEquippedStack(EquipmentSlot.OFFHAND));
        }
        if (!cfg.armorShowEmpty) items.removeIf(ItemStack::isEmpty);
        if (items.isEmpty()) return;

        float scale = cfg.armorScale / 100f;
        boolean horiz = cfg.armorHorizontal;
        boolean text = cfg.armorText || cfg.armorNumber;
        // the text for each slot: percent and / or the real durability numbers (left / max)
        String[] labels = new String[items.size()];
        int[] colors = new int[items.size()];
        int textW = 0;
        for (int i = 0; i < labels.length; i++) {
            ItemStack s = items.get(i);
            labels[i] = "";
            if (text && s.isDamageable() && s.getMaxDamage() > 0) {
                int left = s.getMaxDamage() - s.getDamage();
                int pct = left * 100 / s.getMaxDamage();
                colors[i] = pct > 60 ? 0xFF7CFF8A : pct > 30 ? 0xFFFFD24D : 0xFFFF5A5A;
                StringBuilder sb = new StringBuilder();
                if (cfg.armorNumber) sb.append(left).append('/').append(s.getMaxDamage());
                if (cfg.armorText) sb.append(sb.length() > 0 ? "  " : "").append(pct).append('%');
                labels[i] = sb.toString();
                textW = Math.max(textW, mc.textRenderer.getWidth(labels[i]));
            }
        }
        // size of one slot, in unscaled units
        int cellW = horiz ? Math.max(18, textW) : 18 + (text ? textW + 2 : 0);
        int cellH = horiz ? 18 + (text ? 10 : 0) : 18;
        int n = items.size();
        int w = horiz ? cellW * n + 4 : cellW + 4;
        int h = horiz ? cellH + 4 : cellH * n + 4;

        int sw = (int) (w * scale), sh = (int) (h * scale);
        int x = Math.max(0, Math.min(cfg.armorX, mc.getWindow().getScaledWidth() - sw));
        int y = Math.max(0, Math.min(cfg.armorY, mc.getWindow().getScaledHeight() - sh));
        BOUNDS[0] = x; BOUNDS[1] = y; BOUNDS[2] = sw; BOUNDS[3] = sh;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0f);
        ctx.getMatrices().scale(scale, scale, 1f);
        if (cfg.armorBackground) {
            ctx.fill(0, 0, w, h, 0xB0100E08);
            ctx.fill(0, 0, 2, h, 0xFFE0A526);
        }
        for (int i = 0; i < n; i++) {
            ItemStack s = items.get(i);
            int cx = 2 + (horiz ? i * cellW + (cellW - 16) / 2 : 1);
            int cy = 2 + (horiz ? 1 : i * cellH + 1);
            if (!s.isEmpty()) {
                ctx.drawItem(s, cx, cy);
                if (cfg.armorBar && s.isDamageable()) ctx.drawStackOverlay(mc.textRenderer, s, cx, cy);
                String t = labels[i];
                if (!t.isEmpty()) {
                    if (horiz) {
                        ctx.drawTextWithShadow(mc.textRenderer, Text.literal(t),
                            2 + i * cellW + (cellW - mc.textRenderer.getWidth(t)) / 2, cy + 18, colors[i]);
                    } else {
                        ctx.drawTextWithShadow(mc.textRenderer, Text.literal(t), cx + 20, cy + 4, colors[i]);
                    }
                }
            }
        }
        ctx.getMatrices().pop();
    }
}
