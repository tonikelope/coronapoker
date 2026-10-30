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
    void winnerPresentationKeepsItsFeatheredBacklightBelowSeatContent()
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
    void everyWinnerAndTurnUsesTheRemoteSeatGlowLanguage()
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
                "private void drawSeatHalo(");
        String sharedGlow = method(table,
                "private void drawSeatHalo(",
                "private boolean seatTurnDangerActive(");
        String particles = method(table,
                "private void drawLiveWinnerParticles()",
                "private float liveWinnerEffectProgress(");
        String localHud = method(table,
                "private void drawLocalHud(float width, float height)",
                "private void drawPendingRebuyTooltip(");

        assertTrue(effects.contains("drawLiveWinnerParticles()"));
        assertFalse(table.contains("drawLiveWinnerStreaks()"));
        assertTrue(winnerGlow.contains("drawSeatHalo("));
        assertTrue(activeGlow.contains("drawSeatHalo("));
        assertTrue(winnerGlow.contains("seatHaloPulse(totalTime,"));
        assertTrue(activeGlow.contains("seatHaloPulse(totalTime,"));
        assertTrue(winnerGlow.contains("seatHaloStrength(pulse)"));
        assertTrue(activeGlow.contains("seatHaloStrength(pulse)"));
        assertTrue(remoteGlow.contains("drawSoftRoundedGlow("));
        assertFalse(winnerGlow.contains("winner.index == 0"));
        assertTrue(remoteGlow.contains("seat.podX - 6f"));
        assertTrue(remoteGlow.contains("PLAYER_POD_WIDTH + 12f"));
        assertTrue(remoteGlow.contains("PLAYER_POD_HEIGHT + 12f"));
        assertTrue(remoteGlow.contains("17f, 24f, color, strength"));
        assertTrue(sharedGlow.contains("drawRemoteSeatHalo("));
        assertTrue(sharedGlow.contains("localHudLayout("));
        assertTrue(sharedGlow.contains("hud.x() - 8f"));
        assertTrue(sharedGlow.contains("hud.width() + 16f"));
        assertTrue(sharedGlow.contains("19f, 24f, color, strength"));
        assertTrue(winnerGlow.contains("WINNER_NEON_GREEN"));
        assertTrue(winnerGlow.contains("GL20.GL_SRC_ALPHA, GL20.GL_ONE"));
        assertFalse(winnerGlow.contains("drawSoftRoundedOutlineGlow("));
        assertFalse(localHud.contains("drawSoftRoundedGlow("));
        assertFalse(localHud.contains("seatHaloStrength("));
        assertTrue(particles.contains("GL20.GL_SRC_ALPHA, GL20.GL_ONE"));
        assertTrue(particles.contains("POT_GOLD"));
        assertTrue(particles.contains("CYAN"));
        assertFalse(particles.contains("shapes.triangle("));
        assertFalse(winnerGlow.contains("shapes.circle(avatarX"));
        assertFalse(table.contains("winnerAuraShader"));
        assertFalse(table.contains("winnerAuraTexture"));
        assertFalse(table.contains("winnerCrestTexture"));
        assertFalse(table.contains("WINNER_CREST"));
    }

    @Test
    void stateColorsShareOneFeatheredHaloWithoutVisibleRings()
            throws IOException {
        String table = Files.readString(source());
        String seats = method(table,
                "private void drawSeats()",
                "private void drawSidePotWinnerOverlays()");
        String active = method(table,
                "private void drawActiveSeatGlows()",
                "private void drawAllInSeatFlames()");
        String sharedGlow = method(table,
                "private void drawSeatHalo(",
                "private boolean seatTurnDangerActive(");
        String localHud = method(table,
                "private void drawLocalHud(float width, float height)",
                "private void drawPendingRebuyTooltip(");
        String callCost = method(table,
                "private void drawCallCostText(",
                "private void drawPreparationOverlay()");

        assertTrue(active.contains("drawSeatHalo("));
        assertTrue(active.contains("seatTurnDangerActive(seat)"));
        assertTrue(active.contains("danger ? FOLD_RED : ACTIVE_TURN_GOLD"));
        assertFalse(active.contains("drawSoftCircleGlow("));
        assertFalse(active.contains("shapes.circle(avatarX"));
        assertTrue(sharedGlow.contains("drawSoftRoundedGlow("));
        assertFalse(localHud.contains("drawSoftRoundedGlow("));
        assertTrue(localHud.contains("settledShowdownColor("));
        assertTrue(seats.contains("settledShowdownColor("));
        assertTrue(localHud.contains("0.78f + haloPulse"));
        assertTrue(localHud.contains("0.72f + haloPulse"));
        assertTrue(localHud.contains("0.58f + haloPulse * 0.06f"));
        assertFalse(seats.contains("shapes.circle(avatarX"),
                "avatar discs must not fall back to hard polygon edges");
        assertTrue(seats.contains("avatarX - AVATAR_OUTER_RADIUS"));
        assertTrue(seats.contains("avatarX - AVATAR_RIM_RADIUS"));
        assertTrue(seats.contains("avatarX - AVATAR_INNER_RADIUS"));
        assertTrue(seats.contains("GdxAntialiasedRoundedRect.fill(shapes"),
                "all avatar layers use the narrow coverage fringe shared by "
                + "the rest of the premium UI chrome");
        assertTrue(callCost.contains("isActionableTurn(snapshot"));
        assertTrue(callCost.contains("callCostBorderTint("));
        assertFalse(callCost.contains("liveAnimationsEnabled()"));
    }

    @Test
    void activeRemoteAvatarUsesTheExactSeatAndHaloTurnAccent()
            throws IOException {
        String table = Files.readString(source());
        String seats = method(table,
                "private void drawSeats()",
                "private void drawSidePotWinnerOverlays()");
        String avatarRim = method(table,
                "static Color avatarRimColor(",
                "static Color turnAwareActionColor(");
        String turnAccent = method(table,
                "static Color turnAwareActionColor(",
                "static Color liveActionTextColor(");
        String activeGlow = method(table,
                "private void drawActiveSeatGlows()",
                "private void drawRemoteSeatHalo(");

        assertTrue(seats.contains("boolean active = isSeatActive(seat)"));
        assertTrue(seats.contains("boolean partialPresentation = "
                + "!settledShowdown"));
        assertTrue(seats.contains("Boolean partialWinner = partialPresentation"));
        assertTrue(seats.contains("settledShowdown, partialWinner, "
                + "active,"),
                "the renderer must feed authoritative partial-result and turn "
                + "ownership into the avatar-ring resolver");
        assertTrue(seats.contains("active ? ACTIVE_TURN_GOLD : SEAT_RIM"));
        assertTrue(avatarRim.contains("if (partialWinner != null)"));
        assertTrue(avatarRim.contains("return partialHandColor(partialWinner)"));
        assertTrue(avatarRim.contains("if (activeTurn)"));
        assertTrue(avatarRim.indexOf("if (partialWinner != null)")
                < avatarRim.indexOf("if (activeTurn)"),
                "a Monte Carlo result must outrank stale turn ownership");
        assertTrue(avatarRim.contains(
                "turnAwareActionColor(true, semanticColor)"));
        assertTrue(turnAccent.contains(
                "return activeTurn ? ACTIVE_TURN_GOLD : semanticColor"));
        assertTrue(activeGlow.contains(
                "danger ? FOLD_RED : ACTIVE_TURN_GOLD"));
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
