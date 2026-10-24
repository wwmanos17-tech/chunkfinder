package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** Soft click and chime sounds for the menu, with a volume setting and an off switch. */
public final class MenuSounds {
    private MenuSounds() {}

    private static long lastTick = 0;
    // built from the plain sound names, so it does not matter how the game stores them internally
    private static final SoundEvent CLICK = SoundEvent.of(Identifier.ofVanilla("ui.button.click"));
    private static final SoundEvent CHIME = SoundEvent.of(Identifier.ofVanilla("block.amethyst_block.chime"));

    private static void play(SoundEvent e, float pitch, float volume) {
        ChunkFinderConfig c = ChunkFinderConfig.INSTANCE;
        if (!c.menuSounds || c.menuSoundVolume <= 0) return;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getSoundManager() == null) return;
            mc.getSoundManager().play(PositionedSoundInstance.master(e, pitch, volume * c.menuSoundVolume / 100f));
        } catch (Throwable ignored) {
        }
    }

    public static void open() {
        play(CHIME, 1.6f, 0.45f);
        play(CLICK, 1.5f, 0.35f);
    }

    public static void close() {
        play(CLICK, 0.8f, 0.45f);
    }

    /** Switching a tab or a category. */
    public static void tab() {
        play(CLICK, 1.25f, 0.4f);
    }

    /** Opening or closing a module card, pressing a button. */
    public static void click() {
        play(CLICK, 1.0f, 0.5f);
    }

    public static void toggle(boolean on) {
        if (on) play(CHIME, 1.75f, 0.5f);
        else play(CLICK, 0.7f, 0.5f);
    }

    /** A small tick while dragging a slider; the pitch rises with the value. */
    public static void tick(float ratio) {
        long now = System.currentTimeMillis();
        if (now - lastTick < 55) return;
        lastTick = now;
        play(CLICK, 0.8f + 0.9f * Math.max(0f, Math.min(1f, ratio)), 0.25f);
    }
}
