package com.chunkfinder;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** The Pharaoh menu: logo top left, title in the middle, one tab per tool. Drawn by hand, so it needs no widgets. */
public class PharaohScreen extends Screen {
    private static final int PW = 620;
    private static final int HEAD = 76;
    private static final int TAB_H = 26;
    private static final int ROW_H = 22;
    private static final int ROW_GAP = 5;
    private static final int PAD = 20;
    private static final int FOOT = 26;
    private static final int BODY_ROWS = 8;
    private static final int PH = HEAD + TAB_H + PAD + BODY_ROWS * (ROW_H + ROW_GAP) + 34 + FOOT;

    private static final int GOLD = 0xFFE9B523;
    private static final int GOLD_LIGHT = 0xFFFFE680;
    private static final int GOLD_DARK = 0xFF8A5A0B;
    private static final int DIM = 0xFF8D7A3A;
    private static final int PANEL = 0xFF0A0B14;
    private static final int ROW_BG = 0xFF12110D;
    private static final int ROW_BORDER = 0xFF3A2F12;

    private static final String[] TABS = {
        "Overview", "Chunk Finder", "Freecam", "Player ESP", "Chest ESP", "Ore ESP", "Spectator"
    };

    private final Screen parent;
    private int tab = 0;
    private final List<Row> rows = new ArrayList<>();
    private Row dragging = null;
    private float s = 1f;
    private int px;
    private int py;

    public PharaohScreen(Screen parent) {
        super(Text.literal("Pharaoh"));
        this.parent = parent;
    }

    // ---------------------------------------------------------------- rows

    private abstract static class Row {
        final String label;
        int x, y, w;

        Row(String label) {
            this.label = label;
        }

        abstract void render(DrawContext c, PharaohScreen scr, int mx, int my);

        void click(double mx, double my) {}

        void drag(double mx) {}
    }

    private static class ToggleRow extends Row {
        final BooleanSupplier get;
        final Consumer<Boolean> set;

        ToggleRow(String label, BooleanSupplier get, Consumer<Boolean> set) {
            super(label);
            this.get = get;
            this.set = set;
        }

        @Override
        void render(DrawContext c, PharaohScreen scr, int mx, int my) {
            boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + ROW_H;
            box(c, x, y, w, ROW_H, hover);
            c.drawText(scr.textRenderer, Text.literal(label), x + 8, y + 7, 0xFFE8D9A0, false);
            boolean on = get.getAsBoolean();
            int pw = 34;
            int px2 = x + w - pw - 8;
            if (on) {
                c.fillGradient(px2, y + 4, px2 + pw, y + ROW_H - 4, GOLD_LIGHT, GOLD_DARK);
                c.drawCenteredTextWithShadow(scr.textRenderer, Text.literal("ON"), px2 + pw / 2, y + 7, 0xFF0A0B14);
            } else {
                c.fill(px2, y + 4, px2 + pw, y + ROW_H - 4, 0xFF2A2310);
                c.drawCenteredTextWithShadow(scr.textRenderer, Text.literal("OFF"), px2 + pw / 2, y + 7, 0xFFA08A4A);
            }
        }

        @Override
        void click(double mx, double my) {
            set.accept(!get.getAsBoolean());
        }
    }

    private static class SliderRow extends Row {
        final int min;
        final int max;
        final IntSupplier get;
        final IntConsumer set;
        final String suffix;
        static final int TRACK_W = 120;

        SliderRow(String label, int min, int max, String suffix, IntSupplier get, IntConsumer set) {
            super(label);
            this.min = min;
            this.max = max;
            this.suffix = suffix;
            this.get = get;
            this.set = set;
        }

        int trackX() {
            return x + w - TRACK_W - 54;
        }

        @Override
        void render(DrawContext c, PharaohScreen scr, int mx, int my) {
            boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + ROW_H;
            box(c, x, y, w, ROW_H, hover);
            c.drawText(scr.textRenderer, Text.literal(label), x + 8, y + 7, 0xFFE8D9A0, false);

            int tx = trackX();
            int ty = y + ROW_H / 2 - 2;
            c.fill(tx, ty, tx + TRACK_W, ty + 4, 0xFF2A2310);
            float ratio = (get.getAsInt() - min) / (float) (max - min);
            int fillW = (int) (TRACK_W * ratio);
            c.fillGradient(tx, ty, tx + fillW, ty + 4, GOLD_LIGHT, GOLD_DARK);
            c.fill(tx + fillW - 2, ty - 4, tx + fillW + 2, ty + 8, 0xFFFFF0A8);
            c.drawText(scr.textRenderer, Text.literal(get.getAsInt() + suffix), tx + TRACK_W + 10, y + 7, GOLD_LIGHT, false);
        }

        @Override
        void click(double mx, double my) {
            drag(mx);
        }

        @Override
        void drag(double mx) {
            double ratio = (mx - trackX()) / TRACK_W;
            ratio = Math.max(0, Math.min(1, ratio));
            set.accept(min + (int) Math.round(ratio * (max - min)));
        }
    }

    private static class InfoRow extends Row {
        InfoRow(String label) {
            super(label);
        }

        @Override
        void render(DrawContext c, PharaohScreen scr, int mx, int my) {
            c.drawText(scr.textRenderer, Text.literal(label), x + 4, y + 7, DIM, false);
        }
    }

    private static void box(DrawContext c, int x, int y, int w, int h, boolean hover) {
        c.fill(x, y, x + w, y + h, hover ? 0xFF6A5420 : ROW_BORDER);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, ROW_BG);
    }

    // ---------------------------------------------------------------- tabs

    private ToggleRow toggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
        return new ToggleRow(label, get, set);
    }

    private SliderRow slider(String label, int min, int max, String suffix, IntSupplier get, IntConsumer set) {
        return new SliderRow(label, min, max, suffix, get, set);
    }

    private void buildRows() {
        ChunkFinderConfig c = ChunkFinderConfig.INSTANCE;
        rows.clear();
        switch (tab) {
            case 0 -> {
                rows.add(toggle("Chunk Finder", () -> c.enabled, v -> c.enabled = v));
                rows.add(toggle("Freecam", () -> c.freecamEnabled, v -> c.freecamEnabled = v));
                rows.add(toggle("Player ESP", () -> c.playerEspEnabled, v -> c.playerEspEnabled = v));
                rows.add(toggle("Chest ESP", () -> c.chestEspEnabled, v -> c.chestEspEnabled = v));
                rows.add(toggle("Ore ESP", () -> c.oreEspEnabled, v -> c.oreEspEnabled = v));
                rows.add(toggle("Spectator Alert", () -> c.spectatorEnabled, v -> c.spectatorEnabled = v));
                rows.add(new InfoRow("Menu: Right Shift     Freecam: F4     Chunk Finder: J"));
            }
            case 1 -> {
                rows.add(toggle("Chunk Finder", () -> c.enabled, v -> c.enabled = v));
                rows.add(slider("Sensitivity", 1, 10, "", () -> c.sensitivity, v -> c.sensitivity = v));
                rows.add(slider("Simulation Distance", 2, 32, "", () -> c.simulationDistance, v -> c.simulationDistance = v));
                rows.add(toggle("Detect Geodes", () -> c.detectGeodes, v -> c.detectGeodes = v));
                rows.add(toggle("Detect Activity", () -> c.detectActivity, v -> c.detectActivity = v));
                rows.add(toggle("Detect Sus Chunks", () -> c.detectSus, v -> c.detectSus = v));
                rows.add(slider("Sus Sensitivity", 1, 10, "", () -> c.susSensitivity, v -> c.susSensitivity = v));
                rows.add(toggle("Tall Columns", () -> c.tallColumns, v -> c.tallColumns = v));
                rows.add(toggle("Chat Alerts", () -> c.notify, v -> c.notify = v));
                rows.add(slider("Render Height (Y)", -64, 320, "", () -> c.renderY, v -> c.renderY = v));
            }
            case 2 -> {
                rows.add(toggle("Freecam", () -> c.freecamEnabled, v -> c.freecamEnabled = v));
                rows.add(slider("Speed", 1, 20, "", () -> c.freecamSpeed, v -> c.freecamSpeed = v));
                rows.add(new InfoRow("Press F4 in game to fly. W A S D, Space up, Shift down, Ctrl faster."));
            }
            case 3 -> {
                rows.add(toggle("Player ESP", () -> c.playerEspEnabled, v -> c.playerEspEnabled = v));
                rows.add(slider("Range", 16, 512, "m", () -> c.playerEspRange, v -> c.playerEspRange = v));
                rows.add(toggle("Show Names", () -> c.playerEspNames, v -> c.playerEspNames = v));
                rows.add(toggle("Show Distance", () -> c.playerEspDistance, v -> c.playerEspDistance = v));
                rows.add(toggle("Filled Boxes", () -> c.playerEspFill, v -> c.playerEspFill = v));
                rows.add(toggle("Tracers", () -> c.playerEspTracers, v -> c.playerEspTracers = v));
                rows.add(toggle("Show Self In Freecam", () -> c.playerEspSelfInFreecam, v -> c.playerEspSelfInFreecam = v));
            }
            case 4 -> {
                rows.add(toggle("Chest ESP", () -> c.chestEspEnabled, v -> c.chestEspEnabled = v));
                rows.add(slider("Range", 16, 256, "m", () -> c.chestEspRange, v -> c.chestEspRange = v));
                rows.add(toggle("Chests", () -> c.espChest, v -> c.espChest = v));
                rows.add(toggle("Trapped Chests", () -> c.espTrapped, v -> c.espTrapped = v));
                rows.add(toggle("Barrels", () -> c.espBarrel, v -> c.espBarrel = v));
                rows.add(toggle("Ender Chests", () -> c.espEnder, v -> c.espEnder = v));
                rows.add(toggle("Shulker Boxes", () -> c.espShulker, v -> c.espShulker = v));
                rows.add(toggle("Spawners", () -> c.espSpawner, v -> c.espSpawner = v));
                rows.add(toggle("Beacons", () -> c.espBeacon, v -> c.espBeacon = v));
                rows.add(toggle("Beds", () -> c.espBed, v -> c.espBed = v));
                rows.add(toggle("Chest Minecarts", () -> c.espMinecart, v -> c.espMinecart = v));
                rows.add(toggle("Filled Boxes", () -> c.chestEspFill, v -> c.chestEspFill = v));
                rows.add(toggle("Tracers", () -> c.chestEspTracers, v -> c.chestEspTracers = v));
                rows.add(toggle("Labels", () -> c.chestEspLabels, v -> c.chestEspLabels = v));
                rows.add(toggle("Summary List", () -> c.chestEspSummary, v -> c.chestEspSummary = v));
            }
            case 5 -> {
                rows.add(toggle("Ore ESP", () -> c.oreEspEnabled, v -> c.oreEspEnabled = v));
                rows.add(slider("Range (chunks)", 1, 8, "", () -> c.oreEspRange, v -> c.oreEspRange = v));
                rows.add(toggle("Diamond", () -> c.oreDiamond, v -> c.oreDiamond = v));
                rows.add(toggle("Ancient Debris", () -> c.oreDebris, v -> c.oreDebris = v));
                rows.add(toggle("Emerald", () -> c.oreEmerald, v -> c.oreEmerald = v));
                rows.add(toggle("Gold", () -> c.oreGold, v -> c.oreGold = v));
                rows.add(toggle("Iron", () -> c.oreIron, v -> c.oreIron = v));
                rows.add(toggle("Coal", () -> c.oreCoal, v -> c.oreCoal = v));
                rows.add(toggle("Redstone", () -> c.oreRedstone, v -> c.oreRedstone = v));
                rows.add(toggle("Lapis", () -> c.oreLapis, v -> c.oreLapis = v));
                rows.add(toggle("Copper", () -> c.oreCopper, v -> c.oreCopper = v));
                rows.add(toggle("Filled Boxes", () -> c.oreFill, v -> c.oreFill = v));
                rows.add(toggle("Tracers", () -> c.oreTracers, v -> c.oreTracers = v));
            }
            default -> {
                rows.add(toggle("Spectator Alert", () -> c.spectatorEnabled, v -> c.spectatorEnabled = v));
                rows.add(slider("Range", 4, 128, "m", () -> c.spectatorRange, v -> c.spectatorRange = v));
                rows.add(slider("Popup Seconds", 1, 10, "s", () -> c.spectatorPopupSeconds, v -> c.spectatorPopupSeconds = v));
                rows.add(slider("Cooldown (minutes)", 0, 120, "", () -> c.spectatorCooldownMinutes, v -> c.spectatorCooldownMinutes = v));
                rows.add(toggle("Sound", () -> c.spectatorSound, v -> c.spectatorSound = v));
                rows.add(new InfoRow("The message text can be changed in config/chunkfinder.json"));
            }
        }
        layout();
    }

    private void layout() {
        int bodyTop = py + HEAD + TAB_H + PAD;
        boolean two = rows.size() > BODY_ROWS;
        int colW = two ? (PW - 2 * PAD - 14) / 2 : 400;
        int startX1 = two ? px + PAD : px + (PW - colW) / 2;
        int startX2 = px + PAD + colW + 14;

        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int col = two ? i / BODY_ROWS : 0;
            int idx = two ? i % BODY_ROWS : i;
            r.x = col == 0 ? startX1 : startX2;
            r.y = bodyTop + idx * (ROW_H + ROW_GAP);
            r.w = colW;
        }
    }

    // ---------------------------------------------------------------- screen

    @Override
    protected void init() {
        s = Math.min(1f, Math.min((this.height - 8f) / PH, (this.width - 8f) / (PW + 4)));
        float lw = this.width / s;
        float lh = this.height / s;
        px = (int) ((lw - PW) / 2);
        py = (int) ((lh - PH) / 2);
        buildRows();
    }

    private int doneX() {
        return px + PW / 2 - 100;
    }

    private int doneY() {
        return py + PH - FOOT - 30;
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        c.fill(0, 0, this.width, this.height, 0xB0000000);
        c.getMatrices().push();
        c.getMatrices().scale(s, s, 1f);
        int mx = (int) (mouseX / s);
        int my = (int) (mouseY / s);

        // panel
        c.fill(px - 2, py - 2, px + PW + 2, py + PH + 2, GOLD);
        c.fill(px, py, px + PW, py + PH, PANEL);
        c.fillGradient(px, py, px + PW, py + HEAD, 0xFF17161F, PANEL);

        // header: logo top left, name in the middle
        LogoRenderer.draw(c, px + 14, py + 10);
        c.getMatrices().push();
        c.getMatrices().translate(px + PW / 2f, py + HEAD / 2f - 12f, 0f);
        c.getMatrices().scale(3f, 3f, 1f);
        c.drawCenteredTextWithShadow(this.textRenderer, Text.literal("P H A R A O H"), 0, 0, GOLD_LIGHT);
        c.getMatrices().pop();

        // tabs
        int tabW = PW / TABS.length;
        int ty = py + HEAD;
        c.fill(px, ty + TAB_H - 1, px + PW, ty + TAB_H, ROW_BORDER);
        for (int i = 0; i < TABS.length; i++) {
            int tx = px + i * tabW;
            boolean sel = i == tab;
            if (sel) {
                c.fill(tx, ty, tx + tabW, ty + TAB_H, 0xFF16130A);
                c.fill(tx, ty + TAB_H - 3, tx + tabW, ty + TAB_H, GOLD);
            }
            c.drawCenteredTextWithShadow(this.textRenderer, Text.literal(TABS[i]), tx + tabW / 2, ty + 9,
                sel ? GOLD_LIGHT : DIM);
        }

        for (Row r : rows) r.render(c, this, mx, my);

        // done button
        int dx = doneX();
        int dy = doneY();
        c.fillGradient(dx, dy, dx + 200, dy + 22, GOLD_LIGHT, 0xFFC98F14);
        c.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Done"), dx + 100, dy + 7, 0xFF0A0B14);

        // credit
        c.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("Icon: \"Pharaoh\" by Delapouite, game-icons.net, CC BY 3.0"),
            px + PW / 2, py + PH - 16, 0xFF6D5E2C);

        c.getMatrices().pop();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            double mx = mouseX / s;
            double my = mouseY / s;

            int tabW = PW / TABS.length;
            int ty = py + HEAD;
            if (my >= ty && my <= ty + TAB_H && mx >= px && mx <= px + PW) {
                int i = (int) ((mx - px) / tabW);
                if (i >= 0 && i < TABS.length && i != tab) {
                    tab = i;
                    buildRows();
                }
                return true;
            }

            if (mx >= doneX() && mx <= doneX() + 200 && my >= doneY() && my <= doneY() + 22) {
                close();
                return true;
            }

            for (Row r : rows) {
                if (mx >= r.x && mx <= r.x + r.w && my >= r.y && my <= r.y + ROW_H) {
                    r.click(mx, my);
                    if (r instanceof SliderRow) dragging = r;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging != null) {
            dragging.drag(mouseX / s);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != null) {
            dragging = null;
            ChunkFinderConfig.save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
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
