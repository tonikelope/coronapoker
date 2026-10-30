/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GdxCircularCoverageContractTest {

    @Test
    void circleCoverageUsesOneContinuousDiskWithoutQuadrantSeams()
            throws Exception {
        String primitive = source("GdxAntialiasedRoundedRect.java");
        assertTrue(primitive.contains(
                "private static final int CIRCLE_SEGMENTS = CORNER_SEGMENTS * 4;"));
        assertTrue(primitive.contains("circleBand(shapes, centerX, centerY,"));
        assertTrue(primitive.contains(
                "hardCircle(shapes, centerX, centerY, radius - half, FULL);"));
        assertFalse(primitive.contains(
                "fill(shapes, centerX - radius, centerY - radius,"));
    }

    @Test
    void latencyIndicatorsUseCoverageAntialiasingWithoutMovingTheirGeometry()
            throws Exception {
        String table = source("CoronaPokerGdxTable.java");
        assertTrue(table.contains(
                "GdxAntialiasedRoundedRect.fillCircle(shapes, x, y, 10f);"));
        assertTrue(table.contains(
                "GdxAntialiasedRoundedRect.fillCircle(shapes, x, y, 8f);"));
        assertTrue(table.contains(
                "x + 8f, y - 8f, 7f);"));
        assertFalse(table.contains("shapes.circle(x, y, 10f, 28);"));
        assertFalse(table.contains("shapes.circle(x, y, 8f, 28);"));

        String frontend = source("GdxFrontendScreen.java");
        assertTrue(frontend.contains(
                "x + 55f, y + h / 2f, 4f);"));
        assertFalse(frontend.contains(
                "shapes.circle(x + 55f, y + h / 2f, 4f, 20);"));
    }

    @Test
    void profileRingsCropHandlesAndToggleKnobsShareTheCoveragePrimitive()
            throws Exception {
        String frontend = source("GdxFrontendScreen.java");
        assertTrue(frontend.contains(
                "GdxAntialiasedRoundedRect.fillCircle(shapes, 520f, 465f, 70f);"));
        assertTrue(frontend.contains(
                "GdxAntialiasedRoundedRect.fillCircle(shapes, 520f, 465f, 61f);"));
        assertTrue(frontend.contains(
                "GdxAntialiasedRoundedRect.fillCircle(shapes, 380f, 487f, 60f);"));
        assertTrue(frontend.contains(
                "GdxAntialiasedRoundedRect.fillCircle(shapes, 380f, 487f, 52f);"));
        assertTrue(frontend.contains(
                "left, bottom, handle);"));
        assertTrue(frontend.contains(
                "right, top, handle);"));
        assertFalse(frontend.contains(
                "shapes.circle(left, bottom, handle, 24);"));
        assertFalse(frontend.contains(
                "shapes.circle(520f, 465f"));
        assertFalse(frontend.contains(
                "shapes.circle(380f, 487f"));
        assertTrue(frontend.contains(
                "Texture loaded = GdxTextureQuality.loadUi("));
        assertTrue(frontend.contains(
                "Texture texture = GdxTextureQuality.loadUi("));

        String settings = source("GdxSettingsStyle.java");
        assertTrue(settings.contains(
                "GdxAntialiasedRoundedRect.fillCircle(shapes,"));
        assertTrue(settings.contains("TOGGLE_KNOB_RADIUS);"));
        assertFalse(settings.contains("TOGGLE_KNOB_RADIUS, 32);"));

        String table = source("CoronaPokerGdxTable.java");
        assertTrue(table.contains(
                "quickChatAutoClose ? switchX + 28f : switchX + 10f,"));
        assertTrue(table.contains("switchY + 10f, 7f);"));
    }

    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/" + name), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
