package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class CommunitySpectatorRingTest {

    @Test
    public void ringContributorWithBothUnlocksMustConsumeItsCommunityPiece() {
        assertFalse(Crupier.isLocalCommunityObserver(true, new byte[32], new byte[32]));
    }

    @Test
    public void peerOutsideRingOrMissingEitherUnlockIsPassiveObserver() {
        assertTrue(Crupier.isLocalCommunityObserver(false, new byte[32], new byte[32]));
        assertTrue(Crupier.isLocalCommunityObserver(true, null, new byte[32]));
        assertTrue(Crupier.isLocalCommunityObserver(true, new byte[32], null));
    }
}
