/*
 * Sanidad del formato de wire del DUALLOCK_BUNDLE (rotacion-3): el host serializa cada lista como
 * CSV de base64 (joinB64) y el peer la parsea (csvToBytes). Replicamos AMBOS aqui y comprobamos que
 * el flujo host->peer completo (serializar -> parsear -> verifyFullChainWire) verifica un reparto
 * honesto y rechaza uno deshonesto. Caza cualquier desajuste de formato antes del smoke.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DualLockBundleFormatTest {

    private static final BigInteger L = EdwardsPoint.L;
    private static final int DECK = 16;
    private static final int POCKET = 4;
    private static final int COMMUNITY = DECK - POCKET;

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    private static BigInteger scalar() {
        return RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
    }

    /** Replica EXACTA de Crupier.joinB64. */
    private static String joinB64(List<byte[]> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(Base64.getEncoder().encodeToString(items.get(i)));
        }
        return sb.toString();
    }

    /** Replica EXACTA de WaitingRoomFrame.csvToBytes. */
    private static List<byte[]> csvToBytes(String csv) {
        List<byte[]> out = new ArrayList<>();
        if (csv == null || csv.isEmpty()) {
            return out;
        }
        for (String part : csv.split(",")) {
            if (!part.isEmpty()) {
                out.add(Base64.getDecoder().decode(part));
            }
        }
        return out;
    }

    @Test
    public void joinSplitRoundTripIsIdentity() {
        List<byte[]> items = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            byte[] b = new byte[32 + i];
            new SecureRandom().nextBytes(b);
            items.add(b);
        }
        List<byte[]> back = csvToBytes(joinB64(items));
        assertEquals(items.size(), back.size(), "mismo numero de items");
        for (int i = 0; i < items.size(); i++) {
            assertArrayEquals(items.get(i), back.get(i), "item " + i + " identico");
        }
        assertTrue(csvToBytes(joinB64(new ArrayList<>())).isEmpty(), "lista vacia -> vacia");
    }

    /** Construye un reparto dual-lock honesto y devuelve {genesisB, decksB, cProofsB, megaB, rStatesB, rProofsB} en bytes. */
    private static Object[] honestDeal() {
        EdwardsPoint[] genesis = new EdwardsPoint[DECK];
        for (int i = 0; i < DECK; i++) {
            genesis[i] = EdwardsPoint.BASE.scalarMul(scalar());
        }
        List<byte[]> decks = new ArrayList<>();
        List<byte[]> cProofs = new ArrayList<>();
        EdwardsPoint[] cur = genesis;
        for (int m = 0; m < 3; m++) {
            int[] perm = DeckTransform.randomPermutation(DECK);
            BigInteger k = scalar();
            EdwardsPoint[] next = DeckTransform.apply(cur, perm, k);
            cProofs.add(ProofCodec.encodeShuffle(ShuffleArgument.prove(cur, next, perm, k)));
            decks.add(DualLockWire.encodeDeck(next));
            cur = next;
        }
        EdwardsPoint[] preRot = cur;
        EdwardsPoint[] community = Arrays.copyOfRange(preRot, POCKET, DECK);
        List<byte[]> rStates = new ArrayList<>();
        List<byte[]> rProofs = new ArrayList<>();
        EdwardsPoint[] curC = community;
        for (int j = 0; j < 2; j++) {
            BigInteger s = scalar();
            EdwardsPoint[] nx = new EdwardsPoint[COMMUNITY];
            for (int i = 0; i < COMMUNITY; i++) {
                nx[i] = curC[i].scalarMul(s.mod(L));
            }
            rProofs.add(DualLockWire.encodeRotationProof(RotationProof.prove(s, curC, nx)));
            rStates.add(DualLockWire.encodeDeck(nx));
            curC = nx;
        }
        EdwardsPoint[] mega = new EdwardsPoint[DECK];
        System.arraycopy(preRot, 0, mega, 0, POCKET);
        System.arraycopy(curC, 0, mega, POCKET, COMMUNITY);
        return new Object[]{DualLockWire.encodeDeck(genesis), decks, cProofs, DualLockWire.encodeDeck(mega), rStates, rProofs};
    }

    @Test
    @SuppressWarnings("unchecked")
    public void hostToPeerBundleFlowVerifies() {
        Object[] d = honestDeal();
        byte[] genesisB = (byte[]) d[0];
        // host: serializar a CSV (lo que viaja)
        String decksCsv = joinB64((List<byte[]>) d[1]);
        String cProofsCsv = joinB64((List<byte[]>) d[2]);
        String rStatesCsv = joinB64((List<byte[]>) d[4]);
        String rProofsCsv = joinB64((List<byte[]>) d[5]);
        byte[] megaB = (byte[]) d[3];

        // peer: parsear + verificar (pocketCount derivado local)
        boolean ok = DualLockWire.verifyFullChainWire(genesisB, csvToBytes(decksCsv), csvToBytes(cProofsCsv),
                POCKET, megaB, csvToBytes(rStatesCsv), csvToBytes(rProofsCsv));
        assertTrue(ok, "flujo host->peer (CSV) de un reparto honesto -> el peer verifica");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void hostToPeerTamperedMegapacketRejected() {
        Object[] d = honestDeal();
        byte[] genesisB = (byte[]) d[0];
        String decksCsv = joinB64((List<byte[]>) d[1]);
        String cProofsCsv = joinB64((List<byte[]>) d[2]);
        String rStatesCsv = joinB64((List<byte[]>) d[4]);
        String rProofsCsv = joinB64((List<byte[]>) d[5]);
        byte[] megaB = ((byte[]) d[3]).clone();
        megaB[POCKET * 32] ^= 0x01; // megapacket manipulado (community)

        boolean ok = DualLockWire.verifyFullChainWire(genesisB, csvToBytes(decksCsv), csvToBytes(cProofsCsv),
                POCKET, megaB, csvToBytes(rStatesCsv), csvToBytes(rProofsCsv));
        assertFalse(ok, "reparto deshonesto -> el peer lo rechaza (avisaria)");
    }
}
