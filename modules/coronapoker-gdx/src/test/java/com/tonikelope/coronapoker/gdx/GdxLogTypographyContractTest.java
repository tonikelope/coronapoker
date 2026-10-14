/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GdxLogTypographyContractTest {

    @Test
    void gameLogUsesRegularByDefaultAndBoldOnlyForSemanticTokens() {
        assertFalse(GdxGameLogFormatter.runs(
                "CoronaBot$3 CHECK").stream()
                .anyMatch(GdxGameLogFormatter.Run::bold));
        assertTrue(GdxGameLogFormatter.runs(
                "FLOP -> [A♠][K♥][7♣]").stream()
                .anyMatch(run -> run.bold() && run.text().contains("FLOP")));
        assertTrue(GdxGameLogFormatter.runs(
                "CoronaBot$3 GANA BOTE (2.5)").stream()
                .anyMatch(run -> run.bold() && run.text().contains("GANA")));
    }

    @Test
    void independentWindowKeepsEveryRoleOnOneFixedWidthGutter() {
        for (GdxGameLogFormatter.Marker marker
                : GdxGameLogFormatter.Marker.values()) {
            String prefix = GdxGameLogWindow.markerPrefix(marker);
            assertTrue(prefix.isEmpty() || prefix.length() == 3, marker.name());
        }
    }

    @Test
    void everyDebugSurfaceHonoursFormatterBoldRuns() throws IOException {
        Path root = Path.of("src", "main", "java", "com", "tonikelope",
                "coronapoker", "gdx");
        String frontend = Files.readString(root.resolve(
                "GdxFrontendScreen.java")).replace("\r\n", "\n");
        String table = Files.readString(root.resolve(
                "CoronaPokerGdxTable.java")).replace("\r\n", "\n");

        assertTrue(frontend.contains("run.bold()\n"
                + "                        ? debugLogBoldFont : debugLogFont"));
        assertTrue(table.contains("run.bold()\n"
                + "                        ? gameLogBoldFont : gameLogFont"));
    }
}
