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
    private final double requestedAmount;
    private final int hand;

    private EndOfHandRebuyWire(String nick, double requestedAmount, int hand) {
        this.nick = nick;
        this.requestedAmount = requestedAmount;
        this.hand = hand;
    }

    static EndOfHandRebuyWire parse(String[] parts, Collection<String> allowedNicks) {
        if (parts == null || parts.length != 6 || !"GAME".equals(parts[0]) || !"REBUY".equals(parts[2])) {
            throw new IllegalArgumentException("REBUY requires a player, an amount, and a hand");
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
        if (!parts[5].matches("[1-9][0-9]{0,9}")) {
            throw new IllegalArgumentException("invalid REBUY hand");
        }
        return new EndOfHandRebuyWire(nick, ImmediateRebuyWire.parseAmount(parts[4]),
                Integer.parseInt(parts[5]));
    }

    int hand() { return hand; }

    String nick() {
        return nick;
    }

    double requestedAmount() {
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
