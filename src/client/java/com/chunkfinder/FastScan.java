package com.chunkfinder;

import net.minecraft.client.MinecraftClient;

/** Shared settings for the "Fast Scan" switch: how many chunks a scanner may check per tick, and for how long. */
public final class FastScan {
    private FastScan() {}

    /** Chunks this close to you (in chunks) get their real underground data from the server, so they are re-checked often. */
    public static final int NEAR = 3;
    /** Ticks between re-checks of a chunk inside the near zone (20 ticks = 1 second). */
    public static final int NEAR_TICKS = 20;

    public static boolean on() {
        return ChunkFinderConfig.INSTANCE.fastScan;
    }

    private static boolean flying(MinecraftClient mc) {
        return mc.player != null && mc.player.getVelocity().horizontalLength() > 0.6;
    }

    /** Most chunks one scanner may check this tick. */
    public static int maxChunks(MinecraftClient mc, int normal) {
        if (!on()) return normal;
        return flying(mc) ? normal * 10 : normal * 5;
    }

    /** Time one scanner may spend this tick, in nanoseconds (about 2 to 3.5 ms). */
    public static long limitNs(MinecraftClient mc) {
        return flying(mc) ? 3_500_000L : 2_000_000L;
    }
}
