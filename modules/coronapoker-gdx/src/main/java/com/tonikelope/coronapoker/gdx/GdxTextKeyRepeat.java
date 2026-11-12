/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import java.util.Objects;
import java.util.function.IntConsumer;

/** Repeats physical editing keys while the original editor owns the keyboard. */
final class GdxTextKeyRepeat {

    private final GdxKeyRepeat timing = new GdxKeyRepeat();
    private Object owner;

    void press(int keycode, Object editor) {
        if (editor == null || !isEditingKey(keycode)) return;
        if (!Objects.equals(owner, editor)) timing.clear();
        owner = editor;
        timing.press(keycode);
    }

    void release(int keycode) {
        if (timing.keycode() == keycode) clear();
    }

    void clear() {
        timing.clear();
        owner = null;
    }

    void update(float delta, Object editor, IntConsumer edit) {
        int keycode = timing.keycode();
        if (keycode < 0) return;
        boolean focused = !(Gdx.graphics instanceof Lwjgl3Graphics desktop)
                || desktop.getWindow().isFocused();
        update(delta, editor, focused && Gdx.input.isKeyPressed(keycode), edit);
    }

    void update(float delta, Object editor, boolean stillPressed, IntConsumer edit) {
        if (editor == null || !Objects.equals(owner, editor) || !stillPressed) {
            clear();
            return;
        }
        int keycode = timing.keycode();
        int repeats = timing.update(delta, true);
        for (int i = 0; i < repeats && timing.keycode() == keycode; i++) edit.accept(keycode);
    }

    static boolean isEditingKey(int keycode) {
        return switch (keycode) {
            case Input.Keys.BACKSPACE, Input.Keys.FORWARD_DEL, Input.Keys.LEFT, Input.Keys.RIGHT,
                    Input.Keys.HOME, Input.Keys.END, Input.Keys.UP, Input.Keys.DOWN,
                    Input.Keys.PAGE_UP, Input.Keys.PAGE_DOWN -> true;
            default -> false;
        };
    }
}
