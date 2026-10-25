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
    /** Size of the menu in percent. */
    public int menuScale = 100;
    public boolean menuSounds = true;
    public int menuSoundVolume = 60;
    public boolean nameProtect = true;
    public String nameAlias = "Pharaoh";
    public boolean regionMapEnabled = true;
    public boolean fpsEnabled = true;
    public int fpsX = 4;
    public int fpsY = 64;
    public int regionMapX = 4;
    public int regionMapY = 90;
    public int regionMapCell = 12;
    public boolean showModuleList = true;

    // ---------------- Keys that switch a tool on/off (tool name -> GLFW key code) ----------------
    public java.util.Map<String, Integer> moduleKeys = new java.util.HashMap<>();
    /** Tools starred in the menu. */
    public java.util.List<String> favoriteModules = new java.util.ArrayList<>();

    // ---------------- Chunk Finder ----------------
    public boolean enabled = false;
    /** 1..10 - higher flags chunks with fewer activity blocks. */
    public int sensitivity = 5;
    /** Scan radius in chunks (2..32). Also capped by the client's loaded view distance. */
    public int simulationDistance = 8;
    public boolean detectGeodes = true;
    public boolean detectActivity = true;
    public boolean detectSus = true;
    public boolean fastScan = true;
    /** 1..10 - higher flags more suspicious-looking chunks. */
    public int susSensitivity = 5;
    public boolean tallColumns = true;
    public boolean susList = true;
    public boolean susScoreColors = true;
    public boolean susLabels = true;
    public boolean susTracer = false;
    /** Draw boxes on the exact blocks that gave a chunk away, instead of flat chunk-sized slabs. */
    public boolean exactSpots = true;
    public int susListCount = 5;
    public boolean detectLight = true;
    /** Underground lights needed in a chunk before it is flagged (1..20). */
    public int lightMin = 3;
    public int[] lightColor = {255, 230, 80};
    public boolean notify = true;
    public int scanInterval = 20;
    public int renderY = 64;
    public double boxHeight = 1.0;
    public int[] activityColor = {255, 80, 80};
    public int[] geodeColor = {170, 90, 255};
    public int[] susColor = {255, 40, 40};
    /** How solid the sus chunk columns are, in percent (5..95). */
    public int susAlpha = 25;

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
    public boolean chestEspActive = true;
    public boolean alertStash = true;
    public boolean chestEspGroup = true;
    public boolean alertSpawners = true;
    public boolean alertBases = true;

    // ---------------- Player Logger / Armor HUD ----------------
    public boolean playerLogEnabled = false;
    public boolean playerLogShowList = true;
    public boolean playerLogChatEnter = true;
    public boolean playerLogChatLeave = true;
    public int playerLogLines = 6;
    public int playerLogX = 4;
    public int playerLogY = 120;

    public boolean armorEnabled = false;
    public int armorScale = 100;
    public int armorX = 4;
    public int armorY = 200;
    public boolean armorHorizontal = false;
    public boolean armorBar = true;
    public boolean armorText = true;
    public boolean armorNumber = true;
    public boolean armorHands = false;
    public boolean armorShowEmpty = false;
    public boolean armorBackground = true;

    // ---------------- Session Timer ----------------
    public boolean timerEnabled = false;
    public boolean timerShowSession = true;
    public boolean timerShowStopwatch = true;
    public boolean timerShowClock = false;

    // ---------------- Reach Test ----------------
    public boolean reachEnabled = false;
    public int reachX = 4;
    public int reachY = 140;

    // ---------------- Market Helper ----------------
    public boolean marketEnabled = false;
    public int marketMinPercent = 25;
    public int marketMinProfit = 0;
    public int marketFee = 0;
    public boolean marketPerItem = false;
    public boolean marketPanel = true;

    // ---------------- Cave Finder ----------------
    public boolean caveEnabled = false;
    public boolean caveDeep = true;
    public boolean caveExact = true;
    public int caveMin = 30;
    public int caveRange = 8;
    public boolean caveCaves = true;
    public boolean caveRooms = true;
    public boolean caveFill = true;

    // ---------------- Farm Finder ----------------
    public boolean farmEnabled = false;
    public int farmMin = 8;
    public boolean farmUnderground = true;
    public boolean farmChat = true;
    public boolean farmTracer = false;
    public boolean farmLabels = true;

    // ---------------- Sweep Map ----------------
    public boolean sweepEnabled = false;
    public int sweepRadius = 12;
    public boolean sweepFlags = true;
    public int sweepX = 4;
    public int sweepY = 60;

    // ---------------- Activity Detector ----------------
    public boolean activityEnabled = false;
    public int activityRedstoneMin = 8;
    public boolean activityRedstone = true;
    public boolean activityStorage = true;
    public boolean activityStatic = true;
    public boolean activityLabels = true;
    public boolean activityChat = true;

    // ---------------- Entity ESP ----------------
    public boolean entityEspEnabled = false;
    public int entityEspRange = 128;
    public boolean entityEspFrames = true;
    public boolean entityEspStands = true;
    public boolean entityEspVehicles = true;
    public boolean entityEspCrystals = true;
    public boolean entityEspLabels = true;
    public boolean entityEspFill = true;

    // ---------------- Item ESP ----------------
    public boolean itemEspEnabled = false;
    public int itemEspRange = 128;
    public boolean itemEspAll = false;
    public boolean itemEspLabels = true;
    public boolean itemEspFill = true;
    public boolean itemEspTracers = false;
    public boolean itemEspAlert = true;

    // ---------------- Base ESP ----------------
    public boolean baseEspEnabled = false;
    public int baseEspSensitivity = 5;
    public boolean baseEspFill = true;
    public boolean baseEspLabels = true;
    public boolean baseEspTracer = true;
    public boolean baseEspAlert = true;
    public boolean baseEspUseVillagers = true;
    public boolean baseEspUseSus = true;

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
    public boolean oreGilded = true;
    public boolean oreNetherite = true;
    public boolean alertRareOres = true;
    public boolean oreFill = true;
    public boolean oreTracers = false;

    // ---------------- Staff Tracker ----------------
    public boolean staffEnabled = true;
    public boolean staffShowList = true;
    public boolean staffAlerts = true;
    public int staffX = 4;
    public int staffY = 4;
    /** Tab list team names that start with a number up to this one count as staff (0 = off). */
    public int staffMaxRank = 8;
    /** Exact player names that always count as staff. Edit in config/chunkfinder.json. */
    public String[] staffNames = {};
    /** If a player's rank tag in the tab list contains one of these, they count as staff. */
    public String[] staffKeywords = {"owner", "admin", "administrator", "mod", "moderator", "helper", "staff", "support", "manager", "developer", "dev", "builder", "trainee", "trial", "sr", "jr", "head", "founder", "ceo", "operator", "op", "guardian", "warden", "intern"};


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
        if (favoriteModules == null) favoriteModules = new java.util.ArrayList<>();
        sensitivity = clampInt(sensitivity, 1, 10);
        lightMin = clampInt(lightMin, 1, 20);
        if (susAlpha == 50) susAlpha = 25; // the first version used 50, now it is more see-through
        susAlpha = clampInt(susAlpha, 5, 95);
        for (int[] col : new int[][]{activityColor, geodeColor, susColor, lightColor}) {
            if (col != null) for (int i = 0; i < 3 && i < col.length; i++) col[i] = clampInt(col[i], 0, 255);
        }
        lightColor = fixColor(lightColor, new int[]{255, 230, 80});
        susSensitivity = clampInt(susSensitivity, 1, 10);
        simulationDistance = clampInt(simulationDistance, 2, 32);
        scanInterval = clampInt(scanInterval, 5, 200);
        renderY = clampInt(renderY, -64, 320);
        boxHeight = Math.max(0.1, Math.min(16, boxHeight));
        freecamSpeed = clampInt(freecamSpeed, 1, 60);
        playerEspRange = clampInt(playerEspRange, 16, 1024);
        chestEspRange = clampInt(chestEspRange, 16, 2048);
        oreEspRange = clampInt(oreEspRange, 1, 12);
        baseEspSensitivity = clampInt(baseEspSensitivity, 3, 20);
        itemEspRange = clampInt(itemEspRange, 8, 512);
        entityEspRange = clampInt(entityEspRange, 8, 512);
        playerLogLines = clampInt(playerLogLines, 2, 15);
        activityRedstoneMin = clampInt(activityRedstoneMin, 2, 60);
        farmMin = clampInt(farmMin, 3, 60);
        caveMin = clampInt(caveMin, 8, 500);
        caveRange = clampInt(caveRange, 2, 24);
        sweepRadius = clampInt(sweepRadius, 5, 24);
        sweepX = clampInt(sweepX, 0, 4000);
        sweepY = clampInt(sweepY, 0, 4000);
        marketMinPercent = clampInt(marketMinPercent, 5, 95);
        marketMinProfit = clampInt(marketMinProfit, 0, 1000000);
        marketFee = clampInt(marketFee, 0, 30);
        reachX = clampInt(reachX, 0, 2000);
        reachY = clampInt(reachY, 0, 2000);
        armorScale = clampInt(armorScale, 50, 250);
        spectatorRange = clampInt(spectatorRange, 4, 128);
        spectatorHeadPercent = clampInt(spectatorHeadPercent, 10, 100);
        spectatorPopupSeconds = clampInt(spectatorPopupSeconds, 1, 10);
        spectatorCooldownMinutes = clampInt(spectatorCooldownMinutes, 0, 120);
        staffMaxRank = clampInt(staffMaxRank, 0, 99);
        menuScale = clampInt(menuScale, 60, 200);
        menuSoundVolume = clampInt(menuSoundVolume, 0, 100);
        regionMapCell = clampInt(regionMapCell, 6, 30);
        susListCount = clampInt(susListCount, 1, 12);
        if (staffNames == null) staffNames = new String[0];
        if (staffKeywords == null) staffKeywords = new String[0];
        if (spectatorMessage == null || spectatorMessage.equals("Good luck, Pharaoh player")) spectatorMessage = "Goodluck Pharaoh User, Be Careful";
        activityColor = fixColor(activityColor, new int[]{255, 80, 80});
        geodeColor = fixColor(geodeColor, new int[]{170, 90, 255});
        susColor = fixColor(susColor, new int[]{255, 40, 40});
        // sus chunks used to be orange; they are solid red now
        if (susColor[0] == 255 && susColor[1] == 150 && susColor[2] == 0) susColor = new int[]{255, 40, 40};
        playerColor = fixColor(playerColor, new int[]{80, 220, 255});
    }
}
