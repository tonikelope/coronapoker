package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.identity.PlayerIdentity;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Proves that the QA scenarios exercise the production core identity contract. */
final class PlayerIdentityGameCompatibilityTest {

    @Test
    void everyGameDomainIsCompatibleInBothDirections() throws Exception {
        String nickname = "identity-contract-" + Long.toUnsignedString(System.nanoTime());
        Path coronaDirectory = Path.of(System.getProperty("user.home"), ".coronapoker");
        PlayerIdentity core = PlayerIdentity.loadOrCreate(coronaDirectory, nickname);
        TestPlayerIdentity fixture = TestPlayerIdentity.initializeForNick(nickname);
        assertTrue(fixture.isReady(), fixture.getLoadError());
        assertArrayEquals(fixture.getPublicKey(), core.getPublicKey());

        byte[] record = bytes(92, 1);
        byte[] handId = bytes(16, 2);
        byte[] finalHash = bytes(32, 3);
        byte[] pocketKey = bytes(32, 4);
        byte[] rabbitNonce = bytes(16, 5);
        byte[] seatNonce = bytes(32, 6);
        byte[] commitment = bytes(32, 7);

        assertTrue(TestPlayerIdentity.verifyAction(core.getPublicKey(), record,
                core.signAction(record)));
        assertTrue(core.verifyActionSignature(fixture.getPublicKey(), record,
                fixture.signAction(record)));

        assertTrue(TestPlayerIdentity.verifyReceipt(core.getPublicKey(), handId, finalHash,
                (byte) 5, core.signReceipt(handId, finalHash, (byte) 5)));
        assertTrue(core.verifyReceiptSignature(fixture.getPublicKey(), handId, finalHash,
                (byte) 5, fixture.signReceipt(handId, finalHash, (byte) 5)));

        assertTrue(TestPlayerIdentity.verifyShowdownReveal(core.getPublicKey(), handId,
                nickname, pocketKey, 12, 41,
                core.signShowdownReveal(handId, nickname, pocketKey, 12, 41)));
        assertTrue(core.verifyShowdownRevealSignature(fixture.getPublicKey(), handId,
                nickname, pocketKey, 12, 41,
                fixture.signShowdownReveal(handId, nickname, pocketKey, 12, 41)));

        assertTrue(TestPlayerIdentity.verifyStraddleDecision(core.getPublicKey(), handId,
                nickname, 1, core.signStraddleDecision(handId, nickname, 1)));
        assertTrue(core.verifyStraddleDecisionSignature(fixture.getPublicKey(), handId,
                nickname, 1, fixture.signStraddleDecision(handId, nickname, 1)));

        assertTrue(TestPlayerIdentity.verifyRabbitRequest(core.getPublicKey(), handId,
                nickname, rabbitNonce,
                core.signRabbitRequest(handId, nickname, rabbitNonce)));
        assertTrue(core.verifyRabbitRequestSignature(fixture.getPublicKey(), handId,
                nickname, rabbitNonce,
                fixture.signRabbitRequest(handId, nickname, rabbitNonce)));

        assertTrue(TestPlayerIdentity.verifySeatCommit(core.getPublicKey(), seatNonce,
                nickname, commitment,
                core.signSeatCommit(seatNonce, nickname, commitment)));
        assertTrue(core.verifySeatCommitSignature(fixture.getPublicKey(), seatNonce,
                nickname, commitment,
                fixture.signSeatCommit(seatNonce, nickname, commitment)));
    }

    private static byte[] bytes(int length, int seed) {
        byte[] value = new byte[length];
        for (int index = 0; index < value.length; index++) {
            value[index] = (byte) (seed + index * 17);
        }
        return value;
    }
}
