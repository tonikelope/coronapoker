/*
 * Copyright (C) 2020 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Money-critical buy-in ceiling / headroom arithmetic with a configurable
 * [min,max] big-blind range. Guards that no rebuy or top-up can ever push a
 * stack above the table ceiling, in either mode and at any range (deep stack
 * included).
 */
public class BuyinRulesTest {

    @Test
    void rangeFollowsBigBlindAndMultipliers() {
        // Arrange
        float bb = 1.0f;
        // Default 10-100 range: 10BB / 50BB / 100BB.
        assertEquals(10, BuyinRules.min(bb, 10));
        assertEquals(50, BuyinRules.defaultBuyin(bb, 10, 100));
        assertEquals(100, BuyinRules.max(bb, 100));
        // Deep-stack 100-300 range scales the same way.
        assertEquals(100, BuyinRules.min(bb, 100));
        assertEquals(300, BuyinRules.max(bb, 300));
    }

    @Test
    void defaultBuyinClampsIntoTheRange() {
        // 50BB suggestion is in range -> 50.
        assertEquals(50, BuyinRules.defaultBuyin(1.0f, 10, 100));
        // Deep range whose minimum is above 50BB -> clamped up to the minimum.
        assertEquals(100, BuyinRules.defaultBuyin(1.0f, 100, 300));
        // Narrow range whose maximum is below 50BB -> clamped down to the maximum.
        assertEquals(40, BuyinRules.defaultBuyin(1.0f, 10, 40));
    }

    @Test
    void capIsBuyinWhenFixedAndMaxBbWhenVariable() {
        // Fixed mode: the ceiling is the single shared buy-in.
        assertEquals(50, BuyinRules.cap(true, 50, 1.0f, 100));
        // Variable mode: the ceiling is maxBB regardless of the spinner buy-in.
        assertEquals(100, BuyinRules.cap(false, 50, 1.0f, 100));
        // Deep-stack variable mode: ceiling rises to maxBB big blinds.
        assertEquals(300, BuyinRules.cap(false, 50, 1.0f, 300));
    }

    @Test
    void variableHeadroomNeverLetsStackExceedCap() {
        assertEquals(70, BuyinRules.headroom(false, 50, 1.0f, 100, 30f)); // 100 - 30
        assertEquals(0, BuyinRules.headroom(false, 50, 1.0f, 100, 100f)); // already at cap
        assertEquals(0, BuyinRules.headroom(false, 50, 1.0f, 100, 120f)); // over cap -> 0
        // Deep stack: cap 300, headroom scales.
        assertEquals(250, BuyinRules.headroom(false, 50, 1.0f, 300, 50f)); // 300 - 50
    }

    @Test
    void fixedModeTopUpCannotExceedSingleBuyin() {
        assertEquals(20, BuyinRules.headroom(true, 50, 1.0f, 100, 30f));
        assertEquals(0, BuyinRules.headroom(true, 50, 1.0f, 100, 50f));
    }

    @Test
    void fractionalStackRoundsUpConservatively() {
        // bb 0.20, variable cap 100BB = 20 chips.
        assertEquals(0, BuyinRules.headroom(false, 4, 0.20f, 100, 19.5f));
        assertEquals(2, BuyinRules.headroom(false, 4, 0.20f, 100, 18.0f));
    }

    @Test
    void buyinIsClampedToIntSafeCeilingAtHugeBlinds() {
        // The buy-in (chip count) is int across the model; the tallest default
        // ladder level (bb = 10,000,000) would make bb * 500 = 5e9 overflow int and
        // saturate. MAX_BUYIN keeps every chip-count int-safe and deterministic.
        double bb = 10_000_000;
        assertTrue(BuyinRules.MAX_BUYIN < Integer.MAX_VALUE);
        assertEquals(BuyinRules.MAX_BUYIN, BuyinRules.max(bb, BuyinRules.CEIL_MAX_BB));
        // The full range stays inside [0, MAX_BUYIN] and keeps min <= default <= max.
        int lo = BuyinRules.min(bb, 100);
        int def = BuyinRules.defaultBuyin(bb, 100, BuyinRules.CEIL_MAX_BB);
        int hi = BuyinRules.max(bb, BuyinRules.CEIL_MAX_BB);
        assertTrue(lo <= def && def <= hi);
        assertTrue(hi <= BuyinRules.MAX_BUYIN);
        // The variable cap and headroom inherit the bound (never overflow).
        assertTrue(BuyinRules.cap(false, 0, bb, BuyinRules.CEIL_MAX_BB) <= BuyinRules.MAX_BUYIN);
        // Normal tables are completely unaffected: bb=10000, 500BB = 5,000,000.
        assertEquals(5_000_000, BuyinRules.max(10_000, 500));
        assertEquals(50, BuyinRules.max(1.0f, 50));
    }

    @Test
    void applyingHeadroomNeverExceedsCapAcrossTheRange() {
        // Invariant sweep at the deepest allowed range: for any stack in [0, cap],
        // stack + headroom <= cap.
        int cap = BuyinRules.cap(false, 50, 1.0f, BuyinRules.CEIL_MAX_BB); // 500
        for (int s = 0; s <= cap; s++) {
            int head = BuyinRules.headroom(false, 50, 1.0f, BuyinRules.CEIL_MAX_BB, s);
            assertTrue(s + head <= cap, "stack " + s + " + headroom " + head + " > cap " + cap);
        }
    }
}
