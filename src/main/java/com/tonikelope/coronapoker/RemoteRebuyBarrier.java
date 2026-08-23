/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;

/**
 * Orders asynchronous client-side rebuy relays against the next-hand balance
 * boundary. Registration happens on the single socket-reader thread before a
 * relay task is submitted; the dealer may therefore wait for exactly the
 * relays received before a later START_SRA_CASCADE without blocking that
 * reader or assuming that worker tasks finish in arrival order.
 */
final class RemoteRebuyBarrier {

    private final NavigableSet<Long> pending = new TreeSet<>();

    synchronized void register(long sequence) {
        if (sequence <= 0L || !pending.add(sequence)) {
            throw new IllegalArgumentException("positive unique rebuy sequence required");
        }
    }

    synchronized void complete(long sequence) {
        if (sequence > 0L && pending.remove(sequence)) {
            notifyAll();
        }
    }

    synchronized boolean hasPendingThrough(long cutoff) {
        return cutoff > 0L && !pending.isEmpty() && pending.first() <= cutoff;
    }

    synchronized boolean awaitThrough(long cutoff, BooleanSupplier cancelled) {
        if (cancelled == null) {
            throw new IllegalArgumentException("cancellation supplier required");
        }
        while (hasPendingThrough(cutoff) && !cancelled.getAsBoolean()) {
            try {
                wait();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !cancelled.getAsBoolean() && !hasPendingThrough(cutoff);
    }

    synchronized void signalStateChange() {
        notifyAll();
    }
}
