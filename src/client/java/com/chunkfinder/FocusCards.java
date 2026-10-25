package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Focus Cards: instead of writing a text label over every find at once, each find is offered here and only the one
 * closest to your crosshair gets a small info card (title, distance, headline, details). Everything else stays a plain
 * box in the world, so the screen stays clear.
 */
public final class FocusCards {
    private FocusCards() {}

    private record Item(String key, float sx, float sy, String tag, String dist, String headline, String right, String detail, String foot, int rgb) {}

    private static final List<Item> ITEMS = new ArrayList<>();
    private static String lastKey = null;
    private static long lastAt = 0;

    public static boolean on() {
        return ChunkFinderConfig.INSTANCE.focusCards;
    }

    public static void begin() {
        ITEMS.clear();
    }

    public static void offer(String key, float[] screen, String tag, int dist, String headline, String right, String detail, String foot, int rgb) {
        if (screen == null) return;
        ITEMS.add(new Item(key, screen[0], screen[1], tag, dist + "m", headline, right == null ? "" : right,
            detail == null ? "" : detail, foot == null ? "" : foot, rgb & 0xFFFFFF));
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "..";
    }

    public static void draw(DrawContext ctx, MinecraftClient mc) {
        if (ITEMS.isEmpty()) return;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        double cx = sw / 2.0, cy = sh / 2.0;
        Item best = null, held = null;
        double bestD = Double.MAX_VALUE, heldD = Double.MAX_VALUE;
        long now = System.currentTimeMillis();
        for (Item it : ITEMS) {
            double d = Math.hypot(it.sx() - cx, it.sy() - cy);
            if (d < bestD) { bestD = d; best = it; }
            if (it.key().equals(lastKey) && now - lastAt < 600) { held = it; heldD = d; }
        }
        // stay on the card you were looking at unless another one is clearly closer to the crosshair
        Item pick = (held != null && heldD < bestD + 40) ? held : best;
        double pickD = pick == best ? bestD : heldD;
        if (pick == null || pickD > 170) return;
        lastKey = pick.key();
        lastAt = now;

        int pad = 8;
        int w1 = mc.textRenderer.getWidth(pick.tag()) * 3 / 4 + mc.textRenderer.getWidth(pick.dist()) + 18;
        int w2 = (int) (mc.textRenderer.getWidth(pick.headline()) * 1.25) + mc.textRenderer.getWidth(pick.right()) + 18;
        int w3 = mc.textRenderer.getWidth(pick.detail());
        int w4 = mc.textRenderer.getWidth(pick.foot());
        int w = Math.min(280, Math.max(Math.max(w1, w2), Math.max(w3, w4)) + pad * 2);
        int h = 8 + 10 + 4 + 14 + (pick.detail().isEmpty() ? 0 : 14) + (pick.foot().isEmpty() ? 0 : 14) + 4;
        int x = (int) pick.sx() + 14;
        if (x + w > sw - 4) x = (int) pick.sx() - 14 - w;
        x = Math.max(4, x);
        int y = Math.max(4, Math.min(sh - h - 4, (int) pick.sy() - h / 2));
        int accent = 0xFF000000 | pick.rgb();

        // dot on the find and a short line to the card
        int px = (int) pick.sx(), py = (int) pick.sy();
        ctx.fill(px - 2, py - 2, px + 3, py + 3, accent);
        int lx = x > px ? x - 1 : x + w;
        ctx.fill(Math.min(px + 3, lx), py, Math.max(px - 2, lx), py + 1, accent & 0xAAFFFFFF);

        HudPanel.draw(ctx, x, y, w, h, accent);
        int ty = y + 6;
        // tag (small) and distance (right)
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + pad + 2, ty, 0f);
        ctx.getMatrices().scale(0.75f, 0.75f, 1f);
        ctx.drawTextWithShadow(mc.textRenderer, Text.literal(pick.tag()), 0, 0, 0xFF8C93A8);
        ctx.getMatrices().pop();
        ctx.drawTextWithShadow(mc.textRenderer, Text.literal(pick.dist()), x + w - pad - mc.textRenderer.getWidth(pick.dist()), ty, 0xFF9AA3BC);
        ty += 14;
        // headline (bigger) and right text
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + pad + 2, ty, 0f);
        ctx.getMatrices().scale(1.25f, 1.25f, 1f);
        ctx.drawTextWithShadow(mc.textRenderer, Text.literal(cut(pick.headline(), 26)), 0, 0, 0xFFF4F6FF);
        ctx.getMatrices().pop();
        if (!pick.right().isEmpty()) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x + w - pad - mc.textRenderer.getWidth(pick.right()) * 0.85f, ty + 2, 0f);
            ctx.getMatrices().scale(0.85f, 0.85f, 1f);
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal(pick.right()), 0, 0, 0xFFB4BCD2);
            ctx.getMatrices().pop();
        }
        ty += 16;
        if (!pick.detail().isEmpty()) {
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal(cut(pick.detail(), 44)), x + pad + 2, ty, 0xFFDDE2F2);
            ty += 14;
        }
        if (!pick.foot().isEmpty()) {
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal(cut(pick.foot(), 46)), x + pad + 2, ty, 0xFF7C849C);
        }
    }
}
