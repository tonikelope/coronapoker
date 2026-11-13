package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Input;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class GdxVolumeKeyRepeatTest {

    @Test
    void aHeldShortcutRepeatsItsDirectionAndIgnoresDuplicatePresses() {
        var previews = new AtomicInteger();
        var repeat = new GdxVolumeKeyRepeat(previews::incrementAndGet);
        assertTrue(repeat.press(Input.Keys.DOWN, -1));
        assertEquals(0, repeat.update(0.30f, -1));
        assertFalse(repeat.press(Input.Keys.DOWN, -1));
        assertEquals(-1, repeat.update(0.13f, -1));
        assertEquals(-2, repeat.update(0.09f, -1));
        repeat.release(Input.Keys.UP);
        assertEquals(0, previews.get());
        repeat.release(Input.Keys.DOWN);
        assertEquals(0, repeat.update(1f, -1));
        repeat.release(Input.Keys.DOWN);
        assertEquals(1, previews.get());
    }

    @Test
    void losingTheModifierStopsRepeatUntilAFreshPress() {
        var previews = new AtomicInteger();
        var repeat = new GdxVolumeKeyRepeat(previews::incrementAndGet);
        assertTrue(repeat.press(Input.Keys.UP, 1));
        assertEquals(1, repeat.update(0.43f, 1));
        assertEquals(0, repeat.update(0.5f, 0));
        repeat.release(Input.Keys.UP);
        assertEquals(1, previews.get());
        assertEquals(0, repeat.update(0.5f, 1));
        assertTrue(repeat.press(Input.Keys.UP, 1));
        assertEquals(1, repeat.update(0.43f, 1));
        assertEquals(1, previews.get());
    }

    @Test
    void shortTapPreviewsOnceAndAnInterruptedShortcutStaysSilent() {
        var previews = new AtomicInteger();
        var repeat = new GdxVolumeKeyRepeat(previews::incrementAndGet);
        repeat.press(Input.Keys.UP, 1);
        repeat.release(Input.Keys.UP);
        assertEquals(1, previews.get());

        repeat.press(Input.Keys.DOWN, -1);
        repeat.clear();
        repeat.release(Input.Keys.DOWN);
        assertEquals(1, previews.get());
    }
}
