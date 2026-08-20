package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Regression coverage for CP-ZT-001's pocket/preflop proof barrier. */
public class PreflopFoldCannotSettleWithoutRequiredShuffleProofTest {

    private static byte[] deck(int tag) {
        byte[] value = new byte[32];
        value[0] = (byte) tag;
        return value;
    }

    @Test
    public void preflopFoldCannotSettleWithoutRequiredShuffleProof() {
        byte[] currentDeck = deck(1);
        assertEquals(Crupier.ShuffleProofGateDecision.WAIT,
                Crupier.shuffleProofGateDecision(Crupier.UNLOCK_PHASE_POCKET,
                        currentDeck, currentDeck, null, null));
    }

    @Test
    public void invalidPocketShuffleNeverStartsBetting() {
        byte[] currentDeck = deck(1);
        assertEquals(Crupier.ShuffleProofGateDecision.REJECT,
                Crupier.shuffleProofGateDecision(Crupier.UNLOCK_PHASE_POCKET,
                        currentDeck, currentDeck, null, currentDeck));
    }

    @Test
    public void proofIsBoundToCurrentDeck() {
        byte[] currentDeck = deck(1);
        assertNotEquals(Crupier.ShuffleProofGateDecision.ALLOW,
                Crupier.shuffleProofGateDecision(Crupier.UNLOCK_PHASE_POCKET,
                        currentDeck, currentDeck, deck(2), null));
    }

    @Test
    public void proofContextIsBoundToSessionHandAndOrderedRoster() {
        byte[] handA = new byte[16];
        byte[] handB = new byte[16];
        handB[0] = 1;
        String[] roster = {"alice", "bob"};
        String[] reversed = {"bob", "alice"};
        byte[] baseline = Crupier.contextBoundShuffleGenesis("session-a", handA, roster);

        assertNotEquals(java.util.Arrays.toString(baseline), java.util.Arrays.toString(
                Crupier.contextBoundShuffleGenesis("session-b", handA, roster)));
        assertNotEquals(java.util.Arrays.toString(baseline), java.util.Arrays.toString(
                Crupier.contextBoundShuffleGenesis("session-a", handB, roster)));
        assertNotEquals(java.util.Arrays.toString(baseline), java.util.Arrays.toString(
                Crupier.contextBoundShuffleGenesis("session-a", handA, reversed)));
    }

    @Test
    public void proofTimeoutCausesMisdealInsteadOfBetting() {
        assertEquals(Crupier.ShuffleProofStartDecision.MISDEAL,
                Crupier.shuffleProofStartDecision(Crupier.ShuffleProofGateDecision.REJECT));
    }

    @Test
    public void onlyVerifiedProofStartsBetting() {
        assertEquals(Crupier.ShuffleProofStartDecision.START_BETTING,
                Crupier.shuffleProofStartDecision(Crupier.ShuffleProofGateDecision.ALLOW));
        assertEquals(Crupier.ShuffleProofStartDecision.MISDEAL,
                Crupier.shuffleProofStartDecision(Crupier.ShuffleProofGateDecision.WAIT));
    }
}
