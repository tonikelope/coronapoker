/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Strict decoder for the current host-to-client MISDEAL command. */
final class MisdealWire {

    private MisdealWire() {
    }

    static String parse(String[] parts) {
        if (parts == null || parts.length != 4
                || !"GAME".equals(parts[0]) || !"MISDEAL".equals(parts[2])) {
            throw new IllegalArgumentException("MISDEAL requires exactly 4 fields");
        }
        try {
            byte[] reasonBytes = Base64.getDecoder().decode(parts[3]);
            if (!Base64.getEncoder().encodeToString(reasonBytes).equals(parts[3])) {
                throw new IllegalArgumentException("non-canonical MISDEAL reason encoding");
            }
            String reason = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(reasonBytes))
                    .toString();
            if (reason.isEmpty()) {
                throw new IllegalArgumentException("MISDEAL reason is empty");
            }
            return reason;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid MISDEAL reason", ex);
        }
    }
}
