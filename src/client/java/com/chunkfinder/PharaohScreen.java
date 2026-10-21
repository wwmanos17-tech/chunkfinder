package com.chunkfinder;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Pharaoh click GUI: floating category panels you can drag around.
 * Left click a tool to switch it on or off, right click it to open its settings.
 * Nothing is scaled, so the text stays sharp.
 */
public class PharaohScreen extends Screen {
    private static final int PW = 116;
    private static final int HEAD = 17;
    private static final int ROW = 15;

    private static final int ACCENT = 0xFFE0A526;
    private static final int TEXT = 0xFFE6E6E6;
    private static final int DIM = 0xFF8A8A96;
    private static final int PANEL_BG = 0xE0121219;
    private static final int HEAD_BG = 0xF01A1A24;
    private static final int SET_BG = 0xE00C0C12;

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
    }

    private static class Module {
        String name;
        BooleanSupplier on;
        Consumer<Boolean> setOn;
        List<Setting> settings = new ArrayList<>();
        boolean open = false;
    }

    private static class Panel {
        String title;
        List<Module> mods = new ArrayList<>();
        int x, y;
        int scroll = 0;
        boolean placed = false;
        int color = ACCENT;
    }

    private record Hit(Panel p, Module m, Setting s, int x, int y, int w, int h) {}

    // panels live as long as the game runs, so their positions are remembered
    private static final List<Panel> PANELS = new ArrayList<>();

    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private Panel dragPanel = null;
    private int dragDx, dragDy;
    private Hit dragSlider = null;
    private Setting listening = null;

    public PharaohScreen(Screen parent) {
        super(Text.literal("Pharaoh"));
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

    private static Module module(String name, BooleanSupplier on, Consumer<Boolean> setOn, Setting... settings) {
        Module m = new Module();
        m.name = name;
        m.on = on;
        m.setOn = setOn;
        boolean hasKey = false;
        for (Setting s : settings) if (s.keySupplier != null) hasKey = true;
        if (!hasKey) {
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
        for (Module m : mods) p.mods.add(m);
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
                    mc.player.sendMessage(Text.literal(m.name + ": " + (now ? "ON" : "OFF")), true);
                }
            }
        }
    }

    private static String keyName(int code) {
        if (code <= 0) return "None";
        return net.minecraft.client.util.InputUtil.fromKeyCode(code, 0).getLocalizedText().getString();
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
                slider("Sus Sensitivity", 1, 10, "", () -> c.susSensitivity, v -> c.susSensitivity = v),
                toggle("Tall Columns", () -> c.tallColumns, v -> c.tallColumns = v),
                toggle("Chat Alerts", () -> c.notify, v -> c.notify = v),
                slider("Render Height", -64, 320, "", () -> c.renderY, v -> c.renderY = v)),
            module("Player ESP", () -> c.playerEspEnabled, v -> c.playerEspEnabled = v,
                slider("Range", 16, 512, "m", () -> c.playerEspRange, v -> c.playerEspRange = v),
                toggle("Names", () -> c.playerEspNames, v -> c.playerEspNames = v),
                toggle("Distance", () -> c.playerEspDistance, v -> c.playerEspDistance = v),
                toggle("Filled", () -> c.playerEspFill, v -> c.playerEspFill = v),
                toggle("Tracers", () -> c.playerEspTracers, v -> c.playerEspTracers = v),
                toggle("Self In Freecam", () -> c.playerEspSelfInFreecam, v -> c.playerEspSelfInFreecam = v)),
            module("Chest ESP", () -> c.chestEspEnabled, v -> c.chestEspEnabled = v,
                slider("Range", 16, 1024, "m", () -> c.chestEspRange, v -> c.chestEspRange = v),
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
                slider("Range (chunks)", 1, 8, "", () -> c.oreEspRange, v -> c.oreEspRange = v),
                toggle("Diamond", () -> c.oreDiamond, v -> c.oreDiamond = v),
                toggle("Ancient Debris", () -> c.oreDebris, v -> c.oreDebris = v),
                toggle("Emerald", () -> c.oreEmerald, v -> c.oreEmerald = v),
                toggle("Gold", () -> c.oreGold, v -> c.oreGold = v),
                toggle("Iron", () -> c.oreIron, v -> c.oreIron = v),
                toggle("Coal", () -> c.oreCoal, v -> c.oreCoal = v),
                toggle("Redstone", () -> c.oreRedstone, v -> c.oreRedstone = v),
                toggle("Lapis", () -> c.oreLapis, v -> c.oreLapis = v),
                toggle("Copper", () -> c.oreCopper, v -> c.oreCopper = v),
                toggle("Filled", () -> c.oreFill, v -> c.oreFill = v),
                toggle("Tracers", () -> c.oreTracers, v -> c.oreTracers = v))));

        PANELS.add(panel("Combat", 0xFFF97316,
            module("Trigger Bot", () -> c.triggerBotEnabled, v -> c.triggerBotEnabled = v,
                toggle("Players Only", () -> c.triggerPlayersOnly, v -> c.triggerPlayersOnly = v),
                slider("Weapon Charge", 50, 100, "%", () -> c.triggerCharge, v -> c.triggerCharge = v)),
            module("Auto W-Tap", () -> c.autoWTapEnabled, v -> c.autoWTapEnabled = v,
                slider("Tap Ticks", 1, 4, "", () -> c.autoWTapTicks, v -> c.autoWTapTicks = v)),
            module("Auto Crystal", () -> c.autoCrystalEnabled, v -> c.autoCrystalEnabled = v,
                toggle("Place Crystals", () -> c.autoCrystalPlace, v -> c.autoCrystalPlace = v),
                toggle("Break Crystals", () -> c.autoCrystalBreak, v -> c.autoCrystalBreak = v),
                toggle("Place Obsidian", () -> c.autoCrystalObsidian, v -> c.autoCrystalObsidian = v),
                slider("Enemy Range", 3, 10, "", () -> c.autoCrystalRange, v -> c.autoCrystalRange = v),
                slider("Delay (ticks)", 1, 10, "", () -> c.autoCrystalDelay, v -> c.autoCrystalDelay = v),
                slider("Safe Distance", 0, 6, "", () -> c.autoCrystalSafeDistance, v -> c.autoCrystalSafeDistance = v)),
            module("Auto Anchor", () -> c.autoAnchorEnabled, v -> c.autoAnchorEnabled = v,
                slider("Enemy Range", 3, 10, "", () -> c.autoAnchorRange, v -> c.autoAnchorRange = v),
                slider("Delay (ticks)", 1, 10, "", () -> c.autoAnchorDelay, v -> c.autoAnchorDelay = v),
                slider("Safe Distance", 0, 8, "", () -> c.autoAnchorSafeDistance, v -> c.autoAnchorSafeDistance = v))));

        PANELS.add(panel("Movement", 0xFF3B82F6,
            module("Freecam", () -> c.freecamEnabled, v -> c.freecamEnabled = v,
                keybind(ChunkFinderClient::freecamKey),
                slider("Speed", 1, 20, "", () -> c.freecamSpeed, v -> c.freecamSpeed = v))));

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
                toggle("Join / Leave Alerts", () -> c.staffAlerts, v -> c.staffAlerts = v))));

        PANELS.add(panel("Client", 0xFFE0A526,
            module("Menu", () -> true, v -> { },
                keybind("Menu Key", ChunkFinderClient::menuKey)),
            module("Tool List", () -> c.showModuleList, v -> c.showModuleList = v)));
    }

    // ---------------------------------------------------------------- layout

    private int contentRows(Panel p) {
        int n = 0;
        for (Module m : p.mods) n += 1 + (m.open ? m.settings.size() : 0);
        return n;
    }

    private int maxPanelHeight() {
        return this.height - 40 - 18;
    }

    @Override
    protected void init() {
        buildPanels();
        int i = 0;
        for (Panel p : PANELS) {
            if (!p.placed) {
                int perRow = Math.max(1, (this.width - 12) / (PW + 8));
                p.x = 12 + (i % perRow) * (PW + 8);
                p.y = 42 + (i / perRow) * 120;
                p.placed = true;
            }
            p.x = Math.max(0, Math.min(this.width - PW, p.x));
            p.y = Math.max(0, Math.min(this.height - HEAD, p.y));
            i++;
        }
    }

    /** Mixes color c into base by t (0..1), fully opaque. */
    private static int mix(int c, int base, float t) {
        int r = (int) (((c >> 16) & 255) * t + ((base >> 16) & 255) * (1 - t));
        int g = (int) (((c >> 8) & 255) * t + ((base >> 8) & 255) * (1 - t));
        int b = (int) ((c & 255) * t + (base & 255) * (1 - t));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** A filled rectangle with rounded corners of radius r. */
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

    private static int alpha(int c, int a) {
        return (a << 24) | (c & 0xFFFFFF);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        c.fill(0, 0, this.width, this.height, 0x80050610);
        hits.clear();

        for (Panel p : PANELS) drawPanel(c, p, mx, my);

        // logo top left, name in the middle
        LogoRenderer.draw(c, 8, 4);
        c.drawTextWithShadow(this.textRenderer,
            Text.literal("Left click: on/off   Right click: settings   Middle click: set its key (Backspace clears)"),
            8, this.height - 22, DIM);
        c.drawTextWithShadow(this.textRenderer,
            Text.literal("Icon: \"Pharaoh\" by Delapouite, game-icons.net, CC BY 3.0"),
            8, this.height - 11, 0xFF55555F);
    }

    /** Small pill switch: colored with the knob on the right when on, dark with a white dot when off. */
    private static void pill(DrawContext c, int x, int y, boolean on, int col) {
        round(c, x, y, x + 18, y + 9, on ? col : 0xFF1B1E2D, 4);
        int kx = on ? x + 10 : x + 3;
        round(c, kx, y + 2, kx + 5, y + 7, 0xFFFFFFFF, 2);
    }

    private void drawPanel(DrawContext c, Panel p, int mx, int my) {
        int total = contentRows(p) * ROW;
        int vh = Math.min(total, maxPanelHeight());
        p.scroll = Math.max(0, Math.min(p.scroll, Math.max(0, total - vh)));

        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        int col = p.color;
        int bright = mix(col, 0xFFFFFFFF, 0.45f);
        int deep = 0xF00A0C18;       // the deep navy used behind everything
        int deeper = 0xF2070813;     // settings area, a little darker
        int headBg = mix(col, 0x0B0D1C, 0.16f);

        int bottom = p.y + HEAD + vh + 2;
        round(c, p.x - 1, p.y - 1, p.x + PW + 1, bottom + 1, alpha(col, 0x90), 4);
        round(c, p.x, p.y, p.x + PW, bottom, deep, 3);

        // header: deep tint of the panel color, colored title, little square icon
        round(c, p.x, p.y, p.x + PW, p.y + HEAD, headBg, 3);
        c.fill(p.x, p.y + HEAD / 2, p.x + PW, p.y + HEAD, headBg);
        c.fill(p.x, p.y + HEAD - 1, p.x + PW, p.y + HEAD, alpha(col, 0xC0));
        round(c, p.x + 6, p.y + 5, p.x + 13, p.y + 12, col, 2);
        c.drawTextWithShadow(this.textRenderer, Text.literal(p.title), p.x + 18, p.y + 4, bright);

        int y0 = p.y + HEAD;
        c.enableScissor(p.x, y0, p.x + PW, y0 + vh);
        int y = y0 - p.scroll;
        for (Module m : p.mods) {
            boolean on = m.on.getAsBoolean();
            boolean hover = mx >= p.x && mx < p.x + PW && my >= y && my < y + ROW && my >= y0 && my < y0 + vh;
            if (hover) c.fill(p.x, y, p.x + PW, y + ROW, 0x16FFFFFF);
            if (on) {
                // same look as the on-screen tool list: tinted box with a colored bar on the right edge
                c.fill(p.x, y, p.x + PW, y + ROW, alpha(col, 0x38));
                c.fill(p.x + PW - 2, y, p.x + PW, y + ROW, col);
            }
            c.drawTextWithShadow(this.textRenderer, Text.literal(m.name), p.x + 7, y + 3, on ? bright : TEXT);
            Integer boundKey = cfg.moduleKeys.get(m.name);
            if (boundKey != null && boundKey > 0) {
                String tag = "[" + keyName(boundKey) + "]";
                int nameW = this.textRenderer.getWidth(m.name);
                int tagW = this.textRenderer.getWidth(tag);
                if (7 + nameW + 4 + tagW < PW - 38) {
                    c.drawTextWithShadow(this.textRenderer, Text.literal(tag), p.x + 7 + nameW + 4, y + 3, DIM);
                }
            }
            if (!m.settings.isEmpty()) {
                c.drawTextWithShadow(this.textRenderer, Text.literal(m.open ? "v" : ">"), p.x + PW - 33, y + 3, DIM);
            }
            pill(c, p.x + PW - 24, y + 3, on, col);
            addHit(p, m, null, p.x, y, PW, y0, vh);
            y += ROW;

            if (m.open) {
                for (Setting s : m.settings) {
                    boolean sh = mx >= p.x && mx < p.x + PW && my >= y && my < y + ROW && my >= y0 && my < y0 + vh;
                    c.fill(p.x, y, p.x + PW, y + ROW, sh ? 0xF0101224 : deeper);
                    if (s.modKeyFor != null) {
                        Integer kc = ChunkFinderConfig.INSTANCE.moduleKeys.get(s.modKeyFor);
                        String value = listening == s ? "press a key..." : keyName(kc == null ? 0 : kc);
                        int vw = this.textRenderer.getWidth(value);
                        c.drawTextWithShadow(this.textRenderer, Text.literal("Toggle Key"), p.x + 10, y + 3, TEXT);
                        c.drawTextWithShadow(this.textRenderer, Text.literal(value), p.x + PW - vw - 6, y + 3, listening == s ? 0xFFFFFFFF : bright);
                    } else if (s.keySupplier != null) {
                        String value = listening == s ? "press a key..." : s.keySupplier.get().getBoundKeyLocalizedText().getString();
                        int vw = this.textRenderer.getWidth(value);
                        c.drawTextWithShadow(this.textRenderer, Text.literal(s.label), p.x + 10, y + 3, TEXT);
                        c.drawTextWithShadow(this.textRenderer, Text.literal(value), p.x + PW - vw - 6, y + 3, listening == s ? 0xFFFFFFFF : bright);
                    } else if (s.slider) {
                        String value = s.iget.getAsInt() + s.suffix;
                        int vw = this.textRenderer.getWidth(value);
                        String label = this.textRenderer.trimToWidth(s.label, PW - vw - 20);
                        c.drawTextWithShadow(this.textRenderer, Text.literal(label), p.x + 10, y + 2, TEXT);
                        c.drawTextWithShadow(this.textRenderer, Text.literal(value), p.x + PW - vw - 6, y + 2, bright);
                        c.fill(p.x + 8, y + ROW - 3, p.x + PW - 8, y + ROW - 1, 0xFF1B1E2D);
                        float ratio = (s.iget.getAsInt() - s.min) / (float) (s.max - s.min);
                        int fx = p.x + 8 + Math.round((PW - 16) * ratio);
                        c.fill(p.x + 8, y + ROW - 3, fx, y + ROW - 1, col);
                    } else {
                        boolean sOn = s.get.getAsBoolean();
                        String label = this.textRenderer.trimToWidth(s.label, PW - 38);
                        c.drawTextWithShadow(this.textRenderer, Text.literal(label), p.x + 10, y + 3, sOn ? TEXT : DIM);
                        pill(c, p.x + PW - 24, y + 3, sOn, col);
                    }
                    addHit(p, m, s, p.x, y, PW, y0, vh);
                    y += ROW;
                }
            }
        }
        c.disableScissor();
    }

    private void addHit(Panel p, Module m, Setting s, int x, int y, int w, int top, int vh) {
        int y1 = Math.max(y, top);
        int y2 = Math.min(y + ROW, top + vh);
        if (y2 > y1) hits.add(new Hit(p, m, s, x, y1, w, y2 - y1));
    }

    // ---------------------------------------------------------------- input

    private Hit hitAt(double mx, double my) {
        for (Hit h : hits) {
            if (mx >= h.x() && mx < h.x() + h.w() && my >= h.y() && my < h.y() + h.h()) return h;
        }
        return null;
    }

    private void setSlider(Hit h, double mx) {
        Setting s = h.s();
        double ratio = (mx - (h.x() + 6)) / (double) (h.w() - 12);
        ratio = Math.max(0, Math.min(1, ratio));
        s.iset.accept(s.min + (int) Math.round(ratio * (s.max - s.min)));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // title bars (topmost panel first)
        for (int i = PANELS.size() - 1; i >= 0; i--) {
            Panel p = PANELS.get(i);
            if (button == 0 && mx >= p.x && mx < p.x + PW && my >= p.y && my < p.y + HEAD) {
                dragPanel = p;
                dragDx = (int) mx - p.x;
                dragDy = (int) my - p.y;
                PANELS.remove(i);
                PANELS.add(p);
                return true;
            }
        }
        Hit h = hitAt(mx, my);
        if (h != null) {
            if (h.s() == null) {
                if (button == 0) {
                    h.m().setOn.accept(!h.m().on.getAsBoolean());
                    ChunkFinderConfig.save();
                    ChunkFinderClient.clearAll();
                } else if (button == 1 && !h.m().settings.isEmpty()) {
                    h.m().open = !h.m().open;
                } else if (button == 2) {
                    // middle click: set this tool's on/off key right away
                    for (Setting ks : h.m().settings) {
                        if (ks.modKeyFor != null || ks.keySupplier != null) {
                            listening = (listening == ks) ? null : ks;
                            break;
                        }
                    }
                }
            } else if (button == 0) {
                Setting s = h.s();
                if (s.keySupplier != null || s.modKeyFor != null) {
                    listening = (listening == s) ? null : s;
                } else if (s.slider) {
                    dragSlider = h;
                    setSlider(h, mx);
                } else {
                    s.set.accept(!s.get.getAsBoolean());
                    ChunkFinderConfig.save();
                }
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragPanel != null) {
            dragPanel.x = Math.max(0, Math.min(this.width - PW, (int) mx - dragDx));
            dragPanel.y = Math.max(0, Math.min(this.height - HEAD, (int) my - dragDy));
            return true;
        }
        if (dragSlider != null) {
            setSlider(dragSlider, mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean used = dragPanel != null || dragSlider != null;
        dragPanel = null;
        dragSlider = null;
        if (used) {
            ChunkFinderConfig.save();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        for (int i = PANELS.size() - 1; i >= 0; i--) {
            Panel p = PANELS.get(i);
            int vh = Math.min(contentRows(p) * ROW, maxPanelHeight());
            if (mx >= p.x && mx < p.x + PW && my >= p.y && my < p.y + HEAD + vh) {
                p.scroll -= (int) (vertical * ROW);
                return true;
            }
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listening != null) {
            Setting s = listening;
            listening = null;
            if (s.modKeyFor != null) {
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                    ChunkFinderConfig.INSTANCE.moduleKeys.remove(s.modKeyFor);
                } else if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                    ChunkFinderConfig.INSTANCE.moduleKeys.put(s.modKeyFor, keyCode);
                }
                ChunkFinderConfig.save();
            } else if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                ChunkFinderClient.rebind(s.keySupplier.get(), keyCode);
            }
            return true;
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
