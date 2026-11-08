/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.concurrent.TimeUnit;

/** Bounds the visible wait for the renderer, bridge and first OSR frame. */
final class PokerVideoDocumentLoad {
    static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30);
    private boolean loaded;
    private boolean painted;
    private long paintWaitingSince;
    private long waitingSince;
    private String error;

    synchronized void reset() {
        loaded = false;
        painted = false;
        paintWaitingSince = waitingSince = 0;
        error = null;
    }

    synchronized void loading() { loaded = false; waitingSince = 0; }
    synchronized void loaded() { loaded = true; pulse(); }
    synchronized void pulse() { waitingSince = 0; }
    synchronized void failed(String message) { error = message; }
    synchronized String error() { return error; }

    synchronized boolean poll(boolean running, boolean hasFrame, long now) {
        if (!running || error != null) {
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
