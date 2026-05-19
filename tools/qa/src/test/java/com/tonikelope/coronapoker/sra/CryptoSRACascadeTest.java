/*
 * SRA cascade stress test.
 *
 * Verifies that the commutative-lock + deterministic-shuffle pipeline used by
 * the in-game dealer holds up over a large number of hands with N players. If
 * the cryptographic core has any accumulation issue (state pollution between
 * hands, curve point degeneration after many lock applications, etc.), this
 * test forces it to manifest by playing many simulated hands end-to-end:
 *
 *   1. All N players cascade-lock + shuffle the genesis deck (each pass: lock
 *      with own scalar, then deterministic shuffle with own seed).
 *   2. The dealer "deals" 2 pocket cards per player + 5 community cards out of
 *      the cascaded deck.
 *   3. Two unlock scenarios are exercised per hand:
 *        a) Community cards: ALL N players reveal their unlock scalar, the
 *           card is fully decrypted, resolveCardIndex must return 0-51.
 *        b) Pocket cards: the OTHER N-1 players reveal their unlock to the
 *           owner, then the owner applies their own unlock last. resolveCardIndex
 *           must return 0-51.
 *   4. Across all 25 dealt cards of a hand, no two indices may collide.
 *
 * Any -1 from resolveCardIndex, any duplicate within a hand, or any thrown
 * exception is recorded as a hand failure. The test prints per-1000 progress so
 * a long run can be aborted early if failures pile up.
 */
package com.tonikelope.coronapoker.sra;

import com.tonikelope.coronapoker.CryptoSRA;
import com.tonikelope.coronapoker.Helpers;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CryptoSRACascadeTest {

    private static final int NUM_PLAYERS_DEFAULT = 10;
    private static final int NUM_HANDS_DEFAULT = 20000;
    private static final int POCKET_PER_PLAYER = 2;
    private static final int COMMUNITY_CARDS = 5;
    private static final int CARD_BYTES = 32;

    private static int intProp(String name, int def) {
        String v = System.getProperty(name);
        if (v == null || v.isEmpty()) {
            return def;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    @Test
    public void testCascadeAndUnlockManyHands() {
        // CryptoSRA.generateLockScalar() reads from Helpers.CSPRNG_GENERATOR,
        // which is initialized by Init.main() in a normal run. In the test JVM
        // we never call Init.main, so we have to populate it ourselves before
        // calling anything that needs it.
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }

        int numPlayers = intProp("sra.players", NUM_PLAYERS_DEFAULT);
        int numHands = intProp("sra.hands", NUM_HANDS_DEFAULT);
        int dealtPerHand = numPlayers * POCKET_PER_PLAYER + COMMUNITY_CARDS;

        System.out.printf("SRA cascade test: players=%d hands=%d dealt/hand=%d%n",
                numPlayers, numHands, dealtPerHand);

        int totalUnresolvedCommunity = 0;
        int totalUnresolvedPocket = 0;
        int totalDuplicates = 0;
        int totalExceptions = 0;
        long startNs = System.nanoTime();

        for (int hand = 0; hand < numHands; hand++) {
            try {
                // ----- Per-hand secrets -----
                byte[][] lockScalars = new byte[numPlayers][];
                byte[][] unlockScalars = new byte[numPlayers][];
                byte[][] shuffleSeeds = new byte[numPlayers][];
                for (int p = 0; p < numPlayers; p++) {
                    lockScalars[p] = CryptoSRA.generateLockScalar();
                    unlockScalars[p] = CryptoSRA.getUnlockScalar(lockScalars[p]);
                    shuffleSeeds[p] = new byte[48];
                    Helpers.CSPRNG_GENERATOR.nextBytes(shuffleSeeds[p]);
                }

                // ----- Cascade lock + shuffle -----
                byte[] deck = CryptoSRA.getGenesisDeck();
                for (int p = 0; p < numPlayers; p++) {
                    deck = CryptoSRA.applyCommutativeLock(deck, lockScalars[p]);
                    deck = CryptoSRA.shuffleDeck(deck, shuffleSeeds[p]);
                }

                // Sanity: deck still has the right byte length.
                if (deck.length != 52 * CARD_BYTES) {
                    totalExceptions++;
                    continue;
                }

                // ----- Deal -----
                // Layout: card 0..(numPlayers*POCKET_PER_PLAYER - 1) = pockets;
                //         next COMMUNITY_CARDS = community.
                byte[][] dealtCards = new byte[dealtPerHand][CARD_BYTES];
                int[] dealtOwners = new int[dealtPerHand];
                for (int i = 0; i < dealtPerHand; i++) {
                    System.arraycopy(deck, i * CARD_BYTES, dealtCards[i], 0, CARD_BYTES);
                    if (i < numPlayers * POCKET_PER_PLAYER) {
                        dealtOwners[i] = i / POCKET_PER_PLAYER;
                    } else {
                        dealtOwners[i] = -1; // community
                    }
                }

                // ----- Unlock + resolve -----
                Set<Integer> resolvedIndices = new HashSet<>();
                int unresolvedCommunityHere = 0;
                int unresolvedPocketHere = 0;

                for (int i = 0; i < dealtPerHand; i++) {
                    byte[] card = dealtCards[i];
                    int owner = dealtOwners[i];

                    if (owner < 0) {
                        // Community: every player unlocks.
                        for (int p = 0; p < numPlayers; p++) {
                            card = CryptoSRA.applyCommutativeLock(card, unlockScalars[p]);
                        }
                    } else {
                        // Pocket: every OTHER player unlocks, then owner unlocks last.
                        for (int p = 0; p < numPlayers; p++) {
                            if (p == owner) {
                                continue;
                            }
                            card = CryptoSRA.applyCommutativeLock(card, unlockScalars[p]);
                        }
                        card = CryptoSRA.applyCommutativeLock(card, unlockScalars[owner]);
                    }

                    int idx = CryptoSRA.resolveCardIndex(card);
                    if (idx < 0) {
                        if (owner < 0) {
                            unresolvedCommunityHere++;
                        } else {
                            unresolvedPocketHere++;
                        }
                    } else if (!resolvedIndices.add(idx)) {
                        totalDuplicates++;
                    }
                }

                totalUnresolvedCommunity += unresolvedCommunityHere;
                totalUnresolvedPocket += unresolvedPocketHere;
            } catch (Throwable t) {
                totalExceptions++;
                if (totalExceptions <= 5) {
                    System.out.printf("Exception at hand %d: %s%n", hand, t);
                }
            }

            if ((hand + 1) % 1000 == 0) {
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
                System.out.printf("  ... %d hands done in %d ms (unresolved community=%d, "
                        + "unresolved pocket=%d, duplicates=%d, exceptions=%d)%n",
                        hand + 1, elapsedMs,
                        totalUnresolvedCommunity, totalUnresolvedPocket,
                        totalDuplicates, totalExceptions);
            }
        }

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
        System.out.printf("DONE. %d hands in %d ms (%.2f ms/hand). "
                + "unresolved community=%d, unresolved pocket=%d, duplicates=%d, exceptions=%d%n",
                numHands, elapsedMs, (double) elapsedMs / numHands,
                totalUnresolvedCommunity, totalUnresolvedPocket,
                totalDuplicates, totalExceptions);

        assertEquals(0, totalExceptions, "SRA threw exceptions during cascade or unlock");
        assertEquals(0, totalUnresolvedCommunity, "SRA failed to resolve community cards (resolveCardIndex == -1)");
        assertEquals(0, totalUnresolvedPocket, "SRA failed to resolve pocket cards (resolveCardIndex == -1)");
        assertEquals(0, totalDuplicates, "SRA produced duplicate cards within a single hand");
    }

    /**
     * Smoke check that a single round-trip works at all. Cheap, runs even when
     * the main stress test is disabled (or when sra.hands is set very low).
     */
    @Test
    public void testSingleHandRoundTrip() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
        int numPlayers = 6;
        byte[][] lockScalars = new byte[numPlayers][];
        byte[][] unlockScalars = new byte[numPlayers][];
        byte[][] seeds = new byte[numPlayers][];
        for (int p = 0; p < numPlayers; p++) {
            lockScalars[p] = CryptoSRA.generateLockScalar();
            unlockScalars[p] = CryptoSRA.getUnlockScalar(lockScalars[p]);
            seeds[p] = new byte[48];
            Helpers.CSPRNG_GENERATOR.nextBytes(seeds[p]);
        }

        byte[] deck = CryptoSRA.getGenesisDeck();
        for (int p = 0; p < numPlayers; p++) {
            deck = CryptoSRA.applyCommutativeLock(deck, lockScalars[p]);
            deck = CryptoSRA.shuffleDeck(deck, seeds[p]);
        }

        // Pick the first 52 cards (all of them) and unlock + resolve. We expect
        // a permutation of [0..51].
        Set<Integer> resolved = new HashSet<>();
        for (int i = 0; i < 52; i++) {
            byte[] card = new byte[CARD_BYTES];
            System.arraycopy(deck, i * CARD_BYTES, card, 0, CARD_BYTES);
            for (int p = 0; p < numPlayers; p++) {
                card = CryptoSRA.applyCommutativeLock(card, unlockScalars[p]);
            }
            int idx = CryptoSRA.resolveCardIndex(card);
            assertTrue(idx >= 0 && idx < 52,
                    "Card " + i + " resolved to " + idx + " (expected 0-51)");
            assertTrue(resolved.add(idx), "Duplicate card index " + idx + " at position " + i);
        }
        assertEquals(52, resolved.size(), "Final deck must be a 52-card permutation");
    }
}
