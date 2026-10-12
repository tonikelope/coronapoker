/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

/** Owns one press-and-hold pointer gesture until its matching release. */
final class GdxPointerHold {

    private int pointer = -1;
    private int button = -1;

    boolean capture(int pointer, int button) {
        if (active()) return false;
        this.pointer = pointer;
        this.button = button;
        return true;
    }

    boolean owns(int pointer, int button) {
        return active() && this.pointer == pointer && this.button == button;
    }

    boolean release(int pointer, int button) {
        if (!owns(pointer, button)) return false;
        clear();
        return true;
    }

    boolean active() {
        return pointer >= 0;
    }

    void clear() {
        pointer = -1;
        button = -1;
    }
}
