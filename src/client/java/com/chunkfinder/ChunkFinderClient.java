package com.chunkfinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChunkFinderClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("pharaoh");

    private static KeyBinding toggleKey;
    private static KeyBinding menuKey;
    private static KeyBinding freecamKey;
    private static ClientWorld lastWorld = null;

    /** Forget everything every tool has found, so the next scan starts fresh. */
    public static void clearAll() {
        ChunkScanner.clear();
        SusScanner.clear();
        ChestEsp.clear();
        OreEsp.clear();
        SpectatorAlert.clear();
    }

    @Override
    public void onInitializeClient() {
        ChunkFinderConfig.load();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.chunkfinder.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.chunkfinder"));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.chunkfinder.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.chunkfinder"));
        freecamKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.chunkfinder.freecam", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F4, "key.categories.chunkfinder"));

        // Freecam reads the keyboard and releases the real movement keys before the player's own tick.
        ClientTickEvents.START_CLIENT_TICK.register(Freecam::tick);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (freecamKey.wasPressed()) {
                ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
                if (cfg.freecamEnabled) {
                    Freecam.toggle(mc);
                } else if (mc.player != null) {
                    mc.player.sendMessage(Text.literal("Freecam is switched off in the Pharaoh menu"), true);
                }
            }
            if (!ChunkFinderConfig.INSTANCE.freecamEnabled && Freecam.isActive()) {
                Freecam.toggle(mc);
            }

            while (toggleKey.wasPressed()) {
                ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
                cfg.enabled = !cfg.enabled;
                ChunkScanner.clear();
                SusScanner.clear();
                ChunkFinderConfig.save();
                if (mc.player != null) {
                    mc.player.sendMessage(Text.literal("Chunk Finder: " + (cfg.enabled ? "ON" : "OFF")), true);
                }
            }
            while (menuKey.wasPressed()) {
                if (mc.currentScreen == null) {
                    mc.setScreen(new PharaohScreen(null));
                }
            }

            // new world or dimension: start fresh
            if (mc.world != lastWorld) {
                lastWorld = mc.world;
                clearAll();
            }

            tickTools(mc);
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(EspRenderer::render);
        PharaohHud.register();
    }

    private static void tickTools(MinecraftClient mc) {
        try {
            ChunkScanner.tick(mc);
            SusScanner.tick(mc);
            ChestEsp.tick(mc);
            OreEsp.tick(mc);
            SpectatorAlert.tick(mc);
        } catch (Throwable t) {
            LOGGER.error("A Pharaoh tool failed this tick", t);
        }
    }
}
