package com.smartcooking.app;

/** Identifies one discovery attempt so stale timeouts cannot finish a newer scan. */
final class DiscoveryWindow {
    private volatile long token;
    private volatile boolean active;
    long begin() { active = true; return ++token; }
    long token() { return token; }
    boolean active() { return active; }
    boolean finish(long expected) {
        if (!active || token != expected) return false;
        active = false;
        return true;
    }
}
