package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProofTimeoutMisdealTest {
    @Test
    public void timeoutTerminalDecisionCannotStartBetting() {
        assertEquals(Crupier.ShuffleProofStartDecision.MISDEAL,
                Crupier.shuffleProofStartDecision(Crupier.ShuffleProofGateDecision.REJECT));
    }
}
