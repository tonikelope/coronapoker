/*
 * Motor de barajado — ladrillo 2: Transcript Fiat–Shamir.
 *
 * Suite adversaria de la pieza donde se cuela la unsoundness: determinismo, que CUALQUIER cambio
 * en lo absorbido (dato, label, orden, dominio) cambia el reto, que el length-prefixing evita la
 * ambigüedad de concatenación, y que los retos consecutivos son independientes (plegado).
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TranscriptTest {

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    @Test
    public void deterministicForSameInputs() {
        Transcript a = new Transcript("dom");
        Transcript b = new Transcript("dom");
        a.absorb("x", new byte[]{1, 2, 3});
        b.absorb("x", new byte[]{1, 2, 3});
        assertArrayEquals(a.challengeBytes("c", 32), b.challengeBytes("c", 32),
                "mismos absorbs -> mismo reto");
    }

    @Test
    public void changingAbsorbedDataChangesChallenge() {
        Transcript a = new Transcript("dom");
        Transcript b = new Transcript("dom");
        a.absorb("x", new byte[]{1, 2, 3});
        b.absorb("x", new byte[]{1, 2, 4});
        assertFalse(Arrays.equals(a.challengeBytes("c", 32), b.challengeBytes("c", 32)),
                "cambiar un byte absorbido cambia el reto");
    }

    @Test
    public void changingLabelChangesChallenge() {
        Transcript a = new Transcript("dom");
        Transcript b = new Transcript("dom");
        a.absorb("x", new byte[]{9});
        b.absorb("y", new byte[]{9});
        assertFalse(Arrays.equals(a.challengeBytes("c", 32), b.challengeBytes("c", 32)),
                "cambiar el label del absorb cambia el reto");
    }

    @Test
    public void domainSeparation() {
        Transcript a = new Transcript("domA");
        Transcript b = new Transcript("domB");
        assertFalse(Arrays.equals(a.challengeBytes("c", 32), b.challengeBytes("c", 32)),
                "distinto dominio -> distinto reto");
    }

    @Test
    public void challengeLabelMatters() {
        Transcript a = new Transcript("dom");
        Transcript b = new Transcript("dom");
        a.absorb("x", new byte[]{5});
        b.absorb("x", new byte[]{5});
        assertFalse(Arrays.equals(a.challengeBytes("c1", 32), b.challengeBytes("c2", 32)),
                "distinto label de reto -> distinto reto");
    }

    @Test
    public void absorbOrderMatters() {
        Transcript a = new Transcript("dom");
        Transcript b = new Transcript("dom");
        a.absorb("p", new byte[]{1});
        a.absorb("q", new byte[]{2});
        b.absorb("q", new byte[]{2});
        b.absorb("p", new byte[]{1});
        assertFalse(Arrays.equals(a.challengeBytes("c", 32), b.challengeBytes("c", 32)),
                "el orden de los absorbs importa");
    }

    @Test
    public void lengthPrefixingPreventsConcatenationAmbiguity() {
        // absorb("ab") debe diferir de absorb("a") seguido de absorb("b").
        Transcript a = new Transcript("dom");
        Transcript b = new Transcript("dom");
        a.absorb("k", new byte[]{'a', 'b'});
        b.absorb("k", new byte[]{'a'});
        b.absorb("k", new byte[]{'b'});
        assertFalse(Arrays.equals(a.challengeBytes("c", 16), b.challengeBytes("c", 16)),
                "length-prefixing evita la ambigüedad de concatenación");
    }

    @Test
    public void consecutiveChallengesAreIndependent() {
        Transcript a = new Transcript("dom");
        a.absorb("x", new byte[]{7});
        byte[] c1 = a.challengeBytes("c", 32);
        byte[] c2 = a.challengeBytes("c", 32); // mismo label, sin absorber nada nuevo
        assertFalse(Arrays.equals(c1, c2),
                "dos retos seguidos con el mismo label difieren (plegado del estado)");
    }

    @Test
    public void challengeScalarInRange() {
        Transcript a = new Transcript("dom");
        a.absorb("x", new byte[]{1});
        for (int i = 0; i < 50; i++) {
            BigInteger s = a.challengeScalar("s");
            assertTrue(s.signum() >= 0 && s.compareTo(EdwardsPoint.L) < 0, "reto escalar en [0, L)");
        }
    }

    @Test
    public void challengeBytesExpandsBeyondOneBlock() {
        // Pedir más de 64 bytes (un bloque SHA-512) debe seguir siendo determinista y completo.
        Transcript a = new Transcript("dom");
        Transcript b = new Transcript("dom");
        a.absorb("x", new byte[]{2});
        b.absorb("x", new byte[]{2});
        byte[] ca = a.challengeBytes("big", 200);
        byte[] cb = b.challengeBytes("big", 200);
        assertEquals(200, ca.length);
        assertArrayEquals(ca, cb, "expansión multi-bloque determinista");
    }

    @Test
    public void absorbPointMatchesItsEncoding() {
        EdwardsPoint p = EdwardsPoint.BASE.scalarMul(RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar()));
        Transcript a = new Transcript("dom");
        Transcript b = new Transcript("dom");
        a.absorbPoint("pt", p);
        b.absorb("pt", Ristretto255.encode(p));
        assertArrayEquals(a.challengeBytes("c", 32), b.challengeBytes("c", 32),
                "absorbPoint == absorber su encoding canónico");
    }

    @Test
    public void absorbScalarBindsValue() {
        Transcript a = new Transcript("dom");
        Transcript b = new Transcript("dom");
        a.absorbScalar("s", BigInteger.valueOf(100));
        b.absorbScalar("s", BigInteger.valueOf(101));
        assertFalse(Arrays.equals(a.challengeBytes("c", 16), b.challengeBytes("c", 16)),
                "absorber un escalar distinto cambia el reto");
        // mismo escalar (incluso > L, reducido) coincide
        Transcript c = new Transcript("dom");
        Transcript d = new Transcript("dom");
        c.absorbScalar("s", BigInteger.valueOf(5));
        d.absorbScalar("s", BigInteger.valueOf(5).add(EdwardsPoint.L));
        assertArrayEquals(c.challengeBytes("c", 16), d.challengeBytes("c", 16),
                "absorbScalar reduce mod L (5 y 5+L coinciden)");
    }

    // Duplicados de Transcript (privados allí) para que la referencia sea INDEPENDIENTE.
    private static final byte[] DOMAIN_PREFIX = "CoronaPoker/Transcript/v1/".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TAG_ABSORB = "ABSORB".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TAG_CHALLENGE = "CHALLENGE".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TAG_FOLD = "FOLD".getBytes(StandardCharsets.UTF_8);

    // Byte-identidad tras el cambio a MessageDigest reutilizado (reset()+update() en vez de
    // getInstance() por hash): recomputa el reto esperado con un digest FRESCO por paso (el camino
    // anterior a la optimización) y el MISMO framing/length-prefix, y exige igualdad byte a byte.
    // Es el único test que fija el VALOR de salida contra una referencia externa (los demás son
    // relacionales y un cambio de layout CONSISTENTE se les escaparía). Ejercita absorb, retos de
    // uno y varios bloques, y el plegado (fold) entre retos.
    @Test
    public void challengesMatchFreshDigestReference() {
        byte[] data = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, (byte) 0xff, (byte) 0x80};

        Transcript t = new Transcript("dom");
        t.absorb("lbl", data);
        byte[] ch1 = t.challengeBytes("c1", 64);   // exactamente un bloque SHA-512
        byte[] ch2 = t.challengeBytes("c2", 100);  // dos bloques -> ejercita el bucle del contador

        byte[] state = sha512Fresh(DOMAIN_PREFIX, "dom".getBytes(StandardCharsets.UTF_8));
        state = sha512Fresh(state, TAG_ABSORB,
                lenPrefixed("lbl".getBytes(StandardCharsets.UTF_8)), lenPrefixed(data));

        byte[] c1Block = sha512Fresh(state, TAG_CHALLENGE,
                lenPrefixed("c1".getBytes(StandardCharsets.UTF_8)), counterBytes(0));
        byte[] expectedCh1 = Arrays.copyOf(c1Block, 64);
        state = sha512Fresh(state, TAG_FOLD, expectedCh1);

        byte[] c2b0 = sha512Fresh(state, TAG_CHALLENGE,
                lenPrefixed("c2".getBytes(StandardCharsets.UTF_8)), counterBytes(0));
        byte[] c2b1 = sha512Fresh(state, TAG_CHALLENGE,
                lenPrefixed("c2".getBytes(StandardCharsets.UTF_8)), counterBytes(1));
        byte[] expectedCh2 = new byte[100];
        System.arraycopy(c2b0, 0, expectedCh2, 0, 64);
        System.arraycopy(c2b1, 0, expectedCh2, 64, 36);

        assertArrayEquals(expectedCh1, ch1, "reto de un bloque debe coincidir con la referencia (digest fresco)");
        assertArrayEquals(expectedCh2, ch2, "reto multi-bloque debe coincidir con la referencia (digest fresco)");
    }

    private static byte[] sha512Fresh(byte[]... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            for (byte[] p : parts) {
                md.update(p);
            }
            return md.digest();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-512 unavailable", e);
        }
    }

    private static byte[] lenPrefixed(byte[] data) {
        byte[] out = new byte[8 + data.length];
        long len = data.length;
        for (int i = 0; i < 8; i++) {
            out[i] = (byte) (len >>> (8 * (7 - i)));
        }
        System.arraycopy(data, 0, out, 8, data.length);
        return out;
    }

    private static byte[] counterBytes(long c) {
        byte[] b = new byte[8];
        for (int i = 0; i < 8; i++) {
            b[i] = (byte) (c >>> (8 * (7 - i)));
        }
        return b;
    }
}
