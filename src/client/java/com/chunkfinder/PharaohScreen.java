package com.chunkfinder;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Pharaoh menu: one window with tabs on top, categories on the left and a card for every tool.
 * Nothing is scaled, so the text stays sharp.
 */
public class PharaohScreen extends Screen {
    private static final int ROW = 15;

    private static final Identifier UI_FONT = Identifier.of("chunkfinder", "ui");
    private static final Identifier BOLD_FONT = Identifier.of("chunkfinder", "ui_bold");

    /** Smooth font (Poppins) so the menu text isn't blocky. */
    private static net.minecraft.text.MutableText ui(String s) {
        return Text.literal(s).styled(st -> st.withFont(UI_FONT));
    }

    private int uw(String s) {
        return this.textRenderer.getWidth(ui(s));
    }

    private String trim(String s, int max) {
        if (uw(s) <= max) return s;
        String t = s;
        while (!t.isEmpty() && uw(t + "..") > max) t = t.substring(0, t.length() - 1);
        return t + "..";
    }

    private static final int ACCENT = 0xFFE0A526;
    private static final int TEXT = 0xFFF4ECD6;
    private static final int DIM = 0xFFA39A80;

    // ---------------------------------------------------------------- data

    private static class Setting {
        String label;
        boolean slider;
        BooleanSupplier get;
        Consumer<Boolean> set;
        int min, max;
        String suffix = "";
        IntSupplier iget;
        IntConsumer iset;
        net.minecraft.client.option.KeyBinding key;
        java.util.function.Supplier<net.minecraft.client.option.KeyBinding> keySupplier;
        String modKeyFor;
        java.util.function.Supplier<int[]> swatch;
    }

    private static class Module {
        String name;
        BooleanSupplier on;
        Consumer<Boolean> setOn;
        List<Setting> settings = new ArrayList<>();
        boolean open = false;
        String desc = "";
        Panel panel;
    }

    private static class Panel {
        String title;
        List<Module> mods = new ArrayList<>();
        int color = ACCENT;
    }

    /** kind: 0 switch, 1 "Configure", 2 star, 3 card, 4 setting row */
    private record Hit(int kind, Module m, Setting s, int x, int y, int w, int h) {}

    private static final List<Panel> PANELS = new ArrayList<>();

    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private Hit dragSlider = null;
    private Setting listening = null;

    private int tab = 0;          // 0 Modules, 1 Favorites, 2 Appearance
    private int category = 0;     // 0 = overview, otherwise an index into the shown categories
    private int scroll = 0;
    private String search = "";
    private boolean searchFocus = false;
    private int wx, wy, ww, wh;
    private int dragHud = 0; // 0 none, 1 staff list, 2 region map, 3 fps bar, 4 player log, 5 armor hud
    private int hudOffX, hudOffY;
    private float d = 1f;
    // smooth animations
    private final java.util.IdentityHashMap<Object, float[]> swAnim = new java.util.IdentityHashMap<>();
    private final java.util.IdentityHashMap<Object, Float> hovAnim = new java.util.IdentityHashMap<>();
    private final java.util.IdentityHashMap<Object, float[]> openAnim = new java.util.IdentityHashMap<>();
    private long lastFrame = System.nanoTime();
    private float dt = 0.016f;
    private final long openedAt = System.currentTimeMillis();
    private float openE = 0f;
    private float scrollShown = 0f, scrollVel = 0f;
    private float tabPillXv = 0, tabPillWv = 0, catPillYv = 0;

    private final java.util.IdentityHashMap<Object, Float> slAnim = new java.util.IdentityHashMap<>();
    /** Animated position of every card in content space: x, y, width. */
    private final java.util.IdentityHashMap<Object, float[]> cardPos = new java.util.IdentityHashMap<>();
    private final java.util.IdentityHashMap<Object, Long> cardAppear = new java.util.IdentityHashMap<>();
    private final float[] catHov = new float[24];
    private final float[] tabHov = new float[4];
    private final float[] btnHov = new float[3];
    private float tabPillX = -1, tabPillW = 0, catPillY = -1, catPillA = 0, searchAnim = 0, barAlpha = 0;
    private int lastPage = -1, lastMaxScroll = 0, lastContentH = 0, lastViewH = 1;
    private long lastWheel = 0;
    private boolean closing = false;
    private boolean openSoundPlayed = false;
    private int lastSliderValue = Integer.MIN_VALUE;
    private long closeAt = 0;

    /** Frame rate independent smoothing: the same speed at 30 fps and at 240 fps. */
    private float k(float speed) {
        return 1f - (float) Math.exp(-dt * speed);
    }

    private float step(float v, float target, float speed) {
        v += (target - v) * k(speed);
        return Math.abs(target - v) < 0.002f ? target : v;
    }

    /** Critically damped spring: starts gently, glides, and settles without overshoot, at any frame rate. */
    private void spring(float[] s, float target, float omega) {
        float x = s[0] - target, v = s[1];
        float e = (float) Math.exp(-omega * dt);
        float c2 = v + omega * x;
        s[0] = target + (x + c2 * dt) * e;
        s[1] = (v - omega * c2 * dt) * e;
        if (Math.abs(s[0] - target) < 0.003f && Math.abs(s[1]) < 0.02f) { s[0] = target; s[1] = 0f; }
    }

    private float springAnim(java.util.IdentityHashMap<Object, float[]> map, Object key, float target, float omega) {
        float[] s = map.get(key);
        if (s == null) { s = new float[] {target, 0f}; map.put(key, s); }
        spring(s, target, omega);
        return s[0];
    }

    /** Spring on two slots of an array: value at pi, velocity at vi. */
    private void springAt(float[] a, int pi, int vi, float target, float omega) {
        float[] t = {a[pi], a[vi]};
        spring(t, target, omega);
        a[pi] = t[0];
        a[vi] = t[1];
    }

    private float anim(java.util.IdentityHashMap<Object, Float> map, Object key, float target, float speed) {
        Float cur = map.get(key);
        float v = step(cur == null ? target : cur, target, speed);
        map.put(key, v);
        return v;
    }

    private static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float u = 1f - t;
        return 1f - u * u * u * u;
    }
    private int vw = 0, vh = 0;

    public PharaohScreen(Screen parent) {
        super(Text.of("Pharaoh"));
        this.parent = parent;
    }

    // ---------------------------------------------------------------- building

    private static Setting toggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
        Setting s = new Setting();
        s.label = label;
        s.get = get;
        s.set = set;
        return s;
    }

    private static Setting slider(String label, int min, int max, String suffix, IntSupplier get, IntConsumer set) {
        Setting s = new Setting();
        s.label = label;
        s.slider = true;
        s.min = min;
        s.max = max;
        s.suffix = suffix;
        s.iget = get;
        s.iset = set;
        return s;
    }

    private static Setting keybind(java.util.function.Supplier<net.minecraft.client.option.KeyBinding> kb) {
        return keybind("Keybind", kb);
    }

    private static Setting keybind(String label, java.util.function.Supplier<net.minecraft.client.option.KeyBinding> kb) {
        Setting s = new Setting();
        s.label = label;
        s.keySupplier = kb;
        return s;
    }

    private static void addColor(java.util.List<Setting> out, String name, java.util.function.Supplier<int[]> get) {
        Setting sw = new Setting();
        sw.label = name + " Color";
        sw.swatch = get;
        out.add(sw);
        String[] ch = {"Red", "Green", "Blue"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            out.add(slider("  " + ch[i], 0, 255, "", () -> get.get()[idx], v -> get.get()[idx] = v));
        }
    }

    private static final java.util.Map<String, String> DESC = java.util.Map.ofEntries(
        java.util.Map.entry("Chunk Finder", "Geodes, bases & player activity"),
        java.util.Map.entry("Chunk Colors", "Colors of the flagged chunks"),
        java.util.Map.entry("Region Map", "Donut regions with your position"),
        java.util.Map.entry("Name Protector", "Hides your name on your screen"),
        java.util.Map.entry("FPS Counter", "Logo with your FPS"),
        java.util.Map.entry("Player ESP", "See players through walls"),
        java.util.Map.entry("Chest ESP", "Chests, spawners & storage"),
        java.util.Map.entry("Ore ESP", "Ores & gilded blackstone"),
        java.util.Map.entry("Freecam", "Explore with a detached camera"),
        java.util.Map.entry("Spectator Alert", "Warns you when someone spectates"),
        java.util.Map.entry("Staff Tracker", "Shows which staff are online"),
        java.util.Map.entry("Menu", "The key that opens this menu"),
        java.util.Map.entry("Tool List", "On-screen list of active tools"));

    private static Module module(String name, BooleanSupplier on, Consumer<Boolean> setOn, Setting... settings) {
        Module m = new Module();
        m.name = name;
        m.on = on;
        m.setOn = setOn;
        m.desc = DESC.getOrDefault(name, "");
        boolean hasKey = false;
        for (Setting s : settings) if (s.keySupplier != null) hasKey = true;
        if (!hasKey && !name.equals("Chunk Colors")) {
            Setting k = new Setting();
            k.label = "Toggle Key";
            k.modKeyFor = name;
            m.settings.add(k);
        }
        for (Setting s : settings) m.settings.add(s);
        return m;
    }

    private static Panel panel(String title, int color, Module... mods) {
        Panel p = new Panel();
        p.title = title;
        p.color = color;
        for (Module m : mods) {
            m.panel = p;
            p.mods.add(m);
        }
        return p;
    }

    /** Builds the tool list early, so the toggle keys work before the menu is ever opened. */
    public static void ensureBuilt() {
        buildPanels();
    }

    private static final java.util.Map<String, Boolean> KEY_WAS_DOWN = new java.util.HashMap<>();

    /** Called every tick: switches a tool when its toggle key is pressed. */
    public static void pollKeys(net.minecraft.client.MinecraftClient mc) {
        if (mc.player == null || mc.getWindow() == null) return;
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        long window = mc.getWindow().getHandle();
        boolean free = mc.currentScreen == null;
        for (Panel p : PANELS) {
            for (Module m : p.mods) {
                Integer code = cfg.moduleKeys.get(m.name);
                if (code == null || code <= 0) continue;
                boolean down = net.minecraft.client.util.InputUtil.isKeyPressed(window, code);
                boolean was = KEY_WAS_DOWN.getOrDefault(m.name, false);
                KEY_WAS_DOWN.put(m.name, down);
                if (down && !was && free) {
                    boolean now = !m.on.getAsBoolean();
                    m.setOn.accept(now);
                    ChunkFinderConfig.save();
                    ChunkFinderClient.clearAll();
                    mc.player.sendMessage(ui(m.name + ": " + (now ? "ON" : "OFF")), true);
                }
            }
        }
    }

    private static Setting[] chunkColorSettings(ChunkFinderConfig c) {
        java.util.List<Setting> out = new java.util.ArrayList<>();
        addColor(out, "Sus Chunk", () -> c.susColor);
        out.add(slider("Sus Opacity", 5, 95, "%", () -> c.susAlpha, v -> c.susAlpha = v));
        addColor(out, "Activity", () -> c.activityColor);
        addColor(out, "Geode", () -> c.geodeColor);
        addColor(out, "Light", () -> c.lightColor);
        return out.toArray(new Setting[0]);
    }

    private static void buildPanels() {
        if (!PANELS.isEmpty()) return;
        ChunkFinderConfig c = ChunkFinderConfig.INSTANCE;

        PANELS.add(panel("Visuals", 0xFFE0A526,
            module("Chunk Finder", () -> c.enabled, v -> c.enabled = v,
                keybind(ChunkFinderClient::toggleKey),
                slider("Sensitivity", 1, 10, "", () -> c.sensitivity, v -> c.sensitivity = v),
                slider("Simulation Dist", 2, 32, "", () -> c.simulationDistance, v -> c.simulationDistance = v),
                toggle("Geodes", () -> c.detectGeodes, v -> c.detectGeodes = v),
                toggle("Activity", () -> c.detectActivity, v -> c.detectActivity = v),
                toggle("Sus Chunks", () -> c.detectSus, v -> c.detectSus = v),
                toggle("Fast Scan (all scanners, more when flying)", () -> c.fastScan, v -> c.fastScan = v),
                toggle("Light Finder", () -> c.detectLight, v -> c.detectLight = v),
                slider("Min Lights", 1, 20, "", () -> c.lightMin, v -> c.lightMin = v),
                slider("Sus Sensitivity", 1, 10, "", () -> c.susSensitivity, v -> c.susSensitivity = v),
                toggle("Tall Columns", () -> c.tallColumns, v -> c.tallColumns = v),
                toggle("Sus Chunk List", () -> c.susList, v -> c.susList = v),
                toggle("Colour By Strength", () -> c.susScoreColors, v -> c.susScoreColors = v),
                toggle("Labels On Strongest", () -> c.susLabels, v -> c.susLabels = v),
                toggle("Exact Spots (No Flat Slabs)", () -> c.exactSpots, v -> c.exactSpots = v),
                toggle("Tracer To Strongest", () -> c.susTracer, v -> c.susTracer = v),
                slider("List Length", 1, 12, "", () -> c.susListCount, v -> c.susListCount = v),
                toggle("Chat Alerts", () -> c.notify, v -> c.notify = v),
                slider("Render Height", -64, 320, "", () -> c.renderY, v -> c.renderY = v)),
            module("Chunk Colors", () -> true, v -> { }, chunkColorSettings(c)),
            module("Region Map", () -> c.regionMapEnabled, v -> c.regionMapEnabled = v,
                slider("Map Size", 6, 30, "", () -> c.regionMapCell, v -> c.regionMapCell = v)),
            module("FPS Counter", () -> c.fpsEnabled, v -> c.fpsEnabled = v),
            module("Player ESP", () -> c.playerEspEnabled, v -> c.playerEspEnabled = v,
                slider("Range", 16, 1024, "m", () -> c.playerEspRange, v -> c.playerEspRange = v),
                toggle("Names", () -> c.playerEspNames, v -> c.playerEspNames = v),
                toggle("Distance", () -> c.playerEspDistance, v -> c.playerEspDistance = v),
                toggle("Filled", () -> c.playerEspFill, v -> c.playerEspFill = v),
                toggle("Tracers", () -> c.playerEspTracers, v -> c.playerEspTracers = v),
                toggle("Self In Freecam", () -> c.playerEspSelfInFreecam, v -> c.playerEspSelfInFreecam = v)),
            module("Session Timer", () -> c.timerEnabled, v -> c.timerEnabled = v,
                keybind("Stopwatch Start / Stop Key", ChunkFinderClient::stopwatchKey),
                keybind("Stopwatch Reset Key", ChunkFinderClient::stopwatchResetKey),
                toggle("Time Online", () -> c.timerShowSession, v -> c.timerShowSession = v),
                toggle("Stopwatch", () -> c.timerShowStopwatch, v -> c.timerShowStopwatch = v),
                toggle("Computer Clock", () -> c.timerShowClock, v -> c.timerShowClock = v)),
            module("Player Logger", () -> c.playerLogEnabled, v -> c.playerLogEnabled = v,
                toggle("Show List", () -> c.playerLogShowList, v -> c.playerLogShowList = v),
                slider("List Lines", 2, 15, "", () -> c.playerLogLines, v -> c.playerLogLines = v),
                toggle("Chat: Player Came Into Range", () -> c.playerLogChatEnter, v -> c.playerLogChatEnter = v),
                toggle("Chat: Player Left Range", () -> c.playerLogChatLeave, v -> c.playerLogChatLeave = v)),
            module("Armor HUD", () -> c.armorEnabled, v -> c.armorEnabled = v,
                slider("Size", 50, 250, "%", () -> c.armorScale, v -> c.armorScale = v),
                toggle("Side By Side", () -> c.armorHorizontal, v -> c.armorHorizontal = v),
                toggle("Durability Numbers (1850/2031)", () -> c.armorNumber, v -> c.armorNumber = v),
                toggle("Durability Percent", () -> c.armorText, v -> c.armorText = v),
                toggle("Durability Bar", () -> c.armorBar, v -> c.armorBar = v),
                toggle("Show Hand Items", () -> c.armorHands, v -> c.armorHands = v),
                toggle("Show Empty Slots", () -> c.armorShowEmpty, v -> c.armorShowEmpty = v),
                toggle("Background", () -> c.armorBackground, v -> c.armorBackground = v)),
            module("Activity Detector", () -> c.activityEnabled, v -> c.activityEnabled = v,
                slider("Redstone Parts To Flag", 2, 60, "", () -> c.activityRedstoneMin, v -> c.activityRedstoneMin = v),
                toggle("Redstone Changes", () -> c.activityRedstone, v -> c.activityRedstone = v),
                toggle("Chest Changes", () -> c.activityStorage, v -> c.activityStorage = v),
                toggle("Show Redstone Chunks", () -> c.activityStatic, v -> c.activityStatic = v),
                toggle("Labels", () -> c.activityLabels, v -> c.activityLabels = v),
                toggle("Chat Alerts", () -> c.activityChat, v -> c.activityChat = v)),
            module("Reach Test", () -> c.reachEnabled, v -> c.reachEnabled = v,
                slider("Panel X", 0, 600, "", () -> c.reachX, v -> c.reachX = v),
                slider("Panel Y", 0, 400, "", () -> c.reachY, v -> c.reachY = v)),
            module("Market Helper", () -> c.marketEnabled, v -> c.marketEnabled = v,
                slider("Min Discount", 5, 95, "%", () -> c.marketMinPercent, v -> c.marketMinPercent = v),
                slider("Min Total Profit", 0, 100000, "", () -> c.marketMinProfit, v -> c.marketMinProfit = v),
                slider("Sale Fee", 0, 30, "%", () -> c.marketFee, v -> c.marketFee = v),
                toggle("Listed Price Is Per Item", () -> c.marketPerItem, v -> c.marketPerItem = v),
                toggle("Best Flips Panel", () -> c.marketPanel, v -> c.marketPanel = v),
                toggle("Forget Learned Prices", () -> false, v -> { if (v) MarketHelper.reset(); })),
            module("Portal Calc", () -> c.portalEnabled, v -> c.portalEnabled = v,
                toggle("Learn Portals When I Walk Through", () -> c.portalAuto, v -> c.portalAuto = v),
                toggle("Show Link Zones In World", () -> c.portalMarks, v -> c.portalMarks = v),
                toggle("Also Convert Where I Look", () -> c.portalLook, v -> c.portalLook = v),
                keybind("Save Portal Here Key", ChunkFinderClient::portalSaveKey),
                keybind("Remove Nearest Portal Key", ChunkFinderClient::portalDeleteKey),
                toggle("Clear Typed Target (/pgoto)", () -> false, v -> { if (v) PortalCalc.clearTarget(); }),
                toggle("Forget All Portals On This Server", () -> false, v -> { if (v) PortalCalc.clearServer(); })),
            module("Sound Radar", () -> c.soundEnabled, v -> c.soundEnabled = v,
                slider("Show For", 2, 30, " s", () -> c.soundSeconds, v -> c.soundSeconds = v),
                toggle("Mark Block In World", () -> c.soundMarks, v -> c.soundMarks = v),
                toggle("Doors And Gates", () -> c.soundDoors, v -> c.soundDoors = v),
                toggle("Chests And Barrels", () -> c.soundStorage, v -> c.soundStorage = v),
                toggle("Digging", () -> c.soundDig, v -> c.soundDig = v),
                toggle("Pistons, Furnaces, Hoppers", () -> c.soundMachines, v -> c.soundMachines = v)),
            module("Cave Finder", () -> c.caveEnabled, v -> c.caveEnabled = v,
                toggle("Exact Outline (see inside)", () -> c.caveExact, v -> c.caveExact = v),
                toggle("Deep Mode (below y0, every second)", () -> c.caveDeep, v -> c.caveDeep = v),
                slider("Smallest Hollow", 8, 500, " blocks", () -> c.caveMin, v -> c.caveMin = v),
                slider("Range (chunks)", 2, 24, "", () -> c.caveRange, v -> c.caveRange = v),
                toggle("Show Caves (yellow)", () -> c.caveCaves, v -> c.caveCaves = v),
                toggle("Show Rooms And Bases (orange)", () -> c.caveRooms, v -> c.caveRooms = v),
                toggle("Filled Caves", () -> c.caveFill, v -> c.caveFill = v)),
            module("Farm Finder", () -> c.farmEnabled, v -> c.farmEnabled = v,
                slider("Entities To Flag", 3, 60, "", () -> c.farmMin, v -> c.farmMin = v),
                toggle("Underground Only", () -> c.farmUnderground, v -> c.farmUnderground = v),
                toggle("Labels", () -> c.farmLabels, v -> c.farmLabels = v),
                toggle("Tracers", () -> c.farmTracer, v -> c.farmTracer = v),
                toggle("Chat Alerts", () -> c.farmChat, v -> c.farmChat = v)),
            module("Sweep Map", () -> c.sweepEnabled, v -> c.sweepEnabled = v,
                slider("Map Radius (chunks)", 5, 24, "", () -> c.sweepRadius, v -> c.sweepRadius = v),
                toggle("Show Sus And Farms", () -> c.sweepFlags, v -> c.sweepFlags = v)),
            module("Entity ESP", () -> c.entityEspEnabled, v -> c.entityEspEnabled = v,
                slider("Range", 8, 512, "m", () -> c.entityEspRange, v -> c.entityEspRange = v),
                toggle("Item Frames (With Items)", () -> c.entityEspFrames, v -> c.entityEspFrames = v),
                toggle("Armor Stands", () -> c.entityEspStands, v -> c.entityEspStands = v),
                toggle("Chest Boats / Storage Carts / TNT", () -> c.entityEspVehicles, v -> c.entityEspVehicles = v),
                toggle("End Crystals", () -> c.entityEspCrystals, v -> c.entityEspCrystals = v),
                toggle("Filled", () -> c.entityEspFill, v -> c.entityEspFill = v),
                toggle("Labels", () -> c.entityEspLabels, v -> c.entityEspLabels = v)),
            module("Item ESP", () -> c.itemEspEnabled, v -> c.itemEspEnabled = v,
                slider("Range", 8, 512, "m", () -> c.itemEspRange, v -> c.itemEspRange = v),
                toggle("Show All Items", () -> c.itemEspAll, v -> c.itemEspAll = v),
                toggle("Filled", () -> c.itemEspFill, v -> c.itemEspFill = v),
                toggle("Labels", () -> c.itemEspLabels, v -> c.itemEspLabels = v),
                toggle("Tracers (Valuable)", () -> c.itemEspTracers, v -> c.itemEspTracers = v),
                toggle("Chat Alert: Rare Items", () -> c.itemEspAlert, v -> c.itemEspAlert = v)),
            module("Cluster ESP", () -> c.clusterEnabled, v -> c.clusterEnabled = v,
                slider("Sensitivity (low = more)", 3, 20, "", () -> c.clusterSensitivity, v -> c.clusterSensitivity = v),
                slider("Join Distance", 10, 64, " blocks", () -> c.clusterJoin, v -> c.clusterJoin = v),
                toggle("Filled", () -> c.clusterFill, v -> c.clusterFill = v),
                toggle("Labels", () -> c.clusterLabels, v -> c.clusterLabels = v),
                toggle("Mark Each Part", () -> c.clusterParts, v -> c.clusterParts = v),
                toggle("Tracer To Strongest", () -> c.clusterTracer, v -> c.clusterTracer = v),
                toggle("Count Villagers", () -> c.clusterUseVillagers, v -> c.clusterUseVillagers = v),
                toggle("Count Flagged Chunks", () -> c.clusterUseSus, v -> c.clusterUseSus = v),
                slider("Scan Range", 16, 2048, "m", () -> c.chestEspRange, v -> c.chestEspRange = v),
                toggle("Count Chests", () -> c.espChest, v -> c.espChest = v),
                toggle("Count Trapped Chests", () -> c.espTrapped, v -> c.espTrapped = v),
                toggle("Count Barrels", () -> c.espBarrel, v -> c.espBarrel = v),
                toggle("Count Ender Chests", () -> c.espEnder, v -> c.espEnder = v),
                toggle("Count Shulkers", () -> c.espShulker, v -> c.espShulker = v),
                toggle("Count Spawners", () -> c.espSpawner, v -> c.espSpawner = v),
                toggle("Count Beacons", () -> c.espBeacon, v -> c.espBeacon = v),
                toggle("Count Beds", () -> c.espBed, v -> c.espBed = v),
                toggle("Count Chest Minecarts", () -> c.espMinecart, v -> c.espMinecart = v),
                toggle("Chat Alert", () -> c.clusterAlert, v -> c.clusterAlert = v),
                toggle("Alert: Spawners", () -> c.alertSpawners, v -> c.alertSpawners = v),
                toggle("Alert: Stashes", () -> c.alertStash, v -> c.alertStash = v),
                toggle("Alert: Bases", () -> c.alertBases, v -> c.alertBases = v)),
            module("Ore ESP", () -> c.oreEspEnabled, v -> c.oreEspEnabled = v,
                slider("Range (chunks)", 1, 12, "", () -> c.oreEspRange, v -> c.oreEspRange = v),
                toggle("Diamond", () -> c.oreDiamond, v -> c.oreDiamond = v),
                toggle("Ancient Debris", () -> c.oreDebris, v -> c.oreDebris = v),
                toggle("Emerald", () -> c.oreEmerald, v -> c.oreEmerald = v),
                toggle("Gold", () -> c.oreGold, v -> c.oreGold = v),
                toggle("Iron", () -> c.oreIron, v -> c.oreIron = v),
                toggle("Coal", () -> c.oreCoal, v -> c.oreCoal = v),
                toggle("Redstone", () -> c.oreRedstone, v -> c.oreRedstone = v),
                toggle("Lapis", () -> c.oreLapis, v -> c.oreLapis = v),
                toggle("Copper", () -> c.oreCopper, v -> c.oreCopper = v),
                toggle("Gilded Blackstone", () -> c.oreGilded, v -> c.oreGilded = v),
                toggle("Netherite Block", () -> c.oreNetherite, v -> c.oreNetherite = v),
                toggle("Chat Alert: Rare Finds", () -> c.alertRareOres, v -> c.alertRareOres = v),
                toggle("Filled", () -> c.oreFill, v -> c.oreFill = v),
                toggle("Tracers", () -> c.oreTracers, v -> c.oreTracers = v))));

        PANELS.add(panel("Movement", 0xFFF0C14B,
            module("Freecam", () -> c.freecamEnabled, v -> c.freecamEnabled = v,
                keybind(ChunkFinderClient::freecamKey),
                slider("Speed", 1, 60, "", () -> c.freecamSpeed, v -> c.freecamSpeed = v))));

        PANELS.add(panel("Alerts", 0xFFD9822B,
            module("Spectator Alert", () -> c.spectatorEnabled, v -> c.spectatorEnabled = v,
                slider("Range", 4, 128, "m", () -> c.spectatorRange, v -> c.spectatorRange = v),
                slider("Head Size", 10, 100, "%", () -> c.spectatorHeadPercent, v -> c.spectatorHeadPercent = v),
                slider("Popup Seconds", 1, 10, "s", () -> c.spectatorPopupSeconds, v -> c.spectatorPopupSeconds = v),
                slider("Cooldown (min)", 0, 120, "", () -> c.spectatorCooldownMinutes, v -> c.spectatorCooldownMinutes = v),
                toggle("Sound", () -> c.spectatorSound, v -> c.spectatorSound = v),
                toggle("Guardian Effect", () -> c.spectatorGuardian, v -> c.spectatorGuardian = v),
                toggle("Tab List Detect", () -> c.spectatorTabList, v -> c.spectatorTabList = v),
                toggle("Test Alert (click)", () -> false, v -> {
                    net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
                    mc.setScreen(null);
                    SpectatorAlert.test(mc);
                })),
            module("Staff Tracker", () -> c.staffEnabled, v -> c.staffEnabled = v,
                toggle("Show List", () -> c.staffShowList, v -> c.staffShowList = v),
                slider("Staff Rank No. (max)", 0, 20, "", () -> c.staffMaxRank, v -> c.staffMaxRank = v),
                toggle("Join / Leave Alerts", () -> c.staffAlerts, v -> c.staffAlerts = v),
                toggle("Print Tab Tags (click)", () -> false, v -> {
                    net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
                    mc.setScreen(null);
                    StaffTracker.printTags(mc);
                }))));

        PANELS.add(panel("Client", 0xFFE0A526,
            module("Menu", () -> true, v -> { },
                keybind("Menu Key", ChunkFinderClient::menuKey),
                slider("Menu Size (applies on release)", 60, 200, "%", () -> c.menuScale, v -> c.menuScale = v),
                toggle("Menu Sounds", () -> c.menuSounds, v -> c.menuSounds = v),
                toggle("Focus Cards (one info card at a time)", () -> c.focusCards, v -> c.focusCards = v),
                slider("Sound Volume", 0, 100, "%", () -> c.menuSoundVolume, v -> c.menuSoundVolume = v)),
            module("Name Protector", () -> c.nameProtect, v -> c.nameProtect = v),
            module("Tool List", () -> c.showModuleList, v -> c.showModuleList = v)));
    }

    private static int mix(int c, int base, float t) {
        int r = (int) (((c >> 16) & 255) * t + ((base >> 16) & 255) * (1 - t));
        int g = (int) (((c >> 8) & 255) * t + ((base >> 8) & 255) * (1 - t));
        int b = (int) ((c & 255) * t + (base & 255) * (1 - t));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int alpha(int c, int a) {
        return (a << 24) | (c & 0xFFFFFF);
    }

    private static void round(DrawContext c, int x1, int y1, int x2, int y2, int color, int r) {
        r = Math.max(0, Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
        if (r == 0) {
            c.fill(x1, y1, x2, y2, color);
            return;
        }
        c.fill(x1, y1 + r, x2, y2 - r, color);
        for (int dy = 0; dy < r; dy++) {
            double d = r - dy - 0.5;
            int inset = r - (int) Math.floor(Math.sqrt(r * r - d * d));
            c.fill(x1 + inset, y1 + dy, x2 - inset, y1 + dy + 1, color);
            c.fill(x1 + inset, y2 - 1 - dy, x2 - inset, y2 - dy, color);
        }
    }

    /** A properly round disc, drawn row by row. */
    private static void disc(DrawContext c, float cx, float cy, float r, int color) {
        for (int dy = (int) -Math.ceil(r); dy <= (int) Math.ceil(r); dy++) {
            float yy = dy + 0.5f;
            if (Math.abs(yy) > r) continue;
            float half = (float) Math.sqrt(r * r - yy * yy);
            int x1 = Math.round(cx - half), x2 = Math.round(cx + half);
            if (x2 > x1) c.fill(x1, (int) cy + dy, x2, (int) cy + dy + 1, color);
        }
    }

    private static void capsule(DrawContext c, int x, int y, int w, int h, int color) {
        float r = h / 2f;
        disc(c, x + r, y + r, r, color);
        disc(c, x + w - r, y + r, r, color);
        c.fill((int) (x + r), y, (int) (x + w - r), y + h, color);
    }

    private static void pill(DrawContext c, int x, int y, float t, int col) {
        // round switch: blue when on, white when off, the knob slides
        capsule(c, x - 1, y - 1, 34, 20, mix(0xFF9CC8FF, 0xFFFFFFFF, t));
        capsule(c, x, y, 32, 18, mix(0xFF2F7BFF, 0xFFDDE6F5, t));
        disc(c, x + 9 + 14 * t, y + 9, 7f, mix(0xFFFFFFFF, 0xFF2F7BFF, t));
    }

    private static void plusButton(DrawContext c, int x, int y, float open) {
        round(c, x, y, x + 18, y + 18, 0xFFFFE08A, 4);
        round(c, x + 1, y + 1, x + 17, y + 17, 0xFF2A2314, 4);
        c.fill(x + 4, y + 8, x + 14, y + 10, 0xFFFFE08A);
        int bar = Math.round(10 * (1f - open));
        if (bar > 0) c.fill(x + 8, y + 9 - bar / 2, x + 10, y + 9 + (bar + 1) / 2, 0xFFFFE08A);
    }

    private static final String[] STAR = {
        "...#...", "...#...", "#######", ".#####.", "..###..", ".##.##.", ".#...#."
    };

    private static void star(DrawContext c, int x, int y, int color) {
        // rounded five point star: polygon + round tips + a round middle, with a dark shadow
        for (int pass = 0; pass < 2; pass++) {
            float cx = x + 7 + (pass == 0 ? 1 : 0), cy = y + 7.6f + (pass == 0 ? 1 : 0);
            int col = pass == 0 ? 0xFF000000 : color;
            float R = 8f, r = 4.6f;
            float[] px = new float[10], py = new float[10];
            for (int i = 0; i < 10; i++) {
                double ang = -Math.PI / 2 + i * Math.PI / 5;
                float rad = (i % 2 == 0) ? R : r;
                px[i] = cx + (float) Math.cos(ang) * rad;
                py[i] = cy + (float) Math.sin(ang) * rad;
            }
            for (int row = (int) Math.floor(cy - R); row <= (int) Math.ceil(cy + R); row++) {
                float yy = row + 0.5f;
                float[] xs = new float[10];
                int n = 0;
                for (int i = 0; i < 10; i++) {
                    int j = (i + 1) % 10;
                    if ((py[i] <= yy && py[j] > yy) || (py[j] <= yy && py[i] > yy)) {
                        xs[n++] = px[i] + (yy - py[i]) * (px[j] - px[i]) / (py[j] - py[i]);
                    }
                }
                java.util.Arrays.sort(xs, 0, n);
                for (int k = 0; k + 1 < n; k += 2) {
                    c.fill(Math.round(xs[k]), row, Math.round(xs[k + 1]), row + 1, col);
                }
            }
            for (int i = 0; i < 10; i += 2) disc(c, px[i] + (px[i] < cx ? 0.6f : px[i] > cx ? -0.6f : 0f),
                py[i] + (i == 0 ? 0.8f : 0f), 1.4f, col);
            disc(c, cx, cy, 3.6f, col);
        }
    }

    private static String keyName(int code) {
        if (code <= 0) return "None";
        return net.minecraft.client.util.InputUtil.fromKeyCode(code, 0).getLocalizedText().getString();
    }

    private static Setting keySetting(Module m) {
        for (Setting k : m.settings) if (k.modKeyFor != null || k.keySupplier != null) return k;
        return null;
    }

    private static String currentKey(Module m) {
        Setting k = keySetting(m);
        if (k == null) return "None";
        if (k.modKeyFor != null) {
            Integer kc = ChunkFinderConfig.INSTANCE.moduleKeys.get(k.modKeyFor);
            return keyName(kc == null ? 0 : kc);
        }
        return k.keySupplier.get().getBoundKeyLocalizedText().getString();
    }

    /** Puts a key setting back to its default: no key for tool keys, J / K / F4 for the main ones. */
    private static void resetKey(Setting k) {
        if (k.modKeyFor != null) {
            ChunkFinderConfig.INSTANCE.moduleKeys.remove(k.modKeyFor);
            ChunkFinderConfig.save();
        } else if (k.keySupplier != null) {
            net.minecraft.client.option.KeyBinding kb = k.keySupplier.get();
            ChunkFinderClient.rebind(kb, kb.getDefaultKey().getCode());
        }
    }

    private static boolean isFavorite(Module m) {
        return ChunkFinderConfig.INSTANCE.favoriteModules.contains(m.name);
    }

    // ---------------------------------------------------------------- which tools to show

    private List<Panel> categories() {
        List<Panel> out = new ArrayList<>();
        for (Panel p : PANELS) {
            for (Module m : p.mods) {
                if (!APPEARANCE.contains(m.name)) {
                    out.add(p);
                    break;
                }
            }
        }
        return out;
    }

    private static final java.util.Set<String> APPEARANCE = java.util.Set.of("Chunk Colors", "Tool List", "Menu");

    private List<Module> shown() {
        List<Module> out = new ArrayList<>();
        List<Panel> cats = categories();
        for (Panel p : PANELS) {
            for (Module m : p.mods) {
                boolean app = APPEARANCE.contains(m.name);
                boolean take;
                if (tab == 0) {
                    take = !app && (category == 0 || (category - 1 < cats.size() && cats.get(category - 1) == p));
                } else if (tab == 1) {
                    take = isFavorite(m);
                } else {
                    take = app;
                }
                if (!take) continue;
                if (!search.isEmpty()) {
                    String q = search.toLowerCase(java.util.Locale.ROOT);
                    if (!m.name.toLowerCase(java.util.Locale.ROOT).contains(q)
                        && !m.desc.toLowerCase(java.util.Locale.ROOT).contains(q)) continue;
                }
                out.add(m);
            }
        }
        return out;
    }

    private String heading() {
        if (tab == 1) return "Favorites";
        if (tab == 2) return "Appearance";
        List<Panel> cats = categories();
        if (category == 0 || category - 1 >= cats.size()) return "All modules";
        return cats.get(category - 1).title + " modules";
    }

    private String subheading() {
        if (tab == 1) return "Your starred modules.";
        if (tab == 2) return "Colors, keys and the on-screen list.";
        List<Panel> cats = categories();
        if (category == 0 || category - 1 >= cats.size()) return "Everything Pharaoh can do.";
        return switch (cats.get(category - 1).title) {
            case "Visuals" -> "A clearer view of your world.";
            case "Movement" -> "Move and look your way.";
            case "Alerts" -> "Know who is around you.";
            default -> "";
        };
    }

    // ---------------------------------------------------------------- init

    @Override
    protected void init() {
        if (!openSoundPlayed) { openSoundPlayed = true; MenuSounds.open(); }
        buildPanels();
        // Draw in finer units: one unit is about one real screen pixel per font pixel, so shapes and text are crisp.
        int s = this.client != null ? (int) Math.round(this.client.getWindow().getScaleFactor()) : 2;
        d = 100f / ChunkFinderConfig.INSTANCE.menuScale;
        vw = (int) (this.width * d);
        vh = (int) (this.height * d);
        ww = Math.min(vw - 16, 720);
        wh = Math.min(vh - 16, 400);
        wx = (vw - ww) / 2;
        wy = (vh - wh) / 2;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        try {
            long nowNs = System.nanoTime();
            dt = Math.min(0.05f, (nowNs - lastFrame) / 1_000_000_000f);
            lastFrame = nowNs;
            long nowMs = System.currentTimeMillis();
            if (closing) {
                float q = (nowMs - closeAt) / 200f;
                if (q >= 1f) { doClose(); return; }
                openE = 1f - q * q * q;
            } else {
                float p = Math.min(1f, (nowMs - openedAt) / 380f);
                float e = easeOut(p);
                openE = Math.min(1f, e);
            }
            c.getMatrices().push();
            try {
                c.getMatrices().scale(1f / d, 1f / d, 1f);
                float cx = wx + ww / 2f, cy = wy + wh / 2f;
                c.getMatrices().translate(cx, cy + (1f - openE) * 18f, 0f);
                float sc = 0.94f + 0.06f * openE;
                c.getMatrices().scale(sc, sc, 1f);
                c.getMatrices().translate(-cx, -cy, 0f);
                renderInner(c, (int) (mx * d), (int) (my * d), delta);
            } finally {
                c.getMatrices().pop();
            }
        } catch (Throwable t) {
            ChunkFinderClient.LOGGER.error("Menu drawing failed, closing the menu", t);
            try { c.disableScissor(); } catch (Throwable ignored) { }
            doClose();
        }
    }

    private int tabX(int i) {
        int total = 0;
        for (String t : TABS) total += uw(t) + 26;
        int x = wx + (ww - total) / 2;
        for (int k = 0; k < i; k++) x += uw(TABS[k]) + 26;
        return x;
    }

    private static final String[] TABS = {"Modules", "Favorites", "Appearance"};

    private void renderInner(DrawContext c, int mx, int my, float delta) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        hits.clear();
        c.fill(-vw, -vh, vw * 2, vh * 2, (((int) (0x99 * openE)) << 24) | 0x060504);

        // window
        round(c, wx - 1, wy - 1, wx + ww + 1, wy + wh + 1, 0xFF4A3A16, 7);
        round(c, wx, wy, wx + ww, wy + wh, 0xF20D0B08, 6);
        c.fill(wx + 1, wy + HEADER, wx + ww - 1, wy + HEADER + 1, 0xFF2E2612);
        c.fill(wx + SIDE, wy + HEADER + 8, wx + SIDE + 1, wy + wh - 8, 0xFF2E2612);

        // logo and name
        LogoRenderer.draw(c, wx + 10, wy + 4);
        c.drawTextWithShadow(this.textRenderer, ui("PHARAOH").styled(st -> st.withFont(BOLD_FONT)), wx + 48, wy + 16, ACCENT);

        // tabs: the highlight slides from one tab to the next
        {
            int tx = tabX(tab), tw = uw(TABS[tab]) + 22;
            if (tabPillX < 0) { tabPillX = tx; tabPillW = tw; }
            float[] pxs = {tabPillX, tabPillXv}, pws = {tabPillW, tabPillWv};
            spring(pxs, tx, 17f);
            spring(pws, tw, 17f);
            tabPillX = pxs[0]; tabPillXv = pxs[1]; tabPillW = pws[0]; tabPillWv = pws[1];
            int pix = (int) Math.floor(tabPillX);
            c.getMatrices().push();
            c.getMatrices().translate(tabPillX - pix, 0f, 0f);
            round(c, pix, wy + 9, pix + Math.round(tabPillW), wy + 31, 0xFF221C0F, 5);
            c.getMatrices().pop();
        }
        for (int i = 0; i < TABS.length; i++) {
            int tx = tabX(i);
            int tw = uw(TABS[i]) + 22;
            boolean th = mx >= tx && mx < tx + tw && my >= wy + 9 && my < wy + 31;
            tabHov[i] = step(tabHov[i], i == tab ? 1f : (th ? 0.7f : 0f), 16f);
            c.drawTextWithShadow(this.textRenderer, ui(TABS[i]), tx + 11, wy + 16, mix(TEXT, DIM, tabHov[i]));
        }
        boolean overX = mx >= wx + ww - 26 && mx < wx + ww - 6 && my >= wy + 8 && my < wy + 32;
        btnHov[2] = step(btnHov[2], overX ? 1f : 0f, 18f);
        c.drawTextWithShadow(this.textRenderer, ui("x"), wx + ww - 17, wy + 16, mix(0xFFFFFFFF, DIM, btnHov[2]));

        // size buttons: smaller / bigger menu
        for (int b = 0; b < 2; b++) {
            int bx = wx + ww - 92 + b * 28;
            boolean hv = mx >= bx && mx < bx + 24 && my >= wy + 10 && my < wy + 30;
            btnHov[b] = step(btnHov[b], hv ? 1f : 0f, 18f);
            round(c, bx, wy + 10, bx + 24, wy + 30, mix(0xFF4A3A16, 0xFF221C0F, btnHov[b]), 5);
            String lab = b == 0 ? "A-" : "A+";
            c.drawTextWithShadow(this.textRenderer, ui(lab), bx + 12 - uw(lab) / 2, wy + 16, mix(0xFFFFFFFF, TEXT, btnHov[b]));
        }

        // sidebar: categories (the highlight slides between them)
        List<Panel> cats = categories();
        c.drawTextWithShadow(this.textRenderer, ui("MODULES"), wx + 14, wy + HEADER + 12, DIM);
        {
            float targetY = wy + HEADER + 28 + category * 22;
            if (catPillY < 0) catPillY = targetY;
            float[] cps = {catPillY, catPillYv};
            spring(cps, targetY, 16f);
            catPillY = cps[0]; catPillYv = cps[1];
            catPillA = step(catPillA, tab == 0 ? 1f : 0f, 14f);
            if (catPillA > 0.01f) {
                int py = (int) Math.floor(catPillY);
                c.getMatrices().push();
                c.getMatrices().translate(0f, catPillY - py, 0f);
                round(c, wx + 8, py, wx + SIDE - 8, py + 20, mix(0xFF221C0F, 0xFF0D0B08, catPillA), 5);
                int bar = mix(ACCENT, 0xFF0D0B08, catPillA);
                c.fill(wx + 8, py + 3, wx + 10, py + 17, bar);
                c.getMatrices().pop();
            }
        }
        for (int i = 0; i <= cats.size() && i < catHov.length; i++) {
            int iy = wy + HEADER + 28 + i * 22;
            boolean sel = tab == 0 && category == i;
            boolean hov = mx >= wx + 8 && mx < wx + SIDE - 8 && my >= iy && my < iy + 20;
            catHov[i] = step(catHov[i], sel ? 1f : (hov ? 0.85f : 0f), 16f);
            if (!sel && catHov[i] > 0.02f) {
                round(c, wx + 8, iy, wx + SIDE - 8, iy + 20, mix(0xFF18140C, 0xFF0D0B08, catHov[i]), 5);
            }
            String label = i == 0 ? "Overview" : cats.get(i - 1).title;
            c.drawTextWithShadow(this.textRenderer, ui(label), wx + 18, iy + 6, mix(TEXT, DIM, catHov[i]));
        }

        // sidebar: footer
        int active = 0;
        int total = 0;
        for (Panel p : PANELS) {
            for (Module m : p.mods) {
                if (APPEARANCE.contains(m.name)) continue;
                total++;
                if (m.on.getAsBoolean()) active++;
            }
        }
        c.drawTextWithShadow(this.textRenderer, ui("PHARAOH CLIENT"), wx + 14, wy + wh - 40, DIM);
        c.drawTextWithShadow(this.textRenderer, ui("Fabric 1.21.4"), wx + 14, wy + wh - 28, TEXT);
        c.drawTextWithShadow(this.textRenderer, ui(active + " / " + total + " active"), wx + 14, wy + wh - 16, DIM);

        // content header
        int x0 = wx + SIDE + 12;
        int cw = ww - SIDE - 24;
        c.drawTextWithShadow(this.textRenderer, ui(heading()).styled(st -> st.withFont(BOLD_FONT)), x0, wy + HEADER + 10, TEXT);
        c.drawTextWithShadow(this.textRenderer, ui(subheading()), x0, wy + HEADER + 23, DIM);

        // search box
        int sbw = Math.min(150, cw / 2);
        int sbx = x0 + cw - sbw;
        int sby = wy + HEADER + 9;
        searchAnim = step(searchAnim, searchFocus ? 1f : 0f, 16f);
        round(c, sbx - (searchAnim > 0.4f ? 1 : 0), sby - (searchAnim > 0.4f ? 1 : 0), sbx + sbw + (searchAnim > 0.4f ? 1 : 0),
            sby + 18 + (searchAnim > 0.4f ? 1 : 0), mix(ACCENT, 0xFF2E2612, searchAnim), 5);
        round(c, sbx, sby, sbx + sbw, sby + 18, 0xFF110E09, 5);
        String shownSearch = search.isEmpty() && !searchFocus ? "Search modules..." : trim(search + (searchFocus && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : ""), sbw - 12);
        c.drawTextWithShadow(this.textRenderer, ui(shownSearch), sbx + 6, sby + 5, search.isEmpty() && !searchFocus ? DIM : TEXT);

        // module list: every card glides to its place, so opening one or searching never makes things jump
        int top = wy + HEADER + 40;
        int bottom = wy + wh - 10;
        List<Module> mods = shown();
        int page = tab * 100 + category;
        if (page != lastPage) {
            lastPage = page;
            cardPos.clear();
            cardAppear.clear();
            scrollShown = 0;
            scrollVel = 0;
        }
        {
            float[] ss = {scrollShown, scrollVel};
            spring(ss, scroll, 15f);
            scrollShown = ss[0]; scrollVel = ss[1];
        }
        long nowMs = System.currentTimeMillis();
        int colw = (cw - 8) / 2;
        int ly = 0;          // layout position inside the content (no scroll)
        int col = 0;
        int rowH = 0;
        int idx = 0;
        c.enableScissor(x0 - 2, top, x0 + cw + 2, bottom);
        for (Module m : mods) {
            float oa = springAnim(openAnim, m, m.open ? 1f : 0f, 14f);
            int h = CARD + Math.round((m.settings.size() * ROW + 6) * oa);
            boolean wide = m.open || oa > 0.02f;
            int tx, tw;
            if (wide) {
                if (col == 1) { ly += rowH + 8; col = 0; rowH = 0; }
                tx = 0; tw = cw;
            } else {
                tx = col * (colw + 8); tw = colw;
            }
            int tyLay = ly;
            if (wide) { ly += h + 8; }
            else {
                rowH = Math.max(rowH, h);
                col++;
                if (col == 2) { ly += rowH + 8; col = 0; rowH = 0; }
            }

            float[] pos = cardPos.get(m);
            if (pos == null) {
                pos = new float[] {tx, tyLay + 18, tw, 0f, 0f, 0f};
                cardPos.put(m, pos);
                cardAppear.put(m, nowMs + Math.min(idx, 10) * 30L);
            }
            long appear = cardAppear.getOrDefault(m, 0L);
            float fade = easeOut((nowMs - appear) / 260f);
            if (nowMs >= appear) {
                springAt(pos, 0, 3, tx, 14f);
                springAt(pos, 1, 4, tyLay, 14f);
                springAt(pos, 2, 5, tw, 14f);
            }
            idx++;

            float sy = top - scrollShown + pos[1];
            int iy = (int) Math.floor(sy);
            float fy = sy - iy;
            float sx = x0 + pos[0];
            int ix = (int) Math.floor(sx);
            float fx = sx - ix;
            int iw = Math.round(pos[2]);
            if (iy + h < top || iy > bottom) continue;
            c.getMatrices().push();
            c.getMatrices().translate(fx, fy, 0f);
            drawCard(c, m, ix, iy, iw, h, oa, mx, my, top, bottom);
            if (fade < 0.99f) round(c, ix, iy, ix + iw, iy + h, (((int) ((1f - fade) * 0xE8)) << 24) | 0x0D0B08, 7);
            c.getMatrices().pop();
        }
        if (col == 1) ly += rowH + 8;
        c.disableScissor();
        if (mods.isEmpty()) {
            String msg = tab == 1 ? "No favorites yet. Click the star on a module." : "No modules found.";
            c.drawTextWithShadow(this.textRenderer, ui(msg), x0, top + 6, DIM);
        }
        int contentH = ly;
        int maxScroll = Math.max(0, contentH - (bottom - top));
        lastMaxScroll = maxScroll;
        lastContentH = contentH;
        lastViewH = bottom - top;
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        // thin scroll bar that fades in while scrolling
        boolean moving = Math.abs(scroll - scrollShown) > 0.6f || Math.abs(scrollVel) > 4f || nowMs - lastWheel < 700;
        barAlpha = step(barAlpha, maxScroll > 0 && moving ? 1f : 0f, moving ? 20f : 5f);
        if (barAlpha > 0.02f && maxScroll > 0) {
            int trackH = bottom - top;
            int thumbH = Math.max(24, (int) (trackH * (trackH / (float) contentH)));
            float frac = Math.max(0f, Math.min(1f, scrollShown / maxScroll));
            int ty = top + Math.round((trackH - thumbH) * frac);
            int bx = wx + ww - 9;
            round(c, bx, ty, bx + 3, ty + thumbH, (((int) (0x99 * barAlpha)) << 24) | 0xE8C170, 1);
        }
    }

    private static final int HEADER = 40;
    private static final int SIDE = 118;
    private static final int CARD = 46;

    private void addHit(int kind, Module m, Setting s, int x, int y, int w, int h, int top, int bottom) {
        int y1 = Math.max(y, top);
        int y2 = Math.min(y + h, bottom);
        if (y2 > y1) hits.add(new Hit(kind, m, s, x, y1, w, y2 - y1));
    }

    /** Draws one tool card and returns its height. */
    private int drawCard(DrawContext c, Module m, int x, int y, int w, int h, float oa, int mx, int my, int top, int bottom) {
        boolean on = m.on.getAsBoolean();
        int col = m.panel != null ? m.panel.color : ACCENT;
        int bright = mix(col, 0xFFFFFFFF, 0.5f);
        float swT = springAnim(swAnim, m, on ? 1f : 0f, 15f);

        addHit(3, m, null, x, y, w - 86, CARD, top, bottom);
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + CARD && my >= top && my < bottom;
        round(c, x, y, x + w, y + h, mix(mix(col, 0xFF4A3A16, 0.55f), 0xFF4A3A16, swT), 7);
        float hvT = anim(hovAnim, m, hov ? 1f : 0f, 14f);
        round(c, x + 1, y + 1, x + w - 1, y + h - 1, mix(0xFF1D180E, 0xFF14110B, hvT), 6);
        if (swT > 0.3f) round(c, x, y + 12, x + 3, y + CARD - 12, col, 1);

        c.drawTextWithShadow(this.textRenderer, ui(m.name).styled(st -> st.withFont(BOLD_FONT)), x + 14, y + 8, 0xFFFFFFFF);

        // key box
        Setting ks = keySetting(m);
        String kt;
        if (ks != null && listening == ks) kt = "Press a key...";
        else kt = "Key: " + currentKey(m);
        int kw = Math.min(w - 100, 110);
        round(c, x + 14, y + 25, x + 14 + kw, y + 40, 0xFF0A0804, 4);
        c.drawTextWithShadow(this.textRenderer, ui(trim(kt, kw - 10)), x + 19, y + 29,
            (ks != null && listening == ks) ? 0xFFFFFFFF : (currentKey(m).equals("None") ? 0xFFC2B896 : 0xFFE8C170));
        if (ks != null) addHit(5, m, ks, x + 14, y + 25, kw, 15, top, bottom);

        // + (settings), star, switch
        if (!m.settings.isEmpty()) {
            plusButton(c, x + w - 98, y + 11, oa);
            addHit(1, m, null, x + w - 102, y + 6, 26, 28, top, bottom);
        }
        boolean fav = isFavorite(m);
        star(c, x + w - 70, y + 13, fav ? 0xFFFFC21A : 0xFFC9BC94);
        addHit(2, m, null, x + w - 74, y + 8, 22, 26, top, bottom);
        pill(c, x + w - 42, y + 12, swT, col);
        addHit(0, m, null, x + w - 46, y + 8, 40, 24, top, bottom);

        // settings
        if (oa > 0.01f) {
            c.enableScissor(x, y + CARD, x + w, y + h);
            int sy = y + CARD;
            c.fill(x + 10, sy - 1, x + w - 10, sy, alpha(col, 0x55));
            for (Setting s : m.settings) {
                boolean sh = mx >= x && mx < x + w && my >= sy && my < sy + ROW && my >= top && my < bottom;
                if (sh) round(c, x + 4, sy, x + w - 4, sy + ROW, 0x22FFFFFF, 3);
                int lx = x + 14;
                if (s.swatch != null) {
                    int[] sc = s.swatch.get();
                    c.drawTextWithShadow(this.textRenderer, ui(s.label), lx, sy + 3, TEXT);
                    round(c, x + w - 34, sy + 2, x + w - 10, sy + ROW - 2, 0xFFFFFFFF, 3);
                    round(c, x + w - 33, sy + 3, x + w - 11, sy + ROW - 3, 0xFF000000 | (sc[0] << 16) | (sc[1] << 8) | sc[2], 2);
                } else if (s.modKeyFor != null || s.keySupplier != null) {
                    String value;
                    if (listening == s) {
                        value = "press a key...";
                    } else if (s.modKeyFor != null) {
                        Integer kc = ChunkFinderConfig.INSTANCE.moduleKeys.get(s.modKeyFor);
                        value = keyName(kc == null ? 0 : kc);
                    } else {
                        value = s.keySupplier.get().getBoundKeyLocalizedText().getString();
                    }
                    int vw = uw(value);
                    c.drawTextWithShadow(this.textRenderer, ui(s.modKeyFor != null ? "Toggle Key" : s.label), lx, sy + 3, TEXT);
                    c.drawTextWithShadow(this.textRenderer, ui(value), x + w - vw - 12, sy + 3, listening == s ? 0xFFFFFFFF : bright);
                } else if (s.slider) {
                    String value = s.iget.getAsInt() + s.suffix;
                    int vw = uw(value);
                    c.drawTextWithShadow(this.textRenderer, ui(trim(s.label, w - vw - 40)), lx, sy + 2, TEXT);
                    c.drawTextWithShadow(this.textRenderer, ui(value), x + w - vw - 12, sy + 2, bright);
                    c.fill(x + 12, sy + ROW - 3, x + w - 12, sy + ROW - 1, 0xFF2E2718);
                    float ratio = (s.iget.getAsInt() - s.min) / (float) (s.max - s.min);
                    float shownRatio = anim(slAnim, s, ratio, 26f);
                    c.fill(x + 12, sy + ROW - 3, x + 12 + Math.round((w - 24) * shownRatio), sy + ROW - 1, col);
                } else {
                    boolean sOn = s.get.getAsBoolean();
                    c.drawTextWithShadow(this.textRenderer, ui(trim(s.label, w - 60)), lx, sy + 3, sOn ? TEXT : DIM);
                    float stT = springAnim(swAnim, s, sOn ? 1f : 0f, 15f);
                    round(c, x + w - 32, sy + 3, x + w - 14, sy + 12, mix(col, 0xFF2E2718, stT), 4);
                    int kx = Math.round(x + w - 30 + 7 * stT);
                    round(c, kx, sy + 5, kx + 5, sy + 10, 0xFFFFFFFF, 2);
                }
                if (oa > 0.98f) addHit(4, m, s, x, sy, w, ROW, top, bottom);
                sy += ROW;
            }
            c.disableScissor();
        }
        return h;
    }

    // ---------------------------------------------------------------- input

    private Hit hitAt(double mx, double my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x() && mx < h.x() + h.w() && my >= h.y() && my < h.y() + h.h()) return h;
        }
        return null;
    }

    private void setSlider(Hit h, double mx) {
        Setting s = h.s();
        double ratio = (mx - (h.x() + 12)) / (double) (h.w() - 24);
        ratio = Math.max(0, Math.min(1, ratio));
        int before = s.iset == null ? 0 : s.min + (int) Math.round(ratio * (s.max - s.min));
        s.iset.accept(before);
        if (lastSliderValue != before) { lastSliderValue = before; MenuSounds.tick((float) ratio); }
    }

    private void toggleModule(Module m) {
        m.setOn.accept(!m.on.getAsBoolean());
        MenuSounds.toggle(m.on.getAsBoolean());
        ChunkFinderConfig.save();
        ChunkFinderClient.clearAll();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (closing) return true;
        try {
            // the staff list can be dragged around while the menu is open (when it is outside the menu window)
            boolean inWindow = mx * d >= wx && mx * d < wx + ww && my * d >= wy && my * d < wy + wh;
            if (button == 0 && !inWindow) {
                int[][] boxes = {StaffTracker.BOUNDS, RegionMap.BOUNDS, FpsBar.BOUNDS, PlayerLog.BOUNDS, ArmorHud.BOUNDS, SweepMap.BOUNDS, PortalCalc.BOUNDS};
                for (int i = 0; i < boxes.length; i++) {
                    int[] b = boxes[i];
                    if (b[2] > 0 && mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) {
                        dragHud = i + 1;
                        hudOffX = (int) mx - b[0];
                        hudOffY = (int) my - b[1];
                        return true;
                    }
                }
            }
            return clickInner(mx * d, my * d, button);
        } catch (Throwable t) {
            ChunkFinderClient.LOGGER.error("Menu click failed", t);
            return true;
        }
    }

    private boolean clickInner(double mx, double my, int button) {
        if (button == 0) {
            // size buttons
            for (int b = 0; b < 2; b++) {
                int bx = wx + ww - 92 + b * 28;
                if (mx >= bx && mx < bx + 24 && my >= wy + 10 && my < wy + 30) {
                    resize(b == 0 ? -10 : 10);
                    return true;
                }
            }
            // close
            if (mx >= wx + ww - 26 && mx < wx + ww - 6 && my >= wy + 8 && my < wy + 32) {
                close();
                return true;
            }
            // tabs
            for (int i = 0; i < TABS.length; i++) {
                int tx = tabX(i);
                int tw = uw(TABS[i]) + 22;
                if (mx >= tx && mx < tx + tw && my >= wy + 9 && my < wy + 31) {
                    if (tab != i) MenuSounds.tab();
                    tab = i;
                    scroll = 0;
                    return true;
                }
            }
            // categories
            List<Panel> cats = categories();
            for (int i = 0; i <= cats.size(); i++) {
                int iy = wy + HEADER + 28 + i * 22;
                if (mx >= wx + 8 && mx < wx + SIDE - 8 && my >= iy && my < iy + 20) {
                    if (tab != 0 || category != i) MenuSounds.tab();
                    tab = 0;
                    category = i;
                    scroll = 0;
                    return true;
                }
            }
            // search box
            int cw = ww - SIDE - 24;
            int sbw = Math.min(150, cw / 2);
            int sbx = wx + SIDE + 12 + cw - sbw;
            int sby = wy + HEADER + 9;
            boolean inSearch = mx >= sbx && mx < sbx + sbw && my >= sby && my < sby + 18;
            searchFocus = inSearch;
            if (inSearch) return true;
        }

        Hit h = hitAt(mx, my);
        if (h == null) return super.mouseClicked(mx, my, button);

        switch (h.kind()) {
            case 0 -> {
                if (button == 0) toggleModule(h.m());
            }
            case 1 -> {
                if (button == 0) { h.m().open = !h.m().open; MenuSounds.click(); }
            }
            case 2 -> {
                if (button == 0) {
                    java.util.List<String> fav = ChunkFinderConfig.INSTANCE.favoriteModules;
                    if (!fav.remove(h.m().name)) fav.add(h.m().name);
                    ChunkFinderConfig.save();
                }
            }
            case 5 -> {
                if (button == 0) listening = (listening == h.s()) ? null : h.s();
                else if (button == 1) { listening = null; resetKey(h.s()); }
            }
            case 3 -> {
                if (button == 0) {
                    toggleModule(h.m());
                } else if (button == 1 && !h.m().settings.isEmpty()) {
                    h.m().open = !h.m().open;
                    MenuSounds.click();
                } else if (button == 2) {
                    for (Setting ks : h.m().settings) {
                        if (ks.modKeyFor != null || ks.keySupplier != null) {
                            listening = (listening == ks) ? null : ks;
                            break;
                        }
                    }
                }
            }
            default -> {
                Setting s = h.s();
                if (button == 1 && (s.keySupplier != null || s.modKeyFor != null)) {
                    listening = null;
                    resetKey(s);
                    return true;
                }
                if (button != 0) return true;
                if (s.keySupplier != null || s.modKeyFor != null) {
                    listening = (listening == s) ? null : s;
                } else if (s.slider) {
                    dragSlider = h;
                    setSlider(h, mx);
                } else if (s.get != null && s.set != null) {
                    s.set.accept(!s.get.getAsBoolean());
                    MenuSounds.toggle(s.get.getAsBoolean());
                    ChunkFinderConfig.save();
                }
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        try {
            if (dragHud != 0) {
                if (dragHud == 1) {
                    ChunkFinderConfig.INSTANCE.staffX = (int) mx - hudOffX;
                    ChunkFinderConfig.INSTANCE.staffY = (int) my - hudOffY;
                } else if (dragHud == 2) {
                    ChunkFinderConfig.INSTANCE.regionMapX = (int) mx - hudOffX;
                    ChunkFinderConfig.INSTANCE.regionMapY = (int) my - hudOffY;
                } else if (dragHud == 3) {
                    ChunkFinderConfig.INSTANCE.fpsX = (int) mx - hudOffX;
                    ChunkFinderConfig.INSTANCE.fpsY = (int) my - hudOffY;
                } else if (dragHud == 4) {
                    ChunkFinderConfig.INSTANCE.playerLogX = (int) mx - hudOffX;
                    ChunkFinderConfig.INSTANCE.playerLogY = (int) my - hudOffY;
                } else if (dragHud == 5) {
                    ChunkFinderConfig.INSTANCE.armorX = (int) mx - hudOffX;
                    ChunkFinderConfig.INSTANCE.armorY = (int) my - hudOffY;
                } else if (dragHud == 6) {
                    ChunkFinderConfig.INSTANCE.sweepX = (int) mx - hudOffX;
                    ChunkFinderConfig.INSTANCE.sweepY = (int) my - hudOffY;
                } else {
                    ChunkFinderConfig.INSTANCE.portalX = (int) mx - hudOffX;
                    ChunkFinderConfig.INSTANCE.portalY = (int) my - hudOffY;
                }
                return true;
            }
            if (dragSlider != null) {
                setSlider(dragSlider, mx * d);
                return true;
            }
        } catch (Throwable t) {
            ChunkFinderClient.LOGGER.error("Menu drag failed", t);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragHud != 0) {
            dragHud = 0;
            ChunkFinderConfig.save();
            return true;
        }
        if (dragSlider != null) {
            dragSlider = null;
            ChunkFinderConfig.save();
            init();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    /** Changes the menu size by the given percent and rebuilds the layout right away. */
    private void resize(int delta) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        cfg.menuScale = Math.max(60, Math.min(200, cfg.menuScale + delta));
        ChunkFinderConfig.save();
        scroll = 0;
        init();
    }

    @Override
    public boolean mouseScrolled(double rawX, double rawY, double horizontal, double vertical) {
        {
            // scroll over the region map (outside the menu window) to make it bigger or smaller
            int[] rb = RegionMap.BOUNDS;
            boolean inWin = rawX * d >= wx && rawX * d < wx + ww && rawY * d >= wy && rawY * d < wy + wh;
            if (!inWin && rb[2] > 0 && rawX >= rb[0] && rawX < rb[0] + rb[2] && rawY >= rb[1] && rawY < rb[1] + rb[3]) {
                ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
                cfg.regionMapCell = Math.max(6, Math.min(30, cfg.regionMapCell + (vertical > 0 ? 1 : -1)));
                ChunkFinderConfig.save();
                return true;
            }
        }
        {
            // scroll over the armor HUD (outside the menu window) to make it bigger or smaller
            int[] ab = ArmorHud.BOUNDS;
            boolean inWin2 = rawX * d >= wx && rawX * d < wx + ww && rawY * d >= wy && rawY * d < wy + wh;
            if (!inWin2 && ab[2] > 0 && rawX >= ab[0] && rawX < ab[0] + ab[2] && rawY >= ab[1] && rawY < ab[1] + ab[3]) {
                ChunkFinderConfig cfg2 = ChunkFinderConfig.INSTANCE;
                cfg2.armorScale = Math.max(50, Math.min(250, cfg2.armorScale + (vertical > 0 ? 10 : -10)));
                ChunkFinderConfig.save();
                return true;
            }
        }
        if (hasControlDown()) {
            resize(vertical > 0 ? 10 : -10);
            return true;
        }
        double mx = rawX * d;
        double my = rawY * d;
        if (mx >= wx + SIDE && mx < wx + ww && my >= wy + HEADER && my < wy + wh) {
            scroll -= (int) Math.round(vertical * 26 * d);
            if (scroll < 0) scroll = 0;
            if (scroll > lastMaxScroll) scroll = lastMaxScroll;
            lastWheel = System.currentTimeMillis();
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchFocus && chr >= 32 && chr != 127 && search.length() < 24) {
            search += chr;
            scroll = 0;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listening != null) {
            Setting s = listening;
            listening = null;
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                resetKey(s);
            } else if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                if (s.modKeyFor != null) {
                    ChunkFinderConfig.INSTANCE.moduleKeys.put(s.modKeyFor, keyCode);
                    ChunkFinderConfig.save();
                } else {
                    ChunkFinderClient.rebind(s.keySupplier.get(), keyCode);
                }
            }
            return true;
        }
        if (searchFocus) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
                scroll = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                searchFocus = false;
                return true;
            }
            return true; // typing in the search box never closes the menu
        }
        if (ChunkFinderClient.menuKey().matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Starts the closing animation; the screen really closes when it has finished. */
    @Override
    public void close() {
        if (closing) return;
        closing = true;
        MenuSounds.close();
        closeAt = System.currentTimeMillis();
        listening = null;
        searchFocus = false;
        dragSlider = null;
    }

    private void doClose() {
        ChunkFinderConfig.save();
        ChunkFinderClient.clearAll();
        if (this.client != null) this.client.setScreen(parent);
    }
}
