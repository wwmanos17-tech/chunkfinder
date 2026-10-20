package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

/**
 * Render Method: one key drops your render distance to a few chunks for an instant FPS boost,
 * and pressing it again restores your old render distance. It only changes the normal video setting.
 */
public class RenderMethod {
    public static boolean isActive() {
        return ChunkFinderConfig.INSTANCE.renderMethodActive;
    }

    public static void toggle(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (mc.player == null) return;
        if (!cfg.renderMethodEnabled) {
            mc.player.sendMessage(Text.literal("Render Method is switched off in the Pharaoh menu"), true);
            return;
        }

        SimpleOption<Integer> opt = mc.options.getViewDistance();
        if (!cfg.renderMethodActive) {
            cfg.renderMethodSaved = opt.getValue();
            opt.setValue(cfg.renderMethodChunks);
            cfg.renderMethodActive = true;
            mc.player.sendMessage(Text.literal("Render Method: " + cfg.renderMethodChunks
                + " chunks (was " + cfg.renderMethodSaved + ")"), true);
        } else {
            opt.setValue(cfg.renderMethodSaved);
            cfg.renderMethodActive = false;
            mc.player.sendMessage(Text.literal("Render Method: back to " + cfg.renderMethodSaved + " chunks"), true);
        }
        ChunkFinderConfig.save();
    }

    /** Called when the tool is switched off in the menu while it is active. */
    public static void restore(MinecraftClient mc) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.renderMethodActive) return;
        mc.options.getViewDistance().setValue(cfg.renderMethodSaved);
        cfg.renderMethodActive = false;
        ChunkFinderConfig.save();
    }
}
