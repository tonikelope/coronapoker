/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Rectangle;
import org.junit.jupiter.api.Test;

final class GdxNetworkBlockDialogLayoutTest {

    @Test void dialogStaysCenteredAndRowsScrollInsideTheirViewport() {
        var layout = GdxNetworkBlockDialogLayout.layout(1_920f, 1_080f);
        assertEquals((1_920f - layout.panel().width) / 2f,
                layout.panel().x, 0.01f);
        assertEquals((1_080f - layout.panel().height) / 2f,
                layout.panel().y, 0.01f);

        Rectangle rows = layout.rows();
        float maximum = GdxNetworkBlockDialogLayout.maximumScroll(12, rows);
        assertTrue(maximum > 0f);
        assertEquals(0f, GdxNetworkBlockDialogLayout.clampScroll(
                -100f, 12, rows), 0.01f);
        assertEquals(maximum, GdxNetworkBlockDialogLayout.clampScroll(
                maximum + 100f, 12, rows), 0.01f);
        assertEquals(GdxNetworkBlockDialogLayout.rowStride(),
                GdxNetworkBlockDialogLayout.row(rows, 0, maximum).y
                - GdxNetworkBlockDialogLayout.row(rows, 1, maximum).y,
                0.01f);
    }

    @Test void remainingTimeRoundsUpSoAValidBlockNeverShowsZeroEarly() {
        assertEquals("1 s", GdxNetworkBlockDialogLayout.remaining(1L));
        assertEquals("59 s", GdxNetworkBlockDialogLayout.remaining(59_000L));
        assertEquals("1 min 1 s",
                GdxNetworkBlockDialogLayout.remaining(60_001L));
        assertEquals("0 s", GdxNetworkBlockDialogLayout.remaining(0L));
    }
}
