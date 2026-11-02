package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GdxToggleTransitionStoreTest {

    private static final long SECOND = 1_000_000_000L;

    @Test
    void unseenSwitchStartsAtItsStableEffectiveState() {
        GdxToggleTransitionStore store = new GdxToggleTransitionStore();

        assertEquals(1f, store.sample("child", 1f, 5L));
        assertEquals(0f, store.sample("disabled", 0f, 5L));
    }

    @Test
    void parentAndEveryChildShareTheSameTransitionInstant() {
        GdxToggleTransitionStore store = new GdxToggleTransitionStore();
        long started = 2L * SECOND;
        store.begin("parent", 1f, 0f, started);
        store.begin("visible-child", 1f, 0f, started);
        store.begin("offscreen-child", 1f, 0f, started);

        long frame = started + SECOND / 10L;
        float parent = store.sample("parent", 0f, frame);
        assertEquals(parent, store.sample("visible-child", 0f, frame),
                0.000001f);
        assertEquals(parent, store.sample("offscreen-child", 0f, frame),
                0.000001f);
        assertTrue(parent > 0f && parent < 1f);
    }

    @Test
    void offscreenChildDoesNotBeginAgainWhenScrolledIntoView() {
        GdxToggleTransitionStore store = new GdxToggleTransitionStore();
        long started = 3L * SECOND;
        store.begin("child", 1f, 0f, started);

        assertEquals(0f, store.sample("child", 0f,
                started + 2L * SECOND));
    }

    @Test
    void reversingMidTransitionContinuesFromCurrentVisualPosition() {
        GdxToggleTransitionStore store = new GdxToggleTransitionStore();
        long started = 4L * SECOND;
        store.begin("switch", 0f, 1f, started);
        long reversed = started + SECOND / 10L;
        float before = store.sample("switch", 1f, reversed);
        store.begin("switch", 1f, 0f, reversed);

        assertEquals(before, store.sample("switch", 0f, reversed),
                0.000001f);
        assertTrue(store.sample("switch", 0f,
                reversed + SECOND / 10L) < before);
    }
}
