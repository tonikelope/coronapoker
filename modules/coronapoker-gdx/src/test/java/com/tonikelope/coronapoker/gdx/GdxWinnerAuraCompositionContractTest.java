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
    void winnerPresentationStaysBehindCardsWhileFireKeepsItsDepth()
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
        int localHud = scene.indexOf("drawLocalHud(", handOverlay);
        String backEffectMethod = method(table,
                "private void drawSeatBackEffects()",
                "private void drawActiveSeatGlows()");
        assertTrue(backEffects >= 0 && rearCards > backEffects
                        && seatsLayer > rearCards,
                "seat lighting must remain below cards and seat surfaces");
        assertTrue(handOverlay > seatsLayer && localHud > handOverlay,
                "showdown overlays and the local HUD keep their normal depth");
        assertTrue(backEffectMethod.contains("drawLiveWinnerEffects()"),
                "winner effects must be submitted on the felt plane");
        assertFalse(scene.substring(rearCards).contains(
                "drawLiveWinnerEffects()"),
                "winner effects must never be composited over cards or HUDs");
        assertFalse(scene.contains("drawShowdownOverlay()"));
        assertFalse(seats.contains("drawActiveSeatGlows()"));
        assertTrue(seats.contains("drawAllInSeatFlames()"));
    }

    @Test
    void winnerPresentationUsesDirectionalAccentsWithoutAnyAuraTexture()
            throws IOException {
        String table = Files.readString(source());
        String effects = method(table,
                "private void drawLiveWinnerEffects()",
                "private void updateLiveChipBatch()");
        String streaks = method(table,
                "private void drawLiveWinnerStreaks()",
                "private void drawLiveWinnerFrames()");
        String frames = method(table,
                "private void drawLiveWinnerFrames()",
                "private void drawLiveWinnerParticles()");

        assertTrue(effects.contains("drawLiveWinnerStreaks()"));
        assertTrue(effects.contains("drawLiveWinnerFrames()"));
        assertTrue(effects.contains("drawLiveWinnerParticles()"));
        assertTrue(streaks.contains("shapes.triangle("));
        assertTrue(streaks.contains("for (int side = -1; side <= 1;"));
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
