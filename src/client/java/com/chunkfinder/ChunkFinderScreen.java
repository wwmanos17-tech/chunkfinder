package com.chunkfinder;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/** Settings screen: sensitivity, simulation distance and a few toggles. All widgets are centered and evenly spaced. */
public class ChunkFinderScreen extends Screen {
    private static final int WIDTH = 200;
    private static final int HEIGHT = 20;
    private static final int GAP = 24;

    private final Screen parent;

    public ChunkFinderScreen(Screen parent) {
        super(Text.literal("Chunk Finder"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        int x = this.width / 2 - WIDTH / 2;
        int y = this.height / 2 - 4 * GAP;

        addDrawableChild(ButtonWidget.builder(enabledText(), b -> {
            cfg.enabled = !cfg.enabled;
            if (!cfg.enabled) ChunkScanner.clear();
            b.setMessage(enabledText());
        }).dimensions(x, y, WIDTH, HEIGHT).build());
        y += GAP;

        addDrawableChild(new IntSlider(x, y, "Sensitivity", 1, 10, cfg.sensitivity, v -> cfg.sensitivity = v));
        y += GAP;

        addDrawableChild(new IntSlider(x, y, "Simulation Distance", 2, 32, cfg.simulationDistance,
            v -> cfg.simulationDistance = v));
        y += GAP;

        addDrawableChild(ButtonWidget.builder(toggleText("Detect Geodes", cfg.detectGeodes), b -> {
            cfg.detectGeodes = !cfg.detectGeodes;
            b.setMessage(toggleText("Detect Geodes", cfg.detectGeodes));
        }).dimensions(x, y, WIDTH, HEIGHT).build());
        y += GAP;

        addDrawableChild(ButtonWidget.builder(toggleText("Detect Activity", cfg.detectActivity), b -> {
            cfg.detectActivity = !cfg.detectActivity;
            b.setMessage(toggleText("Detect Activity", cfg.detectActivity));
        }).dimensions(x, y, WIDTH, HEIGHT).build());
        y += GAP;

        addDrawableChild(new IntSlider(x, y, "Render Height (Y)", -64, 320, cfg.renderY, v -> cfg.renderY = v));
        y += GAP * 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
            .dimensions(x, y, WIDTH, HEIGHT).build());
    }

    private static Text enabledText() {
        return toggleText("Chunk Finder", ChunkFinderConfig.INSTANCE.enabled);
    }

    private static Text toggleText(String label, boolean on) {
        return Text.literal(label + ": " + (on ? "ON" : "OFF"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
    }

    @Override
    public void close() {
        ChunkFinderConfig.save();
        ChunkScanner.clear(); // rescan right away with the new settings
        this.client.setScreen(parent);
    }

    /** Integer slider that writes straight back into the config. */
    private static class IntSlider extends SliderWidget {
        interface Setter { void set(int value); }

        private final String label;
        private final int min;
        private final int max;
        private final Setter setter;

        IntSlider(int x, int y, String label, int min, int max, int value, Setter setter) {
            super(x, y, WIDTH, HEIGHT, Text.empty(), (value - min) / (double) (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.setter = setter;
            updateMessage();
        }

        private int current() {
            return min + (int) Math.round(this.value * (max - min));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(label + ": " + current()));
        }

        @Override
        protected void applyValue() {
            setter.set(current());
        }
    }
}
