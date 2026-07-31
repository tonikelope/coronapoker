/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.smoke;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two properties-file failures that used to be fatal at startup.
 *
 * The preferences file is read from a static initializer, so anything that
 * escapes from that read does not surface as a warning: it surfaces as a game
 * that never opens. Two ways in:
 *
 * <ul>
 *   <li>a broken unicode escape in the file, which {@code Properties.load}
 *       reports as an IllegalArgumentException — NOT an IOException, so the
 *       read's catch never saw it;</li>
 *   <li>an unreadable file, where the read returned nothing at all and every
 *       later lookup blew up on the spot, because no caller checks.</li>
 * </ul>
 *
 * These pin the JDK behaviour the fix relies on (the exception type that must
 * be caught, and that store() emits pure ASCII so the atomic write is
 * byte-identical to writing in place).
 */
class PropertiesResilienceSmoke {

    @Test
    @DisplayName("A broken unicode escape is NOT an IOException — the narrow catch could never hold it")
    void brokenUnicodeEscapeIsNotAnIOException() {
        // A unicode escape with non-hex digits is invalid and load() rejects it.
        String broken = "good=1\nbad=\\u00ZZ\n";
        Properties prop = new Properties();

        assertThrows(IllegalArgumentException.class,
                () -> prop.load(new ByteArrayInputStream(broken.getBytes(StandardCharsets.ISO_8859_1))),
                "if this ever became an IOException the narrow catch would have been enough");
    }

    @Test
    @DisplayName("A well formed file still loads, escapes included")
    void wellFormedFileStillLoads() throws Exception {
        String ok = "nick=tonikelope\nacentuado=\\u00e1\\u00e9\\u00ed\n";
        Properties prop = new Properties();
        prop.load(new ByteArrayInputStream(ok.getBytes(StandardCharsets.ISO_8859_1)));

        assertEquals("tonikelope", prop.getProperty("nick"));
        assertEquals("áéí", prop.getProperty("acentuado"));
    }

    @Test
    @DisplayName("store() emits pure ASCII, so writing it atomically is byte-identical")
    void storeEmitsPureAscii() throws Exception {
        // The atomic write goes through a String; that is only safe because
        // store() escapes everything outside ASCII, comments included.
        Properties prop = new Properties();
        prop.setProperty("nick", "tonikelope");
        prop.setProperty("acentuado", "áéí ñ €");
        prop.setProperty("estructura", "1/2, 2/4, 5/10");

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        prop.store(out, null);
        byte[] stored = out.toByteArray();

        for (byte b : stored) {
            assertTrue((b & 0xff) < 0x80,
                    "store() must never emit a byte outside ASCII, or the atomic write would re-encode it");
        }

        // And it round-trips: what we read back is what we put in.
        Properties reloaded = new Properties();
        reloaded.load(new ByteArrayInputStream(stored));
        assertEquals("áéí ñ €", reloaded.getProperty("acentuado"));
        assertEquals("1/2, 2/4, 5/10", reloaded.getProperty("estructura"));

        // Same bytes whether the ISO-8859-1 text is re-encoded as UTF-8 (what the
        // atomic write does) or written straight out: pure ASCII makes them equal.
        String asText = new String(stored, StandardCharsets.ISO_8859_1);
        org.junit.jupiter.api.Assertions.assertArrayEquals(stored, asText.getBytes(StandardCharsets.UTF_8));
    }
}
