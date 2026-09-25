/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import java.util.OptionalInt;
import java.util.function.IntPredicate;

/** Total, bounded traversal of a circular seat ring. */
public final class SeatRing {

    private SeatRing() {
    }

    public static OptionalInt nextActiveSeat(int ringSize, int startInclusive,
            IntPredicate activeSeat) {
        if (ringSize < 0) {
            throw new IllegalArgumentException("ringSize must be non-negative");
        }
        if (activeSeat == null) {
            throw new IllegalArgumentException("activeSeat required");
        }
        for (int offset = 0; offset < ringSize; offset++) {
            int seat = Math.floorMod(startInclusive + offset, ringSize);
            if (activeSeat.test(seat)) {
                return OptionalInt.of(seat);
            }
        }
        return OptionalInt.empty();
    }
}
