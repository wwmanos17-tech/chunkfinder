package com.chunkfinder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persistent settings, saved to config/chunkfinder.json. Colors are {red, green, blue} from 0 to 255. */
public class ChunkFinderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("chunkfinder.json");

    public static ChunkFinderConfig INSTANCE = new ChunkFinderConfig();

    // ---------------- Module list (on-screen list of tools that are ON) ----------------
    public boolean showModuleList = true;

    // ---------------- Keys that switch a tool on/off (tool name -> GLFW key code) ----------------
    public java.util.Map<String, Integer> moduleKeys = new java.util.HashMap<>();

    // ---------------- Chunk Finder ----------------
    public boolean enabled = false;
    /** 1..10 - higher flags chunks with fewer activity blocks. */
    public int sensitivity = 5;
    /** Scan radius in chunks (2..32). Also capped by the client's loaded view distance. */
    public int simulationDistance = 8;
    public boolean detectGeodes = true;
    public boolean detectActivity = true;
    public boolean detectSus = true;
    /** 1..10 - higher flags more suspicious-looking chunks. */
    public int susSensitivity = 5;
    public boolean tallColumns = true;
    public boolean notify = true;
    public int scanInterval = 20;
    public int renderY = 64;
    public double boxHeight = 1.0;
    public int[] activityColor = {255, 80, 80};
    public int[] geodeColor = {170, 90, 255};
    public int[] susColor = {255, 150, 0};

    // ---------------- Freecam ----------------
    public boolean freecamEnabled = true;
    public int freecamSpeed = 5;

    // ---------------- Player ESP ----------------
    public boolean playerEspEnabled = false;
    public int playerEspRange = 256;
    public boolean playerEspNames = true;
    public boolean playerEspDistance = true;
    public boolean playerEspFill = true;
    public boolean playerEspTracers = false;
    public boolean playerEspSelfInFreecam = true;
    public int[] playerColor = {80, 220, 255};

    // ---------------- Chest ESP ----------------
    public boolean chestEspEnabled = false;
    public int chestEspRange = 512;
    public boolean espChest = true;
    public boolean espTrapped = true;
    public boolean espBarrel = true;
    public boolean espEnder = true;
    public boolean espShulker = true;
    public boolean espSpawner = true;
    public boolean espBeacon = true;
    public boolean espBed = true;
    public boolean espMinecart = true;
    public boolean chestEspFill = true;
    public boolean chestEspTracers = true;
    public boolean chestEspLabels = true;
    public boolean chestEspSummary = true;
    /** Chat alerts from the Chest ESP scan. */
    public boolean alertSpawners = true;
    public boolean alertBases = true;

    // ---------------- Ore ESP ----------------
    public boolean oreEspEnabled = false;
    /** Scan radius in chunks (1..8). */
    public int oreEspRange = 4;
    public boolean oreDiamond = true;
    public boolean oreDebris = true;
    public boolean oreEmerald = true;
    public boolean oreGold = true;
    public boolean oreIron = false;
    public boolean oreCoal = false;
    public boolean oreRedstone = false;
    public boolean oreLapis = false;
    public boolean oreCopper = false;
    public boolean oreFill = true;
    public boolean oreTracers = false;

    // ---------------- Staff Tracker ----------------
    public boolean staffEnabled = true;
    public boolean staffShowList = true;
    public boolean staffAlerts = true;
    /** Exact player names that always count as staff. Edit in config/chunkfinder.json. */
    public String[] staffNames = {};
    /** If a player's rank tag in the tab list contains one of these, they count as staff. */
    public String[] staffKeywords = {"owner", "admin", "mod", "helper", "staff", "support", "manager", "developer"};

    // ---------------- Combat (for anarchy servers like 2b2t) ----------------
    public boolean autoWTapEnabled = false;
    /** How many ticks forward stays released (1..4). */
    public int autoWTapTicks = 2;
    public boolean autoCrystalEnabled = false;
    public boolean autoCrystalPlace = true;
    public boolean autoCrystalBreak = true;
    public boolean autoCrystalObsidian = true;
    /** Enemy must be within this many blocks (3..10). */
    public int autoCrystalRange = 6;
    /** Ticks between actions (1..10). */
    public int autoCrystalDelay = 2;
    /** Never break or place a crystal closer than this to you (0..6). */
    public int autoCrystalSafeDistance = 3;

    public boolean triggerBotEnabled = false;
    public boolean triggerPlayersOnly = true;
    /** Weapon charge needed before it hits, in percent (50..100). */
    public int triggerCharge = 95;
    public boolean autoAnchorEnabled = false;
    /** Enemy must be within this many blocks (3..10). */
    public int autoAnchorRange = 6;
    /** Ticks between actions (1..10). */
    public int autoAnchorDelay = 3;
    /** Never place or set off an anchor closer than this to you (0..8). */
    public int autoAnchorSafeDistance = 5;

    // ---------------- Spectator Alert ----------------
    public boolean spectatorEnabled = true;
    public int spectatorRange = 24;
    public int spectatorPopupSeconds = 3;
    public int spectatorCooldownMinutes = 30;
    public boolean spectatorSound = true;
    /** Also show the real elder guardian jump-scare. */
    public boolean spectatorGuardian = true;
    /** Size of the popup head in percent of the full size (10..100). */
    public int spectatorHeadPercent = 50;
    /** Also alert for spectators that only show up in the player list (not loaded near you). */
    public boolean spectatorTabList = true;
    public String spectatorMessage = "Goodluck Pharaoh User, Be Careful";

    public int requiredActivityBlocks() {
        return 11 - Math.max(1, Math.min(10, sensitivity));
    }

    public int requiredSusScore() {
        return 11 - Math.max(1, Math.min(10, susSensitivity));
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

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int[] fixColor(int[] c, int[] fallback) {
        return (c == null || c.length < 3) ? fallback : c;
    }

    private void clamp() {
        if (moduleKeys == null) moduleKeys = new java.util.HashMap<>();
        sensitivity = clampInt(sensitivity, 1, 10);
        susSensitivity = clampInt(susSensitivity, 1, 10);
        simulationDistance = clampInt(simulationDistance, 2, 32);
        scanInterval = clampInt(scanInterval, 5, 200);
        renderY = clampInt(renderY, -64, 320);
        boxHeight = Math.max(0.1, Math.min(16, boxHeight));
        freecamSpeed = clampInt(freecamSpeed, 1, 20);
        playerEspRange = clampInt(playerEspRange, 16, 512);
        chestEspRange = clampInt(chestEspRange, 16, 1024);
        oreEspRange = clampInt(oreEspRange, 1, 8);
        spectatorRange = clampInt(spectatorRange, 4, 128);
        autoWTapTicks = clampInt(autoWTapTicks, 1, 4);
        autoCrystalRange = clampInt(autoCrystalRange, 3, 10);
        autoCrystalDelay = clampInt(autoCrystalDelay, 1, 10);
        autoCrystalSafeDistance = clampInt(autoCrystalSafeDistance, 0, 6);
        triggerCharge = clampInt(triggerCharge, 50, 100);
        autoAnchorRange = clampInt(autoAnchorRange, 3, 10);
        autoAnchorDelay = clampInt(autoAnchorDelay, 1, 10);
        autoAnchorSafeDistance = clampInt(autoAnchorSafeDistance, 0, 8);
        spectatorHeadPercent = clampInt(spectatorHeadPercent, 10, 100);
        spectatorPopupSeconds = clampInt(spectatorPopupSeconds, 1, 10);
        spectatorCooldownMinutes = clampInt(spectatorCooldownMinutes, 0, 120);
        if (staffNames == null) staffNames = new String[0];
        if (staffKeywords == null) staffKeywords = new String[0];
        if (spectatorMessage == null || spectatorMessage.equals("Good luck, Pharaoh player")) spectatorMessage = "Goodluck Pharaoh User, Be Careful";
        activityColor = fixColor(activityColor, new int[]{255, 80, 80});
        geodeColor = fixColor(geodeColor, new int[]{170, 90, 255});
        susColor = fixColor(susColor, new int[]{255, 150, 0});
        playerColor = fixColor(playerColor, new int[]{80, 220, 255});
    }
}
