package com.chunkfinder;

import net.minecraft.client.MinecraftClient;

/**
 * Name protector: swaps your own name for an alias in everything the game draws on YOUR screen
 * (chat, tab list, name tags, scoreboard). It is purely visual, so other players still see your real name.
 */
public final class NameProtect {
    private NameProtect() {}

    public static String apply(String text) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.nameProtect || text == null || text.length() < 3) return text;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return text;
        String name = mc.player.getGameProfile().getName();
        if (name == null || name.isEmpty()) return text;
        if (!text.contains(name)) return text;
        String alias = cfg.nameAlias == null || cfg.nameAlias.isBlank() ? "Pharaoh" : cfg.nameAlias;
        return text.replace(name, alias);
    }
}
