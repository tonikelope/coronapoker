/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;

/** Strict decoder for the single current BUYIN wire format. */
final class InitialBuyinWire {

    private final String nick;
    private final int requestedAmount;

    private InitialBuyinWire(String nick, int requestedAmount) {
        this.nick = nick;
        this.requestedAmount = requestedAmount;
    }

    static InitialBuyinWire parse(String[] parts, Collection<String> expectedNicks) {
        if (parts == null || parts.length != 5 || !"BUYIN".equals(parts[2])) {
            throw new IllegalArgumentException("BUYIN requires exactly five fields");
        }
        if (expectedNicks == null) {
            throw new IllegalArgumentException("expected BUYIN players are required");
        }
        byte[] nickBytes;
        try {
            nickBytes = Base64.getDecoder().decode(parts[3]);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid BUYIN nick encoding", ex);
        }
        if (!Base64.getEncoder().encodeToString(nickBytes).equals(parts[3])) {
            throw new IllegalArgumentException("non-canonical BUYIN nick encoding");
        }
        String nick = decodeStrictUtf8(nickBytes);
        if (nick.isEmpty() || !expectedNicks.contains(nick)) {
            throw new IllegalArgumentException("unexpected BUYIN player");
        }
        final int requestedAmount;
        try {
            requestedAmount = Integer.parseInt(parts[4]);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("invalid BUYIN amount", ex);
        }
        return new InitialBuyinWire(nick, requestedAmount);
    }

    String nick() {
        return nick;
    }

    int requestedAmount() {
        return requestedAmount;
    }

    private static String decodeStrictUtf8(byte[] value) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value)).toString();
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException("invalid BUYIN nick UTF-8", ex);
        }
    }
}
