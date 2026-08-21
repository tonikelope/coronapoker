package com.tonikelope.coronapoker.protocolsim;

import com.tonikelope.coronapoker.CanonicalActionRecord;
import com.tonikelope.coronapoker.GameCommandGate;
import com.tonikelope.coronapoker.GameCommandType;
import com.tonikelope.coronapoker.HandStateChain;
import com.tonikelope.coronapoker.IdentityManager;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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

    private enum Fault {
        HONEST, EXACT_DUPLICATE, ID_COLLISION, SIGNED_MUTATION,
        REORDER, DISCONNECT, RATE_LIMIT, UNKNOWN_COMMAND
    }

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

        private void disconnect() {
            connected = false;
        }

        private void rejectForRateLimit() {
            connected = !gate.rejectForRateLimit("ACTION").closeConnection();
        }

        private void receiveUnknown(Event event) {
            GameCommandGate.Decision decision = gate.accept(
                    "UNKNOWN_CRITICAL", event.id(), event.wire());
            connected = !decision.closeConnection();
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

    @Test
    void seededRandomFaultCampaignNeverSilentlyContinuesACriticalStream() {
        int cases = positiveIntProperty("qa.sim.faults", 2_000, 100_000);
        long seed = longProperty("qa.sim.seed", 0xC0A0_2026L);
        Integer replayCase = optionalNonNegativeIntProperty(
                "qa.sim.fault.case", 100_000 - 1);
        if (replayCase != null) {
            runFaultCase(seed, replayCase);
            return;
        }
        for (int caseNumber = 0; caseNumber < cases; caseNumber++) {
            runFaultCase(seed, caseNumber);
        }
    }

    private static void runFaultCase(long campaignSeed, int caseNumber) {
        Random random = new Random(seedForCase(campaignSeed, caseNumber));
        byte marker = (byte) (1 + random.nextInt(254));
        HandStateChain producer = chain(marker);
        Receiver receiver = new Receiver(chain(marker));
        int eventCount = 2 + random.nextInt(7);
        List<Event> events = new ArrayList<>(eventCount);
        for (int i = 0; i < eventCount; i++) {
            int action = random.nextBoolean()
                    ? CanonicalActionRecord.ACTION_CHECK : CanonicalActionRecord.ACTION_BET;
            long cents = action == CanonicalActionRecord.ACTION_BET
                    ? (1L + random.nextInt(10_000)) : 0L;
            Event generated = event(1_000 + i, producer, action, cents);
            events.add(generated);
            producer.absorb(generated.record(), generated.signature());
        }

        Fault fault = Fault.values()[random.nextInt(Fault.values().length)];
        int faultAt = random.nextInt(eventCount);
        String context = "seed=" + campaignSeed + " fault_case=" + caseNumber
                + " fault=" + fault + " at=" + faultAt;

        for (int i = 0; i < faultAt; i++) {
            receiver.receive(events.get(i));
        }
        switch (fault) {
            case HONEST -> {
                for (int i = faultAt; i < events.size(); i++) {
                    receiver.receive(events.get(i));
                }
                assertTrue(receiver.connected, context);
                assertEquals(eventCount, receiver.processed, context);
                assertArrayEquals(producer.getCurrentHash(), receiver.chain.getCurrentHash(), context);
            }
            case EXACT_DUPLICATE -> {
                receiver.receive(events.get(faultAt));
                receiver.receive(events.get(faultAt));
                for (int i = faultAt + 1; i < events.size(); i++) {
                    receiver.receive(events.get(i));
                }
                assertTrue(receiver.connected, context);
                assertEquals(eventCount, receiver.processed, context);
                assertArrayEquals(producer.getCurrentHash(), receiver.chain.getCurrentHash(), context);
            }
            case ID_COLLISION -> {
                Event original = events.get(faultAt);
                receiver.receive(original);
                byte[] changedSignature = original.signature().clone();
                changedSignature[0] ^= 1;
                receiver.receive(new Event(original.id(), original.record(), changedSignature));
                assertClosedAt(receiver, events, faultAt + 1, marker, context);
            }
            case SIGNED_MUTATION -> {
                Event original = events.get(faultAt);
                byte[] changedRecord = original.record().clone();
                changedRecord[CanonicalActionRecord.OFFSET_AMOUNT_CENTS + 7] ^= 1;
                receiver.receive(new Event(original.id(), changedRecord, original.signature()));
                assertClosedAt(receiver, events, faultAt, marker, context);
            }
            case REORDER -> {
                int later = Math.min(faultAt + 1, events.size() - 1);
                if (later == faultAt) {
                    byte[] stale = events.get(faultAt).record().clone();
                    stale[CanonicalActionRecord.OFFSET_PREV_H] ^= 1;
                    byte[] signature = signer.signAction(stale);
                    receiver.receive(new Event(events.get(faultAt).id(), stale, signature));
                } else {
                    receiver.receive(events.get(later));
                }
                assertClosedAt(receiver, events, faultAt, marker, context);
            }
            case DISCONNECT -> {
                receiver.disconnect();
                assertClosedAt(receiver, events, faultAt, marker, context);
            }
            case RATE_LIMIT -> {
                receiver.rejectForRateLimit();
                assertClosedAt(receiver, events, faultAt, marker, context);
            }
            case UNKNOWN_COMMAND -> {
                receiver.receiveUnknown(events.get(faultAt));
                assertClosedAt(receiver, events, faultAt, marker, context);
            }
        }

        if (Boolean.parseBoolean(System.getProperty("qa.sim.trace", "false"))) {
            System.out.println("PROTOCOL_FAULT_SIM PASS " + context
                    + " processed=" + receiver.processed
                    + " connected=" + receiver.connected);
        }
    }

    private static void assertClosedAt(Receiver receiver, List<Event> events,
            int accepted, byte marker, String context) {
        assertFalse(receiver.connected, context);
        assertEquals(accepted, receiver.processed, context);
        HandStateChain expected = chain(marker);
        for (int i = 0; i < accepted; i++) {
            expected.absorb(events.get(i).record(), events.get(i).signature());
        }
        assertArrayEquals(expected.getCurrentHash(), receiver.chain.getCurrentHash(), context);
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

    private static int positiveIntProperty(String name, int defaultValue, int maximum) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank() || value.startsWith("${")) {
            return defaultValue;
        }
        int parsed = Integer.parseInt(value);
        if (parsed <= 0 || parsed > maximum) {
            throw new IllegalArgumentException(name + " must be in 1.." + maximum);
        }
        return parsed;
    }

    private static long longProperty(String name, long defaultValue) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() || value.startsWith("${")
                ? defaultValue : Long.parseLong(value);
    }

    private static Integer optionalNonNegativeIntProperty(String name, int maximum) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank() || value.startsWith("${")) {
            return null;
        }
        int parsed = Integer.parseInt(value);
        if (parsed < 0 || parsed > maximum) {
            throw new IllegalArgumentException(name + " must be in 0.." + maximum);
        }
        return parsed;
    }

    private static long seedForCase(long campaignSeed, int caseNumber) {
        long value = campaignSeed + 0x9E3779B97F4A7C15L * (caseNumber + 1L);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
