package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PreflopFoldWithoutProofRefundsTest {
    @Test
    public void pendingProofSelectsMisdealRefundNotNormalSettlement() {
        assertEquals(Crupier.ShuffleProofStartDecision.MISDEAL,
                Crupier.shuffleProofStartDecision(Crupier.ShuffleProofGateDecision.WAIT));
    }
}
