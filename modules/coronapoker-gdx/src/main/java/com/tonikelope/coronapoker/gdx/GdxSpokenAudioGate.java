/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Shared serial gate for TTS and recorded voice playback.
 *
 * <p>Swing protects both routes with the same {@code Audio.TTS_LOCK}.  Keeping
 * separate GDX workers is useful because each has different cancellation and
 * decoding rules, but they must never speak over each other.  A fair,
 * interruptible lock preserves that contract without blocking the render
 * thread.</p>
 */
final class GdxSpokenAudioGate {

    private static final ReentrantLock LOCK = new ReentrantLock(true);

    static <T> T call(Callable<T> operation) throws Exception {
        Objects.requireNonNull(operation, "operation");
        LOCK.lockInterruptibly();
        try {
            return operation.call();
        } finally {
            LOCK.unlock();
        }
    }

    private GdxSpokenAudioGate() {
    }
}
