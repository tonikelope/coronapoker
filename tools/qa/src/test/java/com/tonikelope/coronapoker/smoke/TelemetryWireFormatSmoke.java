/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.smoke;

import com.tonikelope.coronapoker.Helpers;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AAA test del wire format TELEMETRY del Sprint 7.
 *
 * Cobertura:
 *   - Round-trip ts + map (un peer, varios peers, vacío).
 *   - Nicks con caracteres conflictivos (#, @, =, /, espacio, unicode).
 *   - Decoder tolera payloads malformados (skip silencioso, no excepción).
 *   - Decoder devuelve null para input vacío / null.
 *   - Decoder rechaza ts no numérico (devuelve null).
 *   - Entradas individuales malformadas se saltan sin romper las demás.
 */
class TelemetryWireFormatSmoke {

    @Test
    @DisplayName("Round-trip: ts + 1 peer")
    void roundTripSinglePeer() {
        Map<String, int[]> data = new HashMap<>();
        data.put("alice", new int[]{120, 130, 3});

        Helpers.TelemetryFrame f = new Helpers.TelemetryFrame(1716559200000L, data);
        String wire = Helpers.encodeTelemetry(f);
        Helpers.TelemetryFrame restored = Helpers.decodeTelemetry(wire);

        assertNotNull(restored);
        assertEquals(1716559200000L, restored.serverTimestampMs);
        assertEquals(1, restored.perPeer.size());
        assertArrayEquals(new int[]{120, 130, 3}, restored.perPeer.get("alice"));
    }

    @Test
    @DisplayName("Round-trip: ts + 3 peers")
    void roundTripMultiplePeers() {
        Map<String, int[]> data = new LinkedHashMap<>();
        data.put("alice", new int[]{50, 55, 0});
        data.put("bob", new int[]{200, 210, 1});
        data.put("carol", new int[]{-1, -1, 0});

        Helpers.TelemetryFrame f = new Helpers.TelemetryFrame(42L, data);
        String wire = Helpers.encodeTelemetry(f);
        Helpers.TelemetryFrame restored = Helpers.decodeTelemetry(wire);

        assertEquals(42L, restored.serverTimestampMs);
        assertEquals(3, restored.perPeer.size());
        assertArrayEquals(new int[]{50, 55, 0}, restored.perPeer.get("alice"));
        assertArrayEquals(new int[]{200, 210, 1}, restored.perPeer.get("bob"));
        assertArrayEquals(new int[]{-1, -1, 0}, restored.perPeer.get("carol"));
    }

    @Test
    @DisplayName("Round-trip: ts + 0 peers (broadcast vacío)")
    void roundTripEmpty() {
        Helpers.TelemetryFrame f = new Helpers.TelemetryFrame(99L, new HashMap<>());
        String wire = Helpers.encodeTelemetry(f);
        Helpers.TelemetryFrame restored = Helpers.decodeTelemetry(wire);

        assertEquals(99L, restored.serverTimestampMs);
        assertTrue(restored.perPeer.isEmpty());
    }

    @Test
    @DisplayName("Nicks con caracteres conflictivos sobreviven via Base64")
    void nicksWithSeparators() {
        Map<String, int[]> data = new HashMap<>();
        data.put("pepe#@=/", new int[]{1, 2, 3});
        data.put("español ñ á é", new int[]{4, 5, 6});
        data.put("中文 unicode", new int[]{7, 8, 9});

        Helpers.TelemetryFrame f = new Helpers.TelemetryFrame(0L, data);
        String wire = Helpers.encodeTelemetry(f);
        Helpers.TelemetryFrame restored = Helpers.decodeTelemetry(wire);

        assertEquals(3, restored.perPeer.size());
        assertArrayEquals(new int[]{1, 2, 3}, restored.perPeer.get("pepe#@=/"));
        assertArrayEquals(new int[]{4, 5, 6}, restored.perPeer.get("español ñ á é"));
        assertArrayEquals(new int[]{7, 8, 9}, restored.perPeer.get("中文 unicode"));
    }

    @Test
    @DisplayName("Decoder: null input → null")
    void decoderNullInput() {
        assertNull(Helpers.decodeTelemetry(null));
    }

    @Test
    @DisplayName("Decoder: empty input → null")
    void decoderEmptyInput() {
        assertNull(Helpers.decodeTelemetry(""));
    }

    @Test
    @DisplayName("Decoder: ts no numérico → null")
    void decoderBadTimestamp() {
        assertNull(Helpers.decodeTelemetry("noTs#anything"));
    }

    @Test
    @DisplayName("Decoder: solo ts sin '#' → frame con map vacío")
    void decoderTsOnly() {
        Helpers.TelemetryFrame f = Helpers.decodeTelemetry("12345");
        assertNotNull(f);
        assertEquals(12345L, f.serverTimestampMs);
        assertTrue(f.perPeer.isEmpty());
    }

    @Test
    @DisplayName("Decoder: entrada malformada se salta, las buenas permanecen")
    void decoderTolerantToBadEntries() {
        // Mezcla: una OK, una sin '=', una con números no parseables, una OK.
        String b64alice = java.util.Base64.getEncoder().encodeToString("alice".getBytes());
        String b64bob = java.util.Base64.getEncoder().encodeToString("bob".getBytes());
        String wire = "100#" + b64alice + "|10/20/30@MALFORMED@" + b64alice + "|notANumber/2/3@" + b64bob + "|40/50/60";

        Helpers.TelemetryFrame f = Helpers.decodeTelemetry(wire);
        assertNotNull(f);
        assertEquals(100L, f.serverTimestampMs);
        // alice OK, MALFORMED skip, alice second-try skip por not-a-number, bob OK
        assertEquals(2, f.perPeer.size());
        assertArrayEquals(new int[]{10, 20, 30}, f.perPeer.get("alice"));
        assertArrayEquals(new int[]{40, 50, 60}, f.perPeer.get("bob"));
    }

    @Test
    @DisplayName("Decoder: entrada con < 3 números se salta")
    void decoderEntryWithMissingNumbers() {
        String b64alice = java.util.Base64.getEncoder().encodeToString("alice".getBytes());
        String b64bob = java.util.Base64.getEncoder().encodeToString("bob".getBytes());
        // alice tiene solo 2 números → debe saltar
        String wire = "5#" + b64alice + "|10/20@" + b64bob + "|30/40/50";

        Helpers.TelemetryFrame f = Helpers.decodeTelemetry(wire);
        assertEquals(1, f.perPeer.size());
        assertFalse(f.perPeer.containsKey("alice"));
        assertArrayEquals(new int[]{30, 40, 50}, f.perPeer.get("bob"));
    }

    @Test
    @DisplayName("Encoder: TelemetryFrame inmutable (copia defensiva)")
    void frameIsImmutable() {
        Map<String, int[]> mutable = new HashMap<>();
        mutable.put("alice", new int[]{1, 2, 3});

        Helpers.TelemetryFrame f = new Helpers.TelemetryFrame(0L, mutable);
        // Modificar el map original no debe afectar al frame
        mutable.put("bob", new int[]{4, 5, 6});

        assertEquals(1, f.perPeer.size());
        assertFalse(f.perPeer.containsKey("bob"));
    }
}
