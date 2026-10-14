/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Rectangle;
import org.junit.jupiter.api.Test;

final class GdxFloatingGameLogLayoutTest {

    private static final float EPSILON = 0.001f;

    @Test
    void initialWindowIsCenteredAndFullyVisible() {
        Rectangle panel = GdxFloatingGameLogLayout.initial(1920f, 1080f);

        assertEquals(1160f, panel.width, EPSILON);
        assertEquals(720f, panel.height, EPSILON);
        assertEquals(380f, panel.x, EPSILON);
        assertEquals(180f, panel.y, EPSILON);
        assertInsideScreen(panel, 1920f, 1080f);
    }

    @Test
    void movingWindowClampsEveryEdgeToTheGameViewport() {
        Rectangle start = new Rectangle(380f, 180f, 1160f, 720f);

        Rectangle upperRight = GdxFloatingGameLogLayout.moved(start,
                5000f, 5000f, 1920f, 1080f);
        Rectangle lowerLeft = GdxFloatingGameLogLayout.moved(start,
                -5000f, -5000f, 1920f, 1080f);

        assertInsideScreen(upperRight, 1920f, 1080f);
        assertInsideScreen(lowerLeft, 1920f, 1080f);
        assertEquals(GdxFloatingGameLogLayout.SCREEN_MARGIN,
                lowerLeft.x, EPSILON);
        assertEquals(GdxFloatingGameLogLayout.SCREEN_MARGIN,
                lowerLeft.y, EPSILON);
    }

    @Test
    void lowerRightResizeKeepsTopLeftAnchorAndHonoursMinimums() {
        Rectangle start = new Rectangle(380f, 180f, 900f, 600f);

        Rectangle larger = GdxFloatingGameLogLayout
                .resizedFromLowerRight(start, 240f, -100f,
                        1920f, 1080f);
        Rectangle minimum = GdxFloatingGameLogLayout
                .resizedFromLowerRight(start, -5000f, 5000f,
                        1920f, 1080f);

        assertEquals(start.x, larger.x, EPSILON);
        assertEquals(start.y + start.height,
                larger.y + larger.height, EPSILON);
        assertTrue(minimum.width >= GdxFloatingGameLogLayout.MIN_WIDTH);
        assertTrue(minimum.height >= GdxFloatingGameLogLayout.MIN_HEIGHT);
        assertInsideScreen(larger, 1920f, 1080f);
        assertInsideScreen(minimum, 1920f, 1080f);
    }

    @Test
    void controlsRemainInsideResizablePanel() {
        Rectangle panel = new Rectangle(100f, 80f, 760f, 430f);
        GdxFloatingGameLogLayout.Layout layout
                = GdxFloatingGameLogLayout.layout(panel);

        assertTrue(panel.contains(layout.closeButton()));
        assertTrue(panel.contains(layout.headerDrag()));
        assertTrue(panel.contains(layout.logFrame()));
        assertTrue(layout.logFrame().contains(layout.content()));
        assertTrue(panel.contains(layout.resizeGrip()));
    }

    private static void assertInsideScreen(Rectangle panel,
            float width, float height) {
        assertTrue(panel.x >= GdxFloatingGameLogLayout.SCREEN_MARGIN);
        assertTrue(panel.y >= GdxFloatingGameLogLayout.SCREEN_MARGIN);
        assertTrue(panel.x + panel.width <= width
                - GdxFloatingGameLogLayout.SCREEN_MARGIN + EPSILON);
        assertTrue(panel.y + panel.height <= height
                - GdxFloatingGameLogLayout.SCREEN_MARGIN + EPSILON);
    }
}
