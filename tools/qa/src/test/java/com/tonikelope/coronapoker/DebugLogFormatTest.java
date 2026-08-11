/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;

/**
 * Locks the locale-independent contract of {@link DebugLog}'s console formatter: English level names
 * and a fixed ISO timestamp, whatever the system locale. The Debug console's per-line colouring keys
 * off exactly this shape, and the JDK's default SimpleFormatter localizes the level name (e.g.
 * "INFORMACIÓN" under a Spanish locale), which would break level detection.
 */
public class DebugLogFormatTest {

    private static final long FIXED_MILLIS = 1_754_930_201_000L;

    private static LogRecord record(Level level, String msg, Object[] params, String cls, String method, Throwable thrown) {
        LogRecord r = new LogRecord(level, msg);
        r.setParameters(params);
        // Setting the source class (even to null) disables LogRecord's lazy caller inference, so the
        // header is fully deterministic instead of walking the test's own call stack.
        r.setSourceClassName(cls);
        r.setSourceMethodName(method);
        r.setThrown(thrown);
        r.setMillis(FIXED_MILLIS);
        return r;
    }

    @Test
    void levelNameStaysEnglishUnderSpanishLocale() {
        Locale prev = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("es-ES"));
            String out = DebugLog.format(record(Level.INFO, "hello {0}", new Object[]{42},
                    "com.tonikelope.coronapoker.Foo", "bar", null));
            String[] lines = out.split("\n", -1);
            assertTrue(lines[0].matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} com\\.tonikelope\\.coronapoker\\.Foo bar"),
                    "header was: " + lines[0]);
            // English "INFO", not the Spanish "INFORMACIÓN", and the {0} parameter substituted.
            assertEquals("INFO: hello 42", lines[1]);
            assertFalse(out.contains("INFORMACIÓN"));
        } finally {
            Locale.setDefault(prev);
        }
    }

    @Test
    void everyStandardLevelTagIsEnglish() {
        assertTrue(DebugLog.format(record(Level.WARNING, "careful", null, "com.x.Y", "m", null)).contains("\nWARNING: careful\n"));
        assertTrue(DebugLog.format(record(Level.SEVERE, "boom", null, "com.x.Y", "m", null)).contains("\nSEVERE: boom\n"));
        assertTrue(DebugLog.format(record(Level.CONFIG, "cfg", null, "com.x.Y", "m", null)).contains("\nCONFIG: cfg\n"));
        assertTrue(DebugLog.format(record(Level.FINE, "trace", null, "com.x.Y", "m", null)).contains("\nFINE: trace\n"));
    }

    @Test
    void isoTimestampAndTrailingNewline() {
        String out = DebugLog.format(record(Level.INFO, "x", null, "com.x.Y", "m", null));
        assertTrue(out.endsWith("\n"));
        assertTrue(out.substring(0, 19).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"),
                "timestamp was: " + out.substring(0, 19));
    }

    @Test
    void stacktraceIsAppendedForThrown() {
        String out = DebugLog.format(record(Level.SEVERE, "failed", null, "com.x.Y", "m",
                new IllegalStateException("kaboom")));
        assertTrue(out.contains("\nSEVERE: failed\n"));
        assertTrue(out.contains("java.lang.IllegalStateException: kaboom"), out);
        assertTrue(out.contains("\tat "), "expected stack frames, got: " + out);
    }

    @Test
    void headerFallsBackToLoggerNameWhenNoSourceClass() {
        LogRecord r = record(Level.INFO, "no source", null, null, null, null);
        r.setLoggerName("com.tonikelope.coronapoker.Boot");
        String[] lines = DebugLog.format(r).split("\n", -1);
        assertTrue(lines[0].endsWith(" com.tonikelope.coronapoker.Boot"), "header was: " + lines[0]);
        assertEquals("INFO: no source", lines[1]);
    }
}
