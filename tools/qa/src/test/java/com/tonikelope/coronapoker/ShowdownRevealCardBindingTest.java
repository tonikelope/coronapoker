package com.tonikelope.coronapoker;

import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ShowdownRevealCardBindingTest {

    @Test
    void changingEitherRevealedCardChangesTheSignedPayload() {
        byte[] handId = new byte[CanonicalActionRecord.HAND_ID_BYTES];
        byte[] key = new byte[32];

        byte[] original = IdentityManager.showdownPayload(handId, "alice", key, 7, 31);
        byte[] changed = IdentityManager.showdownPayload(handId, "alice", key, 7, 32);

        assertFalse(Arrays.equals(original, changed),
                "POTCARDS plaintext must be covered by the player's showdown signature");
        assertArrayEquals(original,
                IdentityManager.showdownPayload(handId, "alice", key, 31, 7),
                "Hold'em pockets are unordered and UI sorting must not invalidate the signature");
        assertThrows(IllegalArgumentException.class,
                () -> IdentityManager.showdownPayload(handId, "alice", key, 7, 7));
    }

    @Test
    void signatureVerifiesOnlyForTheAuthorizedUnorderedPocket() {
        IdentityManager peer = IdentityManager.initializeForNick(
                "__qa_showdown_peer_" + System.nanoTime());
        byte[] handId = new byte[CanonicalActionRecord.HAND_ID_BYTES];
        handId[0] = 9;
        byte[] key = new byte[32];
        key[0] = 3;

        byte[] signature = peer.signShowdownReveal(handId, "alice", key, 7, 31);

        assertTrue(IdentityManager.verifyShowdownReveal(peer.getPublicKey(), handId,
                "alice", key, 7, 31, signature));
        assertTrue(IdentityManager.verifyShowdownReveal(peer.getPublicKey(), handId,
                "alice", key, 31, 7, signature));
        assertFalse(IdentityManager.verifyShowdownReveal(peer.getPublicKey(), handId,
                "alice", key, 7, 32, signature));
    }
}
