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
    private static final int TEXT = 0xFFE6E6E6;
    private static final int DIM = 0xFF8A8A96;

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
    private int d = 1, vw = 0, vh = 0;

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

        PANELS.add(panel("Visuals", 0xFF8B5CF6,
            module("Chunk Finder", () -> c.enabled, v -> c.enabled = v,
                keybind(ChunkFinderClient::toggleKey),
                slider("Sensitivity", 1, 10, "", () -> c.sensitivity, v -> c.sensitivity = v),
                slider("Simulation Dist", 2, 32, "", () -> c.simulationDistance, v -> c.simulationDistance = v),
                toggle("Geodes", () -> c.detectGeodes, v -> c.detectGeodes = v),
                toggle("Activity", () -> c.detectActivity, v -> c.detectActivity = v),
                toggle("Sus Chunks", () -> c.detectSus, v -> c.detectSus = v),
                toggle("Light Finder", () -> c.detectLight, v -> c.detectLight = v),
                slider("Min Lights", 1, 20, "", () -> c.lightMin, v -> c.lightMin = v),
                slider("Sus Sensitivity", 1, 10, "", () -> c.susSensitivity, v -> c.susSensitivity = v),
                toggle("Tall Columns", () -> c.tallColumns, v -> c.tallColumns = v),
                toggle("Chat Alerts", () -> c.notify, v -> c.notify = v),
                slider("Render Height", -64, 320, "", () -> c.renderY, v -> c.renderY = v)),
            module("Chunk Colors", () -> true, v -> { }, chunkColorSettings(c)),
            module("Player ESP", () -> c.playerEspEnabled, v -> c.playerEspEnabled = v,
                slider("Range", 16, 1024, "m", () -> c.playerEspRange, v -> c.playerEspRange = v),
                toggle("Names", () -> c.playerEspNames, v -> c.playerEspNames = v),
                toggle("Distance", () -> c.playerEspDistance, v -> c.playerEspDistance = v),
                toggle("Filled", () -> c.playerEspFill, v -> c.playerEspFill = v),
                toggle("Tracers", () -> c.playerEspTracers, v -> c.playerEspTracers = v),
                toggle("Self In Freecam", () -> c.playerEspSelfInFreecam, v -> c.playerEspSelfInFreecam = v)),
            module("Chest ESP", () -> c.chestEspEnabled, v -> c.chestEspEnabled = v,
                slider("Range", 16, 2048, "m", () -> c.chestEspRange, v -> c.chestEspRange = v),
                toggle("Chests", () -> c.espChest, v -> c.espChest = v),
                toggle("Trapped Chests", () -> c.espTrapped, v -> c.espTrapped = v),
                toggle("Barrels", () -> c.espBarrel, v -> c.espBarrel = v),
                toggle("Ender Chests", () -> c.espEnder, v -> c.espEnder = v),
                toggle("Shulkers", () -> c.espShulker, v -> c.espShulker = v),
                toggle("Spawners", () -> c.espSpawner, v -> c.espSpawner = v),
                toggle("Beacons", () -> c.espBeacon, v -> c.espBeacon = v),
                toggle("Beds", () -> c.espBed, v -> c.espBed = v),
                toggle("Chest Minecarts", () -> c.espMinecart, v -> c.espMinecart = v),
                toggle("Filled", () -> c.chestEspFill, v -> c.chestEspFill = v),
                toggle("Tracers", () -> c.chestEspTracers, v -> c.chestEspTracers = v),
                toggle("Labels", () -> c.chestEspLabels, v -> c.chestEspLabels = v),
                toggle("Summary List", () -> c.chestEspSummary, v -> c.chestEspSummary = v),
                toggle("Alert: Spawners", () -> c.alertSpawners, v -> c.alertSpawners = v),
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

        PANELS.add(panel("Movement", 0xFF3B82F6,
            module("Freecam", () -> c.freecamEnabled, v -> c.freecamEnabled = v,
                keybind(ChunkFinderClient::freecamKey),
                slider("Speed", 1, 60, "", () -> c.freecamSpeed, v -> c.freecamSpeed = v))));

        PANELS.add(panel("Alerts", 0xFFEF4444,
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
                keybind("Menu Key", ChunkFinderClient::menuKey)),
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

    private static void pill(DrawContext c, int x, int y, boolean on, int col) {
        round(c, x, y, x + 24, y + 12, on ? col : 0xFF1C2238, 6);
        int kx = on ? x + 13 : x + 2;
        round(c, kx, y + 2, kx + 9, y + 10, 0xFFFFFFFF, 4);
    }

    private static final String[] STAR = {
        "...#...", "...#...", "#######", ".#####.", "..###..", ".##.##.", ".#...#."
    };

    private static void star(DrawContext c, int x, int y, int color) {
        for (int r = 0; r < STAR.length; r++) {
            for (int q = 0; q < STAR[r].length(); q++) {
                if (STAR[r].charAt(q) == '#') c.fill(x + q, y + r, x + q + 1, y + r + 1, color);
            }
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
        buildPanels();
        // Draw in finer units: one unit is about one real screen pixel per font pixel, so shapes and text are crisp.
        int s = this.client != null ? (int) Math.round(this.client.getWindow().getScaleFactor()) : 2;
        d = Math.max(1, s <= 3 ? s : s / 2);
        vw = this.width * d;
        vh = this.height * d;
        ww = Math.min(vw - 24, 680);
        wh = Math.min(vh - 24, 380);
        wx = (vw - ww) / 2;
        wy = (vh - wh) / 2;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        try {
            c.getMatrices().push();
            try {
                c.getMatrices().scale(1f / d, 1f / d, 1f);
                renderInner(c, mx * d, my * d, delta);
            } finally {
                c.getMatrices().pop();
            }
        } catch (Throwable t) {
            ChunkFinderClient.LOGGER.error("Menu drawing failed, closing the menu", t);
            try { c.disableScissor(); } catch (Throwable ignored) { }
            close();
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
        c.fill(0, 0, vw, vh, 0x99040612);

        // window
        round(c, wx - 1, wy - 1, wx + ww + 1, wy + wh + 1, 0xFF1B2547, 7);
        round(c, wx, wy, wx + ww, wy + wh, 0xF2070B18, 6);
        c.fill(wx + 1, wy + HEADER, wx + ww - 1, wy + HEADER + 1, 0xFF161E38);
        c.fill(wx + SIDE, wy + HEADER + 8, wx + SIDE + 1, wy + wh - 8, 0xFF161E38);

        // logo and name
        LogoRenderer.draw(c, wx + 10, wy + 4);
        c.drawTextWithShadow(this.textRenderer, ui("PHARAOH").styled(st -> st.withFont(BOLD_FONT)), wx + 48, wy + 16, ACCENT);

        // tabs
        for (int i = 0; i < TABS.length; i++) {
            int tx = tabX(i);
            int tw = uw(TABS[i]) + 22;
            if (i == tab) round(c, tx, wy + 9, tx + tw, wy + 31, 0xFF121A33, 5);
            c.drawTextWithShadow(this.textRenderer, ui(TABS[i]), tx + 11, wy + 16, i == tab ? TEXT : DIM);
        }
        boolean overX = mx >= wx + ww - 26 && mx < wx + ww - 6 && my >= wy + 8 && my < wy + 32;
        c.drawTextWithShadow(this.textRenderer, ui("x"), wx + ww - 17, wy + 16, overX ? 0xFFFFFFFF : DIM);

        // sidebar: categories
        List<Panel> cats = categories();
        c.drawTextWithShadow(this.textRenderer, ui("MODULES"), wx + 14, wy + HEADER + 12, DIM);
        for (int i = 0; i <= cats.size(); i++) {
            int iy = wy + HEADER + 28 + i * 22;
            boolean sel = tab == 0 && category == i;
            boolean hov = mx >= wx + 8 && mx < wx + SIDE - 8 && my >= iy && my < iy + 20;
            if (sel) {
                round(c, wx + 8, iy, wx + SIDE - 8, iy + 20, 0xFF121A33, 5);
                c.fill(wx + 8, iy + 3, wx + 10, iy + 17, ACCENT);
            } else if (hov) {
                round(c, wx + 8, iy, wx + SIDE - 8, iy + 20, 0xFF0D1328, 5);
            }
            String label = i == 0 ? "Overview" : cats.get(i - 1).title;
            c.drawTextWithShadow(this.textRenderer, ui(label), wx + 18, iy + 6, sel ? TEXT : (hov ? TEXT : DIM));
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
        round(c, sbx - (searchFocus ? 1 : 0), sby - (searchFocus ? 1 : 0), sbx + sbw + (searchFocus ? 1 : 0), sby + 18 + (searchFocus ? 1 : 0), searchFocus ? ACCENT : 0xFF161E38, 5);
        round(c, sbx, sby, sbx + sbw, sby + 18, 0xFF0C1226, 5);
        String shownSearch = search.isEmpty() && !searchFocus ? "Search modules..." : trim(search + (searchFocus && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : ""), sbw - 12);
        c.drawTextWithShadow(this.textRenderer, ui(shownSearch), sbx + 6, sby + 5, search.isEmpty() && !searchFocus ? DIM : TEXT);

        // module list
        int top = wy + HEADER + 40;
        int bottom = wy + wh - 18;
        List<Module> mods = shown();
        int y = top - scroll;
        int colw = (cw - 8) / 2;
        int col = 0;
        int rowH = 0;
        c.enableScissor(x0 - 2, top, x0 + cw + 2, bottom);
        for (Module m : mods) {
            if (m.open) {
                if (col == 1) { y += rowH + 8; col = 0; rowH = 0; }
                int h = drawCard(c, m, x0, y, cw, mx, my, top, bottom);
                y += h + 8;
            } else {
                int cx = x0 + col * (colw + 8);
                int h = drawCard(c, m, cx, y, colw, mx, my, top, bottom);
                rowH = Math.max(rowH, h);
                col++;
                if (col == 2) { y += rowH + 8; col = 0; rowH = 0; }
            }
        }
        if (col == 1) y += rowH + 8;
        int contentH = y - (top - scroll);
        c.disableScissor();
        if (mods.isEmpty()) {
            String msg = tab == 1 ? "No favorites yet. Click the star on a module." : "No modules found.";
            c.drawTextWithShadow(this.textRenderer, ui(msg), x0, top + 6, DIM);
        }
        int maxScroll = Math.max(0, contentH - (bottom - top));
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        c.drawTextWithShadow(this.textRenderer,
            ui("Icon: \"Pharaoh\" by Delapouite, game-icons.net, CC BY 3.0   |   Click a Key box, then press a key. Right click or Backspace resets it"),
            x0, wy + wh - 12, 0xFF444C66);
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
    private int drawCard(DrawContext c, Module m, int x, int y, int w, int mx, int my, int top, int bottom) {
        boolean on = m.on.getAsBoolean();
        int col = m.panel != null ? m.panel.color : ACCENT;
        int bright = mix(col, 0xFFFFFFFF, 0.5f);
        int h = CARD + (m.open ? m.settings.size() * ROW + 6 : 0);
        if (y + h < top || y > bottom) {
            // off screen: still make the hits line up by skipping them
            return h;
        }

        boolean hov = mx >= x && mx < x + w && my >= y && my < y + CARD && my >= top && my < bottom;
        round(c, x, y, x + w, y + h, on ? mix(col, 0xFF1B2547, 0.55f) : 0xFF1B2547, 7);
        round(c, x + 1, y + 1, x + w - 1, y + h - 1, hov ? 0xFF111A30 : 0xFF0B1224, 6);
        if (on) round(c, x, y + 12, x + 3, y + CARD - 12, col, 1);

        c.drawTextWithShadow(this.textRenderer, ui(m.name).styled(st -> st.withFont(BOLD_FONT)), x + 14, y + 8, 0xFFFFFFFF);

        // key box
        Setting ks = keySetting(m);
        String kt;
        if (ks != null && listening == ks) kt = "Press a key...";
        else kt = "Key: " + currentKey(m);
        int kw = Math.min(w - 100, 110);
        round(c, x + 14, y + 25, x + 14 + kw, y + 40, 0xFF060A14, 4);
        c.drawTextWithShadow(this.textRenderer, ui(trim(kt, kw - 10)), x + 19, y + 29,
            (ks != null && listening == ks) ? 0xFFFFFFFF : (currentKey(m).equals("None") ? 0xFFB4B8C8 : 0xFFE8C170));
        if (ks != null) addHit(5, m, ks, x + 14, y + 25, kw, 15, top, bottom);

        // + (settings), star, switch
        if (!m.settings.isEmpty()) {
            c.drawTextWithShadow(this.textRenderer, ui(m.open ? "-" : "+"), x + w - 76, y + 10, bright);
            addHit(1, m, null, x + w - 84, y + 4, 18, 20, top, bottom);
        }
        boolean fav = isFavorite(m);
        star(c, x + w - 56, y + 15, fav ? ACCENT : 0xFF5A6380);
        addHit(2, m, null, x + w - 62, y + 9, 18, 22, top, bottom);
        pill(c, x + w - 34, y + 14, on, mix(col, 0xFFFFFFFF, 0.12f));
        addHit(0, m, null, x + w - 38, y + 10, 32, 20, top, bottom);
        addHit(3, m, null, x, y, w - 86, CARD, top, bottom);

        // settings
        if (m.open) {
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
                    c.fill(x + 12, sy + ROW - 3, x + w - 12, sy + ROW - 1, 0xFF1C2238);
                    float ratio = (s.iget.getAsInt() - s.min) / (float) (s.max - s.min);
                    c.fill(x + 12, sy + ROW - 3, x + 12 + Math.round((w - 24) * ratio), sy + ROW - 1, col);
                } else {
                    boolean sOn = s.get.getAsBoolean();
                    c.drawTextWithShadow(this.textRenderer, ui(trim(s.label, w - 60)), lx, sy + 3, sOn ? TEXT : DIM);
                    round(c, x + w - 32, sy + 3, x + w - 14, sy + 12, sOn ? col : 0xFF1C2238, 4);
                    int kx = sOn ? x + w - 23 : x + w - 30;
                    round(c, kx, sy + 5, kx + 5, sy + 10, 0xFFFFFFFF, 2);
                }
                addHit(4, m, s, x, sy, w, ROW, top, bottom);
                sy += ROW;
            }
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
        s.iset.accept(s.min + (int) Math.round(ratio * (s.max - s.min)));
    }

    private void toggleModule(Module m) {
        m.setOn.accept(!m.on.getAsBoolean());
        ChunkFinderConfig.save();
        ChunkFinderClient.clearAll();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        try {
            return clickInner(mx * d, my * d, button);
        } catch (Throwable t) {
            ChunkFinderClient.LOGGER.error("Menu click failed", t);
            return true;
        }
    }

    private boolean clickInner(double mx, double my, int button) {
        if (button == 0) {
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
                if (button == 0) h.m().open = !h.m().open;
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
                    ChunkFinderConfig.save();
                }
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        try {
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
        if (dragSlider != null) {
            dragSlider = null;
            ChunkFinderConfig.save();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double rawX, double rawY, double horizontal, double vertical) {
        double mx = rawX * d;
        double my = rawY * d;
        if (mx >= wx + SIDE && mx < wx + ww && my >= wy + HEADER && my < wy + wh) {
            scroll -= (int) (vertical * 22 * d);
            if (scroll < 0) scroll = 0;
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

    @Override
    public void close() {
        ChunkFinderConfig.save();
        ChunkFinderClient.clearAll();
        if (this.client != null) this.client.setScreen(parent);
    }
}
