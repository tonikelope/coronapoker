/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;

/** Repeats a held volume shortcut while its key and modifiers still match. */
final class GdxVolumeKeyRepeat {

    private final GdxKeyRepeat timing = new GdxKeyRepeat();
    private int direction;

    boolean press(int keycode, int newDirection) {
        if (keycode == timing.keycode() && direction == newDirection) return false;
        timing.clear();
        timing.press(keycode);
        direction = newDirection;
        return true;
    }

    void release(int keycode) {
        timing.release(keycode);
    }

    void clear() {
        timing.clear();
    }

    int update(float delta, GdxShortcutBindings bindings, boolean blocked) {
        int keycode = timing.keycode();
        if (keycode < 0) return 0;
        boolean focused = !(Gdx.graphics instanceof Lwjgl3Graphics desktop)
                || desktop.getWindow().isFocused();
        if (blocked || !focused || !Gdx.input.isKeyPressed(keycode)) {
            clear();
            return 0;
        }
        boolean alt = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);
        boolean control = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        String action = bindings.actionFor(keycode, alt, control, shift);
        return update(delta, GdxVolumeOverlayStyle.shortcutDirection(action));
    }

    int update(float delta, int currentDirection) {
        return direction * timing.update(delta, direction == currentDirection);
    }
}
