/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Base64;

/** Strict decoder for the single current immediate-rebuy wire format. */
final class ImmediateRebuyWire {

    private static final int MAX_NICK_BYTES = 256;

    record Relay(String nick, int amount, boolean denied) {
    }

    private ImmediateRebuyWire() {
    }

    static int parseClientRequest(String[] fields) {
        requireEnvelope(fields, "REBUYNOW", 4);
        return canonicalNonnegativeInt(fields[3], "rebuy amount");
    }

    static Relay parseHostRelay(String[] fields) {
        if (fields == null || fields.length != 5 || !"GAME".equals(fields[0])) {
            throw new IllegalArgumentException("immediate rebuy relay has invalid arity");
        }
        canonicalNonnegativeInt(fields[1], "GAME id");
        boolean denied;
        if ("REBUYNOW".equals(fields[2])) {
            denied = false;
        } else if ("REBUYDENIED".equals(fields[2])) {
            denied = true;
        } else {
            throw new IllegalArgumentException("invalid immediate rebuy command");
        }
        String nick = canonicalNick(fields[3]);
        int amount = canonicalNonnegativeInt(fields[4], denied ? "rebuy limit" : "rebuy amount");
        if (denied && amount == 0) {
            throw new IllegalArgumentException("rebuy denial requires a positive limit");
        }
        return new Relay(nick, amount, denied);
    }

    private static void requireEnvelope(String[] fields, String command, int arity) {
        if (fields == null || fields.length != arity
                || !"GAME".equals(fields[0]) || !command.equals(fields[2])) {
            throw new IllegalArgumentException(command + " has invalid arity or command");
        }
        canonicalNonnegativeInt(fields[1], "GAME id");
    }

    private static int canonicalNonnegativeInt(String raw, String label) {
        final int value;
        try {
            value = Integer.parseInt(raw);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("invalid " + label, ex);
        }
        if (value < 0 || !Integer.toString(value).equals(raw)) {
            throw new IllegalArgumentException("non-canonical " + label);
        }
        return value;
    }

    private static String canonicalNick(String encoded) {
        final byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("invalid rebuy nick Base64", ex);
        }
        if (bytes.length == 0 || bytes.length > MAX_NICK_BYTES
                || !Base64.getEncoder().encodeToString(bytes).equals(encoded)) {
            throw new IllegalArgumentException("non-canonical rebuy nick");
        }
        final String nick;
        try {
            nick = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException("rebuy nick is not UTF-8", ex);
        }
        if (!nick.equals(Normalizer.normalize(nick, Normalizer.Form.NFC))) {
            throw new IllegalArgumentException("rebuy nick is not NFC");
        }
        return nick;
    }
}
