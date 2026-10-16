/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GdxFrameRateOverlayStyleTest {

    @Test
    void counterSitsNearTheTopRightWithoutTouchingTheScreenEdges() {
        float x = GdxFrameRateOverlayStyle.x(1920f);
        float y = GdxFrameRateOverlayStyle.y(1080f);

        assertEquals(12f, 1920f - x - GdxFrameRateOverlayStyle.WIDTH);
        assertEquals(10f, 1080f - y - GdxFrameRateOverlayStyle.HEIGHT);
        assertEquals(38f, GdxFrameRateOverlayStyle.HEIGHT);
        assertTrue(x > 0f && y > 0f);
    }

    @Test
    void everyScreenUsesTheMainScreenRightEdgeAlignment() {
        float mainScreenX = GdxFrameRateOverlayStyle.x(1920f);
        float tableX = GdxFrameRateOverlayStyle.x(1920f);
        float finalSummaryX = GdxFrameRateOverlayStyle.x(1920f);

        assertEquals(mainScreenX, tableX);
        assertEquals(mainScreenX, finalSummaryX);
        assertEquals(GdxFrameRateOverlayStyle.RIGHT_MARGIN,
                1920f - finalSummaryX - GdxFrameRateOverlayStyle.WIDTH);
    }

    @Test
    void everySurfaceDelegatesTheWholeBadgeToOneRenderer() throws IOException {
        String frontend = Files.readString(Path.of("src", "main", "java",
                "com", "tonikelope", "coronapoker", "gdx",
                "GdxFrontendScreen.java"));
        String table = Files.readString(Path.of("src", "main", "java",
                "com", "tonikelope", "coronapoker", "gdx",
                "CoronaPokerGdxTable.java"));
        String frontendMethod = method(frontend,
                "private void drawFrontendFpsCounter()",
                "private void drawFittedCenteredInBox(");
        String tableMethod = method(table, "private void drawFpsCounter(",
                "private void drawCentered(");

        assertTrue(frontendMethod.contains("GdxFrameRateOverlayStyle.draw("));
        assertTrue(tableMethod.contains("GdxFrameRateOverlayStyle.draw("));
        assertFalse(frontendMethod.contains("roundedRect("));
        assertFalse(tableMethod.contains("roundedRect("));
        assertFalse(tableMethod.contains("gameLogFont"));
    }

    @Test
    void bothRenderersBuildTheExactSameDedicatedFont() throws IOException {
        String frontend = Files.readString(Path.of("src", "main", "java",
                "com", "tonikelope", "coronapoker", "gdx",
                "GdxFrontendScreen.java"));
        String table = Files.readString(Path.of("src", "main", "java",
                "com", "tonikelope", "coronapoker", "gdx",
                "CoronaPokerGdxTable.java"));

        assertTrue(frontend.contains(
                "nextFps = GdxFrameRateOverlayStyle.font(profile)"));
        assertTrue(table.contains(
                "fpsFont = GdxFrameRateOverlayStyle.font("));
        assertTrue(table.contains("GdxFrameRateOverlayStyle.draw(shapes, batch, fpsFont"));
    }

    private static String method(String source, String startMarker,
            String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(start >= 0 && end > start);
        return source.substring(start, end);
    }
}
