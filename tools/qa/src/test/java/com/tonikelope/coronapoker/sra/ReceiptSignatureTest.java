/*
 * EC-Identity v1 (commit 6): unit tests for the end-of-hand consensus
 * receipt signing scheme.
 *
 * Receipt payload = HAND_ID(16) || H_final(32). Signed under the
 * RECEIPT_V1\0 domain so a receipt sig cannot be replayed as an ACTION sig
 * (and vice versa). Verifies the same invariants we expect on the wire:
 *
 *   - Sign/verify roundtrip on the same keypair.
 *   - Receipt sig produced by peer A does NOT verify as peer B's receipt
 *     (the canonical "host signs in your name" attack).
 *   - Mutating any byte of HAND_ID or H_final invalidates the sig.
 *   - The RECEIPT_V1 domain is mandatory: an ACTION_V1 sig over the same
 *     bytes does NOT verify as a receipt sig.
 */
package com.tonikelope.coronapoker.sra;

import com.tonikelope.coronapoker.CanonicalActionRecord;
import com.tonikelope.coronapoker.IdentityManager;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReceiptSignatureTest {

    private static IdentityManager peerA;
    private static IdentityManager peerB;
    private static byte[] peerAPub;
    private static byte[] peerBPub;

    @BeforeAll
    public static void initIdentities() {
        peerA = IdentityManager.initializeForNick("__qa_receipt_peerA_" + System.nanoTime());
        peerAPub = peerA.getPublicKey();
        peerB = IdentityManager.initializeForNick("__qa_receipt_peerB_" + System.nanoTime());
        peerBPub = peerB.getPublicKey();
    }

    private static byte[] sampleHandId(int seed) {
        byte[] out = new byte[CanonicalActionRecord.HAND_ID_BYTES];
        for (int i = 0; i < out.length; i++) out[i] = (byte) (seed + i);
        return out;
    }

    private static byte[] sampleHFinal(int seed) {
        byte[] out = new byte[32];
        for (int i = 0; i < out.length; i++) out[i] = (byte) (0xA0 + ((seed + i) & 0x1F));
        return out;
    }

    @Test
    public void signAndVerifyRoundtrip() {
        byte[] handId = sampleHandId(0x10);
        byte[] hFinal = sampleHFinal(0x20);
        byte[] sig = peerA.signReceipt(handId, hFinal, (byte) 0);
        assertTrue(IdentityManager.verifyReceipt(peerAPub, handId, hFinal, (byte) 0, sig));
    }

    @Test
    public void receiptSigNotVerifiableUnderOtherPubkey() {
        byte[] handId = sampleHandId(0x10);
        byte[] hFinal = sampleHFinal(0x20);
        byte[] sigA = peerA.signReceipt(handId, hFinal, (byte) 0);
        assertFalse(IdentityManager.verifyReceipt(peerBPub, handId, hFinal, (byte) 0, sigA),
                "peer A's receipt sig must NOT verify under peer B's pubkey");
    }

    @Test
    public void mutatedHandIdInvalidates() {
        byte[] handId = sampleHandId(0x10);
        byte[] hFinal = sampleHFinal(0x20);
        byte[] sig = peerA.signReceipt(handId, hFinal, (byte) 0);

        byte[] tampered = handId.clone();
        tampered[3] ^= 0x01;
        assertFalse(IdentityManager.verifyReceipt(peerAPub, tampered, hFinal, (byte) 0, sig));
    }

    @Test
    public void mutatedHFinalInvalidates() {
        byte[] handId = sampleHandId(0x10);
        byte[] hFinal = sampleHFinal(0x20);
        byte[] sig = peerA.signReceipt(handId, hFinal, (byte) 0);

        byte[] tampered = hFinal.clone();
        tampered[15] ^= 0x80;
        assertFalse(IdentityManager.verifyReceipt(peerAPub, handId, tampered, (byte) 0, sig));
    }

    @Test
    public void actionV1SigIsNotAcceptedAsReceipt() throws Exception {
        // Domain separation: an ACTION_V1 sig over the same exact bytes
        // (HAND_ID || H_final concatenated) must NOT verify as RECEIPT_V1.
        // We bypass the public helpers to construct such a sig deliberately
        // and assert verifyReceipt rejects it.
        byte[] handId = sampleHandId(0x10);
        byte[] hFinal = sampleHFinal(0x20);
        byte[] payload = IdentityManager.receiptPayload(handId, hFinal, (byte) 0);

        Method sign = IdentityManager.class.getDeclaredMethod("sign", byte[].class, byte[].class);
        sign.setAccessible(true);
        byte[] actionDomain = "ACTION_V1\0".getBytes("UTF-8");
        byte[] actionSig = (byte[]) sign.invoke(peerA, actionDomain, payload);

        assertFalse(IdentityManager.verifyReceipt(peerAPub, handId, hFinal, (byte) 0, actionSig),
                "ACTION_V1 sig must NOT pass as a RECEIPT_V1 sig");
    }

    @Test
    public void rejectsMalformedInputs() {
        byte[] handId = sampleHandId(0x10);
        byte[] hFinal = sampleHFinal(0x20);
        byte[] sig = peerA.signReceipt(handId, hFinal, (byte) 0);

        // Wrong-length pubkey is rejected by verifyAction's argument validation
        // and surfaces as false (not as an exception).
        assertFalse(IdentityManager.verifyReceipt(new byte[31], handId, hFinal, (byte) 0, sig));
        // Wrong-length sig is also rejected by Ed25519.verify itself.
        byte[] shortSig = new byte[63];
        System.arraycopy(sig, 0, shortSig, 0, 63);
        assertFalse(IdentityManager.verifyReceipt(peerAPub, handId, hFinal, (byte) 0, shortSig));
    }
}
