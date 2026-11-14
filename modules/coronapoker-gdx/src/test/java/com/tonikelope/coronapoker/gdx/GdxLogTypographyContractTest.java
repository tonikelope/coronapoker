/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void coloursMixedCaseAndOverlappingNicknamesWithoutChangingActionAndAmount() {
        var runs = GdxGameLogFormatter.runs("Ana.M SUBE (+1,50)", java.util.List.of("Ana", "Ana.M"));
        assertTrue(runs.stream().anyMatch(run -> run.text().equals("Ana.M")
                && com.badlogic.gdx.graphics.Color.rgba8888(run.color()) == 0x8edcffff));
        assertTrue(runs.stream().anyMatch(run -> run.text().equals("SUBE")
                && com.badlogic.gdx.graphics.Color.rgba8888(run.color()) == 0xffbd80ff));
        assertTrue(runs.stream().anyMatch(run -> run.text().equals("(+1,50)")
                && com.badlogic.gdx.graphics.Color.rgba8888(run.color()) == 0xffc85aff));
    }

    @Test
    void verifiedHandIsGreenAndFlushLeftAndSecurityTagIsPersistable() {
        String line = "   ✓ MANO 41 VERIFICADA CRIPTOGRÁFICAMENTE POR TODOS LOS JUGADORES";
        String tagged = GdxGameLogFormatter.tagSecurityLine(line);
        assertTrue(tagged.startsWith("[ZERO-TRUST]"));
        assertEquals(tagged, GdxGameLogFormatter.tagSecurityLine(tagged));
        assertEquals("[ZERO-TRUST] MANO 41 VERIFICADA CRIPTOGRÁFICAMENTE POR TODOS LOS JUGADORES",
                GdxGameLogFormatter.displayText(tagged));
        assertTrue(GdxGameLogFormatter.runs(tagged).stream().allMatch(run ->
                com.badlogic.gdx.graphics.Color.rgba8888(run.color()) == 0x8ce5aeff));
        String english = "✓ HAND 41 CRYPTOGRAPHICALLY VERIFIED BY ALL PLAYERS";
        assertEquals(GdxGameLogFormatter.SecurityTone.VERIFIED,
                GdxGameLogFormatter.securityTone(english));
        assertEquals(GdxGameLogFormatter.SecurityTone.PENDING,
                GdxGameLogFormatter.securityTone("⚠ HAND 41: shuffle not verified yet (slow clients)"));
        assertTrue(GdxGameLogFormatter.isHandTitle("MANO (42)"));
        assertFalse(GdxGameLogFormatter.isHandTitle(line));
    }

    @Test
    void visualGapSeparatesSecurityMessagesFromTheBalanceTable() {
        String grid = "(##) ┌─────┬───────┐";
        String status = "[ZERO-TRUST] ✓ MANO 1: barajado verificado";
        assertTrue(GdxGameLogFormatter.needsSectionGap(status, grid));
        assertTrue(GdxGameLogFormatter.needsSectionGap(grid, status));
        assertTrue(GdxGameLogFormatter.needsSectionGap("MANO (1)", grid));
        assertFalse(GdxGameLogFormatter.needsSectionGap(grid, "(##) │ NICK │ STACK │"));
        assertFalse(GdxGameLogFormatter.needsSectionGap("", grid));
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
                + "                        ? settingsDebugBoldFont : gameLogEmphasisFont"));
    }
}
