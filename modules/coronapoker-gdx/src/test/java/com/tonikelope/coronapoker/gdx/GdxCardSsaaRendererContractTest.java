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
