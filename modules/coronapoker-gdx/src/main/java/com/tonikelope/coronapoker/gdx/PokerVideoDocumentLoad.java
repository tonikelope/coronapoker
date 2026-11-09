/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.concurrent.TimeUnit;

/** Bounds the visible wait for the renderer, bridge and first OSR frame. */
final class PokerVideoDocumentLoad {
    static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30);
    static final long INITIAL_NAVIGATION_RETRY_NANOS = TimeUnit.SECONDS.toNanos(3);
    private boolean loaded;
    private boolean painted;
    private boolean initialNavigationRetryAvailable = true;
    private long navigationWaitingSince;
    private long paintWaitingSince;
    private long waitingSince;
    private String error;

    synchronized void reset() {
        loaded = false;
        painted = false;
        initialNavigationRetryAvailable = true;
        navigationWaitingSince = 0;
        paintWaitingSince = waitingSince = 0;
        error = null;
    }

    synchronized void loading() {
        initialNavigationRetryAvailable = false;
        loaded = false;
        waitingSince = 0;
    }

    synchronized void loaded() {
        initialNavigationRetryAvailable = false;
        loaded = true;
        pulse();
    }

    synchronized void pulse() { waitingSince = 0; }
    synchronized void failed(String message) { error = message; }
    synchronized String error() { return error; }

    synchronized boolean takeInitialNavigationRetry(long now) {
        if (!initialNavigationRetryAvailable || error != null) return false;
        if (navigationWaitingSince == 0) navigationWaitingSince = now;
        if (now - navigationWaitingSince < INITIAL_NAVIGATION_RETRY_NANOS) return false;
        initialNavigationRetryAvailable = false;
        return true;
    }

    synchronized boolean poll(boolean running, boolean hasFrame, long now) {
        if (!running || error != null) {
            navigationWaitingSince = 0;
            paintWaitingSince = waitingSince = 0;
            return false;
        }
        painted |= hasFrame;
        if (!painted && paintWaitingSince == 0) paintWaitingSince = now;
        if (waitingSince == 0) waitingSince = now;
        if (now - waitingSince >= TIMEOUT_NANOS) {
            failed(loaded ? "El reproductor dejó de responder. Pulsa Reintentar."
                    : "El navegador del vídeo no respondió. Pulsa Reintentar.");
            return true;
        }
        if (!painted && now - paintWaitingSince >= TIMEOUT_NANOS) {
            failed("No se recibió la primera imagen del vídeo. Pulsa Reintentar.");
            return true;
        }
        return false;
    }
}
