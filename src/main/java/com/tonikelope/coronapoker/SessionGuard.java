/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Monotonic table-session guard for callbacks with domain or SQL effects. */
public final class SessionGuard {

    public static final class Generation {
        private final SessionGuard owner;
        private final long value;

        private Generation(SessionGuard owner, long value) {
            this.owner = owner;
            this.value = value;
        }

        public long value() { return value; }
    }

    private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock(true);
    private long current;

    public Generation beginSession() {
        lifecycle.writeLock().lock();
        try {
            return new Generation(this, ++current);
        } finally {
            lifecycle.writeLock().unlock();
        }
    }

    public void invalidate(Generation generation) {
        lifecycle.writeLock().lock();
        try {
            if (belongsAndMatches(generation)) current++;
        } finally {
            lifecycle.writeLock().unlock();
        }
    }

    public boolean isCurrent(Generation generation) {
        lifecycle.readLock().lock();
        try {
            return belongsAndMatches(generation);
        } finally {
            lifecycle.readLock().unlock();
        }
    }

    /** Runs an effect atomically against invalidate/beginSession. */
    public boolean runIfCurrent(Generation generation, Runnable effect) {
        if (effect == null) throw new IllegalArgumentException("effect is required");
        lifecycle.readLock().lock();
        try {
            if (!belongsAndMatches(generation)) return false;
            effect.run();
            return true;
        } finally {
            lifecycle.readLock().unlock();
        }
    }

    private boolean belongsAndMatches(Generation generation) {
        return generation != null && generation.owner == this && generation.value == current;
    }
}
