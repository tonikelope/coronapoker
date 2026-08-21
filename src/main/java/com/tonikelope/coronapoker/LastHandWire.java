/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Current wire format for scheduling or cancelling the last hand. */
final class LastHandWire {

    static final class Command {
        private final boolean enabled;
        private final boolean recover;
        private final String password;

        Command(boolean enabled, boolean recover, String password) {
            this.enabled = enabled;
            this.recover = recover;
            this.password = password;
        }

        boolean enabled() {
            return enabled;
        }

        boolean recover() {
            return recover;
        }

        String password() {
            return password;
        }
    }

    private LastHandWire() {
    }

    static Command parse(String[] parts) {
        if (parts == null || parts.length < 4
                || !"GAME".equals(parts[0]) || !"LASTHAND".equals(parts[2])) {
            throw new IllegalArgumentException("invalid LASTHAND frame");
        }
        switch (parts[3]) {
            case "0":
                requireLength(parts, 4);
                return new Command(false, false, null);
            case "1":
                requireLength(parts, 4);
                return new Command(true, false, null);
            case "2":
                if (parts.length != 4 && parts.length != 5) {
                    throw new IllegalArgumentException("LASTHAND recover requires 4 or 5 fields");
                }
                return new Command(true, true,
                        parts.length == 5 ? decodePassword(parts[4]) : null);
            default:
                throw new IllegalArgumentException("unknown LASTHAND mode");
        }
    }

    private static void requireLength(String[] parts, int expected) {
        if (parts.length != expected) {
            throw new IllegalArgumentException("unexpected LASTHAND fields");
        }
    }

    private static String decodePassword(String encoded) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (!Base64.getEncoder().encodeToString(bytes).equals(encoded)) {
                throw new IllegalArgumentException("non-canonical LASTHAND password encoding");
            }
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid LASTHAND password", ex);
        }
    }
}
