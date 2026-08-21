/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Binds accounting to the immutable balance roster of one hand. */
public final class HandBalanceRoster {

    private HandBalanceRoster() {
    }

    public static Set<String> bind(Collection<String> players) {
        if (players == null || players.isEmpty() || players.stream()
                .anyMatch(player -> player == null || player.isEmpty())) {
            throw new IllegalArgumentException("hand balance roster is required");
        }
        Set<String> roster = Set.copyOf(players);
        if (roster.size() != players.size()) {
            throw new IllegalArgumentException("duplicate hand balance player");
        }
        return roster;
    }

    public static <T> Map<String, T> selectExact(Set<String> roster,
            Map<String, T> currentValues) {
        Set<String> bound = bind(roster);
        if (currentValues == null) {
            throw new IllegalArgumentException("current hand balances are required");
        }
        LinkedHashMap<String, T> selected = new LinkedHashMap<>();
        for (String player : bound) {
            T value = currentValues.get(player);
            if (value == null) {
                throw new IllegalArgumentException(
                        "missing current balance for hand player " + player);
            }
            selected.put(player, value);
        }
        return selected;
    }
}
