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
    /** Load the saved profile of a server when you join it. */
    public boolean profileAutoLoad = true;
    /** Command Keys: what each of the three keys sends. */
    public boolean cmdEnabled = true;
    public String[] cmdSlots = {"rtp", "home", "spawn"};
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
    /** Sus Chunks only look at things at or below this height, so the surface and the already-griefed upper layers are ignored. */
    public int susMaxY = 2;
    public boolean clusterDeepOnly = true;
    public boolean tallColumns = true;
    public boolean susList = true;
    public boolean susScoreColors = true;
    public boolean susLabels = true;
    public boolean susTracer = false;
    /** Draw boxes on the exact blocks that gave a chunk away, instead of flat chunk-sized slabs. */
    public boolean exactSpots = true;
    public boolean joinCover = true;
    public boolean focusCards = true;
    public boolean pharaohFont = true;
    /** false = the original gold pharaoh head, true = the smooth gold pyramids picture. */
    public boolean logoPyramid = false;
    /** true = the original blocky pixel head. */
    public boolean logoPixel = false;
    public boolean globalFont = false;
    /** Set once, so the "all menus and chat" font is switched off one time for people who already had it on. */
    public boolean fontMigrated = false;
    public boolean pharaohFontPoppins = false;
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

    // ---------------- Market Helper ----------------
    public boolean marketEnabled = false;
    public int marketMinPercent = 25;
    public int marketMinProfit = 0;
    public int marketFee = 0;
    public boolean marketPerItem = false;
    public boolean marketPanel = true;

    // ---------------- RTP Hunter ----------------
    public boolean huntEnabled = false;
    public boolean huntAlert = true;
    public boolean huntBeams = true;
    // ---------------- New Chunks ----------------
    public boolean newChunksEnabled = false;
    public boolean newChunksShowNew = true;
    public boolean newChunksShowOld = false;
    public boolean newChunksOldReset = false;
    public int newChunksRange = 10;
    public int newChunksDown = 12;
    /** Fixed height of the New Chunks colours. */
    public int newChunksY = 62;
    /** The small Sus Chunks list is hidden while RTP Hunter is on, because RTP Hunter shows the same finds. */
    public boolean huntHidesSusList = true;
    public int huntX = 4;
    public int huntY = 90;

    // ---------------- Portal Calc ----------------
    public boolean portalEnabled = false;
    public boolean portalAuto = true;
    public boolean portalMarks = true;
    public boolean portalLook = false;
    public int portalX = 4;
    public int portalY = 200;
    public java.util.List<PortalSave> portalSaved = new java.util.ArrayList<>();

    /** One portal you walked through or saved: dim 0 = Overworld, 1 = Nether, per server. */
    public static class PortalSave {
        public int dim;
        public int x, y, z;
        public String server = "";
    }

    // ---------------- Sound Radar ----------------
    public boolean soundEnabled = false;
    public int soundSeconds = 8;
    public boolean soundMarks = true;
    public boolean soundDoors = true;
    public boolean soundStorage = true;
    public boolean soundDig = true;
    public boolean soundMachines = true;

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
    public boolean clusterEnabled = false;
    public int clusterSensitivity = 5;
    public int clusterJoin = 28;
    public boolean clusterFill = true;
    public boolean clusterLabels = true;
    public boolean clusterTracer = true;
    public boolean clusterAlert = true;
    public boolean clusterParts = true;
    public boolean clusterUseVillagers = true;
    public boolean clusterUseSus = true;

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

    /** The current settings as text (used by Profiles). */
    public static String toJson() {
        INSTANCE.clamp();
        return GSON.toJson(INSTANCE);
    }

    /**
     * Copies a saved profile into the live settings. It fills the same object the menu already uses, so the menu keeps
     * working. Your saved portals and the auto-load switch are kept as they are.
     */
    public static boolean applyJson(String json) {
        try {
            Gson g = new GsonBuilder()
                .registerTypeAdapter(ChunkFinderConfig.class, (com.google.gson.InstanceCreator<ChunkFinderConfig>) t -> INSTANCE)
                .create();
            java.util.List<PortalSave> keepPortals = INSTANCE.portalSaved;
            boolean keepAuto = INSTANCE.profileAutoLoad;
            ChunkFinderConfig r = g.fromJson(json, ChunkFinderConfig.class);
            if (r == null) return false;
            INSTANCE.portalSaved = keepPortals;
            INSTANCE.profileAutoLoad = keepAuto;
            INSTANCE.clamp();
            save();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
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
        susMaxY = clampInt(susMaxY, -60, 64);
        newChunksRange = clampInt(newChunksRange, 2, 24);
        newChunksDown = clampInt(newChunksDown, 2, 120);
        newChunksY = clampInt(newChunksY, -64, 320);
        if (cmdSlots == null || cmdSlots.length != 3) cmdSlots = new String[]{"rtp", "home", "spawn"};
        for (int i = 0; i < 3; i++) if (cmdSlots[i] == null) cmdSlots[i] = "";
        if (!fontMigrated) { globalFont = false; fontMigrated = true; }
        if (!newChunksOldReset) { newChunksShowOld = false; newChunksOldReset = true; }
        simulationDistance = clampInt(simulationDistance, 2, 32);
        scanInterval = clampInt(scanInterval, 5, 200);
        renderY = clampInt(renderY, -64, 320);
        boxHeight = Math.max(0.1, Math.min(16, boxHeight));
        freecamSpeed = clampInt(freecamSpeed, 1, 60);
        playerEspRange = clampInt(playerEspRange, 16, 1024);
        chestEspRange = clampInt(chestEspRange, 16, 2048);
        oreEspRange = clampInt(oreEspRange, 1, 12);
        clusterSensitivity = clampInt(clusterSensitivity, 3, 20);
        clusterJoin = clampInt(clusterJoin, 10, 64);
        chestEspEnabled = false;   // Chest ESP was replaced by Cluster ESP; its scanner still runs for Cluster ESP
        itemEspRange = clampInt(itemEspRange, 8, 512);
        entityEspRange = clampInt(entityEspRange, 8, 512);
        playerLogLines = clampInt(playerLogLines, 2, 15);
        activityRedstoneMin = clampInt(activityRedstoneMin, 2, 60);
        soundSeconds = clampInt(soundSeconds, 2, 30);
        huntX = clampInt(huntX, 0, 3000);
        huntY = clampInt(huntY, 0, 3000);
        portalX = clampInt(portalX, 0, 3000);
        portalY = clampInt(portalY, 0, 3000);
        if (portalSaved == null) portalSaved = new java.util.ArrayList<>();
        portalSaved.removeIf(p -> p == null || (p.dim != 0 && p.dim != 1));
        marketMinPercent = clampInt(marketMinPercent, 5, 95);
        marketMinProfit = clampInt(marketMinProfit, 0, 1000000);
        marketFee = clampInt(marketFee, 0, 30);
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
