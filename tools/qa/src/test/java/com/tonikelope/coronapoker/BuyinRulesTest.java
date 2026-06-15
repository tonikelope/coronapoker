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
 * Money-critical buy-in ceiling / headroom arithmetic. Guards that no rebuy or
 * top-up can ever push a stack above the table ceiling, in either mode.
 */
public class BuyinRulesTest {

    @Test
    void rangeFollowsBigBlind() {
        // Arrange
        float bb = 1.0f;

        // Act / Assert: 10BB / 50BB / 100BB
        assertEquals(10, BuyinRules.min(bb));
        assertEquals(50, BuyinRules.defaultBuyin(bb));
        assertEquals(100, BuyinRules.max(bb));
    }

    @Test
    void capIsBuyinWhenFixedAnd100bbWhenVariable() {
        // Fixed mode: the ceiling is the single shared buy-in.
        assertEquals(50, BuyinRules.cap(true, 50, 1.0f));
        // Variable mode: the ceiling is 100BB regardless of the spinner buy-in.
        assertEquals(100, BuyinRules.cap(false, 50, 1.0f));
    }

    @Test
    void variableHeadroomNeverLetsStackExceedCap() {
        // Arrange: variable mode, cap 100BB.
        // Act / Assert
        assertEquals(70, BuyinRules.headroom(false, 50, 1.0f, 30f)); // 100 - 30
        assertEquals(0, BuyinRules.headroom(false, 50, 1.0f, 100f)); // already at cap
        assertEquals(0, BuyinRules.headroom(false, 50, 1.0f, 120f)); // over cap -> 0, never negative
    }

    @Test
    void fixedModeTopUpCannotExceedSingleBuyin() {
        // A fixed-buy-in-50 player at 30 may add at most 20 (-> 50), not a full 50.
        assertEquals(20, BuyinRules.headroom(true, 50, 1.0f, 30f));
        assertEquals(0, BuyinRules.headroom(true, 50, 1.0f, 50f));
    }

    @Test
    void fractionalStackRoundsUpConservatively() {
        // bb 0.20 -> variable cap = 20 chips.
        // stack 19.5 -> ceil 20 -> no room (a whole chip would overshoot to 20.5).
        assertEquals(0, BuyinRules.headroom(false, 4, 0.20f, 19.5f));
        // stack 18.0 -> ceil 18 -> room for 2.
        assertEquals(2, BuyinRules.headroom(false, 4, 0.20f, 18.0f));
    }

    @Test
    void applyingHeadroomNeverExceedsCapAcrossTheRange() {
        // Invariant sweep: for any stack in [0, cap], stack + headroom <= cap.
        int cap = BuyinRules.cap(false, 50, 1.0f); // 100
        for (int s = 0; s <= cap; s++) {
            int head = BuyinRules.headroom(false, 50, 1.0f, s);
            assertTrue(s + head <= cap, "stack " + s + " + headroom " + head + " > cap " + cap);
        }
    }
}
