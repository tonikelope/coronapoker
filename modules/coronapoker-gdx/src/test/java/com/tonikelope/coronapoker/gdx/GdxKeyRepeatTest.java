package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class GdxKeyRepeatTest {

    @Test
    void waitsBeforeRepeatingThenUsesAStableInterval() {
        GdxKeyRepeat repeat = new GdxKeyRepeat();
        repeat.press(67);
        assertEquals(0, repeat.update(0.41f, true));
        assertEquals(1, repeat.update(0.01f, true));
        assertEquals(2, repeat.update(0.09f, true));
    }

    @Test
    void releaseAndLostPhysicalKeyStopRepeating() {
        GdxKeyRepeat repeat = new GdxKeyRepeat();
        repeat.press(67);
        repeat.release(67);
        assertEquals(-1, repeat.keycode());
        assertEquals(0, repeat.update(1f, true));

        repeat.press(112);
        assertEquals(0, repeat.update(1f, false));
        assertEquals(-1, repeat.keycode());
    }

    @Test
    void duplicateKeyDownDoesNotRestartTheInitialDelay() {
        GdxKeyRepeat repeat = new GdxKeyRepeat();
        repeat.press(67);
        assertEquals(0, repeat.update(0.30f, true));
        repeat.press(67);
        assertEquals(1, repeat.update(0.12f, true));
    }
}
