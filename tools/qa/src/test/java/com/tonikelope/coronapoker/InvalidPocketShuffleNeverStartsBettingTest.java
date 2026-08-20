package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InvalidPocketShuffleNeverStartsBettingTest {
    @Test
    public void failedProofSelectsMisdeal() {
        assertEquals(Crupier.ShuffleProofStartDecision.MISDEAL,
                Crupier.shuffleProofStartDecision(Crupier.ShuffleProofGateDecision.REJECT));
    }
}
