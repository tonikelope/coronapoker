package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.game.GameDecisionSink;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class RitLocalVetoCannotBeOverriddenTest {

    @Test
    void trueResultRequiresThisEligibleClientToHaveVotedForRit() {
        assertTrue(Crupier.ritResultCompatibleWithLocalVote(
                true, GameDecisionSink.VOTE_RUN_IT_TWICE, true));
        assertFalse(Crupier.ritResultCompatibleWithLocalVote(
                true, GameDecisionSink.VOTE_NORMAL, true));
        assertFalse(Crupier.ritResultCompatibleWithLocalVote(
                true, GameDecisionSink.VOTE_PENDING, true));
        assertTrue(Crupier.ritResultCompatibleWithLocalVote(
                false, GameDecisionSink.VOTE_PENDING, true));
        assertTrue(Crupier.ritResultCompatibleWithLocalVote(
                true, GameDecisionSink.VOTE_NORMAL, false));
    }
}
