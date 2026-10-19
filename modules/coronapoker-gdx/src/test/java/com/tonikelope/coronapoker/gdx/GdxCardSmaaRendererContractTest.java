/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GdxCardSmaaRendererContractTest {

    @Test
    void keepsEveryTargetAtNativeResolutionWithSafeDimensionChecks() {
        assertTrue(GdxCardSmaaRenderer.fitsMaximumTextureSize(
                3_840, 2_160, 8_192));
        assertFalse(GdxCardSmaaRenderer.fitsMaximumTextureSize(
                8_193, 2_160, 8_192));
        assertFalse(GdxCardSmaaRenderer.fitsMaximumTextureSize(
                3_840, 0, 8_192));
    }

    @Test
    void tableRoutesPrivateAndCommunityCardsThroughTheSameSmaaLayer()
            throws IOException {
        String table = Files.readString(source("CoronaPokerGdxTable.java"));
        String holeCards = method(table,
                "private void drawHoleCards(boolean foregroundRevealFlights)",
                "private boolean beginCardSmaaLayer()");
        String community = method(table,
                "private void drawCardsAndPot(float cx, float cy, float tableWidth)",
                "private void drawPotPanelSurface()");

        assertTrue(holeCards.contains("beginCardSmaaLayer()"));
        assertTrue(holeCards.contains(
                "drawLiveHoleCards(foregroundRevealFlights)"));
        assertTrue(holeCards.contains("finishCardSmaaLayer()"));
        assertTrue(community.contains("beginCardSmaaLayer()"));
        assertTrue(community.contains("drawLiveCommunityCards("));
        assertTrue(community.contains("finishCardSmaaLayer()"));
    }

    @Test
    void tableKeepsEveryLiveCardFamilyInsideAnSmaaOwnedPass()
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
                "card layers must retain their intentional depth order");

        assertTrue(holeCards.contains("drawDisabledHoleCardsLayer("));
        assertTrue(holeCards.contains("drawLiveHoleFoldLayer("));
        assertTrue(holeCards.contains("drawLiveHoleSwap("));
        assertTrue(holeCards.contains("drawLiveHoleRevealResting("));
        assertTrue(holeCards.contains("drawLiveRestingCard("));
        assertTrue(holeCards.contains("TableVisualEvent.DealHoleCard"));
        assertTrue(holeCards.contains("drawLiveHoleRevealFlights("));

        assertTrue(communityCards.contains("drawLiveRabbitRevealCard("));
        assertTrue(communityCards.contains("drawLiveCommunityRevealCard("));
        assertTrue(communityCards.contains("drawLiveRestingCard("));
        assertTrue(communityCards.contains(
                "TableVisualEvent.DealCommunityCard"));
    }

    @Test
    void nestedFoldAndShowdownLayerUsesTheOuterSmaaTarget()
            throws IOException {
        String table = Files.readString(source("CoronaPokerGdxTable.java"));
        String ensure = method(table,
                "private void ensureDisabledHoleCardsLayer()",
                "private void drawLiveHoleFoldLayer(Texture cardBack)");
        String restore = method(table,
                "private void restoreParentFramebufferAfterNestedPass()",
                "private void drawLiveCommunityCards(float cx, float cardY, float cardW,");

        assertTrue(ensure.contains("cardSmaaRenderer.activeWidth()"));
        assertTrue(ensure.contains("cardSmaaRenderer.activeHeight()"));
        assertTrue(restore.contains("cardSmaaRenderer.resumeAfterNestedPass()"));
    }

    @Test
    void everyCardPassReadsTheCurrentLiveSettingAndPhysicalBackbuffer()
            throws IOException {
        String table = Files.readString(source("CoronaPokerGdxTable.java"));
        String begin = method(table,
                "private boolean beginCardSmaaLayer()",
                "private void finishCardSmaaLayer()");

        assertTrue(begin.contains(
                "presentationSettings.requestedCardRenderingMode()"));
        assertTrue(begin.contains("Gdx.graphics.getBackBufferWidth()"));
        assertTrue(begin.contains("Gdx.graphics.getBackBufferHeight()"));
        assertFalse(begin.contains("settingsOpened"));
    }

    @Test
    void smoothModePrefiltersSharedCardArtworkBeforeRasterization()
            throws IOException {
        String table = Files.readString(source("CoronaPokerGdxTable.java"));
        String artwork = method(table,
                "private static final String CARD_ARTWORK_SAMPLING_GLSL",
                "static final String CARD_FRAGMENT_SHADER");
        String regular = method(table,
                "private void configureCardShader(Texture front, Texture cardBack,",
                "private void setCardShowdownOverlay(boolean highlighted)");
        String rabbit = method(table,
                "private void useRabbitPeelShader(Texture front, float progress,",
                "private void configureCardShader(Texture front, float angle,");

        assertEquals(16, CoronaPokerGdxTable.cardArtworkSmoothSampleCount());
        assertTrue(artwork.contains("uniform float u_artworkSmooth"));
        assertTrue(artwork.contains("if (u_artworkSmooth < 0.5) {"));
        assertTrue(artwork.contains("vec2 uvDx = dFdx(uv)"));
        assertTrue(artwork.contains("vec2 uvDy = dFdy(uv)"));
        assertEquals(16, occurrences(artwork,
                "resolved += texture2D(artwork"));
        assertTrue(artwork.contains("return resolved * 0.0625"));
        assertFalse(artwork.toLowerCase().contains("sharpen"));
        assertTrue(regular.contains(
                "setUniformf(\"u_artworkSmooth\""));
        assertTrue(rabbit.contains(
                "setUniformf(\"u_artworkSmooth\""));
        assertTrue(table.contains(
                "mode == GdxCardSmaaRenderer.RENDERING_SMOOTH ? 1f : 0f"));
    }

    @Test
    void usesOfficialThreePassUltraSmaaWithoutSupersampling()
            throws IOException {
        String smaa = Files.readString(source("GdxCardSmaaRenderer.java"));
        String blend = Files.readString(resource("smaa-blend.frag"));

        assertTrue(smaa.contains("SMAA_PRESET_ULTRA"));
        assertTrue(smaa.contains("SMAA_EDGES_COLOR"));
        assertTrue(smaa.contains("edgesShader.begin()"));
        assertTrue(smaa.contains("weightsShader.begin()"));
        assertTrue(smaa.contains("blendShader.begin()"));
        assertTrue(smaa.contains("area.png"));
        assertTrue(smaa.contains("search.png"));
        assertTrue(smaa.contains("TextureFilter.Linear"));
        assertFalse(smaa.contains("supersampledDimension"));
        assertFalse(smaa.contains("u_linearScale"));
        assertEquals(0.72f,
                GdxCardSmaaRenderer.interiorEdgeBlendStrength());
        assertTrue(blend.contains(
                "color = mix(original, color, resolveStrength)"));
        assertTrue(blend.contains(
                "float resolveStrength = mix(uInteriorEdgeBlendStrength, 1.0"));
        assertFalse(blend.contains("thinStroke"));
        assertFalse(blend.contains("sharpen"),
                "selective SMAA must not add an image-sharpening pass");

        assertTrue(Files.isRegularFile(resource("area.png")));
        assertTrue(Files.isRegularFile(resource("search.png")));
        assertTrue(Files.isRegularFile(resource("LICENSE.txt")));
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    @Test
    void preservesPremultipliedAlphaAndRestoresSharedGlState()
            throws IOException {
        String smaa = Files.readString(source("GdxCardSmaaRenderer.java"));

        assertTrue(smaa.contains("useStraightAlphaCapture(batch)"));
        assertTrue(smaa.contains("usePremultipliedComposite(batch)"));
        assertTrue(smaa.contains("Gdx.gl.glBlendFuncSeparate"));
        assertTrue(smaa.contains("GL20.GL_ONE_MINUS_SRC_ALPHA"));
        assertTrue(smaa.contains("Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)"));
        int composite = smaa.indexOf(
                "fullscreenTriangle.render(blendShader");
        int effective = smaa.indexOf(
                "effectiveRenderingMode = activeRenderingMode");
        assertTrue(composite >= 0 && effective > composite);
        assertTrue(smaa.contains(
                "effectiveRenderingMode = RENDERING_NATIVE"));
        assertTrue(smaa.contains(
                "lastAttemptedRenderingMode == requestedRenderingMode"));
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

    private static Path resource(String filename) {
        return Path.of("src", "main", "resources", "smaa", filename);
    }
}
