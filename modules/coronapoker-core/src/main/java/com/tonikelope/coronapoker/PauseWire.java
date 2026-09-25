/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Base64;

/** Strict decoder for the single current pause request and relay formats. */
final class PauseWire {

    private static final int MAX_NICK_BYTES = 256;

    record Relay(boolean paused, String owner) {
    }

    private PauseWire() {
    }

    static boolean parseClientRequest(String[] fields) {
        requireEnvelope(fields, 4);
        return parseState(fields[3]);
    }

    static Relay parseHostRelay(String[] fields) {
        requireEnvelope(fields, 5);
        return new Relay(parseState(fields[3]), canonicalNick(fields[4]));
    }

    private static void requireEnvelope(String[] fields, int arity) {
        if (fields == null || fields.length != arity
                || !"GAME".equals(fields[0]) || !"PAUSE".equals(fields[2])) {
            throw new IllegalArgumentException("PAUSE has invalid arity or command");
        }
        final int id;
        try {
            id = Integer.parseInt(fields[1]);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("invalid GAME id", ex);
        }
        if (id < 0 || !Integer.toString(id).equals(fields[1])) {
            throw new IllegalArgumentException("non-canonical GAME id");
        }
    }

    private static boolean parseState(String raw) {
        if ("1".equals(raw)) {
            return true;
        }
        if ("0".equals(raw)) {
            return false;
        }
        throw new IllegalArgumentException("PAUSE state must be 0 or 1");
    }

    private static String canonicalNick(String encoded) {
        final byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("invalid PAUSE owner Base64", ex);
        }
        if (bytes.length == 0 || bytes.length > MAX_NICK_BYTES
                || !Base64.getEncoder().encodeToString(bytes).equals(encoded)) {
            throw new IllegalArgumentException("non-canonical PAUSE owner");
        }
        final String nick;
        try {
            nick = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException("PAUSE owner is not UTF-8", ex);
        }
        if (!nick.equals(Normalizer.normalize(nick, Normalizer.Form.NFC))) {
            throw new IllegalArgumentException("PAUSE owner is not NFC");
        }
        return nick;
    }
}
