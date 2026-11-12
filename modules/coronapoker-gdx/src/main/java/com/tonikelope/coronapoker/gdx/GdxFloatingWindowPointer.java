/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.LongSet;

/** Keeps a window's complete pointer gesture on that window, including secondary buttons. */
final class GdxFloatingWindowPointer {

    private final GdxPointerHold primary = new GdxPointerHold();
    private final LongSet secondary = new LongSet();

    boolean capture(int index, int button) {
        if (button == Input.Buttons.LEFT && primary.capture(index, button)) return true;
        if (!primary.owns(index, button)) captureSecondary(index, button);
        return false;
    }

    void captureSecondary(int index, int button) {
        secondary.add(id(index, button));
    }

    boolean ownsPrimary(int index) {
        return primary.owns(index, Input.Buttons.LEFT);
    }

    boolean releasePrimary(int index, int button) {
        return primary.release(index, button);
    }

    boolean releaseSecondary(int index, int button) {
        return secondary.remove(id(index, button));
    }

    boolean active() {
        return primary.active() || secondary.notEmpty();
    }

    void clear() {
        primary.clear();
        secondary.clear();
    }

    private static long id(int index, int button) {
        return (long) index << 32 | Integer.toUnsignedLong(button);
    }
}
