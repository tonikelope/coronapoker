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
        assertTrue(layout.closeButton().x > layout.panel().x
                + layout.panel().width / 2f);
        assertTrue(layout.closeButton().y > layout.panel().y
                + layout.panel().height / 2f,
                "the block list uses the same top-right close affordance as the game log");

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

        var trackClick = GdxScrollbarDrag.begin(
                layout.scrollbar().y + layout.scrollbar().height * 0.25f,
                layout.scrollbar().y, layout.scrollbar().height,
                46f, 0f, true);
        assertTrue(trackClick.ratio() > 0f,
                "clicking empty scrollbar track must reposition and capture the thumb");
    }

    @Test void remainingTimeRoundsUpSoAValidBlockNeverShowsZeroEarly() {
        assertEquals("1 s", GdxNetworkBlockDialogLayout.remaining(1L));
        assertEquals("59 s", GdxNetworkBlockDialogLayout.remaining(59_000L));
        assertEquals("1 min 1 s",
                GdxNetworkBlockDialogLayout.remaining(60_001L));
        assertEquals("0 s", GdxNetworkBlockDialogLayout.remaining(0L));
    }
}
