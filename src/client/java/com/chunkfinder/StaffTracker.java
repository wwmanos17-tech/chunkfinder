package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Shows which staff are online. A player counts as staff if their name is in the config list
 * (staffNames), or if their rank tag in the player list contains one of the staffKeywords
 * (admin, mod, helper...). Alerts in chat when staff join or leave.
 */
public class StaffTracker {
    public record Staff(String name, SkinTextures skin) {}

    private static final Map<UUID, Staff> CURRENT = new LinkedHashMap<>();
    private static boolean primed = false;
    /** Where the list was last drawn: x, y, width, height (used for dragging it). */
    public static final int[] BOUNDS = new int[4];
    private static int timer = 0;

    public static void clear() {
        CURRENT.clear();
        primed = false;
    }

    /** The rank tag of a player: scoreboard team name, prefix and suffix, plus their tab list name without their own name. */
    private static String tagOf(MinecraftClient mc, PlayerListEntry e) {
        String name = e.getProfile().getName();
        String lower = name.toLowerCase(Locale.ROOT);
        StringBuilder tag = new StringBuilder();
        if (mc.world != null) {
            Team team = mc.world.getScoreboard().getScoreHolderTeam(name);
            if (team != null) {
                tag.append(team.getName()).append(' ');
                tag.append(team.getPrefix().getString()).append(' ');
                tag.append(team.getSuffix().getString()).append(' ');
            }
        }
        Text display = e.getDisplayName();
        if (display != null) tag.append(display.getString().toLowerCase(Locale.ROOT).replace(lower, " "));
        return tag.toString().toLowerCase(Locale.ROOT).trim();
    }

    /** True if the word shows up as a whole word (so "mod" matches "[Mod]" but not "modern"). */
    private static boolean hasWord(String text, String word) {
        int from = 0;
        while (true) {
            int i = text.indexOf(word, from);
            if (i < 0) return false;
            boolean left = i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1));
            int end = i + word.length();
            boolean right = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (left && right) return true;
            from = i + 1;
        }
    }

    private static boolean isStaff(MinecraftClient mc, PlayerListEntry e, ChunkFinderConfig cfg) {
        String name = e.getProfile().getName();
        for (String n : cfg.staffNames) {
            if (n != null && n.trim().equalsIgnoreCase(name)) return true;
        }
        // Donut-style tab list: team names look like "08-name-false"; the lower the number, the higher the rank
        if (cfg.staffMaxRank > 0 && mc.world != null) {
            Team team = mc.world.getScoreboard().getScoreHolderTeam(name);
            if (team != null) {
                String tn = team.getName();
                int dash = tn.indexOf('-');
                if (dash > 0 && dash <= 3) {
                    try {
                        if (Integer.parseInt(tn.substring(0, dash)) <= cfg.staffMaxRank) return true;
                    } catch (NumberFormatException ignored) { }
                }
            }
        }
        String t = tagOf(mc, e);
        for (String k : cfg.staffKeywords) {
            if (k != null && !k.isBlank() && hasWord(t, k.trim().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static String printable(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch >= 0xE000 && ch <= 0xF8FF) sb.append(String.format("\\u%04X", (int) ch));
            else sb.append(ch);
        }
        return sb.toString();
    }

    /** Prints every rank tag in the tab list with a few names, so you can see what the server calls its staff. */
    public static void printTags(MinecraftClient mc) {
        if (mc.player == null) return;
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        if (handler == null) return;
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (PlayerListEntry e : handler.getPlayerList()) {
            String tag = tagOf(mc, e);
            groups.computeIfAbsent(tag.isEmpty() ? "(no tag)" : tag, k -> new ArrayList<>()).add(e.getProfile().getName());
        }
        mc.player.sendMessage(Text.literal("[Pharaoh] Tab list tags (" + groups.size() + "):"), false);
        int shown = 0;
        for (Map.Entry<String, List<String>> g : groups.entrySet()) {
            if (shown++ >= 20) break;
            List<String> names = g.getValue();
            String sample = String.join(", ", names.subList(0, Math.min(3, names.size())));
            mc.player.sendMessage(Text.literal("  \"" + printable(g.getKey()) + "\"  x" + names.size() + "  e.g. " + sample), false);
        }
    }

    public static void tick(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.staffEnabled || mc.world == null || mc.player == null) return;
        if (timer-- > 0) return;
        timer = 20;
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        if (handler == null) return;

        Map<UUID, Staff> now = new LinkedHashMap<>();
        for (PlayerListEntry e : handler.getPlayerList()) {
            UUID id = e.getProfile().getId();
            if (id.equals(mc.player.getUuid())) continue;
            if (isStaff(mc, e, cfg)) now.put(id, new Staff(e.getProfile().getName(), e.getSkinTextures()));
        }

        if (primed && cfg.staffAlerts) {
            for (Map.Entry<UUID, Staff> en : now.entrySet()) {
                if (!CURRENT.containsKey(en.getKey())) {
                    mc.player.sendMessage(Text.literal("[Pharaoh] Staff joined: " + en.getValue().name()), false);
                }
            }
            for (Map.Entry<UUID, Staff> en : CURRENT.entrySet()) {
                if (!now.containsKey(en.getKey())) {
                    mc.player.sendMessage(Text.literal("[Pharaoh] Staff left: " + en.getValue().name()), false);
                }
            }
        }
        CURRENT.clear();
        CURRENT.putAll(now);
        primed = true;
    }

    public static void render(DrawContext ctx, MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        boolean editing = mc.currentScreen instanceof PharaohScreen;
        if (!cfg.staffEnabled || !cfg.staffShowList) return;
        if (CURRENT.isEmpty() && !editing) return;

        List<Staff> list = new ArrayList<>(CURRENT.values());
        if (list.isEmpty()) list.add(new Staff("Drag me", null));
        int shown = Math.min(list.size(), 8);
        int rowH = 14;
        int w = mc.textRenderer.getWidth("STAFF " + list.size());
        for (int i = 0; i < shown; i++) w = Math.max(w, 18 + mc.textRenderer.getWidth(list.get(i).name()));
        if (list.size() > shown) w = Math.max(w, mc.textRenderer.getWidth("+" + (list.size() - shown) + " more"));
        w += 10;

        int h = 14 + shown * rowH + (list.size() > shown ? rowH : 0) + 2;
        int x = Math.max(0, Math.min(cfg.staffX, mc.getWindow().getScaledWidth() - w));
        int y = Math.max(0, Math.min(cfg.staffY, mc.getWindow().getScaledHeight() - h));
        BOUNDS[0] = x;
        BOUNDS[1] = y;
        BOUNDS[2] = w;
        BOUNDS[3] = h;
        HudPanel.draw(ctx, x, y, w, h);
        ctx.drawTextWithShadow(mc.textRenderer, Text.literal("STAFF " + list.size()), x + 6, y + 3, 0xFF8A8A96);
        int ry = y + 14;
        for (int i = 0; i < shown; i++) {
            Staff s = list.get(i);
            if (s.skin() != null) PlayerSkinDrawer.draw(ctx, s.skin(), x + 6, ry, 11);
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal(s.name()), x + 21, ry + 2, 0xFFE6E6E6);
            ry += rowH;
        }
        if (list.size() > shown) {
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal("+" + (list.size() - shown) + " more"), x + 6, ry, 0xFF8A8A96);
        }
    }
}
