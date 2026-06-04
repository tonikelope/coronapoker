/*
 * Precursor del cableado del motor de barajado: la permutación de shuffleDeck expuesta como int[]
 * debe COINCIDIR exactamente con lo que hace shuffleDeck (para poder probar el barajado real sin
 * revelar la permutación en la red).
 */
package com.tonikelope.coronapoker;

import java.security.SecureRandom;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DeterministicShuffleTest {

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    private static byte[] randomSeed() {
        byte[] s = new byte[48];
        Helpers.CSPRNG_GENERATOR.nextBytes(s);
        return s;
    }

    private static boolean isPermutation(int[] perm, int n) {
        if (perm.length != n) {
            return false;
        }
        boolean[] seen = new boolean[n];
        for (int v : perm) {
            if (v < 0 || v >= n || seen[v]) {
                return false;
            }
            seen[v] = true;
        }
        return true;
    }

    @Test
    public void permutationMatchesShuffleDeckExactly() {
        byte[] deck = com.tonikelope.coronapoker.crypto.RistrettoSRA.getGenesisDeck(); // 52 * 32
        int n = deck.length / 32;
        for (int trial = 0; trial < 20; trial++) {
            byte[] seed = randomSeed();
            byte[] shuffled = DeterministicShuffle.shuffleDeck(deck, seed);
            int[] perm = DeterministicShuffle.shufflePermutation(n, seed);

            assertTrue(isPermutation(perm, n), "shufflePermutation devuelve una permutación válida");

            byte[] viaPerm = new byte[deck.length];
            for (int i = 0; i < n; i++) {
                System.arraycopy(deck, perm[i] * 32, viaPerm, i * 32, 32);
            }
            assertArrayEquals(shuffled, viaPerm,
                    "aplicar la permutación al deck == shuffleDeck (lock-step con Fisher-Yates)");
        }
    }

    @Test
    public void deterministicForSameSeed() {
        byte[] seed = randomSeed();
        assertArrayEquals(DeterministicShuffle.shufflePermutation(52, seed), DeterministicShuffle.shufflePermutation(52, seed),
                "misma seed -> misma permutación");
    }

    @Test
    public void differentSeedsGiveDifferentPermutation() {
        int[] p1 = DeterministicShuffle.shufflePermutation(52, randomSeed());
        int[] p2 = DeterministicShuffle.shufflePermutation(52, randomSeed());
        assertFalse(Arrays.equals(p1, p2), "seeds distintas -> permutaciones distintas (con prob abrumadora)");
    }
}
