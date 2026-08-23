package com.tonikelope.coronapoker;

import java.util.Base64;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class RecoverySpectatorFossilBindingTest {

    private static final String HAND_ID = Base64.getEncoder().encodeToString(
            new byte[CanonicalActionRecord.HAND_ID_BYTES]);

    @Test
    void incumbentSpectatorInCurrentFossilRingMustReplayRecovery() {
        String fossil = fossil("client1", "client2", HAND_ID);

        assertTrue(Crupier.shouldLoadLocalRecoveryFossil(
                true, true, "client2", HAND_ID, HAND_ID, fossil));
    }

    @Test
    void newcomerOrStaleFossilMustRemainPassiveObserver() {
        String fossil = fossil("client1", "client2", HAND_ID);
        byte[] otherHandBytes = new byte[CanonicalActionRecord.HAND_ID_BYTES];
        otherHandBytes[0] = 1;
        String otherHand = Base64.getEncoder().encodeToString(otherHandBytes);

        assertFalse(Crupier.shouldLoadLocalRecoveryFossil(
                true, false, "client2", HAND_ID, HAND_ID, fossil));
        assertFalse(Crupier.shouldLoadLocalRecoveryFossil(
                true, true, "newcomer", HAND_ID, HAND_ID, fossil));
        assertFalse(Crupier.shouldLoadLocalRecoveryFossil(
                true, true, "client2", HAND_ID, otherHand, fossil));
        assertFalse(Crupier.shouldLoadLocalRecoveryFossil(
                true, true, "client2", HAND_ID, HAND_ID,
                fossil("client1", "client2", otherHand)));
    }

    @Test
    void recoveredPreflopReplayMustNotRewriteImmutableHandRoster() {
        assertTrue(Crupier.shouldPersistStreetRoster(Crupier.PREFLOP, 0));
        assertFalse(Crupier.shouldPersistStreetRoster(Crupier.PREFLOP, 1));
        assertTrue(Crupier.shouldPersistStreetRoster(Crupier.FLOP, 1));
    }

    private static String fossil(String first, String second, String handId) {
        Base64.Encoder encoder = Base64.getEncoder();
        return "ORDER@"
                + encoder.encodeToString(first.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                + ","
                + encoder.encodeToString(second.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                + ",#HAND_ID@" + handId
                + "#RIT@false,false,-1#STRADDLE@false";
    }
}
