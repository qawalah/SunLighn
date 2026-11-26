package dev.qawalah.sunwhere.server;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

/** Counts events per key in a sliding window, in memory (one machine; a restart forgives everyone). */
final class RateLimit {
    private final ConcurrentHashMap<String, ArrayDeque<Long>> hits = new ConcurrentHashMap<>();

    /** Records one event; false (and not recorded) when the key already had {@code max} in the last {@code windowMs}. */
    boolean allow(String key, int max, long windowMs) {
        long now = System.currentTimeMillis();
        ArrayDeque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > windowMs) q.pollFirst();
            if (q.size() >= max) return false;
            q.addLast(now);
            return true;
        }
    }

    /** Drops keys with nothing recent, so the map does not grow for ever. */
    void sweep() {
        long now = System.currentTimeMillis();
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || now - e.getValue().peekLast() > 86_400_000L;
            }
        });
    }
}
