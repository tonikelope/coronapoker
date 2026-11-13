package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Input;
import org.junit.jupiter.api.Test;

final class GdxVolumeKeyRepeatTest {

    @Test
    void aHeldShortcutRepeatsItsDirectionAndIgnoresDuplicatePresses() {
        var repeat = new GdxVolumeKeyRepeat();
        assertTrue(repeat.press(Input.Keys.DOWN, -1));
        assertEquals(0, repeat.update(0.30f, -1));
        assertFalse(repeat.press(Input.Keys.DOWN, -1));
        assertEquals(-1, repeat.update(0.13f, -1));
        assertEquals(-2, repeat.update(0.09f, -1));
        repeat.release(Input.Keys.DOWN);
        assertEquals(0, repeat.update(1f, -1));
    }

    @Test
    void losingTheModifierStopsRepeatUntilAFreshPress() {
        var repeat = new GdxVolumeKeyRepeat();
        assertTrue(repeat.press(Input.Keys.UP, 1));
        assertEquals(1, repeat.update(0.43f, 1));
        assertEquals(0, repeat.update(0.5f, 0));
        assertEquals(0, repeat.update(0.5f, 1));
        assertTrue(repeat.press(Input.Keys.UP, 1));
        assertEquals(1, repeat.update(0.43f, 1));
    }
}
