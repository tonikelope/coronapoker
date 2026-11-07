/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.concurrent.TimeUnit;

/** Bounds the native document stage before JavaScript's own load guards exist. */
final class PokerVideoDocumentLoad {
    static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30);
    private boolean loaded;
    private long waitingSince;
    private String error;

    synchronized void reset() { loaded = false; waitingSince = 0; error = null; }
    synchronized void loaded() { loaded = true; waitingSince = 0; error = null; }
    synchronized void failed(String message) { error = message; waitingSince = 0; }
    synchronized String error() { return error; }

    synchronized boolean poll(boolean running, long now) {
        if (!running || loaded || error != null) { waitingSince = 0; return false; }
        if (waitingSince == 0) waitingSince = now;
        if (now - waitingSince < TIMEOUT_NANOS) return false;
        failed("El navegador del vídeo no respondió. Pulsa Reintentar.");
        return true;
    }
}
