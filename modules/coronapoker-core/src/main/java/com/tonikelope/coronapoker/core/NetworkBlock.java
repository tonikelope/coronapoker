/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core;

import java.util.Objects;

/** Immutable, host-local view of one blocked IP address. */
public record NetworkBlock(String address, String lastNickname,
        long blockedUntilEpochMillis) {

    public NetworkBlock {
        address = Objects.requireNonNull(address, "address").trim();
        lastNickname = lastNickname == null ? "" : lastNickname.trim();
        if (address.isEmpty()) {
            throw new IllegalArgumentException("address is required");
        }
        if (blockedUntilEpochMillis <= 0L) {
            throw new IllegalArgumentException("blockedUntilEpochMillis must be positive");
        }
    }

    public NetworkBlock(String address, long blockedUntilEpochMillis) {
        this(address, "", blockedUntilEpochMillis);
    }

    public long remainingMillis(long nowEpochMillis) {
        if (sessionLifetime()) return Long.MAX_VALUE;
        return Math.max(0L, blockedUntilEpochMillis - nowEpochMillis);
    }

    /** True when the quarantine lasts until the hosting lobby is closed. */
    public boolean sessionLifetime() {
        return blockedUntilEpochMillis == Long.MAX_VALUE;
    }
}
