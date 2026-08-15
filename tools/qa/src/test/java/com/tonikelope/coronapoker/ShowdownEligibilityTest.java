package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ShowdownEligibilityTest {

    @Test
    void disconnectedAllInRemainsInShowdownButOtherExitsDoNot() {
        assertFalse(Crupier.shouldRemoveExitedPlayerFromShowdown(true, Player.ALLIN));
        assertTrue(Crupier.shouldRemoveExitedPlayerFromShowdown(true, Player.FOLD));
        assertTrue(Crupier.shouldRemoveExitedPlayerFromShowdown(true, Player.BET));
        assertFalse(Crupier.shouldRemoveExitedPlayerFromShowdown(false, Player.BET));
    }
}
