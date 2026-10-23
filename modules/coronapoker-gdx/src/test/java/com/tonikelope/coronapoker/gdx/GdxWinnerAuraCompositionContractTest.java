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
    void winnerPresentationReturnsToForegroundWithoutRestoringTheAura()
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
        String backEffectMethod = method(table,
                "private void drawSeatBackEffects()",
                "private void drawActiveSeatGlows()");
        assertTrue(backEffects >= 0 && rearCards > backEffects
                        && seatsLayer > rearCards,
                "seat lighting must remain below cards and seat surfaces");
        assertTrue(handOverlay > seatsLayer && winnerEffects > handOverlay
                        && localHud > winnerEffects,
                "winner accents must cover the showdown but not the local HUD");
        assertFalse(backEffectMethod.contains("drawLiveWinnerEffects()"),
                "winner accents must not be submitted on the felt plane");
        assertFalse(scene.contains("drawShowdownOverlay()"));
        assertFalse(seats.contains("drawActiveSeatGlows()"));
        assertTrue(seats.contains("drawAllInSeatFlames()"));
    }

    @Test
    void winnerPresentationUsesANeonFrameWithoutSideMarksOrAuraTexture()
            throws IOException {
        String table = Files.readString(source());
        String effects = method(table,
                "private void drawLiveWinnerEffects()",
                "private void updateLiveChipBatch()");
        String frames = method(table,
                "private void drawLiveWinnerFrames()",
                "private void drawLiveWinnerParticles()");
        String particles = method(table,
                "private void drawLiveWinnerParticles()",
                "private float liveWinnerEffectProgress(");

        assertTrue(effects.contains("drawLiveWinnerFrames()"));
        assertTrue(effects.contains("drawLiveWinnerParticles()"));
        assertFalse(table.contains("drawLiveWinnerStreaks()"));
        assertTrue(frames.contains("GdxAntialiasedRoundedRect.outline"));
        assertTrue(frames.contains("WINNER_NEON_GREEN"));
        assertTrue(frames.contains("WINNER_NEON_CORE"));
        assertTrue(frames.contains("float perimeter = 2f"));
        assertFalse(frames.contains("roundedRect(frameX"));
        assertTrue(particles.contains("GL20.GL_ONE_MINUS_SRC_ALPHA"));
        assertFalse(particles.contains("GL20.GL_SRC_ALPHA, GL20.GL_ONE)"));
        assertFalse(frames.contains("shapes.circle(avatarX"));
        assertFalse(table.contains("winnerAuraShader"));
        assertFalse(table.contains("winnerAuraTexture"));
        assertFalse(table.contains("winnerCrestTexture"));
        assertFalse(table.contains("WINNER_CREST"));
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
