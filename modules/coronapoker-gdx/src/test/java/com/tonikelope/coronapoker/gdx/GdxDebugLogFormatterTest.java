/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GdxDebugLogFormatterTest {

    @Test
    void mirrorsSwingHeaderLevelAndContinuationHierarchy() {
        List<GdxDebugLogFormatter.Line> lines = GdxDebugLogFormatter.format(
                List.of(
                        "2026-09-25 13:00:00 com.example.Service execute",
                        "WARNING: Retry in 25 seconds",
                        "attempt 2",
                        "    at com.example.Service.execute(Service.java:40)"));

        assertEquals(List.of("2026-09-25 13:00:00 com.example.",
                "Service", " execute"), texts(lines.get(0)));
        assertTrue(lines.get(0).runs().get(1).bold());
        assertEquals("WARNING:", lines.get(1).runs().get(0).text());
        assertEquals("25", lines.get(1).runs().get(2).text());
        assertEquals("2", lines.get(2).runs().get(1).text());
        assertNull(lines.get(3).runs().get(0).background());
    }

    @Test
    void severeTagKeepsSwingRedBandAndExceptionContinuation() {
        List<GdxDebugLogFormatter.Line> lines = GdxDebugLogFormatter.format(
                List.of("SEVERE: Persistence failed",
                        "java.io.IOException: disk error"));

        GdxDebugLogFormatter.Run tag = lines.get(0).runs().get(0);
        assertEquals("SEVERE:", tag.text());
        assertNotNull(tag.background());
        assertTrue(tag.bold());
        assertTrue(lines.get(1).runs().get(0).bold());
    }

    private static List<String> texts(GdxDebugLogFormatter.Line line) {
        return line.runs().stream().map(GdxDebugLogFormatter.Run::text)
                .toList();
    }
}
