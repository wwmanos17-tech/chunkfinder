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
    private static KeyBinding stopwatchKey;
    private static KeyBinding stopwatchResetKey;
    private static ClientWorld lastWorld = null;

    public static KeyBinding menuKey() { return menuKey; }
    public static KeyBinding toggleKey() { return toggleKey; }
    public static KeyBinding freecamKey() { return freecamKey; }
    public static KeyBinding stopwatchKey() { return stopwatchKey; }
    public static KeyBinding stopwatchResetKey() { return stopwatchResetKey; }

    /** Gives a tool a new key, the same as changing it in Options > Controls. */
    public static void rebind(KeyBinding kb, int glfwKey) {
        if (kb == null) return;
        kb.setBoundKey(InputUtil.fromKeyCode(glfwKey, 0));
        KeyBinding.updateKeysByCode();
        MinecraftClient.getInstance().options.write();
    }

    /** Forget everything every tool has found, so the next scan starts fresh. */
    public static void clearAll() {
        ChunkScanner.clear();
        SusScanner.clear();
        BaseEsp.clear();
        ItemEsp.clear();
        EntityEsp.clear();
        PlayerLog.clear();
        SessionTimer.clear();
        ActivityScanner.clear();
        ReachTest.clear();
        LightFinder.clear();
        ChestEsp.clear();
        OreEsp.clear();
        SpectatorAlert.clear();
        StaffTracker.clear();
    }

    @Override
    public void onInitializeClient() {
        ChunkFinderConfig.load();
        PharaohScreen.ensureBuilt();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.chunkfinder.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.chunkfinder"));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.chunkfinder.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.chunkfinder"));
        freecamKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.chunkfinder.freecam", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F4, "key.categories.chunkfinder"));

        stopwatchKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.chunkfinder.stopwatch", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.chunkfinder"));
        stopwatchResetKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.chunkfinder.stopwatch_reset", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.chunkfinder"));

        // Freecam reads the keyboard and releases the real movement keys before the player's own tick.
        ClientTickEvents.START_CLIENT_TICK.register(Freecam::tick);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (stopwatchKey.wasPressed()) {
                if (ChunkFinderConfig.INSTANCE.timerEnabled) SessionTimer.toggleStopwatch();
            }
            while (stopwatchResetKey.wasPressed()) {
                if (ChunkFinderConfig.INSTANCE.timerEnabled) SessionTimer.resetStopwatch();
            }
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
        BaseEsp.clear();
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

            PharaohScreen.pollKeys(mc);
            tickTools(mc);
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(EspRenderer::render);
        PharaohHud.register();
    }

    private static void safe(String name, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            LOGGER.error("Pharaoh tool failed this tick: " + name, t);
        }
    }

    /** Each tool runs on its own, so one failing tool can never stop the others (like Chest ESP). */
    private static void tickTools(MinecraftClient mc) {
        safe("ChestEsp", () -> ChestEsp.tick(mc));
        safe("ChunkScanner", () -> ChunkScanner.tick(mc));
        safe("SusScanner", () -> SusScanner.tick(mc));
        safe("BaseEsp", () -> BaseEsp.tick(mc));
        safe("PlayerLog", () -> PlayerLog.tick(mc));
        safe("ActivityScanner", () -> ActivityScanner.tick(mc));
        safe("ReachTest", () -> ReachTest.tick(mc));
        safe("LightFinder", () -> LightFinder.tick(mc));
        safe("OreEsp", () -> OreEsp.tick(mc));
        safe("SpectatorAlert", () -> SpectatorAlert.tick(mc));
        safe("StaffTracker", () -> StaffTracker.tick(mc));
    }
}
