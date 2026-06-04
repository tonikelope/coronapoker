/*
 * Phase 1 (Ristretto255 engine) — end-to-end SRA cascade test.
 *
 * Exercises the whole engine in the real usage pattern: N players cascade-lock +
 * shuffle the genesis deck (commutative lock with a fresh scalar, then a
 * deterministic shuffle), then cards are unlocked and resolved. Validates:
 *   - a full 52-card hand round-trips to a permutation of 0-51 (no -1, no dups),
 *   - the pocket pattern (others unlock, owner unlocks last) resolves correctly,
 *   - unlock order does not matter (commutativity),
 *   - many hands run clean (state isolation, no degeneration).
 *
 * Reuses DeterministicShuffle.shuffleDeck for the permutation (byte-agnostic, already validated).
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.DeterministicShuffle;
import com.tonikelope.coronapoker.Helpers;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RistrettoSRACascadeTest {

    private static final int CARD_BYTES = 32;

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    @Test
    public void singleHandRoundTripsToPermutation() {
        int numPlayers = 6;
        byte[][] lock = new byte[numPlayers][];
        byte[][] unlock = new byte[numPlayers][];
        byte[][] seed = new byte[numPlayers][];
        for (int p = 0; p < numPlayers; p++) {
            lock[p] = RistrettoSRA.generateLockScalar();
            unlock[p] = RistrettoSRA.getUnlockScalar(lock[p]);
            seed[p] = new byte[48];
            Helpers.CSPRNG_GENERATOR.nextBytes(seed[p]);
        }

        byte[] deck = RistrettoSRA.getGenesisDeck();
        for (int p = 0; p < numPlayers; p++) {
            deck = RistrettoSRA.applyCommutativeLock(deck, lock[p]);
            assertTrue(deck != null, "lock pass " + p + " must succeed");
            deck = DeterministicShuffle.shuffleDeck(deck, seed[p]);
        }

        Set<Integer> resolved = new HashSet<>();
        for (int i = 0; i < 52; i++) {
            byte[] card = Arrays.copyOfRange(deck, i * CARD_BYTES, (i + 1) * CARD_BYTES);
            for (int p = 0; p < numPlayers; p++) {
                card = RistrettoSRA.applyCommutativeLock(card, unlock[p]);
            }
            int idx = RistrettoSRA.resolveCardIndex(card);
            assertTrue(idx >= 0 && idx < 52, "card " + i + " must resolve to 0-51 (got " + idx + ")");
            assertTrue(resolved.add(idx), "card index " + idx + " duplicated");
        }
        assertEquals(52, resolved.size(), "final deck must be a 52-card permutation");
    }

    @Test
    public void pocketPatternOwnerUnlocksLast() {
        int numPlayers = 5;
        byte[][] lock = new byte[numPlayers][];
        byte[][] unlock = new byte[numPlayers][];
        byte[][] seed = new byte[numPlayers][];
        for (int p = 0; p < numPlayers; p++) {
            lock[p] = RistrettoSRA.generateLockScalar();
            unlock[p] = RistrettoSRA.getUnlockScalar(lock[p]);
            seed[p] = new byte[48];
            Helpers.CSPRNG_GENERATOR.nextBytes(seed[p]);
        }
        byte[] deck = RistrettoSRA.getGenesisDeck();
        for (int p = 0; p < numPlayers; p++) {
            deck = RistrettoSRA.applyCommutativeLock(deck, lock[p]);
            deck = DeterministicShuffle.shuffleDeck(deck, seed[p]);
        }

        Set<Integer> resolved = new HashSet<>();
        for (int owner = 0; owner < numPlayers; owner++) {
            for (int slot = 0; slot < 2; slot++) {
                int pos = owner * 2 + slot;
                byte[] card = Arrays.copyOfRange(deck, pos * CARD_BYTES, (pos + 1) * CARD_BYTES);
                // Every OTHER player strips their lock; the owner strips last.
                for (int p = 0; p < numPlayers; p++) {
                    if (p == owner) {
                        continue;
                    }
                    card = RistrettoSRA.applyCommutativeLock(card, unlock[p]);
                }
                // Before the owner's unlock, the card must NOT resolve (still locked).
                assertEquals(-1, RistrettoSRA.resolveCardIndex(card),
                        "owner " + owner + " slot " + slot + " must stay locked until the owner unlocks");
                card = RistrettoSRA.applyCommutativeLock(card, unlock[owner]);
                int idx = RistrettoSRA.resolveCardIndex(card);
                assertTrue(idx >= 0 && idx < 52, "pocket must resolve after owner unlock (got " + idx + ")");
                assertTrue(resolved.add(idx), "pocket card index " + idx + " duplicated");
            }
        }
    }

    @Test
    public void unlockOrderIsIrrelevant() {
        int numPlayers = 4;
        byte[][] lock = new byte[numPlayers][];
        byte[][] unlock = new byte[numPlayers][];
        for (int p = 0; p < numPlayers; p++) {
            lock[p] = RistrettoSRA.generateLockScalar();
            unlock[p] = RistrettoSRA.getUnlockScalar(lock[p]);
        }
        byte[] genesis = RistrettoSRA.getGenesisDeck();
        byte[] card0 = Arrays.copyOfRange(genesis, 0, CARD_BYTES);

        byte[] locked = card0;
        for (int p = 0; p < numPlayers; p++) {
            locked = RistrettoSRA.applyCommutativeLock(locked, lock[p]);
        }
        // Unlock forward order.
        byte[] a = locked;
        for (int p = 0; p < numPlayers; p++) {
            a = RistrettoSRA.applyCommutativeLock(a, unlock[p]);
        }
        // Unlock reverse order.
        byte[] b = locked;
        for (int p = numPlayers - 1; p >= 0; p--) {
            b = RistrettoSRA.applyCommutativeLock(b, unlock[p]);
        }
        assertTrue(Arrays.equals(a, b), "unlock order must not matter");
        assertEquals(0, RistrettoSRA.resolveCardIndex(a), "fully unlocked must be card 0");
    }

    @Test
    public void manyHandsDealCleanly() {
        int numPlayers = 6;
        int numHands = Integer.getInteger("rsra.hands", 30);
        int dealtPerHand = numPlayers * 2 + 5; // pockets + board
        int totalUnresolved = 0;
        int totalDuplicates = 0;

        for (int hand = 0; hand < numHands; hand++) {
            byte[][] lock = new byte[numPlayers][];
            byte[][] unlock = new byte[numPlayers][];
            byte[][] seed = new byte[numPlayers][];
            for (int p = 0; p < numPlayers; p++) {
                lock[p] = RistrettoSRA.generateLockScalar();
                unlock[p] = RistrettoSRA.getUnlockScalar(lock[p]);
                seed[p] = new byte[48];
                Helpers.CSPRNG_GENERATOR.nextBytes(seed[p]);
            }
            byte[] deck = RistrettoSRA.getGenesisDeck();
            for (int p = 0; p < numPlayers; p++) {
                deck = RistrettoSRA.applyCommutativeLock(deck, lock[p]);
                deck = DeterministicShuffle.shuffleDeck(deck, seed[p]);
            }
            Set<Integer> resolved = new HashSet<>();
            for (int i = 0; i < dealtPerHand; i++) {
                byte[] card = Arrays.copyOfRange(deck, i * CARD_BYTES, (i + 1) * CARD_BYTES);
                for (int p = 0; p < numPlayers; p++) {
                    card = RistrettoSRA.applyCommutativeLock(card, unlock[p]);
                }
                int idx = RistrettoSRA.resolveCardIndex(card);
                if (idx < 0) {
                    totalUnresolved++;
                } else if (!resolved.add(idx)) {
                    totalDuplicates++;
                }
            }
        }
        assertEquals(0, totalUnresolved, "no card should fail to resolve across " + numHands + " hands");
        assertEquals(0, totalDuplicates, "no duplicate cards within a hand across " + numHands + " hands");
    }
}
