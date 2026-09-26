package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * TDD coverage for the host-side legality gate applied before a remote ACTION
 * can mutate the replicated player model. Signatures prove who sent a message;
 * they do not make an over-stack or below-minimum bet legal.
 */
class RemoteActionValidationTest {

    @Test
    void acceptsNormalCheckRaiseAndAllIn() {
        assertTrue(Crupier.isLegalRemoteAction(
                com.tonikelope.coronapoker.core.game.GamePlayerController.CHECK, 0d, 10d, 100d, 10d, 20d, 20d));
        assertTrue(Crupier.isLegalRemoteAction(
                com.tonikelope.coronapoker.core.game.GamePlayerController.BET, 40d, 10d, 100d, 20d, 20d, 10d));
        assertTrue(Crupier.isLegalRemoteAction(
                com.tonikelope.coronapoker.core.game.GamePlayerController.ALLIN, 0d, 10d, 100d, 20d, 20d, 10d));
        assertTrue(Crupier.isLegalRemoteAction(
                com.tonikelope.coronapoker.core.game.GamePlayerController.FOLD, 0d, 10d, 100d, 20d, 20d, 10d));
    }

    @Test
    void rejectsAnAuthenticatedButOverStackBet() {
        // A valid signature is not a licence to debit more than the actor owns.
        assertFalse(Crupier.isLegalRemoteAction(
                com.tonikelope.coronapoker.core.game.GamePlayerController.BET, 111d, 10d, 100d, 20d, 20d, 10d));
    }

    @Test
    void rejectsNegativeNonFiniteAndMalformedDecisionAmounts() {
        assertFalse(Crupier.isLegalRemoteAction(
                com.tonikelope.coronapoker.core.game.GamePlayerController.BET, -1d, 10d, 100d, 20d, 20d, 10d));
        assertFalse(Crupier.isLegalRemoteAction(
                com.tonikelope.coronapoker.core.game.GamePlayerController.BET, Double.NaN, 10d, 100d, 20d, 20d, 10d));
        assertFalse(Crupier.isLegalRemoteAction(
                99, 0d, 10d, 100d, 20d, 20d, 10d));
        assertFalse(Crupier.isLegalRemoteAction(
                com.tonikelope.coronapoker.core.game.GamePlayerController.CHECK, 1d, 10d, 100d, 20d, 20d, 10d));
    }

    @Test
    void rejectsBelowMinimumRaiseAndCallThatNeedsAllIn() {
        // Current bet is 20 and the previous raise was 20, so a full raise must
        // reach 40; a partial all-in is represented by ALLIN, not BET.
        assertFalse(Crupier.isLegalRemoteAction(
                com.tonikelope.coronapoker.core.game.GamePlayerController.BET, 35d, 10d, 100d, 20d, 20d, 10d));
        assertFalse(Crupier.isLegalRemoteAction(
                com.tonikelope.coronapoker.core.game.GamePlayerController.CHECK, 0d, 10d, 10d, 20d, 20d, 10d));
    }
}
