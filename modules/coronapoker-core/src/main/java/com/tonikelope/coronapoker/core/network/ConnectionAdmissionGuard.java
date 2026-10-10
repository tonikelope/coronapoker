/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.network;

import com.tonikelope.coronapoker.core.NetworkBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * Bounds unauthenticated socket work and temporarily quarantines addresses
 * that repeatedly fail or are rejected during the lobby handshake.
 */
final class ConnectionAdmissionGuard {

    enum Denial { NONE, BANNED, GLOBAL_CAPACITY, ADDRESS_CAPACITY }

    record Admission(String address, Denial denial) {
        Admission {
            Objects.requireNonNull(address, "address");
            Objects.requireNonNull(denial, "denial");
        }
        boolean admitted() { return denial == Denial.NONE; }
    }

    private static final class AddressState {
        private int pending;
        private int rejected;
        private long rejectionWindowStartedAt;
        private long bannedUntil;
        private NetworkBlock.Reason banReason;
        private long lastSeenAt;
    }

    private final int maxGlobalPending;
    private final int maxAddressPending;
    private final int rejectionThreshold;
    private final long rejectionWindowMillis;
    private final long banMillis;
    private final long idleRetentionMillis;
    private final Map<String, AddressState> addresses = new HashMap<>();
    private int globalPending;

    ConnectionAdmissionGuard(int maxGlobalPending, int maxAddressPending,
            int rejectionThreshold, long rejectionWindowMillis,
            long banMillis, long idleRetentionMillis) {
        if (maxGlobalPending <= 0 || maxAddressPending <= 0
                || maxAddressPending > maxGlobalPending
                || rejectionThreshold <= 0 || rejectionWindowMillis <= 0L
                || banMillis <= 0L || idleRetentionMillis < banMillis) {
            throw new IllegalArgumentException("Invalid admission-guard limits");
        }
        this.maxGlobalPending = maxGlobalPending;
        this.maxAddressPending = maxAddressPending;
        this.rejectionThreshold = rejectionThreshold;
        this.rejectionWindowMillis = rejectionWindowMillis;
        this.banMillis = banMillis;
        this.idleRetentionMillis = idleRetentionMillis;
    }

    synchronized Admission acquire(String address, long nowMillis) {
        String key = Objects.requireNonNull(address, "address");
        prune(nowMillis);
        AddressState state = addresses.get(key);
        if (state != null) {
            state.lastSeenAt = nowMillis;
            if (nowMillis < state.bannedUntil) {
                return new Admission(key, Denial.BANNED);
            }
        }
        // Do not allocate attacker-controlled map entries while all global
        // handshake slots are already occupied.
        if (globalPending >= maxGlobalPending) {
            return new Admission(key, Denial.GLOBAL_CAPACITY);
        }
        if (state == null) {
            state = new AddressState();
            state.lastSeenAt = nowMillis;
            addresses.put(key, state);
        }
        if (state.pending >= maxAddressPending) {
            /*
             * Capacity is containment, not evidence of hostility. Several
             * legitimate clients may share one NAT address and arrive at the
             * same time. Refuse excess work, but never let that refusal build
             * an IP ban by itself.
             */
            return new Admission(key, Denial.ADDRESS_CAPACITY);
        }
        state.pending++;
        globalPending++;
        return new Admission(key, Denial.NONE);
    }

    synchronized void release(Admission admission, long nowMillis) {
        if (admission == null || !admission.admitted()) return;
        AddressState state = addresses.get(admission.address());
        if (state != null && state.pending > 0) {
            state.pending--;
            state.lastSeenAt = nowMillis;
        }
        if (globalPending > 0) globalPending--;
    }

    synchronized void rejected(Admission admission, long nowMillis) {
        if (admission == null || !admission.admitted()) return;
        AddressState state = addresses.computeIfAbsent(admission.address(),
                ignored -> new AddressState());
        state.lastSeenAt = nowMillis;
        reject(state, nowMillis);
    }

    synchronized void accepted(Admission admission, long nowMillis) {
        if (admission == null || !admission.admitted()) return;
        AddressState state = addresses.get(admission.address());
        if (state == null) return;
        state.rejected = 0;
        state.rejectionWindowStartedAt = nowMillis;
        state.bannedUntil = 0L;
        state.banReason = null;
        state.lastSeenAt = nowMillis;
    }

    synchronized boolean banned(String address, long nowMillis) {
        AddressState state = addresses.get(address);
        return state != null && nowMillis < state.bannedUntil;
    }

    synchronized int globalPending() { return globalPending; }

    synchronized int addressPending(String address) {
        AddressState state = addresses.get(address);
        return state == null ? 0 : state.pending;
    }

    synchronized java.util.List<NetworkBlock> activeBlocks(long nowMillis) {
        prune(nowMillis);
        ArrayList<NetworkBlock> result = new ArrayList<>();
        addresses.forEach((address, state) -> {
            if (nowMillis < state.bannedUntil) {
                result.add(new NetworkBlock(address, state.bannedUntil,
                        state.banReason == null
                                ? NetworkBlock.Reason.CONNECTION_ABUSE
                                : state.banReason));
            }
        });
        result.sort(Comparator.comparingLong(
                NetworkBlock::blockedUntilEpochMillis)
                .thenComparing(NetworkBlock::address));
        return java.util.List.copyOf(result);
    }

    synchronized boolean unblock(String address, long nowMillis) {
        AddressState state = addresses.get(Objects.requireNonNull(address,
                "address"));
        if (state == null || nowMillis >= state.bannedUntil) {
            prune(nowMillis);
            return false;
        }
        state.bannedUntil = 0L;
        state.banReason = null;
        state.rejected = 0;
        state.rejectionWindowStartedAt = nowMillis;
        state.lastSeenAt = nowMillis;
        if (state.pending == 0) addresses.remove(address);
        return true;
    }

    private void reject(AddressState state, long nowMillis) {
        if (state.rejectionWindowStartedAt == 0L
                || nowMillis - state.rejectionWindowStartedAt
                        > rejectionWindowMillis) {
            state.rejectionWindowStartedAt = nowMillis;
            state.rejected = 0;
        }
        state.rejected++;
        if (state.rejected >= rejectionThreshold) {
            state.bannedUntil = Math.max(state.bannedUntil,
                    nowMillis + banMillis);
            state.banReason = NetworkBlock.Reason.CONNECTION_ABUSE;
            state.rejected = 0;
            state.rejectionWindowStartedAt = nowMillis;
        }
    }

    private void prune(long nowMillis) {
        Iterator<AddressState> iterator = addresses.values().iterator();
        while (iterator.hasNext()) {
            AddressState state = iterator.next();
            if (state.pending == 0 && nowMillis >= state.bannedUntil
                    && nowMillis - state.lastSeenAt > idleRetentionMillis) {
                iterator.remove();
            }
        }
    }
}
