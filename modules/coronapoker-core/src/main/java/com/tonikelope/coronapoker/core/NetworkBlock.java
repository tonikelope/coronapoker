/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core;

import java.util.Objects;

/** Immutable, host-local view of a temporary network quarantine. */
public record NetworkBlock(String address, long blockedUntilEpochMillis,
        Reason reason) {

    public enum Reason {
        CONNECTION_ABUSE
    }

    public NetworkBlock {
        address = Objects.requireNonNull(address, "address").trim();
        reason = Objects.requireNonNull(reason, "reason");
        if (address.isEmpty()) {
            throw new IllegalArgumentException("address is required");
        }
        if (blockedUntilEpochMillis <= 0L) {
            throw new IllegalArgumentException("blockedUntilEpochMillis must be positive");
        }
    }

    public long remainingMillis(long nowEpochMillis) {
        return Math.max(0L, blockedUntilEpochMillis - nowEpochMillis);
    }
}
