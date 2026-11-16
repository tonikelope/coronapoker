/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.game.GameChannel;
import com.tonikelope.coronapoker.core.identity.GameIdentityProtocol;
import com.tonikelope.coronapoker.crypto.DealChain;
import com.tonikelope.coronapoker.crypto.DeckTransform;
import com.tonikelope.coronapoker.crypto.DualLockWire;
import com.tonikelope.coronapoker.crypto.RistrettoSRA;
import com.tonikelope.coronapoker.crypto.ShuffleCascade;
import com.tonikelope.coronapoker.crypto.ShuffleProofContext;
import com.tonikelope.coronapoker.crypto.UnlockChainWire;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SraResponseAttributionTest {

    private static final String PEER = "Mar\u00eda";
    private static final String OTHER = "Mari\u0301a";
    private static Fixture fixture;

    private record Fixture(ShuffleProofContext context, byte[] before, byte[] after,
            String hash, byte[] cascadeProof, List<String> commands) { }

    @BeforeAll
    static void buildValidReplies() throws Exception {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
        byte[] handId = new byte[16];
        byte[] pocket = RistrettoSRA.generateLockScalar();
        byte[] community = RistrettoSRA.generateLockScalar();
        byte[] pocketKey = RistrettoSRA.commitment(pocket);
        byte[] communityKey = RistrettoSRA.commitment(community);
        byte[] hostPocket = RistrettoSRA.generateLockScalar();
        byte[] hostKey = RistrettoSRA.commitment(hostPocket);
        Map<String, byte[]> pocketKeys = Map.of("host", hostKey, PEER, pocketKey);
        ShuffleProofContext context = new ShuffleProofContext("session", handId, "host",
                new String[]{PEER, "host"}, pocketKeys,
                Map.of("host", hostKey, PEER, communityKey));
        byte[] before = RistrettoSRA.applyCommutativeLock(
                RistrettoSRA.getGenesisDeck(), hostPocket);
        int[] permutation = DeckTransform.randomPermutation(before.length / 32);
        byte[] after = ShuffleCascade.encodeDeck(DeckTransform.apply(
                ShuffleCascade.decodeDeck(before), permutation,
                RistrettoSRA.bytesToScalar(pocket)));
        byte[] cascadeProof = ShuffleCascade.proveBoundStepWire(
                before, after, permutation, pocket, context.shuffleStep(1), pocketKey);
        assertNotNull(cascadeProof);
        assertTrue(ShuffleCascade.verifyBoundStepWire(before, after, cascadeProof,
                context.shuffleStep(1), pocketKey));

        BigInteger rotation = RistrettoSRA.bytesToScalar(RistrettoSRA.getUnlockScalar(pocket))
                .multiply(RistrettoSRA.bytesToScalar(community)).mod(RistrettoSRA.L);
        byte[] communityPieces = Arrays.copyOfRange(after, context.size() * 64, after.length);
        byte[] rotated = RistrettoSRA.applyCommutativeLock(
                communityPieces, RistrettoSRA.scalarToBytes(rotation));
        byte[] rotationProof = DualLockWire.proveBoundRotationStepWire(communityPieces, rotated, rotation,
                context.rotationStep(0), pocketKey, communityKey);
        assertNotNull(rotationProof);
        assertTrue(DualLockWire.verifyBoundRotationStepWire(communityPieces, rotated, rotationProof,
                context.rotationStep(0), pocketKey, communityKey));

        byte[] firstHostPocket = Arrays.copyOfRange(after, 64, 96);
        byte[] secondHostPocket = Arrays.copyOfRange(after, 96, 128);
        DealChain.Extended first = DealChain.extend(firstHostPocket, "", pocketKeys, PEER, pocket);
        DealChain.Extended second = DealChain.extend(secondHostPocket, "", pocketKeys, PEER, pocket);
        assertNotNull(first);
        assertNotNull(second);
        String unlock = UnlockChainWire.serializeResp(List.of(
                new UnlockChainWire.RespItem(1, List.of(first.wire, second.wire))));
        assertNotNull(UnlockChainWire.parseResp(unlock));

        String hash = b64(MessageDigest.getInstance("SHA-256").digest(after));
        String nickname = b64(PEER.getBytes(StandardCharsets.UTF_8));
        byte[] signature = GameIdentityProtocol.signShowdownReveal(
                KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPrivate(),
                handId, PEER, pocket, 0, 1);
        fixture = new Fixture(context, before, after, hash, cascadeProof, List.of(
                "DECK_CASCADE_RESP#" + nickname + "#" + b64(after)
                        + "#" + b64(pocketKey) + "#" + b64(communityKey),
                "DECK_CASCADE_PROOF#" + nickname + "#" + hash + "#" + b64(cascadeProof),
                "DECK_ROTATION_RESP#" + nickname + "#" + b64(rotated) + "#" + b64(rotationProof),
                "RESP_SRA_UNLOCK_CHAIN#" + nickname + "#" + unlock,
                "RESP_SHOWDOWN_KEY#" + nickname + "#" + b64(pocket) + "#" + b64(signature)));
    }

    @Test
    void identicalRepliesAreAuthorizedOnlyForTheirAuthenticatedSender() {
        for (String command : fixture.commands()) {
            Crupier dealer = new Crupier();
            AtomicInteger ids = new AtomicInteger();
            AtomicInteger expectedCloses = new AtomicInteger();
            AtomicInteger otherCloses = new AtomicInteger();

            dispatch(dealer, ids, PEER, command, expectedCloses);
            dispatch(dealer, ids, OTHER, command, otherCloses);

            String accepted = dealer.getReceived_commands().poll();
            assertEquals("GAME#1#" + command, accepted);
            assertNull(dealer.getReceived_commands().poll());
            assertEquals(1, ids.get(), "Only the matching response reached the mailbox");
            assertEquals(0, expectedCloses.get());
            assertEquals(1, otherCloses.get());

            dealer.getReceived_commands().restoreRejected(List.of(accepted));
            assertTrue(dealer.getReceived_commands().reject(dealer.getReceived_commands().poll()));
            assertEquals(1, expectedCloses.get(), "A deferred rejection closes the original source");
            assertEquals(1, otherCloses.get());
        }
    }

    @Test
    void onlyAuthenticatedContributorReachesCollectorForItsStep() throws Exception {
        Crupier dealer = new Crupier();
        AtomicInteger ids = new AtomicInteger();
        AtomicInteger expectedCloses = new AtomicInteger();
        AtomicInteger otherCloses = new AtomicInteger();
        String proof = fixture.commands().get(1);

        dispatch(dealer, ids, OTHER, proof, otherCloses);
        assertNull(dealer.getReceived_commands().peek());
        assertEquals(1, otherCloses.get());
        dispatch(dealer, ids, PEER, proof, expectedCloses);

        Method collect = Crupier.class.getDeclaredMethod("collectAsyncCascadeProofs",
                List.class, List.class, List.class, ShuffleProofContext.class);
        collect.setAccessible(true);
        Map<?, ?> collected = (Map<?, ?>) collect.invoke(dealer,
                List.of(RistrettoSRA.getGenesisDeck(), fixture.before(), fixture.after()),
                Arrays.asList(new int[0], null), List.of("host", PEER), fixture.context());

        assertEquals(1, collected.size());
        assertArrayEquals(fixture.cascadeProof(), (byte[]) collected.get(fixture.hash()));
        assertEquals(0, expectedCloses.get());
    }

    private static void dispatch(Crupier dealer, AtomicInteger ids, String authenticated,
            String command, AtomicInteger closes) {
        assertTrue(CoreGameTableFactory.dispatchParticipantResponse(
                new GameChannel.Inbound(authenticated, command, closes::incrementAndGet),
                dealer, ids));
    }

    private static String b64(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }
}
