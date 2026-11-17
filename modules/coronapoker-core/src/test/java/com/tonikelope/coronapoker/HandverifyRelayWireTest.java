package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.identity.GameIdentityProtocol;
import com.tonikelope.coronapoker.core.network.GameCommandGate;
import com.tonikelope.coronapoker.core.network.GameCommandType.Direction;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandverifyRelayWireTest {
    private static final byte[] HAND = new byte[CanonicalActionRecord.HAND_ID_BYTES];
    private static final byte[] HASH = new byte[32];

    @Test
    void eachRecipientGetsTheOtherOriginalSignedReceiptsInOrder() throws Exception {
        var alice = sign("Alice", key(), HAND, false);
        var bob = sign("Bob", key(), HAND, false);
        var carlos = sign("Carlos", key(), HAND, false);

        var relayed = HandverifyRelayWire.decode(HandverifyRelayWire.encodeFor("Bob", List.of(alice, bob, carlos)));
        assertEquals(List.of("Alice", "Carlos"), relayed.stream().map(HandverifyReceiptEnvelope::nick).toList());
        assertArrayEquals(alice.receipt(), relayed.get(0).receipt());
        assertArrayEquals(carlos.receipt(), relayed.get(1).receipt());
        assertNull(HandverifyRelayWire.encodeFor("Bob", List.of(bob)));
    }

    @Test
    void anInvalidLastSignatureCannotInstallAnyPartOfTheRelay() throws Exception {
        KeyPair aliceKey = key();
        KeyPair bobKey = key();
        var alice = sign("Alice", aliceKey, HAND, false);
        var bob = sign("Bob", bobKey, HAND, true);
        Predicate<HandverifyReceiptEnvelope> signatures = verifyWith(Map.of("Alice", aliceKey, "Bob", bobKey));
        Map<String, byte[]> accepted = new LinkedHashMap<>();
        String batch = HandverifyRelayWire.encodeFor("Host", List.of(alice, bob));

        assertThrows(IllegalArgumentException.class, () -> accepted.putAll(HandverifyRelayWire.validate(
                batch, HAND, Set.of("Alice", "Bob"), accepted.keySet(), signatures)));
        assertTrue(accepted.isEmpty());

        String valid = HandverifyRelayWire.encodeFor("Host", List.of(alice, sign("Bob", bobKey, HAND, false)));
        accepted.putAll(HandverifyRelayWire.validate(valid, HAND, Set.of("Alice", "Bob"), accepted.keySet(), signatures));
        assertEquals(List.of("Alice", "Bob"), List.copyOf(accepted.keySet()));
    }

    @Test
    void rejectsStaleUnexpectedAndAlreadyAcceptedSigners() throws Exception {
        KeyPair key = key();
        var alice = sign("Alice", key, HAND, false);
        String body = HandverifyRelayWire.encodeFor("Bob", List.of(alice));
        var verify = verifyWith(Map.of("Alice", key));
        byte[] nextHand = HAND.clone();
        nextHand[0] = 1;
        assertThrows(IllegalArgumentException.class,
                () -> HandverifyRelayWire.validate(body, nextHand, Set.of("Alice"), Set.of(), verify));
        assertThrows(IllegalArgumentException.class,
                () -> HandverifyRelayWire.validate(body, HAND, Set.of("Bob"), Set.of(), verify));
        assertThrows(IllegalArgumentException.class,
                () -> HandverifyRelayWire.validate(body, HAND, Set.of("Alice"), Set.of("Alice"), verify));
    }

    @Test
    void rejectsTruncationAndDuplicateSignersAndRequiresAuthenticatedHostDirection() throws Exception {
        var alice = sign("Alice", key(), HAND, false);
        String body = HandverifyRelayWire.encodeFor("Bob", List.of(alice));
        String entry = body.substring(body.indexOf('#'));
        assertThrows(IllegalArgumentException.class, () -> HandverifyRelayWire.decode(body + entry));
        assertThrows(IllegalArgumentException.class, () -> HandverifyRelayWire.decode(body + entry.repeat(10)));
        assertThrows(IllegalArgumentException.class, () -> HandverifyRelayWire.decode(body.substring(0, body.lastIndexOf('#'))));

        String frame = "GAME#17#" + body;
        GameCommandGate client = new GameCommandGate(Direction.HOST_TO_CLIENT);
        assertTrue(client.accept(HandverifyRelayWire.COMMAND, 17, frame).enqueue());
        assertFalse(client.accept(HandverifyRelayWire.COMMAND, 17, frame).enqueue());
        assertTrue(new GameCommandGate(Direction.CLIENT_TO_HOST)
                .accept(HandverifyRelayWire.COMMAND, 17, frame).closeConnection());
    }

    private static Predicate<HandverifyReceiptEnvelope> verifyWith(Map<String, KeyPair> signers) {
        return receipt -> {
            byte[] encoded = signers.get(receipt.nick()).getPublic().getEncoded();
            byte[] rawKey = Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length);
            return GameIdentityProtocol.verifyReceipt(rawKey, receipt.handId(), receipt.finalHash(),
                    receipt.flags(), receipt.signature());
        };
    }

    private static KeyPair key() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static HandverifyReceiptEnvelope sign(String nick, KeyPair key, byte[] hand,
            boolean corrupt) {
        byte[] signature = GameIdentityProtocol.signReceipt(key.getPrivate(), hand, HASH, (byte) 0);
        if (corrupt) signature[0] ^= 1;
        byte[] bytes = ByteBuffer.allocate(HandverifyReceiptEnvelope.RECEIPT_BYTES)
                .put(hand).put(HASH).put((byte) 0).put(signature).array();
        Base64.Encoder base64 = Base64.getEncoder();
        return HandverifyReceiptEnvelope.parse(new String[]{"GAME", "0", "HANDVERIFY",
            base64.encodeToString(nick.getBytes(StandardCharsets.UTF_8)), base64.encodeToString(bytes)});
    }
}
