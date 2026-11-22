/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import java.util.Properties;
import java.util.function.ToDoubleFunction;

/** Keeps the exact-value editor and slider on the same FPS preference. */
final class GdxFrameRateControl {
    private static final String FIELD = GdxFrameRateLimit.KEY;
    private final GdxSettingsSlider slider = new GdxSettingsSlider();
    private final GdxTextEditState edit = new GdxTextEditState();
    private final Rectangle box = new Rectangle();
    private final Rectangle boxHit = new Rectangle();
    private String draft = "";
    private int originalLimit;

    void hide() {
        slider.hide();
        boxHit.set(0f, 0f, 0f, 0f);
    }

    void cancel() {
        hide();
        slider.cancel();
        edit.blur();
    }

    boolean focused() {
        return edit.focused(FIELD);
    }

    boolean editable(Properties settings) {
        return boxHit.height > 0f && GdxFrameRateLimit.CUSTOM.enabled(settings);
    }

    String text(Properties settings) {
        return focused() ? draft : Integer.toString(GdxFrameRateLimit.CUSTOM.value(settings));
    }

    float textCenterX() {
        return box.x + box.width / 2f;
    }

    void draw(ShapeRenderer shapes, Rectangle row, GdxSettingsLayout.PixelRows rows,
            Properties settings, boolean available, float alpha, ToDoubleFunction<String> textWidth) {
        int value = GdxFrameRateLimit.CUSTOM.value(settings);
        boolean enabled = available && GdxFrameRateLimit.CUSTOM.enabled(settings);
        slider.draw(shapes, row, rows, GdxFrameRateLimit.CUSTOM, value, enabled, alpha);

        box.set(row.x + row.width - 94f, row.y + row.height * .5f - 20f, 82f, 40f);
        float bottom = Math.max(box.y, rows.viewportBottom());
        float top = Math.min(box.y + box.height, rows.viewportTop());
        boxHit.set(box.x, bottom, box.width, enabled ? Math.max(0f, top - bottom) : 0f);
        boolean active = enabled && focused();
        GdxSettingsStyle.drawRow(shapes, box.x, box.y, box.width, box.height, enabled,
                active, false, alpha);
        if (active) {
            float start = textCenterX() - (float) textWidth.applyAsDouble(draft) / 2f;
            int first = edit.selectionStart(draft);
            int last = edit.selectionEnd(draft);
            float left = (float) textWidth.applyAsDouble(draft.substring(0, first));
            float right = (float) textWidth.applyAsDouble(draft.substring(0, last));
            shapes.setColor(.33f, .81f, 1f, .34f * alpha);
            shapes.rect(start + left, box.y + 6f, right - left, box.height - 12f);
            float caret = edit.caret(draft) == first ? left : right;
            shapes.setColor(.33f, .81f, 1f, alpha);
            shapes.rect(start + caret, box.y + 6f, 2f, box.height - 12f);
        }
    }

    boolean begin(float x, float y, int pointer, Properties settings) {
        if (editable(settings) && boxHit.contains(x, y)) {
            if (!focused()) {
                originalLimit = GdxFrameRateLimit.CUSTOM.value(settings);
                draft = Integer.toString(originalLimit);
                edit.focus(FIELD, draft);
            }
            edit.selectAll(draft);
            return true;
        }
        finishEditing(settings);
        return slider.begin(x, y, pointer, settings);
    }

    boolean drag(float x, int pointer, Properties settings) {
        return slider.drag(x, pointer, settings);
    }

    boolean release(int pointer) {
        return slider.release(pointer);
    }

    boolean keyDown(int code, boolean control, boolean shift, Properties settings) {
        if (!focused() || !editable(settings)) return false;
        switch (code) {
            case Input.Keys.ESCAPE -> {
                GdxFrameRateLimit.CUSTOM.select(settings, originalLimit);
                edit.blur();
            }
            case Input.Keys.ENTER, Input.Keys.NUMPAD_ENTER, Input.Keys.TAB -> finishEditing(settings);
            case Input.Keys.LEFT -> edit.left(draft, shift);
            case Input.Keys.RIGHT -> edit.right(draft, shift);
            case Input.Keys.HOME -> edit.home(draft, shift);
            case Input.Keys.END -> edit.end(draft, shift);
            case Input.Keys.BACKSPACE -> change(edit.backspace(draft), settings);
            case Input.Keys.FORWARD_DEL -> change(edit.delete(draft), settings);
            case Input.Keys.A -> {
                if (control) edit.selectAll(draft);
            }
            case Input.Keys.V -> {
                if (control) {
                    String value = Gdx.app.getClipboard().getContents();
                    if (value != null && value.strip().matches("[0-9]{1,3}")) {
                        change(edit.replaceSelection(draft, value.strip(), 3), settings);
                    }
                }
            }
            default -> { }
        }
        return true;
    }

    boolean keyTyped(char character, Properties settings) {
        if (!focused() || !editable(settings)) return false;
        if (character >= '0' && character <= '9') {
            change(edit.replaceSelection(draft, Character.toString(character), 3), settings);
        }
        return true;
    }

    void finishEditing(Properties settings) {
        if (focused() && (draft.isEmpty() || !GdxFrameRateLimit.inRange(Integer.parseInt(draft)))) {
            GdxFrameRateLimit.CUSTOM.select(settings, originalLimit);
        }
        edit.blur();
    }

    private void change(String value, Properties settings) {
        draft = value;
        if (!draft.isEmpty()) {
            int number = Integer.parseInt(draft);
            if (GdxFrameRateLimit.inRange(number)) {
                GdxFrameRateLimit.CUSTOM.select(settings, number);
            }
        }
    }
}
