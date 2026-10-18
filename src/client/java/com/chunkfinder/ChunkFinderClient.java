package com.chunkfinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChunkFinderClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("chunkfinder");

    private static KeyBinding toggleKey;
    private static KeyBinding settingsKey;

    @Override
    public void onInitializeClient() {
        ChunkFinderConfig.load();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.chunkfinder.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.chunkfinder"));
        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.chunkfinder.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.chunkfinder"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (toggleKey.wasPressed()) {
                ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
                cfg.enabled = !cfg.enabled;
                ChunkScanner.clear();
                ChunkFinderConfig.save();
                if (mc.player != null) {
                    mc.player.sendMessage(Text.literal("Chunk Finder: " + (cfg.enabled ? "ON" : "OFF")), true);
                }
            }
            while (settingsKey.wasPressed()) {
                mc.setScreen(new ChunkFinderScreen(mc.currentScreen));
            }
            ChunkScanner.tick(mc);
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(ChunkRenderer::render);
    }
}
