package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Rectangle;
import org.junit.jupiter.api.Test;

final class GdxTransientNoticeLayoutTest {

    @Test
    void shortMessagesDoNotKeepTheOldFixedWidth() {
        assertEquals(260f, GdxTransientNoticeLayout.contentWidth(
                118f, 0f, 52f, 260f, 820f, 1_840f), 0.001f);
        assertEquals(432f, GdxTransientNoticeLayout.contentWidth(
                380f, 0f, 52f, 260f, 820f, 1_840f), 0.001f);
    }

    @Test
    void longMessagesStayInsideTheViewportAndRemainCentred() {
        float width = GdxTransientNoticeLayout.contentWidth(
                2_000f, 92f, 40f, 300f, 820f, 560f);
        Rectangle bounds = GdxTransientNoticeLayout.topCentered(
                600f, 420f, width, 92f, 98f, 20f);

        assertEquals(560f, width, 0.001f);
        assertEquals(20f, bounds.x, 0.001f);
        assertEquals(230f, bounds.y, 0.001f);
        assertTrue(bounds.x + bounds.width <= 580f);
    }

    @Test
    void invalidMeasurementsCannotBreakRendering() {
        assertEquals(1f, GdxTransientNoticeLayout.contentWidth(
                Float.NaN, Float.POSITIVE_INFINITY, -1f,
                Float.NaN, Float.NaN, Float.NaN), 0.001f);
    }
}
