package com.tonikelope.coronapoker;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless vertical slice for the production EXIT, MISDEAL and recovery
 * contracts. The file stays in the protocol-simulator lane while using the
 * production package so the package-private current-version wire decoders are
 * exercised directly.
 */
@Tag("slow")
@Tag("protocol-sim")
final class ProtocolExitRecoverySimulationTest {

    private static final String HOST = "recovery-host";
    private static final String CLIENT = "recovery-client";

    private static IdentityManager hostIdentity;
    private static IdentityManager clientIdentity;

    @BeforeAll
    static void identities() {
        hostIdentity = IdentityManager.initializeForNick(HOST);
        clientIdentity = IdentityManager.initializeForNick(CLIENT);
        assertTrue(hostIdentity.isReady(), hostIdentity.getLoadError());
        assertTrue(clientIdentity.isReady(), clientIdentity.getLoadError());
    }

    @Test
    void controlledExitKeepsAuthenticatedIdentityAndMandatoryUnlockMaterial() {
        byte[] scalar = scalarOne();
        String scalarB64 = b64(scalar);
        byte[] pocketProof = clientIdentity.signAction(scalar);
        String pocketProofB64 = b64(pocketProof);

        PlayerExitWire.Command request = PlayerExitWire.parseClientRequest(
                new String[]{"GAME", "41", "EXIT", scalarB64, scalarB64, pocketProofB64},
                CLIENT);
        assertEquals(CLIENT, request.nick());
        assertArrayEquals(scalar, request.testament());
        assertArrayEquals(scalar, request.pocketKey());
        assertArrayEquals(pocketProof, request.pocketSignature());

        String clientNickB64 = b64(CLIENT.getBytes(StandardCharsets.UTF_8));
        PlayerExitWire.Command relay = PlayerExitWire.parseHostRelay(
                new String[]{"GAME", "41", "EXIT", clientNickB64,
                    request.testamentWire(), request.pocketKeyWire(),
                    request.pocketSignatureWire()});
        assertEquals(CLIENT, relay.nick());
        assertArrayEquals(request.testament(), relay.testament());
        assertArrayEquals(request.pocketKey(), relay.pocketKey());

        assertThrows(IllegalArgumentException.class, () -> PlayerExitWire.parseClientRequest(
                new String[]{"GAME", "41", "EXIT", scalarB64, scalarB64, "*"}, CLIENT));
    }

    @Test
    void abruptDisconnectTerminatesExplicitlyAndMisdealReasonRemainsCanonical() {
        RecoveryReceiveState recover = new RecoveryReceiveState("session-a");
        recover.rejectTransportClosed();
        assertTrue(recover.isTerminal());
        assertFalse(recover.isSuccess());
        assertEquals("TRANSPORT_CLOSED", recover.error());

        String reason = "peer.community_unlock_no_testament";
        String encodedReason = b64(reason.getBytes(StandardCharsets.UTF_8));
        assertEquals(reason, MisdealWire.parse(
                new String[]{"GAME", "41", "MISDEAL", encodedReason}));
        assertThrows(IllegalArgumentException.class, () -> MisdealWire.parse(
                new String[]{"GAME", "41", "MISDEAL", encodedReason + "="}));
    }

    @Test
    void validatedSnapshotAndSignedActionReplayReachTheUninterruptedHash() {
        RecoverySnapshotV1.Result built = RecoverySnapshotV1.fromMap(
                RecoverySnapshotFixtures.validMap(), "session-a");
        assertTrue(built.isOk(), String.valueOf(built.error()));

        RecoveryReceiveState receive = new RecoveryReceiveState("session-a");
        receive.acceptBase64(b64(built.value().encode()));
        assertTrue(receive.isSuccess(), receive.error());
        assertArrayEquals(built.value().encode(), receive.snapshot().encode());

        Map<String, double[]> localBalances = new LinkedHashMap<>();
        localBalances.put("alice", new double[]{99.50d, 100, 0});
        localBalances.put("bob", new double[]{101.00d, 100, 0});
        RecoveryBalanceReconciler.Result balances = RecoveryBalanceReconciler.reconcileExact(
                (String) RecoverySnapshotFixtures.validMap().get("balance"), localBalances);
        assertTrue(balances.isOk(), String.valueOf(balances.error()));
        assertEquals(9_950L, balances.balances().get("alice").stack().cents());
        assertEquals(10_100L, balances.balances().get("bob").stack().cents());

        Genesis genesis = genesis();
        HandStateChain uninterrupted = genesis.startChain();
        byte[] record = CanonicalActionRecord.encode(uninterrupted.getCurrentHash(), genesis.handId,
                CanonicalActionRecord.playerIdFromNick(CLIENT),
                CanonicalActionRecord.STREET_PREFLOP, CanonicalActionRecord.ACTION_BET,
                125L, false, true);
        byte[] signature = clientIdentity.signAction(record);
        uninterrupted.absorb(record, signature);

        String recoveredWire = RecoveredActionCodec.encodeV1(CLIENT, Player.BET, 1.25d,
                b64(record), b64(signature));
        RecoveryActionReceiveState actionReceive = new RecoveryActionReceiveState();
        actionReceive.acceptFrame("GAME#41#ACTIONDATA#"
                + b64(recoveredWire.getBytes(StandardCharsets.UTF_8)));
        assertTrue(actionReceive.isSuccess(), actionReceive.error());

        RecoveredActionCodec.Result decoded = RecoveredActionCodec.decode(actionReceive.actions());
        assertTrue(decoded.isOk(), String.valueOf(decoded.error()));
        assertEquals(CLIENT, decoded.value().actor());
        assertEquals(125L, decoded.value().amountCents());
        assertTrue(IdentityManager.verifyAction(clientIdentity.getPublicKey(),
                decoded.value().record(), decoded.value().signature()));

        HandStateChain recovered = genesis.startChain();
        recovered.absorb(decoded.value().record(), decoded.value().signature());
        assertArrayEquals(uninterrupted.getCurrentHash(), recovered.getCurrentHash());
        assertEquals(uninterrupted.getAbsorbedActions(), recovered.getAbsorbedActions());
    }

    @Test
    void crossSessionSnapshotAndTamperedRecoveredActionFailBeforeChainMutation() {
        RecoverySnapshotV1.Result built = RecoverySnapshotV1.fromMap(
                RecoverySnapshotFixtures.validMap(), "session-a");
        assertTrue(built.isOk());
        RecoveryReceiveState wrongSession = new RecoveryReceiveState("session-b");
        wrongSession.acceptBase64(b64(built.value().encode()));
        assertFalse(wrongSession.isSuccess());
        assertEquals(RecoveryReceiveState.Status.FAILED, wrongSession.status());

        Genesis genesis = genesis();
        HandStateChain chain = genesis.startChain();
        byte[] before = chain.getCurrentHash();
        byte[] record = CanonicalActionRecord.encode(before, genesis.handId,
                CanonicalActionRecord.playerIdFromNick(CLIENT),
                CanonicalActionRecord.STREET_PREFLOP, CanonicalActionRecord.ACTION_CHECK,
                0L, false, true);
        byte[] signature = clientIdentity.signAction(record);
        record[CanonicalActionRecord.OFFSET_FLAGS] ^= 1;
        assertFalse(IdentityManager.verifyAction(clientIdentity.getPublicKey(), record, signature));
        assertArrayEquals(before, chain.getCurrentHash());
        assertEquals(0, chain.getAbsorbedActions());
    }

    private static Genesis genesis() {
        byte[] handId = new byte[CanonicalActionRecord.HAND_ID_BYTES];
        handId[0] = 42;
        List<byte[]> playerIds = List.of(
                CanonicalActionRecord.playerIdFromNick(HOST),
                CanonicalActionRecord.playerIdFromNick(CLIENT));
        List<byte[]> pocketCommits = new ArrayList<>();
        List<byte[]> communityCommits = new ArrayList<>();
        for (int i = 0; i < playerIds.size(); i++) {
            byte[] pocket = new byte[32];
            byte[] community = new byte[32];
            pocket[0] = (byte) (i + 1);
            community[0] = (byte) (i + 11);
            pocketCommits.add(pocket);
            communityCommits.add(community);
        }
        byte[] deck = new byte[52 * 32];
        Arrays.fill(deck, (byte) 7);
        return new Genesis(handId, playerIds, pocketCommits, communityCommits, deck);
    }

    private record Genesis(byte[] handId, List<byte[]> playerIds,
            List<byte[]> pocketCommits, List<byte[]> communityCommits, byte[] deck) {

        private HandStateChain startChain() {
            return HandStateChain.start(handId, playerIds, pocketCommits, communityCommits, deck);
        }
    }

    private static byte[] scalarOne() {
        byte[] scalar = new byte[32];
        scalar[0] = 1;
        return scalar;
    }

    private static String b64(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }
}
