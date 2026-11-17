/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.network;

import java.util.concurrent.TimeUnit;

/** Inbound traffic can extend inactivity grace, but never the absolute wait cap. */
final class PeerReplyDeadline {
    private final long started;
    private final long idleNanos;
    private final long hardNanos;
    private long progress;

    PeerReplyDeadline(long now, long idleMillis, long hardMillis) {
        if (idleMillis <= 0L || hardMillis < idleMillis) throw new IllegalArgumentException("invalid reply budgets");
        started = progress = now;
        idleNanos = TimeUnit.MILLISECONDS.toNanos(idleMillis);
        hardNanos = TimeUnit.MILLISECONDS.toNanos(hardMillis);
    }

    long remainingNanos(long now, long latestInboundProgress) {
        long elapsed = now - started;
        long candidate = latestInboundProgress - started;
        if (candidate >= 0L && candidate <= elapsed && candidate > progress - started) {
            progress = latestInboundProgress;
        }
        return Math.min(hardNanos - elapsed, idleNanos - (now - progress));
    }

    static long timeoutMillis(long minimum, long lastSuccessfulRtt) {
        if (minimum <= 0L || minimum > 60_000L) throw new IllegalArgumentException("invalid minimum reply budget");
        long rttBudget = lastSuccessfulRtt <= 0L ? 30_000L
                : lastSuccessfulRtt >= 15_000L ? 60_000L : 4L * lastSuccessfulRtt;
        return Math.max(minimum, rttBudget);
    }
}
