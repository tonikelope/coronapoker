/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;

/** Repeats a held volume shortcut and previews the final level once on release. */
final class GdxVolumeKeyRepeat {

    private final GdxKeyRepeat timing = new GdxKeyRepeat();
    private final Runnable previewVolume;
    private int direction;

    GdxVolumeKeyRepeat(Runnable previewVolume) {
        this.previewVolume = previewVolume;
    }

    boolean press(int keycode, int newDirection) {
        if (keycode == timing.keycode() && direction == newDirection) return false;
        timing.clear();
        timing.press(keycode);
        direction = newDirection;
        return true;
    }

    void release(int keycode) {
        if (keycode != timing.keycode()) return;
        if (focused()) finish();
        else clear();
    }

    void clear() {
        timing.clear();
    }

    int update(float delta, GdxShortcutBindings bindings, boolean blocked) {
        int keycode = timing.keycode();
        if (keycode < 0) return 0;
        if (blocked || !focused()) {
            clear();
            return 0;
        }
        if (!Gdx.input.isKeyPressed(keycode)) {
            finish();
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
        if (currentDirection != direction) {
            finish();
            return 0;
        }
        return direction * timing.update(delta, true);
    }

    private void finish() {
        if (timing.keycode() < 0) return;
        clear();
        previewVolume.run();
    }

    private static boolean focused() {
        return !(Gdx.graphics instanceof Lwjgl3Graphics desktop)
                || desktop.getWindow().isFocused();
    }
}
