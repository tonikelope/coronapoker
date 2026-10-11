/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CrupierHandReadyTest {

    @Test
    void acceptsOnlyTheImmediateNextBoundary() {
        assertTrue(Crupier.handReadyMatchesNextHand(
                new String[]{"GAME", "17", "HAND_READY", "2"}, 1));
        assertFalse(Crupier.handReadyMatchesNextHand(
                new String[]{"GAME", "17", "HAND_READY", "1"}, 1));
        assertFalse(Crupier.handReadyMatchesNextHand(
                new String[]{"GAME", "17", "HAND_READY", "3"}, 1));
    }

    @Test
    void acceptsOnlyAnExactAlreadyAppliedCurrentBoundaryAsDuplicate() {
        String[] current = {"GAME", "18", "HAND_READY", "2"};
        assertTrue(Crupier.handReadyMatchesAcceptedCurrentHand(
                current, 2, 2));
        assertFalse(Crupier.handReadyMatchesAcceptedCurrentHand(
                current, 2, 1));
        assertFalse(Crupier.handReadyMatchesAcceptedCurrentHand(
                new String[]{"GAME", "18", "HAND_READY", "1"}, 2, 2));
        assertFalse(Crupier.handReadyMatchesAcceptedCurrentHand(
                new String[]{"GAME", "018", "HAND_READY", "2"}, 2, 2));
    }
}
