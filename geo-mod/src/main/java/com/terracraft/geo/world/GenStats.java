package com.terracraft.geo.world;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Mesures de génération de la Terre (phase 0) : chunks construits, temps par chunk, téléchargements de tuiles
 * retentés ou abandonnés. Remises à zéro par /terracraft generation reset ; lues par tools/geo_bench.sh.
 */
public final class GenStats {
    static final AtomicLong CHUNKS = new AtomicLong();
    static final AtomicLong NANOS = new AtomicLong();
    static final AtomicLong MAX_NANOS = new AtomicLong();
    static final AtomicLong RETRIES = new AtomicLong();
    static final AtomicLong FAILURES = new AtomicLong();

    private GenStats() {
    }

    static void chunk(long nanos) {
        CHUNKS.incrementAndGet();
        NANOS.addAndGet(nanos);
        MAX_NANOS.accumulateAndGet(nanos, Math::max);
    }

    public static void reset() {
        CHUNKS.set(0);
        NANOS.set(0);
        MAX_NANOS.set(0);
        RETRIES.set(0);
        FAILURES.set(0);
    }

    /** Résumé lisible (et analysable par le script de mesure). */
    public static String summary() {
        long chunks = CHUNKS.get();
        double mean = chunks == 0 ? 0 : NANOS.get() / 1e6 / chunks;
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        return String.format(java.util.Locale.ROOT, "chunks=%d moyenne=%.1fms max=%.1fms reessais=%d echecs=%d memoire=%d/%dMo",
                chunks, mean, MAX_NANOS.get() / 1e6, RETRIES.get(), FAILURES.get(), usedMb, runtime.maxMemory() / (1024 * 1024));
    }
}
