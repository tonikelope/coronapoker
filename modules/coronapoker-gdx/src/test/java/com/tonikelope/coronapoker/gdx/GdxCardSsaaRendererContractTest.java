/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GdxCardSsaaRendererContractTest {

    @Test
    void usesTwoByTwoScreenSpaceSamplingWithSafeDimensionChecks() {
        assertEquals(2, GdxCardSsaaRenderer.linearScaleForSamples(4));
        assertEquals(3, GdxCardSsaaRenderer.linearScaleForSamples(9));
        assertEquals(4, GdxCardSsaaRenderer.linearScaleForSamples(16));
        assertEquals(0, GdxCardSsaaRenderer.linearScaleForSamples(8));
        assertEquals(7_680,
                GdxCardSsaaRenderer.supersampledDimension(3_840, 4));
        assertEquals(-1, GdxCardSsaaRenderer.supersampledDimension(0, 4));
        assertEquals(-1, GdxCardSsaaRenderer.supersampledDimension(
                Integer.MAX_VALUE, 4));
        assertTrue(GdxCardSsaaRenderer.fitsMaximumTextureSize(
                3_840, 2_160, 4, 8_192));
        assertFalse(GdxCardSsaaRenderer.fitsMaximumTextureSize(
                4_097, 2_160, 4, 8_192));
    }

    @Test
    void tableRoutesPrivateAndCommunityCardsThroughTheSameSsaaLayer()
            throws IOException {
        String table = Files.readString(source("CoronaPokerGdxTable.java"));
        String holeCards = method(table,
                "private void drawHoleCards(boolean foregroundRevealFlights)",
                "private boolean beginCardSsaaLayer()");
        String community = method(table,
                "private void drawCardsAndPot(float cx, float cy, float tableWidth)",
                "private void drawPotPanelSurface()");

        assertTrue(holeCards.contains("beginCardSsaaLayer()"));
        assertTrue(holeCards.contains(
                "drawLiveHoleCards(foregroundRevealFlights)"));
        assertTrue(holeCards.contains("finishCardSsaaLayer()"));
        assertTrue(community.contains("beginCardSsaaLayer()"));
        assertTrue(community.contains("drawLiveCommunityCards("));
        assertTrue(community.contains("finishCardSsaaLayer()"));
    }

    @Test
    void tableKeepsEveryLiveCardFamilyInsideAnSsaaOwnedPass()
            throws IOException {
        String table = Files.readString(source("CoronaPokerGdxTable.java"));
        String scene = method(table, "private void drawTableScene()",
                "private void drawTableBranding(float height)");
        String holeCards = method(table,
                "private void drawLiveHoleCards(boolean foregroundFlights)",
                "static boolean shouldCompositeDisabledHoleCards(");
        String communityCards = method(table,
                "private void drawLiveCommunityCards(float cx, float cardY, float cardW,",
                "private boolean hasActiveCommunityFlight(int slot)");

        int rearHoleCards = scene.indexOf("drawHoleCards(false)");
        int seats = scene.indexOf("drawSeats()", rearHoleCards);
        int community = scene.indexOf("drawCardsAndPot(", seats);
        int foregroundHoleCards = scene.indexOf("drawHoleCards(true)",
                community);
        assertTrue(rearHoleCards >= 0 && seats > rearHoleCards
                        && community > seats
                        && foregroundHoleCards > community,
                "rear hands, seats, board and foreground flights must retain "
                + "their intentional depth order");

        assertTrue(holeCards.contains("drawDisabledHoleCardsLayer("),
                "dimmed showdown hands must remain in the hole-card pass");
        assertTrue(holeCards.contains("drawLiveHoleFoldLayer("),
                "fold fades must remain in the hole-card pass");
        assertTrue(holeCards.contains("drawLiveHoleSwap("),
                "swapping hands must remain in the hole-card pass");
        assertTrue(holeCards.contains("drawLiveHoleRevealResting("),
                "resting IWTSTH cards must remain in the hole-card pass");
        assertTrue(holeCards.contains("drawLiveRestingCard("),
                "local and remote resting cards share the hole-card pass");
        assertTrue(holeCards.contains("TableVisualEvent.DealHoleCard"),
                "deal flights must remain in the foreground hole-card pass");
        assertTrue(holeCards.contains("drawLiveHoleRevealFlights("),
                "IWTSTH reveal flights must remain in the foreground pass");

        assertTrue(communityCards.contains("drawLiveRabbitRevealCard("),
                "Rabbit peel animations must remain in the board pass");
        assertTrue(communityCards.contains("drawLiveCommunityRevealCard("),
                "flop/turn/river reveals must remain in the board pass");
        assertTrue(communityCards.contains("drawLiveRestingCard("),
                "resting community and Rabbit cards share the board pass");
        assertTrue(communityCards.contains(
                "TableVisualEvent.DealCommunityCard"),
                "community deal flights must remain in the board pass");
    }

    @Test
    void nestedFoldAndShowdownLayerUsesTheOuterSsaaResolution()
            throws IOException {
        String table = Files.readString(source("CoronaPokerGdxTable.java"));
        String ensure = method(table,
                "private void ensureDisabledHoleCardsLayer()",
                "private void drawLiveHoleFoldLayer(Texture cardBack)");
        String restore = method(table,
                "private void restoreParentFramebufferAfterNestedPass()",
                "private void drawLiveCommunityCards(float cx, float cardY, float cardW,");

        assertTrue(ensure.contains("cardSsaaRenderer.activeWidth()"));
        assertTrue(ensure.contains("cardSsaaRenderer.activeHeight()"));
        assertTrue(restore.contains("cardSsaaRenderer.resumeAfterNestedPass()"),
                "the nested fold/showdown pass must return to the outer SSAA "
                + "framebuffer instead of the physical backbuffer");
    }

    @Test
    void everyCardPassReadsTheCurrentLiveSettingAndPhysicalBackbuffer()
            throws IOException {
        String table = Files.readString(source("CoronaPokerGdxTable.java"));
        String begin = method(table,
                "private boolean beginCardSsaaLayer()",
                "private void finishCardSsaaLayer()");

        assertTrue(begin.contains(
                "presentationSettings.requestedCardSsaaSamples()"));
        assertTrue(begin.contains("Gdx.graphics.getBackBufferWidth()"));
        assertTrue(begin.contains("Gdx.graphics.getBackBufferHeight()"));
        assertFalse(begin.contains("settingsOpened"),
                "rendering must not use the stale value captured when the "
                + "settings dialog opened");
    }

    @Test
    void neutralCardShadersDoNotRetainTheSupersededArtworkHeuristic()
            throws IOException {
        String table = Files.readString(source("CoronaPokerGdxTable.java"));

        assertFalse(table.contains("cardArtworkLodBias"));
        assertFalse(table.contains("cardArtworkContrast"));
        assertFalse(table.contains("CARD_ARTWORK_EDGE_AA"));
        assertFalse(table.contains("u_backTextureSize"));
        assertTrue(CoronaPokerGdxTable.CARD_FRAGMENT_SHADER.contains(
                "texture2D(u_frontTexture, sourceUv)"));
        assertTrue(CoronaPokerGdxTable.RABBIT_PEEL_FRAGMENT_SHADER.contains(
                "texture2D(u_overlayTexture, uv)"));
    }

    @Test
    void resolveUsesPremultipliedAlphaToProtectTransparentCardEdges()
            throws IOException {
        String ssaa = Files.readString(source("GdxCardSsaaRenderer.java"));

        assertTrue(ssaa.contains("useStraightAlphaCapture(batch)"));
        assertTrue(ssaa.contains("usePremultipliedComposite(batch)"));
        assertTrue(ssaa.contains("Gdx.gl.glBlendFuncSeparate"),
                "an idle SpriteBatch must not leak premultiplied blending "
                + "into ShapeRenderer or the HUD");
        assertTrue(ssaa.contains("GL20.GL_ONE_MINUS_SRC_ALPHA"));
        assertTrue(ssaa.contains("TextureFilter.Nearest, TextureFilter.Nearest"));
        assertTrue(ssaa.contains("tap(-1.5, -1.5)"),
                "the highest setting must resolve all sixteen subpixels");
        int composite = ssaa.indexOf("batch.draw(resolved");
        int effective = ssaa.indexOf("effectiveSamples = activeSamples");
        assertTrue(composite >= 0 && effective > composite,
                "the UI must report only levels actually resolved by GPU");
        assertTrue(ssaa.contains("effectiveSamples = 0"),
                "failed allocations must be visible instead of silently "
                + "pretending SSAA is active");
        assertTrue(ssaa.contains("lastAttemptedSamples == requestedSamples"),
                "a just-clicked level must not flash as unavailable before "
                + "the next live frame tries it");
    }

    private static String method(String source, String startText,
            String endText) {
        int start = source.indexOf(startText);
        int end = source.indexOf(endText, start);
        assertTrue(start >= 0 && end > start);
        return source.substring(start, end);
    }

    private static Path source(String filename) {
        return Path.of("src", "main", "java", "com", "tonikelope",
                "coronapoker", "gdx", filename);
    }
}
