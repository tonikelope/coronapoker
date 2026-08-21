/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.crypto.RistrettoSRA;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Base64;

/** Strict current formats for a client EXIT request and the host EXIT relay. */
final class PlayerExitWire {

    static final class Command {
        private final String nick;
        private final String testamentWire;
        private final byte[] testament;

        Command(String nick, String testamentWire, byte[] testament) {
            this.nick = nick;
            this.testamentWire = testamentWire;
            this.testament = testament == null ? null : testament.clone();
        }

        String nick() {
            return nick;
        }

        boolean hasTestament() {
            return testament != null;
        }

        String testamentWire() {
            return testamentWire;
        }

        byte[] testament() {
            return testament == null ? null : testament.clone();
        }
    }

    private PlayerExitWire() {
    }

    static Command parseClientRequest(String[] parts, String authenticatedNick) {
        requireBase(parts);
        if (parts.length != 4 || authenticatedNick == null || authenticatedNick.isEmpty()) {
            throw new IllegalArgumentException("client EXIT requires one authenticated testament field");
        }
        if ("*".equals(parts[3])) {
            return new Command(authenticatedNick, null, null);
        }
        byte[] testament = decodeTestament(parts[3]);
        return new Command(authenticatedNick, parts[3], testament);
    }

    static Command parseHostRelay(String[] parts) {
        requireBase(parts);
        if (parts.length != 4 && parts.length != 5) {
            throw new IllegalArgumentException("host EXIT relay requires nick and optional testament");
        }
        String nick = decodeCanonicalNick(parts[3]);
        if (parts.length == 4) {
            return new Command(nick, null, null);
        }
        byte[] testament = decodeTestament(parts[4]);
        return new Command(nick, parts[4], testament);
    }

    private static void requireBase(String[] parts) {
        if (parts == null || parts.length < 3
                || !"GAME".equals(parts[0]) || !"EXIT".equals(parts[2])) {
            throw new IllegalArgumentException("invalid EXIT frame");
        }
    }

    private static String decodeCanonicalNick(String encoded) {
        try {
            byte[] bytes = decodeCanonicalBase64(encoded);
            String nick = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            if (nick.isEmpty() || !Normalizer.normalize(nick, Normalizer.Form.NFC).equals(nick)) {
                throw new IllegalArgumentException("EXIT nick is empty or non-canonical");
            }
            return nick;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid EXIT nick", ex);
        }
    }

    private static byte[] decodeTestament(String encoded) {
        byte[] testament = decodeCanonicalBase64(encoded);
        if (!RistrettoSRA.isValidScalar(testament)) {
            throw new IllegalArgumentException("EXIT testament is not a usable scalar");
        }
        return testament;
    }

    private static byte[] decodeCanonicalBase64(String encoded) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            if (!Base64.getEncoder().encodeToString(decoded).equals(encoded)) {
                throw new IllegalArgumentException("non-canonical EXIT Base64");
            }
            return decoded;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid EXIT Base64", ex);
        }
    }
}
