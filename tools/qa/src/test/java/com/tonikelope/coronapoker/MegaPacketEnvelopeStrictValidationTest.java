package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.crypto.RistrettoSRA;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class MegaPacketEnvelopeStrictValidationTest {

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new java.security.SecureRandom();
        }
    }

    private static String b64(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    private static String b64(String value) {
        return b64(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String commitmentEntry(String nick) {
        return b64(nick) + ":" + b64(RistrettoSRA.commitment(RistrettoSRA.generateLockScalar()))
                + ":" + b64(RistrettoSRA.commitment(RistrettoSRA.generateLockScalar()));
    }

    private static String[] validWire() {
        byte[] handId = new byte[CanonicalActionRecord.HAND_ID_BYTES];
        java.util.Arrays.fill(handId, (byte) 7);
        String encodedRing = b64("alice") + "," + b64("bob") + ",";
        return new String[]{
            "GAME", "1", "MEGAPACKET",
            b64(encodedRing),
            b64(RistrettoSRA.getGenesisDeck()),
            b64(handId),
            commitmentEntry("alice") + ";" + commitmentEntry("bob")
        };
    }

    @Test
    public void exactCurrentEnvelopeParsesAsOneAtomicValue() {
        String[] wire = validWire();
        Crupier.ParsedMegaPacket parsed = Crupier.parseMegaPacketWire(wire);
        assertArrayEquals(new String[]{"alice", "bob"}, parsed.ring);
        assertEquals(1664, parsed.deck.length);
        assertEquals(2, parsed.pocketCommitments.size());
        assertEquals(2, parsed.communityCommitments.size());
    }

    @Test
    public void missingCommitmentIsRejected() {
        String[] wire = validWire();
        wire[6] = commitmentEntry("alice");
        assertThrows(IllegalArgumentException.class, () -> Crupier.parseMegaPacketWire(wire));
    }

    @Test
    public void duplicateRingNickIsRejected() {
        String[] wire = validWire();
        wire[3] = b64(b64("alice") + "," + b64("alice") + ",");
        assertThrows(IllegalArgumentException.class, () -> Crupier.parseMegaPacketWire(wire));
    }

    @Test
    public void wrongHandIdOrDeckLengthIsRejected() {
        String[] shortHand = validWire();
        shortHand[5] = b64(new byte[CanonicalActionRecord.HAND_ID_BYTES - 1]);
        assertThrows(IllegalArgumentException.class, () -> Crupier.parseMegaPacketWire(shortHand));

        String[] shortDeck = validWire();
        shortDeck[4] = b64(new byte[32]);
        assertThrows(IllegalArgumentException.class, () -> Crupier.parseMegaPacketWire(shortDeck));
    }

    @Test
    public void aSecondValidMegaPacketCannotReplaceTheFirst() {
        Crupier.ParsedMegaPacket parsed = Crupier.parseMegaPacketWire(validWire());
        AtomicReference<Crupier.ParsedMegaPacket> accepted = new AtomicReference<>();
        Crupier.acceptMegaPacketOnce(accepted, parsed);
        assertThrows(IllegalArgumentException.class,
                () -> Crupier.acceptMegaPacketOnce(accepted, parsed));
    }
}
