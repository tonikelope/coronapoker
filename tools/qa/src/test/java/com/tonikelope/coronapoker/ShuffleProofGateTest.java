package com.tonikelope.coronapoker;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ShuffleProofGateTest {

    private static final int POCKET = Crupier.UNLOCK_PHASE_POCKET;
    private static final int COMMUNITY = POCKET + 1; // cualquier fase != pocket (flop/turn/river/rabbit)

    private static byte[] deck(int tag) {
        byte[] b = new byte[32];
        b[0] = (byte) tag;
        return b;
    }

    @Test
    public void freshUnverifiedCommunityWaits() {
        // A fresh deal must never reveal community until its proof has verified.
        byte[] m = deck(1);
        assertEquals(Crupier.ShuffleProofGateDecision.WAIT,
                Crupier.shuffleProofGateDecision(COMMUNITY, m, m, null, null),
                "fresh + community + proof pending -> wait");
    }

    @Test
    public void verifiedCommunityAllows() {
        byte[] m = deck(1);
        assertEquals(Crupier.ShuffleProofGateDecision.ALLOW,
                Crupier.shuffleProofGateDecision(COMMUNITY, m, m, deck(1), null));
    }

    @Test
    public void pocketPhaseWaitsWhileProofIsPending() {
        byte[] m = deck(1);
        assertEquals(Crupier.ShuffleProofGateDecision.WAIT,
                Crupier.shuffleProofGateDecision(POCKET, m, m, null, null));
    }

    @Test
    public void identicalDeckBytesNeedFreshVerificationInTheNextHand() throws Exception {
        Crupier dealer = new Crupier();
        byte[] previousDeck = deck(1);
        dealer.local_mega_packet = previousDeck;
        dealer.dual_lock_expect_bundle_for = previousDeck;
        dealer.dual_lock_verified_megapacket = previousDeck;
        assertEquals(Crupier.ShuffleProofGateDecision.ALLOW,
                dealer.awaitShuffleProofGate(POCKET, 0L));

        Method install = Crupier.class.getDeclaredMethod("expectFreshShuffleProof", byte[].class);
        install.setAccessible(true);
        install.invoke(dealer, (Object) previousDeck.clone());
        assertEquals(Crupier.ShuffleProofGateDecision.REJECT,
                dealer.awaitShuffleProofGate(POCKET, 0L),
                "an earlier verdict must not authorize an unlock for the new hand");
    }

    @Test
    public void recoveredDeckWithoutPersistedVerificationRejects() {
        byte[] m = deck(2);
        assertEquals(Crupier.ShuffleProofGateDecision.REJECT,
                Crupier.shuffleProofGateDecision(COMMUNITY, m, null, null, null),
                "recovery without a persisted proof must fail closed");
    }

    @Test
    public void failedProofRejects() {
        byte[] m = deck(1);
        assertEquals(Crupier.ShuffleProofGateDecision.REJECT,
                Crupier.shuffleProofGateDecision(COMMUNITY, m, m, null, deck(1)));
    }

    @Test
    public void aLaterFailureOverridesAnEarlierVerifiedMarker() {
        byte[] m = deck(1);
        assertEquals(Crupier.ShuffleProofGateDecision.REJECT,
                Crupier.shuffleProofGateDecision(COMMUNITY, m, m, deck(1), deck(1)),
                "failure or broadcast loss must dominate an earlier local verification");
    }

    @Test
    public void nullMegapacketRejectsCommunityUnlock() {
        assertEquals(Crupier.ShuffleProofGateDecision.REJECT,
                Crupier.shuffleProofGateDecision(COMMUNITY, null, null, null, null));
    }
}
