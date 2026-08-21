package com.tonikelope.coronapoker.protocolsim;

import com.tonikelope.coronapoker.Card;
import com.tonikelope.coronapoker.CanonicalActionRecord;
import com.tonikelope.coronapoker.DeterministicShuffle;
import com.tonikelope.coronapoker.IdentityManager;
import com.tonikelope.coronapoker.PotCardsEnvelope;
import com.tonikelope.coronapoker.crypto.RistrettoSRA;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end crypto slice from dual-lock deck to atomic POTCARDS. */
@Tag("slow")
@Tag("protocol-sim")
final class ProtocolSraShowdownSimulationTest {

    private static final int PLAYERS = 3;
    private static final int CARD_BYTES = 32;
    private static final int POCKET_SLOTS = PLAYERS * 2;
    private static final List<String> NICKS = List.of("sra-host", "sra-client", "CoronaBot$sra");

    private static IdentityManager hostIdentity;
    private static IdentityManager clientIdentity;

    @BeforeAll
    static void identities() {
        hostIdentity = IdentityManager.initializeForNick(NICKS.get(0));
        clientIdentity = IdentityManager.initializeForNick(NICKS.get(1));
        assertTrue(hostIdentity.isReady(), hostIdentity.getLoadError());
        assertTrue(clientIdentity.isReady(), clientIdentity.getLoadError());
    }

    @Test
    void dualLockDealRitBoardsAndAtomicShowdownStayBoundToOneDeck() {
        byte[][] pocketLocks = new byte[PLAYERS][];
        byte[][] pocketUnlocks = new byte[PLAYERS][];
        byte[][] communityLocks = new byte[PLAYERS][];
        byte[][] communityUnlocks = new byte[PLAYERS][];
        byte[] deck = RistrettoSRA.getGenesisDeck();

        for (int player = 0; player < PLAYERS; player++) {
            pocketLocks[player] = scalar(player + 2);
            pocketUnlocks[player] = RistrettoSRA.getUnlockScalar(pocketLocks[player]);
            communityLocks[player] = scalar(player + 11);
            communityUnlocks[player] = RistrettoSRA.getUnlockScalar(communityLocks[player]);
            deck = RistrettoSRA.applyCommutativeLock(deck, pocketLocks[player]);
            byte[] seed = new byte[48];
            Arrays.fill(seed, (byte) (31 + player));
            deck = DeterministicShuffle.shuffleDeck(deck, seed);
        }

        byte[][] pocketResiduals = new byte[PLAYERS * 2][];
        int[][] pocketCards = new int[PLAYERS][2];
        Set<Integer> resolved = new HashSet<>();
        for (int player = 0; player < PLAYERS; player++) {
            for (int slot = 0; slot < 2; slot++) {
                int offset = (player * 2 + slot) * CARD_BYTES;
                byte[] residual = Arrays.copyOfRange(deck, offset, offset + CARD_BYTES);
                for (int peer = 0; peer < PLAYERS; peer++) {
                    if (peer != player) {
                        residual = RistrettoSRA.applyCommutativeLock(
                                residual, pocketUnlocks[peer]);
                    }
                }
                pocketResiduals[player * 2 + slot] = residual;
                byte[] opened = RistrettoSRA.applyCommutativeLock(
                        residual, pocketUnlocks[player]);
                int card = RistrettoSRA.resolveCardIndex(opened);
                assertTrue(card >= 0 && card < 52);
                assertTrue(resolved.add(card), "duplicate pocket card " + card);
                pocketCards[player][slot] = card;
            }
        }

        List<Integer> communityCards = new ArrayList<>();
        for (int slot = POCKET_SLOTS; slot < 52; slot++) {
            byte[] card = Arrays.copyOfRange(deck, slot * CARD_BYTES, (slot + 1) * CARD_BYTES);
            for (int player = 0; player < PLAYERS; player++) {
                card = RistrettoSRA.applyCommutativeLock(card, pocketUnlocks[player]);
                card = RistrettoSRA.applyCommutativeLock(card, communityLocks[player]);
            }
            for (int player = 0; player < PLAYERS; player++) {
                card = RistrettoSRA.applyCommutativeLock(card, communityUnlocks[player]);
            }
            int index = RistrettoSRA.resolveCardIndex(card);
            assertTrue(index >= 0 && index < 52);
            assertTrue(resolved.add(index), "duplicate community card " + index);
            communityCards.add(index);
        }
        assertEquals(52, resolved.size());
        assertEquals(10, new HashSet<>(communityCards.subList(0, 10)).size(),
                "normal and RIT side-B boards must not reuse a card");

        byte[] handId = new byte[CanonicalActionRecord.HAND_ID_BYTES];
        handId[0] = 77;
        String[] wire = potCardsWire(handId, pocketCards, pocketUnlocks);
        PotCardsEnvelope envelope = PotCardsEnvelope.parse(wire, new HashSet<>(NICKS));
        assertEquals(PLAYERS, envelope.entries().size());
        for (int player = 0; player < PLAYERS; player++) {
            PotCardsEnvelope.Entry entry = envelope.entries().get(player);
            assertTrue(verifyEntry(handId, entry, signerKey(player),
                    pocketResiduals[player * 2], pocketResiduals[player * 2 + 1]));
        }

        String[] changedPlaintext = wire.clone();
        int unusedCard = 0;
        while (resolvedPocketCard(pocketCards, unusedCard)) {
            unusedCard++;
        }
        changedPlaintext[4] = Card.shortStringFromIndex(unusedCard);
        PotCardsEnvelope plaintextEnvelope = PotCardsEnvelope.parse(
                changedPlaintext, new HashSet<>(NICKS));
        assertFalse(verifyEntry(handId, plaintextEnvelope.entries().get(0), signerKey(0),
                pocketResiduals[0], pocketResiduals[1]));

        String[] changedProof = wire.clone();
        byte[] badSignature = Base64.getDecoder().decode(changedProof[7]);
        badSignature[0] ^= 1;
        changedProof[7] = Base64.getEncoder().encodeToString(badSignature);
        PotCardsEnvelope badEnvelope = PotCardsEnvelope.parse(changedProof, new HashSet<>(NICKS));
        assertFalse(verifyEntry(handId, badEnvelope.entries().get(0), signerKey(0),
                pocketResiduals[0], pocketResiduals[1]));
    }

    @Test
    void communityTestamentCannotOpenTheExitedPlayersPocket() {
        byte[] pocketLock = scalar(7);
        byte[] pocketUnlock = RistrettoSRA.getUnlockScalar(pocketLock);
        byte[] communityUnlock = RistrettoSRA.getUnlockScalar(scalar(19));
        byte[] card = Arrays.copyOfRange(RistrettoSRA.getGenesisDeck(), 51 * CARD_BYTES,
                52 * CARD_BYTES);
        byte[] lockedPocket = RistrettoSRA.applyCommutativeLock(card, pocketLock);

        assertEquals(-1, RistrettoSRA.resolveCardIndex(
                RistrettoSRA.applyCommutativeLock(lockedPocket, communityUnlock)));
        assertEquals(51, RistrettoSRA.resolveCardIndex(
                RistrettoSRA.applyCommutativeLock(lockedPocket, pocketUnlock)));
    }

    private static String[] potCardsWire(byte[] handId, int[][] cards, byte[][] keys) {
        List<String> fields = new ArrayList<>();
        fields.add("GAME");
        fields.add("77");
        fields.add("POTCARDS");
        for (int player = 0; player < PLAYERS; player++) {
            String nick = NICKS.get(player);
            byte[] signature = signer(player).signShowdownReveal(handId, nick, keys[player],
                    cards[player][0], cards[player][1]);
            fields.add(Base64.getEncoder().encodeToString(nick.getBytes(StandardCharsets.UTF_8)));
            fields.add(Card.shortStringFromIndex(cards[player][0]));
            fields.add(Card.shortStringFromIndex(cards[player][1]));
            fields.add(Base64.getEncoder().encodeToString(keys[player]));
            fields.add(Base64.getEncoder().encodeToString(signature));
        }
        return fields.toArray(String[]::new);
    }

    private static boolean verifyEntry(byte[] handId, PotCardsEnvelope.Entry entry,
            byte[] signerKey, byte[] firstResidual, byte[] secondResidual) {
        if (!IdentityManager.verifyShowdownReveal(signerKey, handId, entry.nick(),
                entry.pocketKey(), entry.firstCard(), entry.secondCard(), entry.signature())) {
            return false;
        }
        int first = RistrettoSRA.resolveCardIndex(
                RistrettoSRA.applyCommutativeLock(firstResidual, entry.pocketKey()));
        int second = RistrettoSRA.resolveCardIndex(
                RistrettoSRA.applyCommutativeLock(secondResidual, entry.pocketKey()));
        return first == entry.firstCard() && second == entry.secondCard();
    }

    private static IdentityManager signer(int player) {
        return player == 1 ? clientIdentity : hostIdentity;
    }

    private static byte[] signerKey(int player) {
        return signer(player).getPublicKey();
    }

    private static boolean resolvedPocketCard(int[][] cards, int candidate) {
        for (int[] pair : cards) {
            if (pair[0] == candidate || pair[1] == candidate) {
                return true;
            }
        }
        return false;
    }

    private static byte[] scalar(int value) {
        byte[] scalar = new byte[32];
        scalar[0] = (byte) value;
        assertTrue(RistrettoSRA.isValidScalar(scalar));
        return scalar;
    }
}
