package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Input;

/** Shared keyboard contract for every GDX confirmation dialog. */
final class GdxDialogKeyboard {

    private GdxDialogKeyboard() {
    }

    static boolean isConfirm(int keycode) {
        return keycode == Input.Keys.ENTER
                || keycode == Input.Keys.NUMPAD_ENTER;
    }

    static boolean isCancel(int keycode) {
        return keycode == Input.Keys.ESCAPE;
    }
}
