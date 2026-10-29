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
    }

    @Test
    void everyEdgeAndCornerCanResizeWithoutMovingItsOppositeEdge() {
        Rectangle start = new Rectangle(380f, 180f, 900f, 600f);

        Rectangle left = resize(start,
                GdxFloatingGameLogLayout.ResizeHandle.LEFT, -100f, 0f);
        Rectangle right = resize(start,
                GdxFloatingGameLogLayout.ResizeHandle.RIGHT, 100f, 0f);
        Rectangle top = resize(start,
                GdxFloatingGameLogLayout.ResizeHandle.TOP, 0f, 100f);
        Rectangle bottom = resize(start,
                GdxFloatingGameLogLayout.ResizeHandle.BOTTOM, 0f, -100f);
        Rectangle topLeft = resize(start,
                GdxFloatingGameLogLayout.ResizeHandle.TOP_LEFT,
                -100f, 100f);
        Rectangle topRight = resize(start,
                GdxFloatingGameLogLayout.ResizeHandle.TOP_RIGHT,
                100f, 100f);
        Rectangle bottomLeft = resize(start,
                GdxFloatingGameLogLayout.ResizeHandle.BOTTOM_LEFT,
                -100f, -100f);
        Rectangle bottomRight = resize(start,
                GdxFloatingGameLogLayout.ResizeHandle.BOTTOM_RIGHT,
                100f, -100f);

        assertEquals(start.x + start.width, left.x + left.width, EPSILON);
        assertEquals(start.x, right.x, EPSILON);
        assertEquals(start.y, top.y, EPSILON);
        assertEquals(start.y + start.height,
                bottom.y + bottom.height, EPSILON);
        assertEquals(start.x + start.width,
                topLeft.x + topLeft.width, EPSILON);
        assertEquals(start.y, topLeft.y, EPSILON);
        assertEquals(start.x, topRight.x, EPSILON);
        assertEquals(start.y, topRight.y, EPSILON);
        assertEquals(start.x + start.width,
                bottomLeft.x + bottomLeft.width, EPSILON);
        assertEquals(start.y + start.height,
                bottomLeft.y + bottomLeft.height, EPSILON);
        assertEquals(start.x, bottomRight.x, EPSILON);
        assertEquals(start.y + start.height,
                bottomRight.y + bottomRight.height, EPSILON);

        for (GdxFloatingGameLogLayout.ResizeHandle handle
                : GdxFloatingGameLogLayout.ResizeHandle.values()) {
            if (handle == GdxFloatingGameLogLayout.ResizeHandle.NONE) {
                continue;
            }
            assertInsideScreen(resize(start, handle, 5000f, 5000f),
                    1920f, 1080f);
            assertInsideScreen(resize(start, handle, -5000f, -5000f),
                    1920f, 1080f);
        }
    }

    @Test
    void pointerHitTestingDistinguishesCornersSidesAndInterior() {
        Rectangle panel = new Rectangle(100f, 80f, 760f, 430f);

        assertEquals(GdxFloatingGameLogLayout.ResizeHandle.TOP_LEFT,
                handle(panel, 101f, 509f));
        assertEquals(GdxFloatingGameLogLayout.ResizeHandle.TOP_RIGHT,
                handle(panel, 859f, 509f));
        assertEquals(GdxFloatingGameLogLayout.ResizeHandle.BOTTOM_LEFT,
                handle(panel, 101f, 81f));
        assertEquals(GdxFloatingGameLogLayout.ResizeHandle.BOTTOM_RIGHT,
                handle(panel, 859f, 81f));
        assertEquals(GdxFloatingGameLogLayout.ResizeHandle.LEFT,
                handle(panel, 101f, 280f));
        assertEquals(GdxFloatingGameLogLayout.ResizeHandle.RIGHT,
                handle(panel, 859f, 280f));
        assertEquals(GdxFloatingGameLogLayout.ResizeHandle.TOP,
                handle(panel, 480f, 509f));
        assertEquals(GdxFloatingGameLogLayout.ResizeHandle.BOTTOM,
                handle(panel, 480f, 81f));
        assertEquals(GdxFloatingGameLogLayout.ResizeHandle.NONE,
                handle(panel, 480f, 280f));
    }

    private static Rectangle resize(Rectangle start,
            GdxFloatingGameLogLayout.ResizeHandle handle,
            float deltaX, float deltaY) {
        return GdxFloatingGameLogLayout.resized(start, handle,
                deltaX, deltaY, 1920f, 1080f);
    }

    private static GdxFloatingGameLogLayout.ResizeHandle handle(
            Rectangle panel, float x, float y) {
        return GdxFloatingGameLogLayout.resizeHandleAt(panel, x, y);
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
