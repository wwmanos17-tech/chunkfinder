package com.chunkfinder;


import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Session Timer: how long you have been in this world / server, a stopwatch you start, stop and reset with keys,
 * and optionally your computer's clock. It is shown inside the FPS bar (next to the logo and FPS).
 */
public final class SessionTimer {
    private SessionTimer() {}

    private static long sessionStart = -1;
    private static boolean running = false;
    private static long startedAt = 0;
    private static long accumulated = 0;

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void clear() {
        sessionStart = -1;
    }

    public static void toggleStopwatch() {
        long now = System.currentTimeMillis();
        if (running) {
            accumulated += now - startedAt;
            running = false;
        } else {
            startedAt = now;
            running = true;
        }
    }

    public static void resetStopwatch() {
        running = false;
        accumulated = 0;
    }

    private static long stopwatchMs() {
        return accumulated + (running ? System.currentTimeMillis() - startedAt : 0);
    }

    private static String hms(long ms) {
        long s = ms / 1000;
        return String.format("%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    private static String stopwatch(long ms) {
        long tenths = (ms / 100) % 10;
        long s = ms / 1000;
        if (s >= 3600) return String.format("%d:%02d:%02d.%d", s / 3600, (s / 60) % 60, s % 60, tenths);
        return String.format("%02d:%02d.%d", s / 60, s % 60, tenths);
    }

    /** The pieces shown inside the FPS bar: time online, stopwatch, computer clock (whatever is switched on). */
    public static void segments(List<String> text, List<Integer> colors) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.timerEnabled) return;
        if (sessionStart < 0) sessionStart = System.currentTimeMillis();
        if (cfg.timerShowSession) {
            text.add(hms(System.currentTimeMillis() - sessionStart));
            colors.add(0xFFF4ECD6);
        }
        if (cfg.timerShowStopwatch) {
            text.add("SW " + stopwatch(stopwatchMs()));
            colors.add(running ? 0xFFE8C170 : 0xFF9A9078);
        }
        if (cfg.timerShowClock) {
            text.add(LocalTime.now().format(CLOCK));
            colors.add(0xFFF4ECD6);
        }
    }
}
