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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    /**
     * Dual-lock cascade with rotation (Opción G). Cada peer tiene DOS scalars
     * por mano: k_pocket y k_community. Flujo:
     *
     *   1. Cascade normal con k_pocket (sin cambios respecto al protocolo actual).
     *   2. Slice: posiciones 0..N*2-1 = pocket pieces, N*2..51 = community pieces.
     *   3. Rotación sobre community pieces: cada peer aplica k_pocket_inverse
     *      (quita su lock pocket) + k_community (añade lock community). Tras
     *      pasar por todos los peers, las community pieces tienen SOLO locks
     *      de k_community.
     *   4. Pocket dealing: per-recipient cascade con k_pocket (sin cambios).
     *   5. Community dealing: per-recipient cascade con k_community.
     *
     * El test verifica el round-trip completo: todas las pocket cards resuelven
     * a índices válidos 0-51, todas las community cards también, y no hay
     * colisiones (ninguna carta aparece dos veces).
     */
    @Test
    public void testDualLockCascadeWithRotation() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }

        int numPlayers = 6;
        int pocketSlots = numPlayers * POCKET_PER_PLAYER;
        int communitySlots = 52 - pocketSlots;

        // Per-peer secrets: dos scalars (pocket y community) + un shuffle seed.
        byte[][] kPocket = new byte[numPlayers][];
        byte[][] uPocket = new byte[numPlayers][];
        byte[][] kCommunity = new byte[numPlayers][];
        byte[][] uCommunity = new byte[numPlayers][];
        byte[][] seeds = new byte[numPlayers][];
        for (int i = 0; i < numPlayers; i++) {
            kPocket[i] = CryptoSRA.generateLockScalar();
            uPocket[i] = CryptoSRA.getUnlockScalar(kPocket[i]);
            kCommunity[i] = CryptoSRA.generateLockScalar();
            uCommunity[i] = CryptoSRA.getUnlockScalar(kCommunity[i]);
            seeds[i] = new byte[48];
            Helpers.CSPRNG_GENERATOR.nextBytes(seeds[i]);
        }

        // 1) Cascade con k_pocket.
        byte[] deck = CryptoSRA.getGenesisDeck();
        for (int i = 0; i < numPlayers; i++) {
            deck = CryptoSRA.applyCommutativeLock(deck, kPocket[i]);
            deck = CryptoSRA.shuffleDeck(deck, seeds[i]);
        }

        // 2) Slice.
        byte[][] pocketPieces = new byte[pocketSlots][];
        byte[][] communityPiecesRaw = new byte[communitySlots][];
        for (int j = 0; j < pocketSlots; j++) {
            pocketPieces[j] = Arrays.copyOfRange(deck, j * CARD_BYTES, (j + 1) * CARD_BYTES);
        }
        for (int j = 0; j < communitySlots; j++) {
            int src = (pocketSlots + j) * CARD_BYTES;
            communityPiecesRaw[j] = Arrays.copyOfRange(deck, src, src + CARD_BYTES);
        }

        // 3) Rotación: cada peer hace uPocket + kCommunity sobre cada community piece.
        byte[][] communityPieces = new byte[communitySlots][];
        for (int j = 0; j < communitySlots; j++) {
            byte[] piece = Arrays.copyOf(communityPiecesRaw[j], CARD_BYTES);
            for (int i = 0; i < numPlayers; i++) {
                piece = CryptoSRA.applyCommutativeLock(piece, uPocket[i]);
                piece = CryptoSRA.applyCommutativeLock(piece, kCommunity[i]);
            }
            communityPieces[j] = piece;
        }

        // 4) Pocket dealing — cada owner descifra aplicando todos los uPocket.
        Set<Integer> resolvedIndices = new HashSet<>();
        for (int player = 0; player < numPlayers; player++) {
            for (int slot = 0; slot < POCKET_PER_PLAYER; slot++) {
                int pieceIdx = player * POCKET_PER_PLAYER + slot;
                byte[] card = Arrays.copyOf(pocketPieces[pieceIdx], CARD_BYTES);
                // Per-recipient: otros peers aplican su uPocket, owner aplica el suyo último.
                for (int i = 0; i < numPlayers; i++) {
                    if (i == player) continue;
                    card = CryptoSRA.applyCommutativeLock(card, uPocket[i]);
                }
                card = CryptoSRA.applyCommutativeLock(card, uPocket[player]);

                int idx = CryptoSRA.resolveCardIndex(card);
                assertTrue(idx >= 0 && idx < 52,
                        "Pocket player=" + player + " slot=" + slot + " resolved to " + idx);
                assertTrue(resolvedIndices.add(idx),
                        "Pocket player=" + player + " slot=" + slot + " duplicated card index " + idx);
            }
        }

        // 5) Community dealing — cualquier recipient descifra aplicando todos los uCommunity.
        for (int j = 0; j < communitySlots; j++) {
            byte[] card = Arrays.copyOf(communityPieces[j], CARD_BYTES);
            for (int i = 0; i < numPlayers; i++) {
                card = CryptoSRA.applyCommutativeLock(card, uCommunity[i]);
            }
            int idx = CryptoSRA.resolveCardIndex(card);
            assertTrue(idx >= 0 && idx < 52,
                    "Community slot=" + j + " resolved to " + idx);
            assertTrue(resolvedIndices.add(idx),
                    "Community slot=" + j + " duplicated card index " + idx);
        }

        assertEquals(52, resolvedIndices.size(),
                "Dual-lock dealt cards must form a 52-card permutation (no missing, no duplicates)");
    }

    /**
     * Test de seguridad: testamento dual-lock NO leakea pockets.
     *
     * Escenario: peer X hace EXIT mid-hand. El testamento le da al host SOLO
     * el scalar uCommunity_X. Aunque TODOS los demás peers cooperen con el
     * host y le entreguen sus uPocket y uCommunity (worst-case adversarial
     * cooperation), el host NO debe ser capaz de recuperar la identidad de
     * las cartas privadas de X.
     *
     * Concretamente verificamos que, tras aplicar a la pocket piece de X
     * todos los uPocket de los OTROS peers, el punto resultante NO resuelve
     * a un card index — porque sigue cifrado con kPocket_X y solo X tiene
     * uPocket_X.
     *
     * Esto es la propiedad clave que justifica el refactor dual-lock vs el
     * esquema single-lock previo (donde testamento exponía la única clave).
     */
    @Test
    public void testTestamentCommunityDoesNotLeakExitedPlayerPocket() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }

        int numPlayers = 6;
        int victim = 2;             // peer X que va a hacer EXIT
        int pocketSlots = numPlayers * POCKET_PER_PLAYER;
        int communitySlots = 52 - pocketSlots;

        byte[][] kPocket = new byte[numPlayers][];
        byte[][] uPocket = new byte[numPlayers][];
        byte[][] kCommunity = new byte[numPlayers][];
        byte[][] uCommunity = new byte[numPlayers][];
        byte[][] seeds = new byte[numPlayers][];
        for (int i = 0; i < numPlayers; i++) {
            kPocket[i] = CryptoSRA.generateLockScalar();
            uPocket[i] = CryptoSRA.getUnlockScalar(kPocket[i]);
            kCommunity[i] = CryptoSRA.generateLockScalar();
            uCommunity[i] = CryptoSRA.getUnlockScalar(kCommunity[i]);
            seeds[i] = new byte[48];
            Helpers.CSPRNG_GENERATOR.nextBytes(seeds[i]);
        }

        // Cascade + slice + rotación (idéntico al test anterior).
        byte[] deck = CryptoSRA.getGenesisDeck();
        for (int i = 0; i < numPlayers; i++) {
            deck = CryptoSRA.applyCommutativeLock(deck, kPocket[i]);
            deck = CryptoSRA.shuffleDeck(deck, seeds[i]);
        }

        byte[][] pocketPieces = new byte[pocketSlots][];
        for (int j = 0; j < pocketSlots; j++) {
            pocketPieces[j] = Arrays.copyOfRange(deck, j * CARD_BYTES, (j + 1) * CARD_BYTES);
        }

        // El attacker (host malicioso) tiene:
        //   - uPocket[i] de TODOS los peers menos X (worst-case cooperation).
        //   - uCommunity[i] de TODOS los peers menos X.
        //   - uCommunity[X] vía testamento (X lo entrega al hacer EXIT).
        //   - NO tiene uPocket[X].
        //
        // Intento de ataque #1: aplicar todos los uPocket conocidos a las pocket pieces de X.
        for (int slot = 0; slot < POCKET_PER_PLAYER; slot++) {
            byte[] target = Arrays.copyOf(pocketPieces[victim * POCKET_PER_PLAYER + slot], CARD_BYTES);
            for (int i = 0; i < numPlayers; i++) {
                if (i == victim) continue;
                target = CryptoSRA.applyCommutativeLock(target, uPocket[i]);
            }
            // En este punto: target = kPocket[victim] * G_y (sigue bloqueado por la clave que
            // SOLO tiene el peer X). resolveCardIndex debe devolver -1.
            int idx = CryptoSRA.resolveCardIndex(target);
            assertEquals(-1, idx,
                    "LEAK: pocket de peer X resolvió a card index " + idx
                    + " sin uPocket[X]. El testamento community-only NO está protegiendo pocket.");
        }

        // Intento de ataque #2: aplicar testamento (uCommunity[X]) sobre las pocket pieces de X.
        // Esto no tiene sentido criptográfico (las pocket pieces no llevan kCommunity), pero el
        // test verifica explícitamente que un atacante naive no obtiene nada por probarlo.
        for (int slot = 0; slot < POCKET_PER_PLAYER; slot++) {
            byte[] target = Arrays.copyOf(pocketPieces[victim * POCKET_PER_PLAYER + slot], CARD_BYTES);
            for (int i = 0; i < numPlayers; i++) {
                if (i == victim) continue;
                target = CryptoSRA.applyCommutativeLock(target, uPocket[i]);
            }
            target = CryptoSRA.applyCommutativeLock(target, uCommunity[victim]);
            int idx = CryptoSRA.resolveCardIndex(target);
            assertEquals(-1, idx,
                    "LEAK: pocket de peer X resolvió a card index " + idx
                    + " tras aplicar testamento community (ataque naive). El testamento NO debe afectar a pockets.");
        }

        // Intento de ataque #3: aplicar uCommunity de TODOS los peers (incluyendo testamento) a las
        // pocket pieces. Sigue sin tener sentido criptográfico, pero verificamos que no hay colisión
        // accidental.
        for (int slot = 0; slot < POCKET_PER_PLAYER; slot++) {
            byte[] target = Arrays.copyOf(pocketPieces[victim * POCKET_PER_PLAYER + slot], CARD_BYTES);
            for (int i = 0; i < numPlayers; i++) {
                if (i == victim) continue;
                target = CryptoSRA.applyCommutativeLock(target, uPocket[i]);
            }
            for (int i = 0; i < numPlayers; i++) {
                target = CryptoSRA.applyCommutativeLock(target, uCommunity[i]);
            }
            int idx = CryptoSRA.resolveCardIndex(target);
            assertEquals(-1, idx,
                    "LEAK: pocket de peer X resolvió a card index " + idx
                    + " tras aplicar todos los uCommunity + testamento + uPocket de los demás.");
        }

        // Sanity check: el victim sí puede descifrar sus pockets con uPocket[victim].
        for (int slot = 0; slot < POCKET_PER_PLAYER; slot++) {
            byte[] target = Arrays.copyOf(pocketPieces[victim * POCKET_PER_PLAYER + slot], CARD_BYTES);
            for (int i = 0; i < numPlayers; i++) {
                if (i == victim) continue;
                target = CryptoSRA.applyCommutativeLock(target, uPocket[i]);
            }
            target = CryptoSRA.applyCommutativeLock(target, uPocket[victim]);
            int idx = CryptoSRA.resolveCardIndex(target);
            assertTrue(idx >= 0 && idx < 52,
                    "Sanity: peer X debe poder descifrar su propia pocket con uPocket[X]; got " + idx);
            assertNotEquals(-1, idx,
                    "Sanity: peer X aplicando su propia uPocket no debe dar -1");
        }
    }

    /**
     * Showdown zero-trust (PHASE A.1): un peer que MIENTE al showdown enviando
     * un k_pocket_unlock fabricado NO puede convencer a nadie de cartas falsas.
     * Detección por la propia matemática SRA, sin necesidad de un lockdown
     * "punitivo" inventado encima.
     *
     * Si k_real * pocket_piece = G_y (genesis point de la carta real), un
     * k_fake arbitrario produce un punto random de la curva. La probabilidad
     * de que ese punto caiga sobre uno de los 52 genesis (52/2^256) es
     * astronómicamente cero. Para que un cheater consiga apuntar a una carta
     * DIFERENTE válida G_y' tendría que resolver
     * k_fake = (k_real * G_y') / G_y — DLP, intratable.
     *
     * Este test ejercita:
     *   1) Honest: aplicando uPocket[X] real resuelve a 0-51.
     *   2) Cheating: 50 claves fabricadas random sobre el mismo pocket SIEMPRE
     *      dan -1.
     *
     * Consecuencia operativa: clave mala → resolveCardIndex==-1 →
     * unlockPlayerCardsWithSRAKey devuelve false → las cartas del cheater
     * se quedan tapada en la UI de todos, no se emite SHOWCARDS para él, no
     * se añade a POTCARDS. El cheater no consigue ventaja porque el pot ya
     * se resuelve por acciones (no por claims de cartas); como mucho se
     * esconde sus propias cartas a otros — algo que en poker no es per se
     * cheating sino un muck que el motor ya cubre. NO disparamos lockdown
     * en este flujo: el SRA es la verificación, no hace falta castigo extra
     * que abra flanco a falsos positivos por red mala / bug transitorio.
     */
    @Test
    public void testShowdownCheaterDetectedByResolveCardIndex() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }

        int numPlayers = 4;
        int cheater = 1;
        int pocketSlots = numPlayers * POCKET_PER_PLAYER;

        byte[][] kPocket = new byte[numPlayers][];
        byte[][] uPocket = new byte[numPlayers][];
        byte[][] seeds = new byte[numPlayers][];
        for (int i = 0; i < numPlayers; i++) {
            kPocket[i] = CryptoSRA.generateLockScalar();
            uPocket[i] = CryptoSRA.getUnlockScalar(kPocket[i]);
            seeds[i] = new byte[48];
            Helpers.CSPRNG_GENERATOR.nextBytes(seeds[i]);
        }

        byte[] deck = CryptoSRA.getGenesisDeck();
        for (int i = 0; i < numPlayers; i++) {
            deck = CryptoSRA.applyCommutativeLock(deck, kPocket[i]);
            deck = CryptoSRA.shuffleDeck(deck, seeds[i]);
        }

        byte[][] pocketPieces = new byte[pocketSlots][];
        for (int j = 0; j < pocketSlots; j++) {
            pocketPieces[j] = Arrays.copyOfRange(deck, j * CARD_BYTES, (j + 1) * CARD_BYTES);
        }

        // Estado del host al showdown: cada pocket tiene solo el lock del
        // owner (los otros peers ya removieron los suyos en el per-recipient
        // cascade durante el pocket dealing).
        byte[][] cheaterPocketSingleLocked = new byte[POCKET_PER_PLAYER][];
        for (int slot = 0; slot < POCKET_PER_PLAYER; slot++) {
            byte[] piece = Arrays.copyOf(pocketPieces[cheater * POCKET_PER_PLAYER + slot], CARD_BYTES);
            for (int i = 0; i < numPlayers; i++) {
                if (i == cheater) continue;
                piece = CryptoSRA.applyCommutativeLock(piece, uPocket[i]);
            }
            cheaterPocketSingleLocked[slot] = piece;
        }

        // Caso 1 — HONEST: clave real resuelve a 0-51.
        for (int slot = 0; slot < POCKET_PER_PLAYER; slot++) {
            byte[] unlocked = CryptoSRA.applyCommutativeLock(cheaterPocketSingleLocked[slot], uPocket[cheater]);
            int idx = CryptoSRA.resolveCardIndex(unlocked);
            assertTrue(idx >= 0 && idx < 52,
                    "Honest: la clave real del cheater debe resolver a card index válido; got " + idx);
        }

        // Caso 2 — CHEATING: 50 claves fabricadas random NO resuelven.
        int cheatAttempts = 50;
        for (int attempt = 0; attempt < cheatAttempts; attempt++) {
            byte[] fakeKey = CryptoSRA.generateLockScalar();
            for (int slot = 0; slot < POCKET_PER_PLAYER; slot++) {
                byte[] unlocked = CryptoSRA.applyCommutativeLock(cheaterPocketSingleLocked[slot], fakeKey);
                int idx = CryptoSRA.resolveCardIndex(unlocked);
                assertEquals(-1, idx,
                        "Cheating attempt #" + attempt + " slot " + slot
                        + ": una clave random NO debería resolver a card index válido (got " + idx + ")");
            }
        }
    }
}
