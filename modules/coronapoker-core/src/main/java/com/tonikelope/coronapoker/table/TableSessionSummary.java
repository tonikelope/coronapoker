/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.table;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable renderer-neutral result of one completed table session. */
public record TableSessionSummary(
        String localNickname,
        int handCount,
        long durationSeconds,
        long endedAtMillis,
        CloseReason reason,
        List<PlayerBalance> balances) {

    public TableSessionSummary {
        localNickname = Objects.requireNonNull(localNickname,
                "localNickname").trim();
        Objects.requireNonNull(reason, "reason");
        balances = List.copyOf(balances);
        if (handCount < 0 || durationSeconds < 0L || endedAtMillis < 0L) {
            throw new IllegalArgumentException(
                    "Summary counters cannot be negative");
        }
        Set<String> nicknames = new HashSet<>();
        for (PlayerBalance balance : balances) {
            if (!nicknames.add(balance.nickname())) {
                throw new IllegalArgumentException(
                        "Duplicate balance for " + balance.nickname());
            }
        }
    }

    public static TableSessionSummary empty() {
        return new TableSessionSummary("", 0, 0L, 0L,
                CloseReason.EXITED, List.of());
    }

    public boolean hasBalances() {
        return !balances.isEmpty();
    }

    public PlayerBalance localBalance() {
        return balances.stream()
                .filter(balance -> balance.nickname().equals(localNickname))
                .findFirst()
                .orElse(null);
    }

    public enum CloseReason {
        COMPLETED,
        EXITED,
        RECOVERABLE_STOP,
        FAILURE
    }

    public record PlayerBalance(String nickname, double finalStack,
            double totalBuyin, int rebuyCount) {

        public PlayerBalance {
            nickname = Objects.requireNonNull(nickname, "nickname").trim();
            if (nickname.isEmpty()) {
                throw new IllegalArgumentException("Balance nickname is required");
            }
            requireMoney(finalStack, "finalStack");
            requireMoney(totalBuyin, "totalBuyin");
            if (rebuyCount < 0) {
                throw new IllegalArgumentException("rebuyCount cannot be negative");
            }
        }

        public double netResult() {
            return clean(finalStack - totalBuyin);
        }
    }

    private static void requireMoney(double value, String name) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(
                    name + " must be finite and non-negative");
        }
    }

    private static double clean(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
