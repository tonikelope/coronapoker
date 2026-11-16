package com.tonikelope.coronapoker.crypto;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundContributionProofTest {
    private static final byte[] HAND = new byte[16];
    private static final ShuffleProofContext.Step STEP = new ShuffleProofContext.Step("session", HAND, "player", 1);

    @Test
    void shuffleUsesTheExpectedPocketKeyAndContributionContext() {
        byte[] input = Arrays.copyOf(RistrettoSRA.getGenesisDeck(), 8 * 32);
        byte[] witness = RistrettoSRA.generateLockScalar();
        byte[] publicKey = RistrettoSRA.commitment(witness);
        byte[] otherKey = RistrettoSRA.commitment(RistrettoSRA.generateLockScalar());
        int[] permutation = DeckTransform.randomPermutation(8);
        byte[] output = ShuffleCascade.encodeDeck(DeckTransform.apply(ShuffleCascade.decodeDeck(input),
                permutation, RistrettoSRA.bytesToScalar(witness)));
        byte[] bound = ShuffleCascade.proveBoundStepWire(input, output, permutation, witness, STEP, publicKey);
        assertNotNull(bound);
        assertTrue(ShuffleCascade.verifyBoundStepWire(input, output, bound, STEP, publicKey));
        assertFalse(ShuffleCascade.verifyBoundStepWire(input, output, bound, STEP, otherKey));
        assertNull(ShuffleCascade.proveBoundStepWire(input, output, permutation, witness, STEP, otherKey));
        assertFalse(ShuffleCascade.verifyBoundStepWire(input, output, bound, STEP, new byte[32]));
        for (ShuffleProofContext.Step other : differentContexts()) {
            assertFalse(ShuffleCascade.verifyBoundStepWire(input, output, bound, other, publicKey));
        }

        byte[] legacy = ShuffleCascade.proveStepWire(input, output, permutation, witness);
        assertTrue(ShuffleCascade.verifyStepWire(input, output, legacy));
        assertFalse(ShuffleCascade.verifyBoundStepWire(input, output, legacy, STEP, publicKey));
    }

    @Test
    void rotationUsesTheRatioOfBothExpectedKeysAndContributionContext() {
        byte[] input = Arrays.copyOf(RistrettoSRA.getGenesisDeck(), 8 * 32);
        byte[] pocket = RistrettoSRA.generateLockScalar();
        byte[] community = RistrettoSRA.generateLockScalar();
        byte[] kp = RistrettoSRA.commitment(pocket);
        byte[] kc = RistrettoSRA.commitment(community);
        byte[] otherKey = RistrettoSRA.commitment(RistrettoSRA.generateLockScalar());
        BigInteger rotation = RistrettoSRA.bytesToScalar(RistrettoSRA.getUnlockScalar(pocket))
                .multiply(RistrettoSRA.bytesToScalar(community)).mod(EdwardsPoint.L);
        byte[] output = RistrettoSRA.applyCommutativeLock(input, RistrettoSRA.scalarToBytes(rotation));
        byte[] bound = DualLockWire.proveBoundRotationStepWire(input, output, rotation, STEP, kp, kc);
        assertNotNull(bound);
        assertTrue(DualLockWire.verifyBoundRotationStepWire(input, output, bound, STEP, kp, kc));
        assertFalse(DualLockWire.verifyBoundRotationStepWire(input, output, bound, STEP, otherKey, kc));
        assertFalse(DualLockWire.verifyBoundRotationStepWire(input, output, bound, STEP, kp, otherKey));
        assertNull(DualLockWire.proveBoundRotationStepWire(input, output, rotation, STEP, kp, otherKey));
        for (ShuffleProofContext.Step other : differentContexts()) {
            assertFalse(DualLockWire.verifyBoundRotationStepWire(input, output, bound, other, kp, kc));
        }

        byte[] legacy = DualLockWire.encodeRotationProof(RotationProof.prove(rotation,
                ShuffleCascade.decodeDeck(input), ShuffleCascade.decodeDeck(output)));
        assertTrue(DualLockWire.verifyRotationStepWire(input, output, legacy));
        assertFalse(DualLockWire.verifyBoundRotationStepWire(input, output, legacy, STEP, kp, kc));
    }

    @Test
    void hostFirstShuffleAndRingOrderedRotationUseTheirOwnOwnerAndPosition() {
        Map<String, byte[]> keys = Map.of("player", RistrettoSRA.commitment(RistrettoSRA.generateLockScalar()),
                "host", RistrettoSRA.commitment(RistrettoSRA.generateLockScalar()));
        ShuffleProofContext context = new ShuffleProofContext("session", HAND, "host",
                new String[]{"player", "host"}, keys, keys);
        assertEquals("host", context.shuffleStep(0).participant());
        assertEquals("player", context.shuffleStep(1).participant());
        assertEquals("player", context.rotationStep(0).participant());
        assertEquals("host", context.rotationStep(1).participant());
    }

    private static ShuffleProofContext.Step[] differentContexts() {
        byte[] otherHand = HAND.clone();
        otherHand[0] = 1;
        return new ShuffleProofContext.Step[]{
            new ShuffleProofContext.Step("session", otherHand, "player", 1),
            new ShuffleProofContext.Step("another session", HAND, "player", 1),
            new ShuffleProofContext.Step("session", HAND, "other player", 1),
            new ShuffleProofContext.Step("session", HAND, "player", 0)};
    }
}
