/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Current, single-version wire shape for host-driven table termination. */
final class TableTerminationWire {

    static final class ExitCommand {
        private final boolean recover;
        private final String password;

        ExitCommand(boolean recover, String password) {
            this.recover = recover;
            this.password = password;
        }

        boolean recover() {
            return recover;
        }

        String password() {
            return password;
        }
    }

    private TableTerminationWire() {
    }

    static ExitCommand parse(String[] parts) {
        if (parts == null || parts.length < 3 || !"GAME".equals(parts[0])) {
            throw new IllegalArgumentException("table termination requires a GAME frame");
        }

        if ("SERVEREXIT".equals(parts[2])) {
            if (parts.length != 3) {
                throw new IllegalArgumentException("SERVEREXIT requires exactly 3 fields");
            }
            return new ExitCommand(false, null);
        }

        if (!"SERVEREXITRECOVER".equals(parts[2]) || (parts.length != 3 && parts.length != 4)) {
            throw new IllegalArgumentException("invalid SERVEREXITRECOVER shape");
        }

        String decodedPassword = null;
        if (parts.length == 4) {
            try {
                byte[] passwordBytes = Base64.getDecoder().decode(parts[3]);
                if (!Base64.getEncoder().encodeToString(passwordBytes).equals(parts[3])) {
                    throw new IllegalArgumentException("non-canonical recovery password encoding");
                }
                decodedPassword = decodeStrictUtf8(passwordBytes);
            } catch (IllegalArgumentException | CharacterCodingException ex) {
                throw new IllegalArgumentException("invalid recovery password", ex);
            }
        }
        return new ExitCommand(true, decodedPassword);
    }

    static boolean isValidTerminationFrame(String frame) {
        if (frame == null) {
            return false;
        }
        try {
            parse(frame.split("#", -1));
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static String decodeStrictUtf8(byte[] value) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(value))
                .toString();
    }
}
