/*
 * Blindaje de la igualdad Ristretto nativa (equalPoints / isIdentity) y de la cache de encoding:
 * el predicado de 4 multiplicaciones DEBE coincidir exactamente con la igualdad de encodings
 * canonicos (la relacion que usaban los verificadores), incluyendo representantes desplazados por
 * torsion E[4] — que es justo lo que el encoding colapsa y una igualdad ingenua (proyectiva
 * Edwards) NO capturaria. Tambien fija que la siembra de cache en decode no introduce aliasing.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RistrettoEqualityTest {

    // Torsion E[4] de edwards25519: (0,-1) de orden 2 y (±i, 0) de orden 4. Sumar cualquiera de
    // ellos a un punto NO cambia el elemento Ristretto que representa.
    private static final EdwardsPoint T2 =
            EdwardsPoint.fromAffine(Fe25519.ZERO, Fe25519.ONE.negate());
    private static final EdwardsPoint T4 =
            EdwardsPoint.fromAffine(Ristretto255.SQRT_M1, Fe25519.ZERO);

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    private static BigInteger scalar() {
        return RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
    }

    private static EdwardsPoint randomPoint() {
        return EdwardsPoint.BASE.scalarMul(scalar());
    }

    @Test
    public void torsionPointsSanity() {
        assertTrue(T2.isOnCurve(), "T2 = (0,-1) esta en la curva");
        assertTrue(T4.isOnCurve(), "T4 = (i,0) esta en la curva");
        // Ordenes: 2*T2 = O, 2*T4 = T2 (T4 tiene orden 4).
        assertTrue(T2.add(T2).equalsPoint(EdwardsPoint.IDENTITY), "T2 tiene orden 2");
        assertTrue(T4.add(T4).equalsPoint(T2), "2*T4 = T2 (orden 4)");
    }

    @Test
    public void fuzzEqualPointsMatchesEncodeEquality() {
        for (int i = 0; i < 1500; i++) {
            EdwardsPoint p = randomPoint();
            EdwardsPoint q;
            switch (i % 4) {
                case 0:
                    q = randomPoint(); // casi siempre distinto
                    break;
                case 1:
                    q = p.add(T2); // mismo elemento, representante distinto
                    break;
                case 2:
                    q = p.add(T4); // mismo elemento, representante distinto
                    break;
                default:
                    q = p.add(T4).add(T2); // mismo elemento, representante -i
                    break;
            }
            boolean byEncoding = Arrays.equals(Ristretto255.encode(p), Ristretto255.encode(q));
            assertEquals(byEncoding, Ristretto255.equalPoints(p, q),
                    "equalPoints debe coincidir con la igualdad de encodings (iter " + i + ")");
        }
    }

    @Test
    public void torsionCosetsAreTheSameElement() {
        for (int i = 0; i < 50; i++) {
            EdwardsPoint p = randomPoint();
            EdwardsPoint p2 = p.add(T2);
            EdwardsPoint p4 = p.add(T4);
            assertArrayEquals(Ristretto255.encode(p), Ristretto255.encode(p2), "encoding invariante bajo +T2");
            assertArrayEquals(Ristretto255.encode(p), Ristretto255.encode(p4), "encoding invariante bajo +T4");
            assertTrue(Ristretto255.equalPoints(p, p2), "equalPoints invariante bajo +T2");
            assertTrue(Ristretto255.equalPoints(p, p4), "equalPoints invariante bajo +T4");
            // Sanity: la igualdad proyectiva Edwards SI los distingue (por eso no vale como sustituto).
            assertFalse(p.equalsPoint(p2), "equalsPoint (Edwards) distingue el representante");
        }
    }

    @Test
    public void distinctElementsAreNotEqual() {
        for (int i = 0; i < 50; i++) {
            EdwardsPoint p = randomPoint();
            assertFalse(Ristretto255.equalPoints(p, p.negate()), "P y -P son elementos distintos");
            assertFalse(Ristretto255.equalPoints(p, p.add(EdwardsPoint.BASE)), "P y P+B son distintos");
        }
    }

    @Test
    public void identityPredicateMatchesEncoding() {
        byte[] idEnc = Ristretto255.encode(EdwardsPoint.IDENTITY);
        EdwardsPoint[] identityCoset = {
            EdwardsPoint.IDENTITY, T2, T4, T4.negate(), EdwardsPoint.IDENTITY.add(T4)
        };
        for (EdwardsPoint p : identityCoset) {
            assertTrue(Ristretto255.isIdentity(p), "miembro del coset identidad -> isIdentity");
            assertArrayEquals(idEnc, Ristretto255.encode(p), "y su encoding es el de la identidad");
        }
        for (int i = 0; i < 50; i++) {
            EdwardsPoint p = randomPoint();
            assertEquals(Arrays.equals(idEnc, Ristretto255.encode(p)), Ristretto255.isIdentity(p),
                    "isIdentity coincide con la comparacion de encodings");
        }
        assertFalse(Ristretto255.isIdentity(EdwardsPoint.BASE), "BASE no es identidad");
    }

    @Test
    public void decodeSeedsCacheWithoutAliasing() {
        EdwardsPoint p = randomPoint();
        byte[] enc = Ristretto255.encode(p);
        byte[] wire = enc.clone();

        EdwardsPoint q = Ristretto255.decode(wire);
        assertTrue(q != null, "encoding canonico decodifica");
        // Mutar el array de entrada DESPUES de decodificar no debe corromper la cache sembrada.
        wire[0] ^= (byte) 0xff;
        assertArrayEquals(enc, Ristretto255.encode(q), "cache sembrada inmune a mutaciones del wire");

        // Mutar el array devuelto por encode no debe corromper encodes posteriores.
        byte[] out = Ristretto255.encode(q);
        out[5] ^= (byte) 0xff;
        assertArrayEquals(enc, Ristretto255.encode(q), "encode devuelve copias defensivas");

        // Round-trip semantico: decode(encode(p)) es el mismo elemento Ristretto que p.
        assertTrue(Ristretto255.equalPoints(p, q), "round-trip preserva el elemento");
    }
}
