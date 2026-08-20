package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class RitLocalVetoCannotBeOverriddenTest {

    @Test
    void trueResultRequiresThisEligibleClientToHaveVotedForRit() {
        assertTrue(Crupier.ritResultCompatibleWithLocalVote(
                true, RunItTwiceDialog.VOTE_RUN_IT_TWICE, true));
        assertFalse(Crupier.ritResultCompatibleWithLocalVote(
                true, RunItTwiceDialog.VOTE_NORMAL, true));
        assertFalse(Crupier.ritResultCompatibleWithLocalVote(
                true, RunItTwiceDialog.VOTE_PENDING, true));
        assertTrue(Crupier.ritResultCompatibleWithLocalVote(
                false, RunItTwiceDialog.VOTE_PENDING, true));
        assertTrue(Crupier.ritResultCompatibleWithLocalVote(
                true, RunItTwiceDialog.VOTE_NORMAL, false));
    }
}
