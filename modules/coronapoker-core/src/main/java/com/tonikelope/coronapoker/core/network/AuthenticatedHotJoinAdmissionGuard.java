/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.network;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bounds authenticated hot-join churn without turning a shared address into
 * an IP ban.  The persistent player identity is the narrowest useful key: ten
 * legitimate clients behind one NAT remain independent, while one valid
 * identity cannot repeatedly allocate and tear down warming-table state.
 */
final class AuthenticatedHotJoinAdmissionGuard {

    enum Denial { NONE, IDENTITY_RATE, GLOBAL_RATE, STATE_CAPACITY }

    record Decision(Denial denial, long retryAfterMillis) {
        Decision {
            Objects.requireNonNull(denial, "denial");
            retryAfterMillis = Math.max(0L, retryAfterMillis);
        }

        boolean admitted() {
            return denial == Denial.NONE;
        }
    }

    private static final class IdentityState {
        private final ArrayDeque<Long> admissions = new ArrayDeque<>();
        private long blockedUntil;
        private long lastSeenAt;
    }

    private final int maxIdentityAdmissions;
    private final int maxGlobalAdmissions;
    private final int maxTrackedIdentities;
    private final long windowMillis;
    private final long identityCooldownMillis;
    private final long idleRetentionMillis;
    private final Map<String, IdentityState> identities
            = new LinkedHashMap<>();
    private final ArrayDeque<Long> globalAdmissions = new ArrayDeque<>();

    AuthenticatedHotJoinAdmissionGuard(int maxIdentityAdmissions,
            int maxGlobalAdmissions, int maxTrackedIdentities,
            long windowMillis, long identityCooldownMillis,
            long idleRetentionMillis) {
        if (maxIdentityAdmissions <= 0
                || maxGlobalAdmissions < maxIdentityAdmissions
                || maxTrackedIdentities <= 0 || windowMillis <= 0L
                || identityCooldownMillis <= 0L
                || idleRetentionMillis < identityCooldownMillis) {
            throw new IllegalArgumentException(
                    "Invalid authenticated hot-join limits");
        }
        this.maxIdentityAdmissions = maxIdentityAdmissions;
        this.maxGlobalAdmissions = maxGlobalAdmissions;
        this.maxTrackedIdentities = maxTrackedIdentities;
        this.windowMillis = windowMillis;
        this.identityCooldownMillis = identityCooldownMillis;
        this.idleRetentionMillis = idleRetentionMillis;
    }

    synchronized Decision acquire(String identity, long nowMillis) {
        String key = Objects.requireNonNull(identity, "identity");
        prune(nowMillis);
        pruneWindow(globalAdmissions, nowMillis);
        if (globalAdmissions.size() >= maxGlobalAdmissions) {
            return new Decision(Denial.GLOBAL_RATE,
                    retryAfter(globalAdmissions, nowMillis));
        }

        IdentityState state = identities.get(key);
        if (state == null) {
            if (identities.size() >= maxTrackedIdentities) {
                return new Decision(Denial.STATE_CAPACITY, windowMillis);
            }
            state = new IdentityState();
            identities.put(key, state);
        }
        state.lastSeenAt = nowMillis;
        if (nowMillis < state.blockedUntil) {
            return new Decision(Denial.IDENTITY_RATE,
                    state.blockedUntil - nowMillis);
        }
        pruneWindow(state.admissions, nowMillis);
        if (state.admissions.size() >= maxIdentityAdmissions) {
            state.blockedUntil = nowMillis + identityCooldownMillis;
            return new Decision(Denial.IDENTITY_RATE,
                    identityCooldownMillis);
        }
        state.admissions.addLast(nowMillis);
        globalAdmissions.addLast(nowMillis);
        return new Decision(Denial.NONE, 0L);
    }

    private void prune(long nowMillis) {
        Iterator<IdentityState> iterator = identities.values().iterator();
        while (iterator.hasNext()) {
            IdentityState state = iterator.next();
            pruneWindow(state.admissions, nowMillis);
            if (nowMillis >= state.blockedUntil
                    && state.admissions.isEmpty()
                    && nowMillis - state.lastSeenAt > idleRetentionMillis) {
                iterator.remove();
            }
        }
    }

    private void pruneWindow(ArrayDeque<Long> admissions, long nowMillis) {
        long earliest = nowMillis - windowMillis;
        while (!admissions.isEmpty()
                && admissions.peekFirst() <= earliest) {
            admissions.removeFirst();
        }
    }

    private long retryAfter(ArrayDeque<Long> admissions, long nowMillis) {
        return admissions.isEmpty() ? windowMillis
                : Math.max(1L, admissions.peekFirst() + windowMillis
                        - nowMillis);
    }
}
