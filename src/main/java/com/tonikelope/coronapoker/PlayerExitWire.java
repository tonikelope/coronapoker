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
        private final String pocketKeyWire;
        private final byte[] pocketKey;
        private final String pocketSignatureWire;
        private final byte[] pocketSignature;

        Command(String nick, String testamentWire, byte[] testament,
                String pocketKeyWire, byte[] pocketKey,
                String pocketSignatureWire, byte[] pocketSignature) {
            this.nick = nick;
            this.testamentWire = testamentWire;
            this.testament = testament == null ? null : testament.clone();
            this.pocketKeyWire = pocketKeyWire;
            this.pocketKey = pocketKey == null ? null : pocketKey.clone();
            this.pocketSignatureWire = pocketSignatureWire;
            this.pocketSignature = pocketSignature == null ? null : pocketSignature.clone();
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

        boolean hasPocketReveal() {
            return pocketKey != null;
        }

        String pocketKeyWire() {
            return pocketKeyWire;
        }

        byte[] pocketKey() {
            return pocketKey == null ? null : pocketKey.clone();
        }

        String pocketSignatureWire() {
            return pocketSignatureWire;
        }

        byte[] pocketSignature() {
            return pocketSignature == null ? null : pocketSignature.clone();
        }
    }

    private PlayerExitWire() {
    }

    static Command parseClientRequest(String[] parts, String authenticatedNick) {
        requireBase(parts);
        if (parts.length != 6 || authenticatedNick == null || authenticatedNick.isEmpty()) {
            throw new IllegalArgumentException(
                    "client EXIT requires community, pocket-key and pocket-signature fields");
        }
        return decode(authenticatedNick, parts[3], parts[4], parts[5]);
    }

    static Command parseHostRelay(String[] parts) {
        requireBase(parts);
        if (parts.length != 7) {
            throw new IllegalArgumentException(
                    "host EXIT relay requires nick, community, pocket-key and pocket-signature fields");
        }
        String nick = decodeCanonicalNick(parts[3]);
        return decode(nick, parts[4], parts[5], parts[6]);
    }

    private static Command decode(String nick, String testamentWire,
            String pocketKeyWire, String pocketSignatureWire) {
        byte[] testament = "*".equals(testamentWire) ? null : decodeTestament(testamentWire);
        boolean noPocketKey = "*".equals(pocketKeyWire);
        boolean noPocketSignature = "*".equals(pocketSignatureWire);
        if (noPocketKey != noPocketSignature) {
            throw new IllegalArgumentException("EXIT pocket key and signature must be present together");
        }
        byte[] pocketKey = noPocketKey ? null : decodeTestament(pocketKeyWire);
        byte[] pocketSignature = noPocketSignature ? null
                : decodeCanonicalBase64(pocketSignatureWire);
        if (pocketSignature != null && pocketSignature.length != 64) {
            throw new IllegalArgumentException("EXIT pocket signature must be 64 bytes");
        }
        return new Command(nick,
                testament == null ? null : testamentWire, testament,
                pocketKey == null ? null : pocketKeyWire, pocketKey,
                pocketSignature == null ? null : pocketSignatureWire, pocketSignature);
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
