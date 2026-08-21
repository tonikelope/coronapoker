package com.tonikelope.coronapoker.protocolsim;

import com.tonikelope.coronapoker.CanonicalActionRecord;
import com.tonikelope.coronapoker.GameCommandGate;
import com.tonikelope.coronapoker.GameCommandType;
import com.tonikelope.coronapoker.HandStateChain;
import com.tonikelope.coronapoker.IdentityManager;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deterministic fault-injection seam using the production replay gate. */
@Tag("slow")
@Tag("protocol-sim")
final class ProtocolFaultTransportTest {

    private static IdentityManager signer;
    private static byte[] signerKey;
    private static byte[] playerId;

    private record Event(int id, byte[] record, byte[] signature) {
        private String wire() {
            return "GAME#" + id + "#ACTION#"
                    + Base64.getEncoder().encodeToString(record) + "#"
                    + Base64.getEncoder().encodeToString(signature);
        }
    }

    private static final class Receiver {
        private final GameCommandGate gate = new GameCommandGate(
                GameCommandType.Direction.HOST_TO_CLIENT);
        private final HandStateChain chain;
        private boolean connected = true;
        private int processed;

        private Receiver(HandStateChain chain) {
            this.chain = chain;
        }

        private void receive(Event event) {
            if (!connected) {
                return;
            }
            GameCommandGate.Decision decision = gate.accept("ACTION", event.id(), event.wire());
            if (decision.closeConnection()) {
                connected = false;
                return;
            }
            if (!decision.enqueue()) {
                return;
            }
            if (!IdentityManager.verifyAction(signerKey, event.record(), event.signature())) {
                connected = false;
                return;
            }
            try {
                chain.absorb(event.record(), event.signature());
                processed++;
            } catch (IllegalArgumentException | IllegalStateException ex) {
                connected = false;
            }
        }
    }

    @BeforeAll
    static void identity() {
        signer = IdentityManager.initializeForNick("sim-transport-host");
        assertTrue(signer.isReady(), signer.getLoadError());
        signerKey = signer.getPublicKey();
        playerId = CanonicalActionRecord.playerIdFromNick("sim-transport-host");
    }

    @Test
    void exactRetransmissionIsProcessedExactlyOnce() {
        HandStateChain genesis = chain((byte) 1);
        Receiver receiver = new Receiver(chain((byte) 1));
        Event event = event(7, genesis, CanonicalActionRecord.ACTION_CHECK, 0L);

        receiver.receive(event);
        byte[] afterFirst = receiver.chain.getCurrentHash();
        receiver.receive(event);

        assertTrue(receiver.connected);
        assertEquals(1, receiver.processed);
        assertArrayEquals(afterFirst, receiver.chain.getCurrentHash());
        assertEquals(1, receiver.gate.dedupSize());
    }

    @Test
    void sameCommandIdWithDifferentAuthenticatedBytesCloses() {
        HandStateChain producer = chain((byte) 2);
        Receiver receiver = new Receiver(chain((byte) 2));
        Event original = event(11, producer, CanonicalActionRecord.ACTION_CHECK, 0L);
        receiver.receive(original);
        byte[] acceptedHash = receiver.chain.getCurrentHash();

        Event collision = event(11, producer, CanonicalActionRecord.ACTION_BET, 40L);
        receiver.receive(collision);

        assertFalse(receiver.connected);
        assertEquals(1, receiver.processed);
        assertArrayEquals(acceptedHash, receiver.chain.getCurrentHash());
    }

    @Test
    void tamperedSignedActionClosesBeforeChainMutation() {
        HandStateChain producer = chain((byte) 3);
        Receiver receiver = new Receiver(chain((byte) 3));
        byte[] initial = receiver.chain.getCurrentHash();
        Event valid = event(13, producer, CanonicalActionRecord.ACTION_BET, 40L);
        byte[] tamperedRecord = valid.record().clone();
        tamperedRecord[CanonicalActionRecord.OFFSET_AMOUNT_CENTS + 7] ^= 1;

        receiver.receive(new Event(valid.id(), tamperedRecord, valid.signature()));

        assertFalse(receiver.connected);
        assertEquals(0, receiver.processed);
        assertArrayEquals(initial, receiver.chain.getCurrentHash());
    }

    @Test
    void reorderedValidActionClosesOnStalePreviousHash() {
        HandStateChain producer = chain((byte) 4);
        Event first = event(17, producer, CanonicalActionRecord.ACTION_BET, 40L);
        producer.absorb(first.record(), first.signature());
        Event second = event(18, producer, CanonicalActionRecord.ACTION_CHECK, 40L);

        Receiver receiver = new Receiver(chain((byte) 4));
        byte[] initial = receiver.chain.getCurrentHash();
        receiver.receive(second);

        assertFalse(receiver.connected);
        assertEquals(0, receiver.processed);
        assertArrayEquals(initial, receiver.chain.getCurrentHash());
    }

    private static Event event(int id, HandStateChain producer, int action, long cents) {
        byte[] record = CanonicalActionRecord.encode(producer.getCurrentHash(),
                producer.getHandId(), playerId, CanonicalActionRecord.STREET_PREFLOP,
                action, cents, false, true);
        return new Event(id, record, signer.signAction(record));
    }

    private static HandStateChain chain(byte marker) {
        byte[] handId = fill(16, marker);
        byte[] pocket = fill(32, (byte) (marker + 1));
        byte[] community = fill(32, (byte) (marker + 2));
        byte[] deck = fill(52 * 32, (byte) (marker + 3));
        return HandStateChain.start(handId, List.of(playerId), List.of(pocket),
                List.of(community), deck);
    }

    private static byte[] fill(int size, byte value) {
        byte[] bytes = new byte[size];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }
}
