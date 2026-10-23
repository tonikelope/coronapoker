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
        String texture = method(table,
                "private static Texture createWinnerAuraTexture()",
                "private void createChipTexturePair(");
        String effects = method(table,
                "private void drawLiveWinnerEffects()",
                "private void updateLiveChipBatch()");

        assertTrue(texture.contains("Math.atan2(ny, nx)"));
        assertTrue(texture.contains("MathUtils.sin(angle * 3f"));
        assertTrue(texture.contains("MathUtils.sin(angle * 7f"));
        assertTrue(texture.contains("Math.pow(1f - smooth, 1.15f)"));
        assertTrue(texture.contains("pixels.drawPixel(x, y"));
        assertTrue(effects.contains("batch.draw(winnerAuraTexture"));
        assertTrue(effects.contains("winnerProgress * 0.48f"));
        assertTrue(effects.contains("drawLiveWinnerParticles()"));
        assertFalse(table.contains("winnerAuraShader"));
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
