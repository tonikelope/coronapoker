/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.Objects;

/** Shared hover dwell used by every GDX tooltip surface. */
final class GdxTooltipDelay {

    static final long DEFAULT_DELAY_NANOS = 500_000_000L;

    private final long delayNanos;
    private String target;
    private long enteredAtNanos;

    GdxTooltipDelay() {
        this(DEFAULT_DELAY_NANOS);
    }

    GdxTooltipDelay(long delayNanos) {
        if (delayNanos < 0L) {
            throw new IllegalArgumentException("Tooltip delay cannot be negative");
        }
        this.delayNanos = delayNanos;
    }

    boolean ready(String nextTarget) {
        return ready(nextTarget, System.nanoTime());
    }

    boolean ready(String nextTarget, long nowNanos) {
        Objects.requireNonNull(nextTarget, "nextTarget");
        if (!nextTarget.equals(target)) {
            target = nextTarget;
            enteredAtNanos = nowNanos;
            return delayNanos == 0L;
        }
        return nowNanos - enteredAtNanos >= delayNanos;
    }

    void clear() {
        target = null;
        enteredAtNanos = 0L;
    }
}
