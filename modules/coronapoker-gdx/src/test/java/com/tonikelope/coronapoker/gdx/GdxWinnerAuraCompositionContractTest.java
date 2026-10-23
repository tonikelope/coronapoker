/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GdxWinnerAuraCompositionContractTest {

    @Test
    void fireAndWinnerAuraKeepTheirHistoricalShowdownDepth()
            throws IOException {
        String table = Files.readString(source());
        String scene = method(table, "private void drawTableScene()",
                "private void drawTableBranding(float height)");
        String seats = method(table, "private void drawSeats()",
                "private void drawSidePotWinnerOverlays()");

        int backEffects = scene.indexOf("drawSeatBackEffects()");
        int rearCards = scene.indexOf("drawHoleCards(false)");
        int seatsLayer = scene.indexOf("drawSeats()", rearCards);
        int handOverlay = scene.indexOf("drawHandOverlay()", seatsLayer);
        int winnerEffects = scene.indexOf("drawLiveWinnerEffects()",
                handOverlay);
        int localHud = scene.indexOf("drawLocalHud(", winnerEffects);
        assertTrue(backEffects >= 0 && rearCards > backEffects
                        && seatsLayer > rearCards,
                "seat lighting must remain below cards and seat surfaces");
        assertTrue(handOverlay > seatsLayer && winnerEffects > handOverlay
                        && localHud > winnerEffects,
                "winner effects must cover the showdown but not the local HUD");
        assertFalse(scene.contains("drawShowdownOverlay()"));
        assertFalse(seats.contains("drawActiveSeatGlows()"));
        assertTrue(seats.contains("drawAllInSeatFlames()"));
    }

    @Test
    void winnerAuraUsesOneContinuousFieldInsteadOfStackedDiscs()
            throws IOException {
        String table = Files.readString(source());
        String shader = method(table,
                "private static final String WINNER_AURA_FRAGMENT_SHADER",
                "private static final Color BACKGROUND_BOTTOM");
        String effects = method(table,
                "private void drawLiveWinnerEffects()",
                "private void updateLiveChipBatch()");

        assertTrue(shader.contains("smoothstep(0.035, 1.0"));
        assertTrue(shader.contains("auraHash(gl_FragCoord.xy)"));
        assertTrue(effects.contains("winnerAuraShader.setUniformf"));
        assertTrue(effects.contains("drawLiveWinnerParticles()"));
        assertFalse(effects.contains("for (int ring"));
        assertFalse(effects.contains(
                "shapes.circle(winner.x, winner.y, radius"));
    }

    private static String method(String source, String startText,
            String endText) {
        int start = source.indexOf(startText);
        int end = source.indexOf(endText, start);
        assertTrue(start >= 0 && end > start);
        return source.substring(start, end);
    }

    private static Path source() {
        return Path.of("src", "main", "java", "com", "tonikelope",
                "coronapoker", "gdx", "CoronaPokerGdxTable.java");
    }
}
