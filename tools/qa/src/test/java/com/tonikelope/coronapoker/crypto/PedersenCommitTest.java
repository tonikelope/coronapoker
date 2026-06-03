/*
 * Verifiable-shuffle engine — ladrillo 1: Pedersen commitment (C = m·B + r·H).
 *
 * Suite adversaria: completeness (abre con su apertura), binding (mensajes/blindings distintos →
 * commitments distintos; aperturas falsas no verifican), hiding (el blinding importa),
 * homomorfismo (add/scale), y reducción mod L. Cripto aislada, sin protocolo, sin smoke.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PedersenCommitTest {

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    private static BigInteger scalar() {
        return RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
    }

    @Test
    public void commitOpensWithItsOwnOpening() {
        BigInteger m = scalar(), r = scalar();
        byte[] c = PedersenCommit.commit(m, r);
        assertNotNull(c);
        assertTrue(PedersenCommit.verify(c, m, r), "el commitment abre con su (m, r)");
    }

    @Test
    public void deterministicForSameInputs() {
        BigInteger m = BigInteger.valueOf(123456789L), r = BigInteger.valueOf(987654321L);
        assertArrayEquals(PedersenCommit.commit(m, r), PedersenCommit.commit(m, r),
                "mismo (m, r) -> mismo commitment");
    }

    @Test
    public void wrongOpeningFails() {
        BigInteger m = scalar(), r = scalar();
        byte[] c = PedersenCommit.commit(m, r);
        assertFalse(PedersenCommit.verify(c, m.add(BigInteger.ONE), r), "mensaje cambiado no abre");
        assertFalse(PedersenCommit.verify(c, m, r.add(BigInteger.ONE)), "blinding cambiado no abre");
        assertFalse(PedersenCommit.verify(c, r, m), "(m,r) intercambiados no abren (salvo m==r)");
    }

    @Test
    public void bindingDifferentMessagesGiveDifferentCommitments() {
        BigInteger r = scalar();
        byte[] c1 = PedersenCommit.commit(BigInteger.valueOf(7), r);
        byte[] c2 = PedersenCommit.commit(BigInteger.valueOf(8), r);
        assertFalse(Arrays.equals(c1, c2), "mismo blinding, mensaje distinto -> commitment distinto");
    }

    @Test
    public void hidingBlindingChangesCommitment() {
        BigInteger m = BigInteger.valueOf(42);
        byte[] c1 = PedersenCommit.commit(m, scalar());
        byte[] c2 = PedersenCommit.commit(m, scalar());
        assertFalse(Arrays.equals(c1, c2),
                "mismo mensaje, blinding distinto -> commitment distinto (no filtra m trivialmente)");
    }

    @Test
    public void homomorphicAddition() {
        BigInteger m1 = scalar(), r1 = scalar(), m2 = scalar(), r2 = scalar();
        byte[] sum = PedersenCommit.add(PedersenCommit.commit(m1, r1), PedersenCommit.commit(m2, r2));
        assertArrayEquals(PedersenCommit.commit(m1.add(m2), r1.add(r2)), sum,
                "Comm(m1,r1) (+) Comm(m2,r2) = Comm(m1+m2, r1+r2)");
    }

    @Test
    public void homomorphicScaling() {
        BigInteger m = scalar(), r = scalar(), e = scalar();
        byte[] scaled = PedersenCommit.scale(PedersenCommit.commit(m, r), e);
        assertArrayEquals(PedersenCommit.commit(m.multiply(e), r.multiply(e)), scaled,
                "e · Comm(m,r) = Comm(e·m, e·r)");
    }

    @Test
    public void scalarsAreReducedModL() {
        BigInteger m = scalar(), r = scalar();
        byte[] c = PedersenCommit.commit(m, r);
        assertArrayEquals(c, PedersenCommit.commit(m.add(EdwardsPoint.L), r.add(EdwardsPoint.L)),
                "m+L, r+L producen el mismo commitment (aritmética mod L)");
    }

    @Test
    public void hGeneratorIsValidAndDistinctFromBase() {
        byte[] hEnc = Ristretto255.encode(PedersenCommit.H);
        assertNotNull(Ristretto255.decode(hEnc), "H es un punto Ristretto canónico válido");
        assertFalse(Arrays.equals(hEnc, Ristretto255.encode(EdwardsPoint.BASE)),
                "H != B (segundo generador independiente)");
        // Comm(0,1) = H ; Comm(1,0) = B ; deben diferir.
        assertFalse(Arrays.equals(PedersenCommit.commit(BigInteger.ZERO, BigInteger.ONE),
                        PedersenCommit.commit(BigInteger.ONE, BigInteger.ZERO)),
                "el término en H y el término en B son independientes");
    }
}
