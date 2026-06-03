/*
 * Bayer-Groth + batch-DLEQ: orquestador de cadena COMPLETA genesis -> MEGAPACKET (dual-lock).
 * El que cierra el flanco de rotacion de verdad. Suite corona:
 *  - cadena honesta completa verifica
 *  - SMUGGLE pocket->community (el ataque real) -> rechazado
 *  - relocacion/duplicacion en la rotacion, pocket tocado, anclaje roto, cascada deshonesta -> rechazados
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DualLockCascadeTest {

    private static final BigInteger L = EdwardsPoint.L;
    private static final int DECK = 16;
    private static final int POCKET = 4;   // 2 jugadores
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

    /** Holds an honest dual-lock chain ready to verify or tamper. */
    private static final class Chain {
        EdwardsPoint[] genesis;
        EdwardsPoint[][] cascadeDecks;
        ShuffleArgument.Proof[] cascadeProofs;
        EdwardsPoint[] megapacket;
        EdwardsPoint[][] rotationStates;
        RotationProof.Proof[] rotationProofs;
    }

    private static Chain honest(int cascadeSteps, int rotationPeers) {
        Chain c = new Chain();
        c.genesis = new EdwardsPoint[DECK];
        for (int i = 0; i < DECK; i++) {
            c.genesis[i] = EdwardsPoint.BASE.scalarMul(scalar());
        }
        c.cascadeDecks = new EdwardsPoint[cascadeSteps + 1][];
        c.cascadeProofs = new ShuffleArgument.Proof[cascadeSteps];
        c.cascadeDecks[0] = c.genesis;
        for (int m = 0; m < cascadeSteps; m++) {
            int[] perm = DeckTransform.randomPermutation(DECK);
            BigInteger k = scalar();
            c.cascadeDecks[m + 1] = DeckTransform.apply(c.cascadeDecks[m], perm, k);
            c.cascadeProofs[m] = ShuffleArgument.prove(c.cascadeDecks[m], c.cascadeDecks[m + 1], perm, k);
        }
        EdwardsPoint[] preRot = c.cascadeDecks[cascadeSteps];
        EdwardsPoint[] preRotCommunity = Arrays.copyOfRange(preRot, POCKET, DECK);

        c.rotationStates = new EdwardsPoint[rotationPeers + 1][];
        c.rotationProofs = new RotationProof.Proof[rotationPeers];
        c.rotationStates[0] = preRotCommunity;
        for (int j = 0; j < rotationPeers; j++) {
            BigInteger s = scalar();
            EdwardsPoint[] next = new EdwardsPoint[COMMUNITY];
            for (int i = 0; i < COMMUNITY; i++) {
                next[i] = c.rotationStates[j][i].scalarMul(s.mod(L));
            }
            c.rotationProofs[j] = RotationProof.prove(s, c.rotationStates[j], next);
            c.rotationStates[j + 1] = next;
        }
        // MEGAPACKET = pocket intacto del pre-rotacion + community rotado
        c.megapacket = new EdwardsPoint[DECK];
        System.arraycopy(preRot, 0, c.megapacket, 0, POCKET);
        System.arraycopy(c.rotationStates[rotationPeers], 0, c.megapacket, POCKET, COMMUNITY);
        return c;
    }

    private static boolean verify(Chain c) {
        return DualLockCascade.verifyFullChain(c.genesis, c.cascadeDecks, c.cascadeProofs,
                POCKET, c.megapacket, c.rotationStates, c.rotationProofs);
    }

    @Test
    public void honestFullChainVerifies() {
        assertTrue(verify(honest(3, 3)), "cadena dual-lock completa honesta -> verifica");
    }

    @Test
    public void pocketIntoCommunitySmuggleRejected() {
        // EL ATAQUE: el host mete la pocket del jugador 0 (megapacket[0]) en una posicion community.
        Chain c = honest(3, 2);
        c.megapacket[POCKET] = c.megapacket[0]; // community[0] := pocket[0]  (duplicado/relocacion)
        assertFalse(verify(c), "[ATAQUE] pocket colada en community -> rechazado (community != salida rotacion)");
    }

    @Test
    public void rotationRelocationRejected() {
        // Una etapa de rotacion relocaliza en vez de re-keyear en sitio.
        Chain c = honest(2, 2);
        // reconstruir el ultimo paso de rotacion como permutacion
        EdwardsPoint[] prev = c.rotationStates[0];
        BigInteger s = scalar();
        EdwardsPoint[] bad = new EdwardsPoint[COMMUNITY];
        for (int i = 0; i < COMMUNITY; i++) {
            bad[i] = prev[(i + 1) % COMMUNITY].scalarMul(s.mod(L)); // relocacion
        }
        c.rotationStates = new EdwardsPoint[][]{prev, bad};
        c.rotationProofs = new RotationProof.Proof[]{RotationProof.prove(s, prev, bad)};
        System.arraycopy(bad, 0, c.megapacket, POCKET, COMMUNITY);
        assertFalse(verify(c), "[ATAQUE] relocacion en la rotacion -> rechazado");
    }

    @Test
    public void pocketTamperRejected() {
        Chain c = honest(2, 2);
        c.megapacket[1] = c.megapacket[1].add(EdwardsPoint.BASE); // pocket region tocada
        assertFalse(verify(c), "pocket del MEGAPACKET != pre-rotacion -> rechazado");
    }

    @Test
    public void communityMismatchRejected() {
        Chain c = honest(2, 2);
        c.megapacket[POCKET + 1] = c.megapacket[POCKET + 1].add(EdwardsPoint.BASE); // community != salida rotacion
        assertFalse(verify(c), "community del MEGAPACKET != salida de la rotacion -> rechazado");
    }

    @Test
    public void rotationAnchorBreakRejected() {
        Chain c = honest(2, 2);
        // rotationStates[0] ya no es la community del pre-rotacion
        c.rotationStates[0] = c.rotationStates[0].clone();
        c.rotationStates[0][0] = c.rotationStates[0][0].add(EdwardsPoint.BASE);
        assertFalse(verify(c), "anclaje de la rotacion roto -> rechazado");
    }

    @Test
    public void cascadeSmuggleRejected() {
        // Cascada deshonesta: un paso con duplicado.
        Chain c = honest(2, 2);
        // sustituir el deck final de cascada por uno con duplicado y reprobar (mintiendo identidad)
        EdwardsPoint[] prev = c.cascadeDecks[0];
        BigInteger k = scalar();
        EdwardsPoint[] bad = new EdwardsPoint[DECK];
        for (int i = 0; i < DECK; i++) {
            bad[i] = prev[i].scalarMul(k.mod(L));
        }
        bad[1] = prev[0].scalarMul(k.mod(L)); // duplicado
        int[] idPerm = new int[DECK];
        for (int i = 0; i < DECK; i++) {
            idPerm[i] = i;
        }
        c.cascadeDecks = new EdwardsPoint[][]{prev, bad};
        c.cascadeProofs = new ShuffleArgument.Proof[]{ShuffleArgument.prove(prev, bad, idPerm, k)};
        assertFalse(verify(c), "[ATAQUE] cascada deshonesta (duplicado) -> rechazado");
    }
}
