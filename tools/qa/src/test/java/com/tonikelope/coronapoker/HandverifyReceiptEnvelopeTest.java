package com.tonikelope.coronapoker;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

public class HandverifyReceiptEnvelopeTest {

    @Test
    public void exactCurrentReceiptWireParsesOnce() {
        byte[] receipt = new byte[HandverifyReceiptEnvelope.RECEIPT_BYTES];
        receipt[0] = 7;
        String[] wire = {"GAME", "9", "HANDVERIFY", b64("alice"),
            Base64.getEncoder().encodeToString(receipt)};

        HandverifyReceiptEnvelope parsed = HandverifyReceiptEnvelope.parse(wire);
        assertEquals("alice", parsed.nick());
        assertArrayEquals(receipt, parsed.receipt());
    }

    @Test
    public void trailingFieldsWrongLengthAndNonCanonicalNickAreRejected() {
        byte[] receipt = new byte[HandverifyReceiptEnvelope.RECEIPT_BYTES];
        String receiptB64 = Base64.getEncoder().encodeToString(receipt);

        assertThrows(IllegalArgumentException.class, () -> HandverifyReceiptEnvelope.parse(
                new String[]{"GAME", "9", "HANDVERIFY", b64("alice"), receiptB64, "extra"}));
        assertThrows(IllegalArgumentException.class, () -> HandverifyReceiptEnvelope.parse(
                new String[]{"GAME", "9", "HANDVERIFY", b64("alice"),
                    Base64.getEncoder().encodeToString(new byte[112])}));
        assertThrows(IllegalArgumentException.class, () -> HandverifyReceiptEnvelope.parse(
                new String[]{"GAME", "9", "HANDVERIFY", b64("e\u0301"), receiptB64}));
        assertThrows(IllegalArgumentException.class, () -> HandverifyReceiptEnvelope.parse(
                new String[]{"GAME", "9", "HANDVERIFY", null, receiptB64}));
        assertThrows(IllegalArgumentException.class, () -> HandverifyReceiptEnvelope.parse(
                new String[]{"GAME", "9", "HANDVERIFY", b64("alice"),
                    receiptB64.substring(0, receiptB64.length() - 1)}));

        receipt[CanonicalActionRecord.HAND_ID_BYTES + 32] = (byte) 0x80;
        assertThrows(IllegalArgumentException.class, () -> HandverifyReceiptEnvelope.parse(
                new String[]{"GAME", "9", "HANDVERIFY", b64("alice"),
                    Base64.getEncoder().encodeToString(receipt)}));
    }

    private static String b64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
