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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AAA test del helper Helpers.readBoundedLine introducido en Sprint 1 commit 2.
 *
 * Cobertura:
 *   - linea normal con \n -> devuelve cuerpo sin el \n
 *   - linea con CR-LF -> devuelve cuerpo sin CR ni LF
 *   - EOF inmediato sin leer nada -> devuelve null (semantica de readLine())
 *   - EOF con bytes leidos pero sin \n -> devuelve los bytes acumulados
 *   - linea que supera el cap -> lanza IOException
 *   - cap aplica exactamente sobre el numero de chars, no inclusive
 *   - hash y caracteres del wire format (#, +, /, =) pasan limpios
 */
class ReadBoundedLineSmoke {

    @Test
    @DisplayName("linea normal con LF devuelve cuerpo limpio")
    void plainLine() throws IOException {
        BufferedReader r = new BufferedReader(new StringReader("hola mundo\n"));
        String line = Helpers.readBoundedLine(r, 1024);
        assertEquals("hola mundo", line);
    }

    @Test
    @DisplayName("linea con CR-LF devuelve cuerpo sin CR ni LF")
    void crlfStripped() throws IOException {
        BufferedReader r = new BufferedReader(new StringReader("hola\r\n"));
        String line = Helpers.readBoundedLine(r, 1024);
        assertEquals("hola", line);
    }

    @Test
    @DisplayName("EOF inmediato sin leer nada devuelve null")
    void eofReturnsNull() throws IOException {
        BufferedReader r = new BufferedReader(new StringReader(""));
        assertNull(Helpers.readBoundedLine(r, 1024));
    }

    @Test
    @DisplayName("EOF tras leer bytes pero sin \\n devuelve los bytes acumulados")
    void eofMidLineReturnsAccumulated() throws IOException {
        BufferedReader r = new BufferedReader(new StringReader("foo"));
        assertEquals("foo", Helpers.readBoundedLine(r, 1024));
    }

    @Test
    @DisplayName("linea que supera el cap lanza IOException con mensaje DoS guard")
    void overCapThrows() {
        // cap=4 -> linea de 5 chars sin \n
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            huge.append('A');
        }
        huge.append('\n');
        BufferedReader r = new BufferedReader(new StringReader(huge.toString()));
        IOException ex = assertThrows(IOException.class,
                () -> Helpers.readBoundedLine(r, 4));
        assertTrue(ex.getMessage().contains("DoS guard"),
                "Mensaje debe identificar el guard de DoS: " + ex.getMessage());
    }

    @Test
    @DisplayName("linea de exactamente cap chars NO lanza")
    void exactCapBoundary() throws IOException {
        // cap=4 -> linea de exactamente 4 chars + \n debe pasar
        BufferedReader r = new BufferedReader(new StringReader("ABCD\n"));
        assertEquals("ABCD", Helpers.readBoundedLine(r, 4));
    }

    @Test
    @DisplayName("Caracteres del wire format (#, +, /, =, base64, dígitos) pasan limpios")
    void wireFormatChars() throws IOException {
        String wire = "GAME#1234567890#ACTION#aGVsbG8rZ29vZGJ5ZS9zaG93ZG93bg==\n";
        BufferedReader r = new BufferedReader(new StringReader(wire));
        String line = Helpers.readBoundedLine(r, 1024);
        assertEquals("GAME#1234567890#ACTION#aGVsbG8rZ29vZGJ5ZS9zaG93ZG93bg==", line);
    }
}
