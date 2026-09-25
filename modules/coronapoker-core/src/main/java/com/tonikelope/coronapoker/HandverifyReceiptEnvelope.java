/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Base64;

/** Strict parser for the only supported HANDVERIFY receipt wire. */
public final class HandverifyReceiptEnvelope {

    public static final int RECEIPT_BYTES = CanonicalActionRecord.HAND_ID_BYTES + 32 + 1 + 64;
    private static final int MAX_NICK_BYTES = 256;

    private final String nick;
    private final byte[] receipt;

    private HandverifyReceiptEnvelope(String nick, byte[] receipt) {
        this.nick = nick;
        this.receipt = receipt.clone();
    }

    public String nick() { return nick; }
    public byte[] receipt() { return receipt.clone(); }
    public byte[] handId() {
        return Arrays.copyOfRange(receipt, 0, CanonicalActionRecord.HAND_ID_BYTES);
    }
    public byte[] finalHash() {
        return Arrays.copyOfRange(receipt, CanonicalActionRecord.HAND_ID_BYTES,
                CanonicalActionRecord.HAND_ID_BYTES + 32);
    }
    public byte flags() { return receipt[CanonicalActionRecord.HAND_ID_BYTES + 32]; }
    public byte[] signature() {
        return Arrays.copyOfRange(receipt,
                CanonicalActionRecord.HAND_ID_BYTES + 32 + 1, RECEIPT_BYTES);
    }

    public static HandverifyReceiptEnvelope parse(String[] wire) {
        if (wire == null || wire.length != 5
                || !"GAME".equals(wire[0]) || !"HANDVERIFY".equals(wire[2])
                || wire[3] == null || wire[4] == null
                || wire[3].length() > 344 || wire[4].length() != 152) {
            throw new IllegalArgumentException("HANDVERIFY receipt requires exactly five fields");
        }
        try {
            byte[] nickBytes = Base64.getDecoder().decode(wire[3]);
            if (!Base64.getEncoder().encodeToString(nickBytes).equals(wire[3])) {
                throw new IllegalArgumentException("non-canonical HANDVERIFY nick encoding");
            }
            String nick = decodeStrictUtf8(nickBytes);
            if (nick.isEmpty() || nick.getBytes(StandardCharsets.UTF_8).length > MAX_NICK_BYTES
                    || !nick.equals(Normalizer.normalize(nick, Normalizer.Form.NFC))) {
                throw new IllegalArgumentException("invalid HANDVERIFY nick");
            }
            byte[] receipt = Base64.getDecoder().decode(wire[4]);
            if (!Base64.getEncoder().encodeToString(receipt).equals(wire[4])) {
                throw new IllegalArgumentException("non-canonical HANDVERIFY receipt encoding");
            }
            if (receipt.length != RECEIPT_BYTES) {
                throw new IllegalArgumentException("invalid HANDVERIFY receipt length");
            }
            byte flags = receipt[CanonicalActionRecord.HAND_ID_BYTES + 32];
            if ((flags & ~0x07) != 0) {
                throw new IllegalArgumentException("unknown HANDVERIFY receipt flags");
            }
            return new HandverifyReceiptEnvelope(nick, receipt);
        } catch (IllegalArgumentException ex) {
            throw ex;
        }
    }

    private static String decodeStrictUtf8(byte[] value) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value)).toString();
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException("HANDVERIFY nick is not UTF-8", ex);
        }
    }
}
