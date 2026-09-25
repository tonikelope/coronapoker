/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;

/** Strict decoder for the single current end-of-hand REBUY wire format. */
final class EndOfHandRebuyWire {

    private final String nick;
    private final int requestedAmount;

    private EndOfHandRebuyWire(String nick, int requestedAmount) {
        this.nick = nick;
        this.requestedAmount = requestedAmount;
    }

    static EndOfHandRebuyWire parse(String[] parts, Collection<String> allowedNicks) {
        if (parts == null || parts.length != 5 || !"REBUY".equals(parts[2])) {
            throw new IllegalArgumentException("REBUY requires exactly five fields");
        }
        if (allowedNicks == null) {
            throw new IllegalArgumentException("allowed REBUY players are required");
        }
        byte[] nickBytes;
        try {
            nickBytes = Base64.getDecoder().decode(parts[3]);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid REBUY nick encoding", ex);
        }
        if (!Base64.getEncoder().encodeToString(nickBytes).equals(parts[3])) {
            throw new IllegalArgumentException("non-canonical REBUY nick encoding");
        }
        String nick = decodeStrictUtf8(nickBytes);
        if (nick.isEmpty() || !allowedNicks.contains(nick)) {
            throw new IllegalArgumentException("unexpected REBUY player");
        }
        final int requestedAmount;
        try {
            requestedAmount = Integer.parseInt(parts[4]);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("invalid REBUY amount", ex);
        }
        return new EndOfHandRebuyWire(nick, requestedAmount);
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
            throw new IllegalArgumentException("invalid REBUY nick UTF-8", ex);
        }
    }
}
