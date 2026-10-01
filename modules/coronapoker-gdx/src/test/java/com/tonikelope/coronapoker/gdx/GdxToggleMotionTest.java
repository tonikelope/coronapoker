package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GdxToggleMotionTest {

    @Test
    void switchIdentityIsStableWhenItsDisplayedLabelChanges() {
        String before = GdxToggleMotion.stableKey(
                "SETTINGS:MENU:APPEARANCE:1", "ANIMACIONES *",
                100f, 200f, 500f, false);
        String after = GdxToggleMotion.stableKey(
                "SETTINGS:MENU:APPEARANCE:1", "ANIMACIONES",
                100f, 200f, 500f, false);

        assertEquals(before, after);
    }

    @Test
    void switchTravelIsSmoothAndNotInstantaneous() {
        float position = 0f;
        position = GdxToggleMotion.next(position, 1f, 1f / 60f);

        assertTrue(position > 0f);
        assertTrue(position < 0.2f);
    }

    @Test
    void switchTravelIsStableAcrossFrameRates() {
        float sixtyFps = advance(60, 1f / 60f);
        float thirtyFps = advance(30, 1f / 30f);

        assertEquals(sixtyFps, thirtyFps, 0.0001f);
        assertTrue(sixtyFps > 0.99f);
    }

    private static float advance(int frames, float delta) {
        float position = 0f;
        for (int frame = 0; frame < frames; frame++) {
            position = GdxToggleMotion.next(position, 1f, delta);
        }
        return position;
    }
}
