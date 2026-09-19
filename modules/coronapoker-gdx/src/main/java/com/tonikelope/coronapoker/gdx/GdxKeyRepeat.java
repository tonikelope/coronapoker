/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

/** Frame-driven repeat timing for editing keys not repeated by keyTyped. */
final class GdxKeyRepeat {

    static final float INITIAL_DELAY_SECONDS = 0.42f;
    static final float INTERVAL_SECONDS = 0.045f;
    private static final int MAX_REPEATS_PER_FRAME = 8;

    private int keycode = -1;
    private float elapsed;
    private float nextRepeat;

    void press(int pressedKeycode) {
        if (pressedKeycode == keycode) return;
        keycode = pressedKeycode;
        elapsed = 0f;
        nextRepeat = INITIAL_DELAY_SECONDS;
    }

    void release(int releasedKeycode) {
        if (releasedKeycode == keycode) clear();
    }

    void clear() {
        keycode = -1;
        elapsed = 0f;
        nextRepeat = INITIAL_DELAY_SECONDS;
    }

    int keycode() {
        return keycode;
    }

    int update(float deltaSeconds, boolean stillPressed) {
        if (keycode < 0) return 0;
        if (!stillPressed) {
            clear();
            return 0;
        }
        elapsed += Math.max(0f, deltaSeconds);
        int repeats = 0;
        while (elapsed >= nextRepeat
                && repeats < MAX_REPEATS_PER_FRAME) {
            repeats++;
            nextRepeat += INTERVAL_SECONDS;
        }
        return repeats;
    }
}
