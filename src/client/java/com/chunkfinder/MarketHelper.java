package com.chunkfinder;

import com.chunkfinder.mixin.HandledScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Market Helper: reads the prices the Auction House and Orders screens already show you ("$ 184" under the item name),
 * remembers what it has seen this session, and marks listings that are cheap compared with what the same item sells
 * for (or what an order pays). It only reads text on your screen. It never clicks, buys or sells for you.
 */
public final class MarketHelper {
    private MarketHelper() {}

    private static final Pattern PRICE = Pattern.compile("\\$\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kKmMbBtT]?)");

    /** Unit prices seen on Auction House pages, per item. */
    private static final Map<String, List<Double>> ASKS = new HashMap<>();
    /** Unit prices seen on Orders pages (what buyers pay), per item. */
    private static final Map<String, List<Double>> BIDS = new HashMap<>();
    private static final Set<Integer> SEEN = new HashSet<>();

    private record Row(int sx, int sy, String key, String name, int count, double unit, double total) {}
    private record Deal(Row row, double refUnit, double profitUnit, double profitTotal, double pct, String source) {}

    public static void reset() {
        ASKS.clear();
        BIDS.clear();
        SEEN.clear();
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof HandledScreen<?> hs) {
                ScreenEvents.afterRender(screen).register((s, ctx, mx, my, td) -> {
                    try {
                        render(hs, ctx);
                    } catch (Throwable t) {
                        ChunkFinderClient.LOGGER.error("Market helper failed", t);
                    }
                });
            }
        });
    }

    private static double parsePrice(ItemStack st) {
        LoreComponent lore = st.get(DataComponentTypes.LORE);
        if (lore == null) return -1;
        for (Text line : lore.lines()) {
            Matcher m = PRICE.matcher(line.getString());
            if (!m.find()) continue;
            try {
                double v = Double.parseDouble(m.group(1).replace(",", ""));
                String suf = m.group(2).toLowerCase();
                if (suf.equals("k")) v *= 1e3;
                else if (suf.equals("m")) v *= 1e6;
                else if (suf.equals("b")) v *= 1e9;
                else if (suf.equals("t")) v *= 1e12;
                return v;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    private static String keyOf(ItemStack st) {
        String id = Registries.ITEM.getId(st.getItem()).getPath();
        // enchanted or renamed items are not the same product as the plain item, so they get their own key
        if (st.hasEnchantments() || st.contains(DataComponentTypes.CUSTOM_NAME)) return id + "|" + st.getName().getString();
        return id;
    }

    private static double median(List<Double> l) {
        List<Double> c = new ArrayList<>(l);
        Collections.sort(c);
        int n = c.size();
        return n % 2 == 1 ? c.get(n / 2) : (c.get(n / 2 - 1) + c.get(n / 2)) / 2.0;
    }

    private static void remember(Map<String, List<Double>> map, String key, double unit) {
        List<Double> l = map.computeIfAbsent(key, k -> new ArrayList<>());
        l.add(unit);
        if (l.size() > 80) l.remove(0);
    }

    public static String money(double v) {
        if (v >= 1e12) return fmt(v / 1e12) + "T";
        if (v >= 1e9) return fmt(v / 1e9) + "B";
        if (v >= 1e6) return fmt(v / 1e6) + "M";
        if (v >= 1e3) return fmt(v / 1e3) + "K";
        return fmt(v);
    }

    private static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.05) return String.valueOf((long) Math.rint(v));
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private static void render(HandledScreen<?> hs, DrawContext ctx) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.marketEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        String title = hs.getTitle().getString().toLowerCase();
        boolean orders = title.contains("order");
        boolean auction = title.contains("auction");
        if (!orders && !auction) return;

        HandledScreenAccessor acc = (HandledScreenAccessor) hs;
        int gx = acc.chunkfinder$getX(), gy = acc.chunkfinder$getY();

        List<Row> rows = new ArrayList<>();
        int sig = title.hashCode();
        for (Slot slot : hs.getScreenHandler().slots) {
            if (slot.inventory == mc.player.getInventory()) continue;
            ItemStack st = slot.getStack();
            if (st.isEmpty()) continue;
            double price = parsePrice(st);
            if (price <= 0) continue;
            int count = Math.max(1, st.getCount());
            double unit = cfg.marketPerItem ? price : price / count;
            double total = cfg.marketPerItem ? price * count : price;
            rows.add(new Row(gx + slot.x, gy + slot.y, keyOf(st), st.getName().getString(), count, unit, total));
            sig = 31 * sig + (keyOf(st).hashCode() * 17 + count * 7 + Double.hashCode(price));
        }
        if (rows.isEmpty()) return;

        // learn from each page once (the same page is drawn many times a second)
        if (SEEN.add(sig)) {
            for (Row r : rows) remember(orders ? BIDS : ASKS, r.key(), r.unit());
        }

        double fee = cfg.marketFee / 100.0;
        List<Deal> deals = new ArrayList<>();
        for (Row r : rows) {
            Deal d = judge(r, orders, fee, cfg);
            if (d != null) deals.add(d);
        }

        // frames and small labels on the good slots
        for (Deal d : deals) {
            Row r = d.row();
            boolean strong = d.pct() >= cfg.marketMinPercent * 2;
            int col = strong ? 0xFF3CFF6A : 0xFFE6F23C;
            ctx.fill(r.sx() - 1, r.sy() - 1, r.sx() + 17, r.sy(), col);
            ctx.fill(r.sx() - 1, r.sy() + 16, r.sx() + 17, r.sy() + 17, col);
            ctx.fill(r.sx() - 1, r.sy(), r.sx(), r.sy() + 16, col);
            ctx.fill(r.sx() + 16, r.sy(), r.sx() + 17, r.sy() + 16, col);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(r.sx(), r.sy(), 400f);
            ctx.getMatrices().scale(0.5f, 0.5f, 1f);
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal("+" + Math.round(d.pct()) + "%"), 1, 1, col);
            ctx.getMatrices().pop();
        }

        // best flips panel beside the window
        if (cfg.marketPanel) {
            deals.sort((a, b) -> Double.compare(b.profitTotal(), a.profitTotal()));
            List<String> lines = new ArrayList<>();
            lines.add("BEST FLIPS ON THIS PAGE");
            int n = Math.min(6, deals.size());
            for (int i = 0; i < n; i++) {
                Deal d = deals.get(i);
                lines.add(shorten(d.row().name(), 16) + " x" + d.row().count() + "  $" + money(d.row().total())
                    + "  +$" + money(d.profitTotal()) + " (" + d.source() + ")");
            }
            if (deals.isEmpty()) {
                int seen = 0;
                for (List<Double> l : ASKS.values()) seen += l.size();
                lines.add("none yet. Open more pages so I can learn prices (" + seen + " seen)");
            }
            int w = 0;
            for (String s : lines) w = Math.max(w, mc.textRenderer.getWidth(s));
            w += 12;
            int h = 6 + lines.size() * 10 + 4;
            int sw = mc.getWindow().getScaledWidth();
            int x = gx + acc.chunkfinder$getBackgroundWidth() + 8;
            if (x + w > sw) x = Math.max(2, gx - w - 8);
            int y = gy;
            HudPanel.draw(ctx, x, y, w, h);
            for (int i = 0; i < lines.size(); i++) {
                ctx.drawTextWithShadow(mc.textRenderer, Text.literal(lines.get(i)), x + 6, y + 4 + i * 10,
                    i == 0 ? 0xFFF4ECD6 : 0xFF7CFF8A);
            }
        }
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "..";
    }

    /** Decides whether a row is a good deal, or null. */
    private static Deal judge(Row r, boolean orders, double fee, ChunkFinderConfig cfg) {
        double ref;
        double profitUnit;
        String src;
        if (!orders) {
            // an Auction listing: worth buying when it is far below what the item sells for
            List<Double> bids = BIDS.get(r.key());
            double bid = 0;
            if (bids != null) for (double b : bids) bid = Math.max(bid, b);
            List<Double> asks = ASKS.get(r.key());
            if (bid > 0) {
                ref = bid * (1 - fee);
                src = "order";
            } else if (asks != null && asks.size() >= 3) {
                ref = median(asks) * (1 - fee);
                src = "avg";
            } else {
                return null;
            }
            profitUnit = ref - r.unit();
        } else {
            // an Order: worth filling when the cheapest Auction listing of the item is far below what it pays
            List<Double> asks = ASKS.get(r.key());
            if (asks == null || asks.isEmpty()) return null;
            double cheapest = Double.MAX_VALUE;
            for (double a : asks) cheapest = Math.min(cheapest, a);
            ref = r.unit() * (1 - fee);
            profitUnit = ref - cheapest;
            src = "AH";
        }
        if (ref <= 0 || profitUnit <= 0) return null;
        double pct = profitUnit / ref * 100.0;
        double total = profitUnit * r.count();
        if (pct < cfg.marketMinPercent || total < cfg.marketMinProfit) return null;
        return new Deal(r, ref, profitUnit, total, pct, src);
    }
}
