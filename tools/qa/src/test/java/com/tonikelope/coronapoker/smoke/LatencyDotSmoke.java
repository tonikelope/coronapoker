/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.smoke;

import com.tonikelope.coronapoker.LatencyDot;
import java.awt.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AAA test del LatencyDot — Sprint 7 component. Cubre el mapping
 * latencia → color que decide visualmente la calidad de enlace.
 *
 * Cobertura:
 *   - umbrales exactos (boundary values: 80, 81, 200, 201, 400, 401).
 *   - latencia negativa (-1 unknown/timeout) → ROJO.
 *   - edad > STALE_THRESHOLD_MS → GRIS (independiente de la latencia).
 *   - edad muy fresh + latencia normal → color por latencia.
 *   - setLatency persiste los 3 valores leíbles desde getters.
 */
class LatencyDotSmoke {

    @Test
    @DisplayName("Latencia 0 ms / age=0 → GREEN")
    void zeroLatencyGreen() {
        assertEquals(LatencyDot.COLOR_GREEN, LatencyDot.colorFor(0, 0));
    }

    @Test
    @DisplayName("Latencia justo en boundary GREEN (80) → GREEN")
    void boundaryGreen() {
        assertEquals(LatencyDot.COLOR_GREEN, LatencyDot.colorFor(80, 0));
    }

    @Test
    @DisplayName("Latencia 81 → YELLOW")
    void crossesIntoYellow() {
        assertEquals(LatencyDot.COLOR_YELLOW, LatencyDot.colorFor(81, 0));
    }

    @Test
    @DisplayName("Latencia 200 → YELLOW (boundary)")
    void boundaryYellow() {
        assertEquals(LatencyDot.COLOR_YELLOW, LatencyDot.colorFor(200, 0));
    }

    @Test
    @DisplayName("Latencia 201 → ORANGE")
    void crossesIntoOrange() {
        assertEquals(LatencyDot.COLOR_ORANGE, LatencyDot.colorFor(201, 0));
    }

    @Test
    @DisplayName("Latencia 400 → ORANGE (boundary)")
    void boundaryOrange() {
        assertEquals(LatencyDot.COLOR_ORANGE, LatencyDot.colorFor(400, 0));
    }

    @Test
    @DisplayName("Latencia 401 → RED")
    void crossesIntoRed() {
        assertEquals(LatencyDot.COLOR_RED, LatencyDot.colorFor(401, 0));
    }

    @Test
    @DisplayName("Latencia 9999 → RED (very high)")
    void veryHighRed() {
        assertEquals(LatencyDot.COLOR_RED, LatencyDot.colorFor(9999, 0));
    }

    @Test
    @DisplayName("Latencia -1 → RED (unknown/timeout)")
    void negativeRed() {
        assertEquals(LatencyDot.COLOR_RED, LatencyDot.colorFor(-1, 0));
    }

    @Test
    @DisplayName("Age > STALE_THRESHOLD → GRIS independiente de latencia")
    void staleAgeOverrideAllLatencies() {
        long stale = LatencyDot.STALE_THRESHOLD_MS + 1;
        // Hasta una latencia EXCELENTE pasa a stale si la edad supera el umbral
        assertEquals(LatencyDot.COLOR_STALE, LatencyDot.colorFor(0, stale));
        assertEquals(LatencyDot.COLOR_STALE, LatencyDot.colorFor(50, stale));
        assertEquals(LatencyDot.COLOR_STALE, LatencyDot.colorFor(150, stale));
        assertEquals(LatencyDot.COLOR_STALE, LatencyDot.colorFor(500, stale));
        assertEquals(LatencyDot.COLOR_STALE, LatencyDot.colorFor(-1, stale));
    }

    @Test
    @DisplayName("Age EXACTAMENTE en STALE_THRESHOLD → todavía vigente (no STALE)")
    void ageAtBoundaryNotStale() {
        // age == STALE_THRESHOLD_MS no es > → no stale
        Color c = LatencyDot.colorFor(50, LatencyDot.STALE_THRESHOLD_MS);
        assertEquals(LatencyDot.COLOR_GREEN, c);
    }

    @Test
    @DisplayName("setLatency persiste valores leíbles desde getters")
    void setLatencyPersistsValues() {
        LatencyDot d = new LatencyDot();
        d.setLatency(123, 5);
        assertEquals(123, d.getLatencyMs());
        assertEquals(5, d.getReconnectionCount());
        assertTrue(d.getLastUpdateMs() > 0);
    }

    @Test
    @DisplayName("Constructor: preferredSize 22x22 por defecto, sin update inicial")
    void initialState() {
        LatencyDot d = new LatencyDot();
        assertNotNull(d.getPreferredSize());
        assertEquals(22, d.getPreferredSize().width);
        assertEquals(22, d.getPreferredSize().height);
        // last_update_ms inicial = 0 → primera consulta de color va a STALE
        // (porque age = now - 0 = enorme, > STALE_THRESHOLD_MS)
        assertEquals(0L, d.getLastUpdateMs());
    }
}
