/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tonikelope.coronapoker.core.network.TelemetryCodec;
import com.tonikelope.coronapoker.core.network.TelemetryFrame;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class CrupierTelemetryWiringTest {

    @Test
    void preservesTheCodecPayloadDelimiterInsideTheGameCommand() {
        TelemetryFrame source = new TelemetryFrame(1234L,
                Map.of("invitado", new int[]{42, 47, 2}));

        TelemetryFrame decoded = Crupier.parseTelemetryCommand(
                "TELEMETRY#" + TelemetryCodec.encode(source));

        assertEquals(1234L, decoded.serverTimestampMs);
        assertArrayEquals(new int[]{42, 47, 2},
                decoded.perPeer.get("invitado"));
    }

    @Test
    void rejectsMissingOrMalformedTelemetryPayloads() {
        assertThrows(IllegalArgumentException.class,
                () -> Crupier.parseTelemetryCommand("TELEMETRY"));
        assertThrows(IllegalArgumentException.class,
                () -> Crupier.parseTelemetryCommand("TELEMETRY#not-a-time"));
    }
}
