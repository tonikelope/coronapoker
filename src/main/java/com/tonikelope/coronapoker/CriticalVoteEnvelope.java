/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Base64;

/** Strict parser for the current RIT and voluntary-straddle decision wires. */
public final class CriticalVoteEnvelope {

    private static final int MAX_NICK_BYTES = 256;
    private static final int SIGNATURE_BYTES = 64;

    private final String nick;
    private final int decision;
    private final byte[] signature;

    private CriticalVoteEnvelope(String nick, int decision, byte[] signature) {
        this.nick = nick;
        this.decision = decision;
        this.signature = signature == null ? null : signature.clone();
    }

    public String nick() {
        return nick;
    }

    public int decision() {
        return decision;
    }

    public byte[] signature() {
        return signature == null ? null : signature.clone();
    }

    public static CriticalVoteEnvelope parseRitResponse(String[] fields) {
        return parse(fields, "RIT_VOTE_RESP", 5, false);
    }

    public static CriticalVoteEnvelope parseStraddleResponse(String[] fields) {
        return parse(fields, "STRADDLE_RESP", 6, true);
    }

    public static CriticalVoteEnvelope parseStraddleDecision(String[] fields) {
        return parse(fields, "STRADDLE_DECISION", 6, true);
    }

    private static CriticalVoteEnvelope parse(String[] fields, String command,
            int expectedFields, boolean signed) {
        if (fields == null || fields.length != expectedFields
                || !"GAME".equals(fields[0]) || !command.equals(fields[2])) {
            throw new IllegalArgumentException(command + " has invalid arity or command");
        }
        final int gameId;
        try {
            gameId = Integer.parseInt(fields[1]);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("invalid GAME id", ex);
        }
        if (!String.valueOf(gameId).equals(fields[1])) {
            throw new IllegalArgumentException("non-canonical GAME id");
        }

        String nick = decodeCanonicalNick(fields[3]);
        final int decision;
        try {
            decision = Integer.parseInt(fields[4]);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("invalid decision", ex);
        }
        if ((decision != 0 && decision != 1)
                || !String.valueOf(decision).equals(fields[4])) {
            throw new IllegalArgumentException("decision must be canonical 0 or 1");
        }

        byte[] signature = null;
        if (signed) {
            signature = decodeCanonicalBase64(fields[5], command + " signature");
            if (signature.length != SIGNATURE_BYTES) {
                throw new IllegalArgumentException("invalid " + command + " signature length");
            }
        }
        return new CriticalVoteEnvelope(nick, decision, signature);
    }

    private static String decodeCanonicalNick(String encoded) {
        byte[] nickBytes = decodeCanonicalBase64(encoded, "nick");
        if (nickBytes.length == 0 || nickBytes.length > MAX_NICK_BYTES) {
            throw new IllegalArgumentException("invalid nick length");
        }
        final String nick;
        try {
            nick = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(nickBytes)).toString();
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException("nick is not UTF-8", ex);
        }
        if (!nick.equals(Normalizer.normalize(nick, Normalizer.Form.NFC))) {
            throw new IllegalArgumentException("nick is not NFC");
        }
        return nick;
    }

    private static byte[] decodeCanonicalBase64(String encoded, String label) {
        if (encoded == null) {
            throw new IllegalArgumentException(label + " is missing");
        }
        final byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid " + label + " Base64", ex);
        }
        if (!Base64.getEncoder().encodeToString(decoded).equals(encoded)) {
            throw new IllegalArgumentException("non-canonical " + label + " Base64");
        }
        return decoded;
    }
}
