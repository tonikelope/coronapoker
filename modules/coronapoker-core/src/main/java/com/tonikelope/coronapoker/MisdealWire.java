/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Strict decoder for the canonical host-to-client MISDEAL command. */
final class MisdealWire {

    private MisdealWire() {
    }

    static String parseClientCommand(String[] parts) {
        if (parts == null || parts.length != 2 || !"MISDEAL".equals(parts[0])) {
            throw new IllegalArgumentException("MISDEAL requires exactly 2 fields");
        }
        try {
            byte[] reasonBytes = Base64.getDecoder().decode(parts[1]);
            if (!Base64.getEncoder().encodeToString(reasonBytes).equals(parts[1])) {
                throw new IllegalArgumentException("non-canonical MISDEAL reason encoding");
            }
            String reason = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(reasonBytes)).toString();
            if (reason.isBlank()) {
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
