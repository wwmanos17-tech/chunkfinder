package com.chunkfinder;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Command Keys: one key sends one chat command, exactly as if you had typed it yourself (for example /rtp).
 * One press sends one command, never more, and presses closer than one second apart are ignored.
 * It does not click anything, repeat anything or wait for anything.
 * Set what a key sends with /pcmd set 1 rtp (handled on your own computer, not sent to the server).
 */
public final class CommandKeys {
    private CommandKeys() {}

    public static final int SLOTS = 3;
    private static long lastMs = 0;

    private static void say(MinecraftClient mc, String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("[Pharaoh] " + s), true);
    }

    static String clean(String s) {
        if (s == null) return "";
        s = s.trim();
        while (s.startsWith("/")) s = s.substring(1).trim();
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) if (ch >= 32 && ch != 127 && ch != '§') b.append(ch);
        s = b.toString();
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    /** Sends the command saved in a slot (0 to 2). */
    public static void run(MinecraftClient mc, int slot) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.cmdEnabled || mc.player == null || mc.player.networkHandler == null || mc.currentScreen != null) return;
        if (slot < 0 || slot >= SLOTS) return;
        String cmd = clean(cfg.cmdSlots[slot]);
        if (cmd.isEmpty()) { say(mc, "Slot " + (slot + 1) + " is empty. Type /pcmd set " + (slot + 1) + " rtp"); return; }
        long now = System.currentTimeMillis();
        if (now - lastMs < 1000) return;
        lastMs = now;
        mc.player.networkHandler.sendChatCommand(cmd);
        say(mc, "Sent /" + cmd);
    }

    /** /pcmd set 1 rtp   and   /pcmd list */
    public static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("pcmd")
                .then(ClientCommandManager.literal("list").executes(ctx -> {
                    ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
                    for (int i = 0; i < SLOTS; i++) {
                        String c = clean(cfg.cmdSlots[i]);
                        ctx.getSource().sendFeedback(Text.literal("[Pharaoh] Slot " + (i + 1) + ": " + (c.isEmpty() ? "(empty)" : "/" + c)));
                    }
                    return 1;
                }))
                .then(ClientCommandManager.literal("set")
                    .then(ClientCommandManager.argument("slot", IntegerArgumentType.integer(1, SLOTS))
                        .then(ClientCommandManager.argument("command", StringArgumentType.greedyString()).executes(ctx -> {
                            int slot = IntegerArgumentType.getInteger(ctx, "slot");
                            String c = clean(StringArgumentType.getString(ctx, "command"));
                            ChunkFinderConfig.INSTANCE.cmdSlots[slot - 1] = c;
                            ChunkFinderConfig.save();
                            ctx.getSource().sendFeedback(Text.literal("[Pharaoh] Slot " + slot + " now sends " + (c.isEmpty() ? "nothing" : "/" + c)));
                            return 1;
                        })))));
        });
    }
}
