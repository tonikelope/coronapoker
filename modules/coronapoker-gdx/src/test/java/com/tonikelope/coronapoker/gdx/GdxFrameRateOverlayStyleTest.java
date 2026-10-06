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
        float x = GdxFrameRateOverlayStyle.x(1920f, false);
        float y = GdxFrameRateOverlayStyle.y(1080f);

        assertEquals(12f, 1920f - x - GdxFrameRateOverlayStyle.WIDTH);
        assertEquals(10f, 1080f - y - GdxFrameRateOverlayStyle.HEIGHT);
        assertEquals(38f, GdxFrameRateOverlayStyle.HEIGHT);
        assertTrue(x > 0f && y > 0f);
    }

    @Test
    void finalSummaryStillReservesItsTopRightControl() {
        float x = GdxFrameRateOverlayStyle.x(1920f, true);

        assertEquals(GdxFrameRateOverlayStyle.FINAL_SUMMARY_RIGHT_MARGIN,
                1920f - x - GdxFrameRateOverlayStyle.WIDTH);
    }

    @Test
    void tableCounterAlsoOmitsTheOldCyanUnderline() throws IOException {
        String table = Files.readString(Path.of("src", "main", "java",
                "com", "tonikelope", "coronapoker", "gdx",
                "CoronaPokerGdxTable.java"));
        int start = table.indexOf("private void drawFpsCounter(");
        int end = table.indexOf("private void drawCentered(", start);
        String method = table.substring(start, end);

        assertTrue(method.contains("GdxFrameRateOverlayStyle.x("));
        assertFalse(method.contains("shapes.rect("),
                "the table overlay must not draw the old cyan underline");
    }
}
