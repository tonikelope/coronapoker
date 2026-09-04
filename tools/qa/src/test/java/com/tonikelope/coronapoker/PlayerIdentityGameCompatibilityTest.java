package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.identity.PlayerIdentity;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Proves that Swing and Core/GDX use identical game-signature contracts. */
final class PlayerIdentityGameCompatibilityTest {

    @Test
    void everyGameDomainIsCompatibleInBothDirections() throws Exception {
        String nickname = "identity-contract";
        Path coronaDirectory = Path.of(System.getProperty("user.home"), ".coronapoker");
        PlayerIdentity core = PlayerIdentity.loadOrCreate(coronaDirectory, nickname);
        IdentityManager swing = IdentityManager.initializeForNick(nickname);
        assertTrue(swing.isReady(), swing.getLoadError());
        assertArrayEquals(swing.getPublicKey(), core.getPublicKey());

        byte[] record = bytes(92, 1);
        byte[] handId = bytes(16, 2);
        byte[] finalHash = bytes(32, 3);
        byte[] pocketKey = bytes(32, 4);
        byte[] rabbitNonce = bytes(16, 5);
        byte[] seatNonce = bytes(32, 6);
        byte[] commitment = bytes(32, 7);

        assertTrue(IdentityManager.verifyAction(core.getPublicKey(), record,
                core.signAction(record)));
        assertTrue(core.verifyActionSignature(swing.getPublicKey(), record,
                swing.signAction(record)));

        assertTrue(IdentityManager.verifyReceipt(core.getPublicKey(), handId, finalHash,
                (byte) 5, core.signReceipt(handId, finalHash, (byte) 5)));
        assertTrue(core.verifyReceiptSignature(swing.getPublicKey(), handId, finalHash,
                (byte) 5, swing.signReceipt(handId, finalHash, (byte) 5)));

        assertTrue(IdentityManager.verifyShowdownReveal(core.getPublicKey(), handId,
                nickname, pocketKey, 12, 41,
                core.signShowdownReveal(handId, nickname, pocketKey, 12, 41)));
        assertTrue(core.verifyShowdownRevealSignature(swing.getPublicKey(), handId,
                nickname, pocketKey, 12, 41,
                swing.signShowdownReveal(handId, nickname, pocketKey, 12, 41)));

        assertTrue(IdentityManager.verifyStraddleDecision(core.getPublicKey(), handId,
                nickname, 1, core.signStraddleDecision(handId, nickname, 1)));
        assertTrue(core.verifyStraddleDecisionSignature(swing.getPublicKey(), handId,
                nickname, 1, swing.signStraddleDecision(handId, nickname, 1)));

        assertTrue(IdentityManager.verifyRabbitRequest(core.getPublicKey(), handId,
                nickname, rabbitNonce,
                core.signRabbitRequest(handId, nickname, rabbitNonce)));
        assertTrue(core.verifyRabbitRequestSignature(swing.getPublicKey(), handId,
                nickname, rabbitNonce,
                swing.signRabbitRequest(handId, nickname, rabbitNonce)));

        assertTrue(IdentityManager.verifySeatCommit(core.getPublicKey(), seatNonce,
                nickname, commitment,
                core.signSeatCommit(seatNonce, nickname, commitment)));
        assertTrue(core.verifySeatCommitSignature(swing.getPublicKey(), seatNonce,
                nickname, commitment,
                swing.signSeatCommit(seatNonce, nickname, commitment)));
    }

    private static byte[] bytes(int length, int seed) {
        byte[] value = new byte[length];
        for (int index = 0; index < value.length; index++) {
            value[index] = (byte) (seed + index * 17);
        }
        return value;
    }
}
