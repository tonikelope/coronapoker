package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class ProofBoundToHandTest {
    @Test
    public void anotherHandDerivesAnotherProofGenesis() {
        byte[] handA = new byte[16];
        byte[] handB = new byte[16];
        handB[15] = 1;
        String[] roster = {"alice", "bob"};
        assertFalse(java.util.Arrays.equals(
                Crupier.contextBoundShuffleGenesis("session", handA, roster),
                Crupier.contextBoundShuffleGenesis("session", handB, roster)));
    }
}
