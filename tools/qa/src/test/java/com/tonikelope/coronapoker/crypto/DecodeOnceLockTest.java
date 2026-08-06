/*
 * Blindaje del atajo "decodificar una vez" (helpers lockPoints + encodeDeck) que usan los handlers de
 * cascada y rotacion tras el cambio: (1) el computo es byte-identico a applyCommutativeLock, y (2) la
 * puerta de validacion decodeDeck==null es EXACTAMENTE equivalente al viejo arePointsValid==false (la
 * invariante de seguridad "un decode valido ES la prueba de pertenencia al grupo"). Sin (2), un atajo
 * mal hecho dejaria pasar puntos sin validar, y eso no lo caza un smoke de juego honesto.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DecodeOnceLockTest {

    private static final BigInteger L = EdwardsPoint.L;

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    private static byte[] randomScalar(Random r) {
        while (true) {
            byte[] raw = new byte[32];
            r.nextBytes(raw);
            raw[31] &= (byte) 0x1f;
            BigInteger s = RistrettoSRA.bytesToScalar(raw);
            if (s.signum() != 0 && s.compareTo(L) < 0) {
                return raw;
            }
        }
    }

    /** encodeDeck(lockPoints(decodeDeck(deck), s)) debe ser byte-identico a applyCommutativeLock(deck, s). */
    @Test
    public void lockPointsMatchesApplyCommutativeLock() {
        Random r = new Random(0xDEC0DE01L);
        byte[] deck = RistrettoSRA.getGenesisDeck();
        for (int i = 0; i < 400; i++) {
            byte[] s = randomScalar(r);
            byte[] viaBytes = RistrettoSRA.applyCommutativeLock(deck, s);
            byte[] viaPoints = ShuffleCascade.encodeDeck(
                    RistrettoSRA.lockPoints(ShuffleCascade.decodeDeck(deck), s));
            assertNotNull(viaBytes, "applyCommutativeLock null en iter " + i);
            assertArrayEquals(viaBytes, viaPoints, "lock mismatch en iter " + i);
            deck = viaBytes; // encadenar: cada iteracion sobre un deck valido distinto
        }
    }

    /** encodeDeck . decodeDeck es la identidad byte a byte sobre decks canonicos. */
    @Test
    public void encodeDecodeRoundTrip() {
        byte[] deck = RistrettoSRA.getGenesisDeck();
        assertArrayEquals(deck, ShuffleCascade.encodeDeck(ShuffleCascade.decodeDeck(deck)));
    }

    /**
     * LA prueba de seguridad del cambio: decodeDeck(d)==null debe ser EXACTAMENTE arePointsValid(d)==false,
     * sobre entradas validas y adversarias. Si divergieran, sustituir arePointsValid por decodeDeck en los
     * handlers dejaria entrar (o rechazaria de mas) puntos que el otro no.
     */
    @Test
    public void decodeGateEqualsArePointsValid() {
        byte[] good = RistrettoSRA.getGenesisDeck();
        byte[] badLen = Arrays.copyOf(good, good.length - 1);      // 1663 bytes: no multiplo de 32
        byte[] corruptFirst = good.clone();
        Arrays.fill(corruptFirst, 0, 32, (byte) 0xFF);             // primer punto no canonico (>= p)
        byte[] corruptMid = good.clone();
        Arrays.fill(corruptMid, 25 * 32, 26 * 32, (byte) 0xFF);    // punto 25 no canonico
        byte[] empty = new byte[0];
        byte[] onePointBad = new byte[32];
        Arrays.fill(onePointBad, (byte) 0xFF);

        byte[][] cases = {good, badLen, corruptFirst, corruptMid, empty, onePointBad, null};
        for (int i = 0; i < cases.length; i++) {
            boolean valid = RistrettoSRA.arePointsValid(cases[i]);
            boolean decoded = ShuffleCascade.decodeDeck(cases[i]) != null;
            assertEquals(valid, decoded, "gate divergente en caso " + i);
        }

        // El deck bueno pasa; los corruptos NO son ni validos ni decodificables.
        assertTrue(RistrettoSRA.arePointsValid(good));
        assertNotNull(ShuffleCascade.decodeDeck(good));
        assertFalse(RistrettoSRA.arePointsValid(corruptFirst));
        assertNull(ShuffleCascade.decodeDeck(corruptFirst));
        assertNull(ShuffleCascade.decodeDeck(corruptMid));
    }

    /** lockPoints/encodeDeck propagan el rechazo: null-in y elemento null-in devuelven null. */
    @Test
    public void lockPointsAndEncodeRejectNulls() {
        assertNull(RistrettoSRA.lockPoints(null, randomScalar(new Random(1))));
        EdwardsPoint[] withNull = ShuffleCascade.decodeDeck(RistrettoSRA.getGenesisDeck());
        withNull[10] = null;
        assertNull(RistrettoSRA.lockPoints(withNull, randomScalar(new Random(2))));
        assertNull(ShuffleCascade.encodeDeck(null));
        assertNull(ShuffleCascade.encodeDeck(new EdwardsPoint[0]));
        assertNull(ShuffleCascade.encodeDeck(withNull));
    }

    /**
     * La rotacion en vivo consume inR (=decodeDeck(incoming)) y outR (=lockPoints(inR, s)) directamente,
     * en vez de re-decodificar 'rotated'. Debe verificar igual que con outR' = decodeDeck(encodeDeck(outR)).
     */
    @Test
    public void rotationProofOverReusedPointsVerifies() {
        Random r = new Random(0x707A7107L);
        byte[] deck = RistrettoSRA.applyCommutativeLock(RistrettoSRA.getGenesisDeck(), randomScalar(r));
        EdwardsPoint[] inR = ShuffleCascade.decodeDeck(deck);
        BigInteger s = RistrettoSRA.bytesToScalar(randomScalar(r));
        EdwardsPoint[] outR = RistrettoSRA.lockPoints(inR, RistrettoSRA.scalarToBytes(s));
        byte[] rotated = ShuffleCascade.encodeDeck(outR);
        // La prueba sobre los puntos reutilizados verifica contra el 'rotated' que viaja por el wire.
        byte[] rp = DualLockWire.encodeRotationProof(RotationProof.prove(s, inR, outR));
        assertNotNull(rp);
        assertTrue(DualLockWire.verifyRotationStepWire(deck, rotated, rp));
    }
}
