/*
 * Phase 3 (group migration) — dual-lock cascade flow on the Ristretto engine.
 *
 * Ports the dual-lock dealing tests (previously validated on the Montgomery engine
 * in com.tonikelope.coronapoker.sra.CryptoSRACascadeTest) to RistrettoSRA, so the
 * EXACT flow Crupier runs is covered automatically — minimising manual smoke:
 *   - cascade-lock+shuffle with k_pocket, slice, community rotation (uPocket+kCommunity),
 *   - pocket dealing (others unlock, owner last),
 *   - community dealing (all uCommunity),
 *   - testament: a community-only key handed over on EXIT does NOT leak the pocket,
 *   - showdown: a fabricated key never resolves to a card.
 *
 * Uses CryptoSRA.shuffleDeck (byte-agnostic permutation, unchanged by the migration).
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.CryptoSRA;
import com.tonikelope.coronapoker.Helpers;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RistrettoSRADualLockTest {

    private static final int POCKET_PER_PLAYER = 2;
    private static final int CARD_BYTES = 32;

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    @Test
    public void dualLockCascadeWithRotation() {
        int numPlayers = 6;
        int pocketSlots = numPlayers * POCKET_PER_PLAYER;
        int communitySlots = 52 - pocketSlots;

        byte[][] kPocket = new byte[numPlayers][];
        byte[][] uPocket = new byte[numPlayers][];
        byte[][] kCommunity = new byte[numPlayers][];
        byte[][] uCommunity = new byte[numPlayers][];
        byte[][] seeds = new byte[numPlayers][];
        for (int i = 0; i < numPlayers; i++) {
            kPocket[i] = RistrettoSRA.generateLockScalar();
            uPocket[i] = RistrettoSRA.getUnlockScalar(kPocket[i]);
            kCommunity[i] = RistrettoSRA.generateLockScalar();
            uCommunity[i] = RistrettoSRA.getUnlockScalar(kCommunity[i]);
            seeds[i] = new byte[48];
            Helpers.CSPRNG_GENERATOR.nextBytes(seeds[i]);
        }

        // 1) Cascade with k_pocket.
        byte[] deck = RistrettoSRA.getGenesisDeck();
        for (int i = 0; i < numPlayers; i++) {
            deck = RistrettoSRA.applyCommutativeLock(deck, kPocket[i]);
            deck = CryptoSRA.shuffleDeck(deck, seeds[i]);
        }

        // 2) Slice.
        byte[][] pocketPieces = new byte[pocketSlots][];
        byte[][] communityRaw = new byte[communitySlots][];
        for (int j = 0; j < pocketSlots; j++) {
            pocketPieces[j] = Arrays.copyOfRange(deck, j * CARD_BYTES, (j + 1) * CARD_BYTES);
        }
        for (int j = 0; j < communitySlots; j++) {
            int src = (pocketSlots + j) * CARD_BYTES;
            communityRaw[j] = Arrays.copyOfRange(deck, src, src + CARD_BYTES);
        }

        // 3) Rotation: each peer does uPocket + kCommunity on each community piece.
        byte[][] communityPieces = new byte[communitySlots][];
        for (int j = 0; j < communitySlots; j++) {
            byte[] piece = Arrays.copyOf(communityRaw[j], CARD_BYTES);
            for (int i = 0; i < numPlayers; i++) {
                piece = RistrettoSRA.applyCommutativeLock(piece, uPocket[i]);
                piece = RistrettoSRA.applyCommutativeLock(piece, kCommunity[i]);
            }
            communityPieces[j] = piece;
        }

        Set<Integer> resolved = new HashSet<>();

        // 4) Pocket dealing — others unlock, owner last.
        for (int player = 0; player < numPlayers; player++) {
            for (int slot = 0; slot < POCKET_PER_PLAYER; slot++) {
                byte[] card = Arrays.copyOf(pocketPieces[player * POCKET_PER_PLAYER + slot], CARD_BYTES);
                for (int i = 0; i < numPlayers; i++) {
                    if (i == player) {
                        continue;
                    }
                    card = RistrettoSRA.applyCommutativeLock(card, uPocket[i]);
                }
                card = RistrettoSRA.applyCommutativeLock(card, uPocket[player]);
                int idx = RistrettoSRA.resolveCardIndex(card);
                assertTrue(idx >= 0 && idx < 52, "pocket p=" + player + " slot=" + slot + " -> " + idx);
                assertTrue(resolved.add(idx), "pocket duplicate index " + idx);
            }
        }

        // 5) Community dealing — all uCommunity.
        for (int j = 0; j < communitySlots; j++) {
            byte[] card = Arrays.copyOf(communityPieces[j], CARD_BYTES);
            for (int i = 0; i < numPlayers; i++) {
                card = RistrettoSRA.applyCommutativeLock(card, uCommunity[i]);
            }
            int idx = RistrettoSRA.resolveCardIndex(card);
            assertTrue(idx >= 0 && idx < 52, "community slot=" + j + " -> " + idx);
            assertTrue(resolved.add(idx), "community duplicate index " + idx);
        }

        assertEquals(52, resolved.size(), "dual-lock dealt cards must form a 52-card permutation");
    }

    @Test
    public void testamentCommunityKeyDoesNotLeakExitedPlayerPocket() {
        int numPlayers = 6;
        int victim = 2;
        int pocketSlots = numPlayers * POCKET_PER_PLAYER;

        byte[][] kPocket = new byte[numPlayers][];
        byte[][] uPocket = new byte[numPlayers][];
        byte[][] kCommunity = new byte[numPlayers][];
        byte[][] uCommunity = new byte[numPlayers][];
        byte[][] seeds = new byte[numPlayers][];
        for (int i = 0; i < numPlayers; i++) {
            kPocket[i] = RistrettoSRA.generateLockScalar();
            uPocket[i] = RistrettoSRA.getUnlockScalar(kPocket[i]);
            kCommunity[i] = RistrettoSRA.generateLockScalar();
            uCommunity[i] = RistrettoSRA.getUnlockScalar(kCommunity[i]);
            seeds[i] = new byte[48];
            Helpers.CSPRNG_GENERATOR.nextBytes(seeds[i]);
        }

        byte[] deck = RistrettoSRA.getGenesisDeck();
        for (int i = 0; i < numPlayers; i++) {
            deck = RistrettoSRA.applyCommutativeLock(deck, kPocket[i]);
            deck = CryptoSRA.shuffleDeck(deck, seeds[i]);
        }
        byte[][] pocketPieces = new byte[pocketSlots][];
        for (int j = 0; j < pocketSlots; j++) {
            pocketPieces[j] = Arrays.copyOfRange(deck, j * CARD_BYTES, (j + 1) * CARD_BYTES);
        }

        // Worst case: the host has every OTHER peer's uPocket + victim's community key
        // (testament), but NOT victim's uPocket. The victim's pocket must stay locked.
        for (int slot = 0; slot < POCKET_PER_PLAYER; slot++) {
            byte[] target = Arrays.copyOf(pocketPieces[victim * POCKET_PER_PLAYER + slot], CARD_BYTES);
            for (int i = 0; i < numPlayers; i++) {
                if (i == victim) {
                    continue;
                }
                target = RistrettoSRA.applyCommutativeLock(target, uPocket[i]);
            }
            assertEquals(-1, RistrettoSRA.resolveCardIndex(target),
                    "LEAK: victim pocket resolved without uPocket[victim]");
            // Applying the testament community key must not help either.
            byte[] withTestament = RistrettoSRA.applyCommutativeLock(target, uCommunity[victim]);
            assertEquals(-1, RistrettoSRA.resolveCardIndex(withTestament),
                    "LEAK: community testament must not unlock a pocket");
        }

        // Sanity: the victim's own uPocket does resolve it.
        for (int slot = 0; slot < POCKET_PER_PLAYER; slot++) {
            byte[] target = Arrays.copyOf(pocketPieces[victim * POCKET_PER_PLAYER + slot], CARD_BYTES);
            for (int i = 0; i < numPlayers; i++) {
                if (i == victim) {
                    continue;
                }
                target = RistrettoSRA.applyCommutativeLock(target, uPocket[i]);
            }
            target = RistrettoSRA.applyCommutativeLock(target, uPocket[victim]);
            int idx = RistrettoSRA.resolveCardIndex(target);
            assertTrue(idx >= 0 && idx < 52, "victim must decrypt its own pocket; got " + idx);
        }
    }

    @Test
    public void showdownFabricatedKeyNeverResolves() {
        int numPlayers = 4;
        int cheater = 1;
        int pocketSlots = numPlayers * POCKET_PER_PLAYER;

        byte[][] kPocket = new byte[numPlayers][];
        byte[][] uPocket = new byte[numPlayers][];
        byte[][] seeds = new byte[numPlayers][];
        for (int i = 0; i < numPlayers; i++) {
            kPocket[i] = RistrettoSRA.generateLockScalar();
            uPocket[i] = RistrettoSRA.getUnlockScalar(kPocket[i]);
            seeds[i] = new byte[48];
            Helpers.CSPRNG_GENERATOR.nextBytes(seeds[i]);
        }
        byte[] deck = RistrettoSRA.getGenesisDeck();
        for (int i = 0; i < numPlayers; i++) {
            deck = RistrettoSRA.applyCommutativeLock(deck, kPocket[i]);
            deck = CryptoSRA.shuffleDeck(deck, seeds[i]);
        }
        byte[][] pocketPieces = new byte[pocketSlots][];
        for (int j = 0; j < pocketSlots; j++) {
            pocketPieces[j] = Arrays.copyOfRange(deck, j * CARD_BYTES, (j + 1) * CARD_BYTES);
        }

        // Single-locked residual: only the cheater's lock remains.
        byte[][] singleLocked = new byte[POCKET_PER_PLAYER][];
        for (int slot = 0; slot < POCKET_PER_PLAYER; slot++) {
            byte[] piece = Arrays.copyOf(pocketPieces[cheater * POCKET_PER_PLAYER + slot], CARD_BYTES);
            for (int i = 0; i < numPlayers; i++) {
                if (i == cheater) {
                    continue;
                }
                piece = RistrettoSRA.applyCommutativeLock(piece, uPocket[i]);
            }
            singleLocked[slot] = piece;
        }

        // Honest key resolves.
        for (int slot = 0; slot < POCKET_PER_PLAYER; slot++) {
            byte[] unlocked = RistrettoSRA.applyCommutativeLock(singleLocked[slot], uPocket[cheater]);
            int idx = RistrettoSRA.resolveCardIndex(unlocked);
            assertTrue(idx >= 0 && idx < 52, "honest cheater key must resolve; got " + idx);
        }

        // 50 fabricated keys never resolve.
        for (int attempt = 0; attempt < 50; attempt++) {
            byte[] fake = RistrettoSRA.generateLockScalar();
            for (int slot = 0; slot < POCKET_PER_PLAYER; slot++) {
                byte[] unlocked = RistrettoSRA.applyCommutativeLock(singleLocked[slot], fake);
                int idx = RistrettoSRA.resolveCardIndex(unlocked);
                assertEquals(-1, idx, "fabricated key #" + attempt + " slot " + slot + " must not resolve (got " + idx + ")");
            }
        }
    }
}
