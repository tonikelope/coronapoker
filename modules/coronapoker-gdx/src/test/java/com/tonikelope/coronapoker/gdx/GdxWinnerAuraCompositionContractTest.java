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
    void winnerPresentationKeepsItsFeatheredLightInTheForeground()
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
    void winnerPresentationUsesTheSameFilledGlowAsTheActiveTurn()
            throws IOException {
        String table = Files.readString(source());
        String effects = method(table,
                "private void drawLiveWinnerEffects()",
                "private void updateLiveChipBatch()");
        String winnerGlow = method(table,
                "private void drawWinnerSeatGlows()",
                "private void drawActiveSeatGlows()");
        String activeGlow = method(table,
                "private void drawActiveSeatGlows()",
                "private void drawRemoteSeatHalo(");
        String remoteGlow = method(table,
                "private void drawRemoteSeatHalo(",
                "private void drawAllInSeatFlames()");
        String particles = method(table,
                "private void drawLiveWinnerParticles()",
                "private float liveWinnerEffectProgress(");
        String localHud = method(table,
                "private void drawLocalHud(float width, float height)",
                "private void drawPendingRebuyTooltip(");

        assertTrue(effects.contains("drawLiveWinnerParticles()"));
        assertFalse(table.contains("drawLiveWinnerStreaks()"));
        assertTrue(winnerGlow.contains("drawRemoteSeatHalo("));
        assertTrue(activeGlow.contains("drawRemoteSeatHalo("));
        assertTrue(winnerGlow.contains(
                "remoteSeatHaloPulse(totalTime,"));
        assertTrue(activeGlow.contains(
                "remoteSeatHaloPulse(totalTime,"));
        assertTrue(winnerGlow.contains("seatHaloStrength(pulse)"));
        assertTrue(activeGlow.contains("seatHaloStrength(pulse)"));
        assertTrue(remoteGlow.contains("drawSoftRoundedGlow("));
        assertTrue(winnerGlow.contains("winner.index == 0"));
        assertTrue(remoteGlow.contains("seat.podX - 6f"));
        assertTrue(remoteGlow.contains("PLAYER_POD_WIDTH + 12f"));
        assertTrue(remoteGlow.contains("PLAYER_POD_HEIGHT + 12f"));
        assertTrue(remoteGlow.contains("17f, 24f, color, strength"));
        assertTrue(winnerGlow.contains("WINNER_NEON_GREEN"));
        assertTrue(winnerGlow.contains("GL20.GL_SRC_ALPHA, GL20.GL_ONE"));
        assertFalse(winnerGlow.contains("drawSoftRoundedOutlineGlow("));
        assertTrue(localHud.contains(
                "float haloPulse = localHudHaloPulse(totalTime)"));
        assertTrue(localHud.contains(
                "float glowStrength = seatHaloStrength("));
        assertTrue(localHud.contains(
                "danger ? dangerPulse : haloPulse"));
        assertTrue(particles.contains("GL20.GL_SRC_ALPHA, GL20.GL_ONE)"));
        assertTrue(particles.contains("POT_GOLD"));
        assertTrue(particles.contains("CYAN"));
        assertFalse(particles.contains("shapes.triangle("));
        assertFalse(winnerGlow.contains("shapes.circle(avatarX"));
        assertTrue(table.contains(
                "&& !Boolean.TRUE.equals(settledWinner)"));
        assertFalse(table.contains("winnerAuraShader"));
        assertFalse(table.contains("winnerAuraTexture"));
        assertFalse(table.contains("winnerCrestTexture"));
        assertFalse(table.contains("WINNER_CREST"));
    }

    @Test
    void seatAndTimeoutLightUseFeatheredGlowInsteadOfVisibleRings()
            throws IOException {
        String table = Files.readString(source());
        String active = method(table,
                "private void drawActiveSeatGlows()",
                "private void drawAllInSeatFlames()");
        String localHud = method(table,
                "private void drawLocalHud(float width, float height)",
                "private void drawPendingRebuyTooltip(");
        String callCost = method(table,
                "private void drawCallCostText(",
                "private void drawPreparationOverlay()");

        assertTrue(active.contains("drawSoftRoundedGlow("));
        assertFalse(active.contains("drawSoftCircleGlow("));
        assertFalse(active.contains("shapes.circle(avatarX"));
        assertTrue(localHud.contains("drawSoftRoundedGlow("));
        assertTrue(localHud.contains("GL20.GL_SRC_ALPHA, GL20.GL_ONE"));
        assertTrue(localHud.contains("winnerGlow || danger || localTurn"));
        assertTrue(localHud.contains("WINNER_NEON_GREEN"));
        assertTrue(localHud.contains("ACTIVE_TURN_GOLD"));
        assertTrue(localHud.contains("hudWidth + 12f, hudHeight + 21f"));
        assertTrue(localHud.contains(
                "glowColor, glowStrength * localSeatOpacity"));
        assertFalse(localHud.contains("hudX - 22f"));
        assertFalse(localHud.contains("hudX - 14f"));
        assertTrue(callCost.contains("isActionableTurn(snapshot"));
        assertTrue(callCost.contains("callCostBorderTint("));
        assertFalse(callCost.contains("liveAnimationsEnabled()"));
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
