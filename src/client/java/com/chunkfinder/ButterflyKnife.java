package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Butterfly Knife module: switches the built-in butterfly sword skin on and off from the menu,
 * so you never have to open the resource pack screen. It only changes which built-in pack is enabled.
 */
public class ButterflyKnife {
    private static int timer = 0;
    private static boolean warned = false;

    private static String find(ResourcePackManager pm, String suffix) {
        for (String id : pm.getIds()) {
            if (id.contains("chunkfinder") && id.endsWith(suffix)) return id;
        }
        return null;
    }

    public static void tick(MinecraftClient mc) {
        if (mc.world == null || timer-- > 0) return;
        timer = 20;
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        ResourcePackManager pm = mc.getResourcePackManager();
        String pause = find(pm, "butterfly");
        String flip = find(pm, "butterfly_flip");
        if (pause == null || flip == null) {
            if (!warned) {
                warned = true;
                ChunkFinderClient.LOGGER.warn("Butterfly packs not found in the pack list");
            }
            return;
        }
        String want = !cfg.butterflyEnabled ? null : (cfg.butterflyNonStop ? flip : pause);
        List<String> enabled = new ArrayList<>(pm.getEnabledIds());
        boolean hasPause = enabled.contains(pause);
        boolean hasFlip = enabled.contains(flip);
        boolean ok = (want == null && !hasPause && !hasFlip)
            || (want != null && enabled.contains(want) && (want.equals(pause) ? !hasFlip : !hasPause));
        if (ok) return;
        enabled.remove(pause);
        enabled.remove(flip);
        if (want != null) enabled.add(want);
        pm.setEnabledProfiles(enabled);
        mc.options.refreshResourcePacks(pm);
    }
}
