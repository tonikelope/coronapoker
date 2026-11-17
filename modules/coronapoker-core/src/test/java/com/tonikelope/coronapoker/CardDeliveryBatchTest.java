package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.network.CardDeliveryBatch;
import com.tonikelope.coronapoker.core.network.CardDeliveryBatch.Phase;
import com.tonikelope.coronapoker.core.network.ConfirmationTracker;
import com.tonikelope.coronapoker.core.network.GameCommandGate;
import com.tonikelope.coronapoker.core.network.GameCommandType.Direction;
import com.tonikelope.coronapoker.crypto.RistrettoSRA;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class CardDeliveryBatchTest {
    private static final byte[] HAND = new byte[CanonicalActionRecord.HAND_ID_BYTES];
    private static final byte[] POCKET = Arrays.copyOf(RistrettoSRA.getGenesisDeck(), 64);

    @ParameterizedTest
    @ValueSource(ints = {2, 6, 10})
    void deliversOneCompleteOrderedBatchWithoutChangingResidues(int seats) {
        Map<String, byte[]> original = new LinkedHashMap<>();
        for (int seat = 0; seat < seats; seat++) {
            original.put("Jugadór" + seat, POCKET);
        }
        String body = CardDeliveryBatch.encode(HAND, Phase.POCKET_CARDS, original);
        Map<String, byte[]> parsed = Crupier.checkedCardDelivery(CardDeliveryBatch.decode(body),
                HAND, Phase.POCKET_CARDS, original.keySet());
        assertIterableEquals(original.keySet(), parsed.keySet());
        parsed.values().forEach(residue -> assertArrayEquals(POCKET, residue));
    }

    @Test
    void rejectsTruncationDuplicatesAndOversizedBatches() {
        String body = CardDeliveryBatch.encode(HAND, Phase.POCKET_CARDS, Map.of("Alice", POCKET));
        String entry = body.substring(body.indexOf('#', body.indexOf('#',
                body.indexOf('#') + 1) + 1));
        assertThrows(IllegalArgumentException.class, () -> CardDeliveryBatch.decode(body + entry));
        assertThrows(IllegalArgumentException.class,
                () -> CardDeliveryBatch.decode(body.substring(0, body.lastIndexOf('#'))));
        assertThrows(IllegalArgumentException.class, () -> CardDeliveryBatch.decode(body + entry.repeat(10)));
        assertThrows(IllegalArgumentException.class, () -> CardDeliveryBatch.decode(body + "A".repeat(5120)));
    }

    @Test
    void rejectsWrongHandStreetAndIncompleteOrSubstitutedRoster() {
        byte[] flop = Arrays.copyOf(RistrettoSRA.getGenesisDeck(), 96);
        CardDeliveryBatch batch = CardDeliveryBatch.decode(CardDeliveryBatch.encode(HAND,
                Phase.FLOP_PIECE, Map.of("Alice", flop, "Bob", flop)));
        Set<String> recipients = Set.of("Alice", "Bob");
        byte[] nextHand = HAND.clone();
        nextHand[0] = 1;
        assertThrows(IllegalArgumentException.class, () -> batch.requireDelivery(nextHand, Phase.FLOP_PIECE, recipients));
        assertThrows(IllegalArgumentException.class, () -> batch.requireDelivery(HAND, Phase.RIT2_FLOP_PIECE, recipients));
        assertThrows(IllegalArgumentException.class, () -> batch.requireDelivery(HAND, Phase.FLOP_PIECE, Set.of("Alice", "Bob", "Carlos")));
        assertThrows(IllegalArgumentException.class, () -> batch.requireDelivery(HAND, Phase.FLOP_PIECE, Set.of("Alice", "Carlos")));
    }

    @Test
    void invalidFinalPointRejectsTheEntireBatchBeforeExposingItsEntries() {
        Map<String, byte[]> residues = new LinkedHashMap<>();
        residues.put("Alice", POCKET);
        byte[] corrupt = POCKET.clone();
        Arrays.fill(corrupt, 32, 64, (byte) 0xff);
        residues.put("Bob", corrupt);
        CardDeliveryBatch batch = CardDeliveryBatch.decode(CardDeliveryBatch.encode(HAND,
                Phase.POCKET_CARDS, residues));
        assertThrows(IllegalArgumentException.class, () -> Crupier.checkedCardDelivery(batch,
                HAND, Phase.POCKET_CARDS, residues.keySet()));
    }

    @Test
    void retriesAcknowledgeTheSameBatchWithoutReapplyingAndRejectConflictingIds() {
        String body = CardDeliveryBatch.encode(HAND, Phase.POCKET_CARDS, Map.of("Alice", POCKET));
        String frame = "GAME#42#" + body;
        GameCommandGate receiver = new GameCommandGate(Direction.HOST_TO_CLIENT);
        assertTrue(receiver.accept(CardDeliveryBatch.COMMAND, 42, frame).enqueue());
        GameCommandGate.Decision retry = receiver.accept(CardDeliveryBatch.COMMAND, 42, frame);
        assertTrue(retry.acknowledge());
        assertFalse(retry.enqueue());
        assertTrue(receiver.accept(CardDeliveryBatch.COMMAND, 42, frame + "#more").closeConnection());
        assertTrue(new GameCommandGate(Direction.CLIENT_TO_HOST)
                .accept(CardDeliveryBatch.COMMAND, 42, frame).closeConnection());
    }

    @Test
    void onePeerCannotConfirmForAnotherAndDisconnectCannotCompleteTheBatch() {
        ConfirmationTracker tracker = new ConfirmationTracker();
        ConfirmationTracker.Request delivery = tracker.register(43, List.of("Alice", "Bob"));
        assertFalse(tracker.confirm("Mallory", 43));
        assertFalse(tracker.confirm("Bob", 42));
        assertTrue(tracker.confirm("Alice", 43));
        assertFalse(tracker.confirm("Alice", 43));
        assertFalse(tracker.wasFullyConfirmed(delivery));
        tracker.cancelPeer(delivery, "Bob");
        assertFalse(tracker.wasFullyConfirmed(delivery));
        tracker.close(delivery);

        ConfirmationTracker.Request retransmission = tracker.register(44, List.of("Alice", "Bob"));
        assertTrue(tracker.confirm("Bob", 44));
        assertTrue(tracker.confirm("Alice", 44));
        assertTrue(tracker.wasFullyConfirmed(retransmission));
    }
}
