package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.crypto.RistrettoSRA;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

public class PocketCardEnvelopeValidationTest {

    private static String b64(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    private static String b64(String value) {
        return b64(value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] twoCanonicalPoints() {
        return java.util.Arrays.copyOf(RistrettoSRA.getGenesisDeck(), 64);
    }

    private static String[] pocketWire(String nick, byte[] residue) {
        return new String[]{"GAME", "9", "POCKET_CARDS", b64(nick), b64(residue)};
    }

    @Test
    public void exactCurrentPocketEnvelopeIsAccepted() {
        byte[] residue = twoCanonicalPoints();
        Crupier.ParsedPocketCards parsed = Crupier.parsePocketCardsWire(
                pocketWire("alice", residue), new String[]{"alice", "bob"});
        assertEquals("alice", parsed.targetNick);
        assertArrayEquals(residue, parsed.residue);
    }

    @Test
    public void foreignShortOrInvalidPocketIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Crupier.parsePocketCardsWire(
                pocketWire("mallory", twoCanonicalPoints()), new String[]{"alice", "bob"}));
        assertThrows(IllegalArgumentException.class, () -> Crupier.parsePocketCardsWire(
                pocketWire("alice", new byte[32]), new String[]{"alice", "bob"}));
        assertThrows(IllegalArgumentException.class, () -> Crupier.parsePocketCardsWire(
                pocketWire("alice", new byte[64]), new String[]{"alice", "bob"}));
    }

    @Test
    public void aSecondDeliveryForTheSameSeatCannotOverwriteTheFirst() {
        ConcurrentHashMap<String, byte[]> installed = new ConcurrentHashMap<>();
        Crupier.ParsedPocketCards parsed = Crupier.parsePocketCardsWire(
                pocketWire("alice", twoCanonicalPoints()), new String[]{"alice", "bob"});
        Crupier.installPocketCardsOnce(installed, parsed);
        assertThrows(IllegalArgumentException.class,
                () -> Crupier.installPocketCardsOnce(installed, parsed));
        assertArrayEquals(parsed.residue, installed.get("alice"));
    }

    @Test
    public void deferredMarkerMustTargetThisClientInTheCurrentRing() {
        String[] valid = {"GAME", "10", "POCKET_DEFERRED", b64("alice")};
        assertEquals("alice", Crupier.parsePocketDeferredWire(
                valid, new String[]{"alice", "bob"}, "alice"));
        assertThrows(IllegalArgumentException.class, () -> Crupier.parsePocketDeferredWire(
                valid, new String[]{"alice", "bob"}, "bob"));

        AtomicBoolean accepted = new AtomicBoolean();
        Crupier.acceptPocketDeferredOnce(accepted);
        assertThrows(IllegalArgumentException.class,
                () -> Crupier.acceptPocketDeferredOnce(accepted));
    }
}
