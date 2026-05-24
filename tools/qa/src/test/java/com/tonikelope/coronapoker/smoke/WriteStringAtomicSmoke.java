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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AAA test del Helpers.writeStringAtomic introducido en deferred 🟠-26.
 *
 * Cobertura:
 *   - Crear fichero nuevo (target no existía).
 *   - Sobrescribir fichero existente.
 *   - Después de éxito NO queda ningún .tmp- huérfano en el dir.
 *   - null target rechazado.
 *   - Datos con saltos de línea + UTF-8 (preserve byte-for-byte).
 *
 * NO testeable directamente sin mockear el filesystem:
 *   - Resistencia real a process-kill mid-write. La garantía es
 *     "tmp se escribe completo antes del move" — testeable por
 *     inspección de código, no por test runtime.
 */
class WriteStringAtomicSmoke {

    private Path tmpDir;

    @BeforeEach
    void setupTmpDir() throws IOException {
        tmpDir = Files.createTempDirectory("writeAtomicSmoke");
    }

    @AfterEach
    void cleanupTmpDir() throws IOException {
        if (tmpDir == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(tmpDir)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
        }
    }

    @Test
    @DisplayName("Crear fichero nuevo: target tiene el contenido exacto")
    void createsNewFile() throws IOException {
        Path target = tmpDir.resolve("balance.txt");
        Helpers.writeStringAtomic(target, "hola mundo");
        assertTrue(Files.exists(target));
        assertEquals("hola mundo", Files.readString(target));
    }

    @Test
    @DisplayName("Sobrescribe fichero existente con contenido nuevo")
    void overwritesExistingFile() throws IOException {
        Path target = tmpDir.resolve("balance.txt");
        Files.writeString(target, "viejo contenido");
        Helpers.writeStringAtomic(target, "nuevo contenido");
        assertEquals("nuevo contenido", Files.readString(target));
    }

    @Test
    @DisplayName("Tras éxito NO queda ningún fichero .tmp- huérfano en el dir")
    void noOrphanTmpFile() throws IOException {
        Path target = tmpDir.resolve("balance.txt");
        Helpers.writeStringAtomic(target, "datos");
        try (Stream<Path> entries = Files.list(tmpDir)) {
            long tmpCount = entries.filter(p -> p.getFileName().toString().contains(".tmp-")).count();
            assertEquals(0, tmpCount, "Debe haber 0 ficheros .tmp- tras éxito");
        }
    }

    @Test
    @DisplayName("Datos UTF-8 + saltos de línea preserved exact")
    void preservesContent() throws IOException {
        Path target = tmpDir.resolve("balance.txt");
        String content = "alice|100.50@bob|200.75\nñ á é è ç 中文\r\n";
        Helpers.writeStringAtomic(target, content);
        assertEquals(content, Files.readString(target));
    }

    @Test
    @DisplayName("null target lanza IllegalArgumentException")
    void nullTargetRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Helpers.writeStringAtomic(null, "data"));
    }

    @Test
    @DisplayName("Contenido vacío también funciona")
    void emptyContent() throws IOException {
        Path target = tmpDir.resolve("balance.txt");
        Helpers.writeStringAtomic(target, "");
        assertTrue(Files.exists(target));
        assertEquals("", Files.readString(target));
    }

    @Test
    @DisplayName("Múltiples writes consecutivos: último gana")
    void multipleWritesLastWins() throws IOException {
        Path target = tmpDir.resolve("balance.txt");
        Helpers.writeStringAtomic(target, "v1");
        Helpers.writeStringAtomic(target, "v2");
        Helpers.writeStringAtomic(target, "v3");
        assertEquals("v3", Files.readString(target));
    }

    @Test
    @DisplayName("Write a dir inexistente lanza IOException (no NPE)")
    void writeToNonexistentDirThrows() {
        Path target = Paths.get(tmpDir.toString(), "subdir-no-existe", "balance.txt");
        assertThrows(IOException.class, () -> Helpers.writeStringAtomic(target, "data"));
    }

    @Test
    @DisplayName("Sigue sin haber tmp huérfano si target ya existía + sobrescribe N veces")
    void noOrphanTmpAfterMultipleOverwrites() throws IOException {
        Path target = tmpDir.resolve("balance.txt");
        for (int i = 0; i < 5; i++) {
            Helpers.writeStringAtomic(target, "v" + i);
        }
        try (Stream<Path> entries = Files.list(tmpDir)) {
            long tmpCount = entries.filter(p -> p.getFileName().toString().contains(".tmp-")).count();
            assertEquals(0, tmpCount);
        }
        assertEquals("v4", Files.readString(target));
    }
}
