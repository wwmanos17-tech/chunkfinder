package com.chunkfinder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persistent settings, saved to config/chunkfinder.json. */
public class ChunkFinderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("chunkfinder.json");

    public static ChunkFinderConfig INSTANCE = new ChunkFinderConfig();

    public boolean enabled = false;

    /** 1..10 - higher flags chunks with fewer activity blocks. */
    public int sensitivity = 5;

    /** Scan radius in chunks (2..32). Also capped by the client's loaded view distance. */
    public int simulationDistance = 8;

    public boolean detectGeodes = true;
    public boolean detectActivity = true;

    /** Ticks between scans. */
    public int scanInterval = 40;

    /** Height all highlights are drawn at, so they stay aligned on one plane. */
    public int renderY = 64;
    public double boxHeight = 1.0;

    /** Freecam flying speed, 1..20 (5 is about 10 blocks per second). */
    public int freecamSpeed = 5;

    // ARGB-free simple RGB colors (0..255)
    public int[] activityColor = {255, 80, 80};
    public int[] geodeColor = {170, 90, 255};

    public int requiredActivityBlocks() {
        return 11 - Math.max(1, Math.min(10, sensitivity));
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                ChunkFinderConfig loaded = GSON.fromJson(Files.readString(FILE), ChunkFinderConfig.class);
                if (loaded != null) INSTANCE = loaded;
            }
        } catch (Exception e) {
            ChunkFinderClient.LOGGER.warn("Could not read config, using defaults", e);
        }
        INSTANCE.clamp();
    }

    public static void save() {
        INSTANCE.clamp();
        try {
            Files.writeString(FILE, GSON.toJson(INSTANCE));
        } catch (IOException e) {
            ChunkFinderClient.LOGGER.warn("Could not save config", e);
        }
    }

    private void clamp() {
        sensitivity = Math.max(1, Math.min(10, sensitivity));
        simulationDistance = Math.max(2, Math.min(32, simulationDistance));
        scanInterval = Math.max(5, Math.min(200, scanInterval));
        renderY = Math.max(-64, Math.min(320, renderY));
        boxHeight = Math.max(0.1, Math.min(16, boxHeight));
        freecamSpeed = Math.max(1, Math.min(20, freecamSpeed));
    }
}
