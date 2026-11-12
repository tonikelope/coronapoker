/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

/** One provider navigation and a shared cooldown across all gallery windows. */
final class GdxImageSearchGate {

    static final long BUSY = -1L;
    private Object active;
    private long blockedUntil;

    synchronized long begin(Object search, long now) {
        long wait = remainingSeconds(now);
        if (wait > 0) return wait;
        if (active != null) return BUSY;
        active = search;
        return 0;
    }

    synchronized long finish(Object search, long now, long retryAt) {
        if (active == search && search != null) {
            active = null;
            blockedUntil = Math.max(blockedUntil, retryAt);
        }
        return remainingSeconds(now);
    }

    synchronized long remainingSeconds(long now) {
        long wait = blockedUntil - now;
        return wait > 0 ? (wait - 1) / 1000 + 1 : 0;
    }
}
