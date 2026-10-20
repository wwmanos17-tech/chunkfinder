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
    private static int timer = 0;

    public static void clear() {
        CURRENT.clear();
        primed = false;
    }

    private static boolean isStaff(MinecraftClient mc, PlayerListEntry e, ChunkFinderConfig cfg) {
        String name = e.getProfile().getName();
        String lower = name.toLowerCase(Locale.ROOT);
        for (String n : cfg.staffNames) {
            if (n != null && n.trim().equalsIgnoreCase(name)) return true;
        }
        StringBuilder tag = new StringBuilder();
        if (mc.world != null) {
            Team team = mc.world.getScoreboard().getScoreHolderTeam(name);
            if (team != null) tag.append(team.getPrefix().getString()).append(' ');
        }
        Text display = e.getDisplayName();
        if (display != null) tag.append(display.getString().toLowerCase(Locale.ROOT).replace(lower, " "));
        String t = tag.toString().toLowerCase(Locale.ROOT);
        for (String k : cfg.staffKeywords) {
            if (k != null && !k.isBlank() && t.contains(k.trim().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
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
        if (!cfg.staffEnabled || !cfg.staffShowList || CURRENT.isEmpty()) return;

        List<Staff> list = new ArrayList<>(CURRENT.values());
        int shown = Math.min(list.size(), 8);
        int rowH = 12;
        int w = mc.textRenderer.getWidth("STAFF " + list.size());
        for (int i = 0; i < shown; i++) w = Math.max(w, 14 + mc.textRenderer.getWidth(list.get(i).name()));
        if (list.size() > shown) w = Math.max(w, mc.textRenderer.getWidth("+" + (list.size() - shown) + " more"));
        w += 10;

        int x = 4;
        int y = 4;
        int h = 14 + shown * rowH + (list.size() > shown ? rowH : 0) + 2;
        ctx.fill(x, y, x + w, y + h, 0xB0101018);
        ctx.fill(x, y, x + 2, y + h, 0xFFE0A526);
        ctx.drawTextWithShadow(mc.textRenderer, Text.literal("STAFF " + list.size()), x + 6, y + 3, 0xFF8A8A96);
        int ry = y + 14;
        for (int i = 0; i < shown; i++) {
            Staff s = list.get(i);
            if (s.skin() != null) PlayerSkinDrawer.draw(ctx, s.skin(), x + 6, ry, 8);
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal(s.name()), x + 18, ry, 0xFFE6E6E6);
            ry += rowH;
        }
        if (list.size() > shown) {
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal("+" + (list.size() - shown) + " more"), x + 6, ry, 0xFF8A8A96);
        }
    }
}
