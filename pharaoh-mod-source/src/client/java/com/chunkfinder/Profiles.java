package com.chunkfinder;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Config Profiles: save all your settings under a slot or under the server you are on, and load them again with one
 * click. When you join a server that has a saved profile, it can be loaded for you. Profiles are plain JSON files in
 * config/chunkfinder-profiles. Nothing else is read or written.
 */
public final class Profiles {
    private Profiles() {}

    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("chunkfinder-profiles");
    private static Object lastWorld = null;

    private static void say(String s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal("[Pharaoh] " + s), true);
    }

    /** A safe file name for the server you are on, or null when you are not on a multiplayer server. */
    private static String serverKey(MinecraftClient mc) {
        ServerInfo e = mc.getCurrentServerEntry();
        if (e == null || e.address == null) return null;
        String k = e.address.toLowerCase().replaceAll("[^a-z0-9._-]", "_");
        return k.isEmpty() ? null : "server-" + k;
    }

    private static boolean write(String name) {
        try {
            Files.createDirectories(DIR);
            Files.writeString(DIR.resolve(name + ".json"), ChunkFinderConfig.toJson());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean read(String name) {
        Path f = DIR.resolve(name + ".json");
        try {
            if (!Files.exists(f)) return false;
            if (!ChunkFinderConfig.applyJson(Files.readString(f))) return false;
            ChunkFinderClient.clearAll();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static void saveSlot(int n) {
        say(write("slot" + n) ? "Saved your settings to slot " + n : "Could not save slot " + n);
    }

    public static void loadSlot(int n) {
        say(read("slot" + n) ? "Loaded slot " + n : "Slot " + n + " is empty");
    }

    public static void saveServer() {
        String k = serverKey(MinecraftClient.getInstance());
        if (k == null) { say("Join a multiplayer server first"); return; }
        say(write(k) ? "Saved a profile for this server" : "Could not save the profile");
    }

    public static void loadServer() {
        String k = serverKey(MinecraftClient.getInstance());
        if (k == null) { say("Join a multiplayer server first"); return; }
        say(read(k) ? "Loaded the profile for this server" : "This server has no saved profile");
    }

    /** Loads the saved profile of a server when you join it. */
    public static void tick(MinecraftClient mc) {
        if (mc.world == lastWorld) return;
        lastWorld = mc.world;
        if (mc.world == null || !ChunkFinderConfig.INSTANCE.profileAutoLoad) return;
        String k = serverKey(mc);
        if (k != null && Files.exists(DIR.resolve(k + ".json")) && read(k)) say("Loaded the profile for this server");
    }
}
