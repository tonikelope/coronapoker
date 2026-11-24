/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Input and range mapping for the numeric game settings visible on a page. */
final class GdxGameSettingsSliders {

    @FunctionalInterface
    interface ValueFont {
        BitmapFont fit(String value, float width);
    }

    enum Control {
        BLIND_INTERVAL(blindIntervalRange(false, Integer.MAX_VALUE)),
        MIN_BUYIN(new Range(NewGameTableDraft.MIN_BUYIN_BB, NewGameTableDraft.MAX_BUYIN_BB, 5, 0)),
        MAX_BUYIN(MIN_BUYIN.range),
        INITIAL_BUYIN(null),
        REBUYS(new Range(1, Integer.MAX_VALUE, 1, 20)),
        HAND_LIMIT(new Range(1, Integer.MAX_VALUE, 1, 250)),
        THINK_TIME(new Range(NewGameTableDraft.MIN_THINK_SECONDS, NewGameTableDraft.MAX_THINK_SECONDS, 1, 0)),
        SHOWDOWN(new Range(NewGameTableDraft.MIN_SHOWDOWN_SECONDS, NewGameTableDraft.MAX_SHOWDOWN_SECONDS, 1, 0)),
        SMALL_BLIND(new Range(5, 400_000_000, 5, 1_000)),
        BIG_BLIND(SMALL_BLIND.range),
        AUTO_CALL(new Range(5, Integer.MAX_VALUE, 5, 1_000));

        final Range range;

        Control(Range range) {
            this.range = range;
        }

        Range between(int min, int max) {
            return new Range(min, max, range.step(), range.fineSpan());
        }

        boolean isBlind() {
            return this == SMALL_BLIND || this == BIG_BLIND;
        }
    }

    static Range blindIntervalRange(boolean hands, int max) {
        int lastLinearValue = hands ? 1_000 : 600;
        return new Range(1, max, 1, lastLinearValue - 1);
    }

    record Range(int min, int max, int step, int fineSpan) {
        // For counts with very high limits, keep ordinary values easy to
        // select and compress the remaining range into the right end.
        private static final double FINE_TRACK_SHARE = 0.85d;

        float progress(int value) {
            double span = (double) max - min;
            if (span <= 0d) return 0f;
            double offset = (double) MathUtils.clamp(value, min, max) - min;
            if (fineSpan == 0 || span <= fineSpan) return (float) (offset / span);
            if (offset <= fineSpan) return (float) (offset / fineSpan * FINE_TRACK_SHARE);
            return (float) (FINE_TRACK_SHARE + (1d - FINE_TRACK_SHARE)
                    * Math.log(offset / fineSpan) / Math.log(span / fineSpan));
        }

        int valueAt(double progress) {
            if (progress <= 0d || max <= min) return min;
            if (progress >= 1d) return max;
            double span = (double) max - min;
            double offset = progress * span;
            if (fineSpan > 0 && span > fineSpan) {
                offset = progress <= FINE_TRACK_SHARE
                        ? progress / FINE_TRACK_SHARE * fineSpan
                        : fineSpan * Math.pow(span / fineSpan,
                                (progress - FINE_TRACK_SHARE) / (1d - FINE_TRACK_SHARE));
            }
            return (int) Math.min(max, min + Math.round(offset / step) * step);
        }

        int adjust(int value, int direction) {
            return (int) Math.max(min, Math.min(max, (long) value + direction * (long) step));
        }

        int parseExact(String value, boolean blind) {
            if (!value.matches(blind ? "[0-9]*(?:[.,][0-9]{0,2})?" : "[0-9]+")) return -1;
            try {
                int number = blind ? new BigDecimal(value.replace(',', '.')).movePointRight(2).intValueExact()
                        : Integer.parseInt(value);
                return number >= min && number <= max && (!blind || (number - min) % step == 0)
                        ? number : -1;
            } catch (NumberFormatException | ArithmeticException invalid) {
                return -1;
            }
        }
    }

    record ExactValue(String text, Pattern syntax, int maxLength, Predicate<String> save) {
        private static final Pattern INTEGER = Pattern.compile("[0-9]*");
        private static final Pattern DECIMAL = Pattern.compile("[0-9]*(?:[.,][0-9]{0,2})?");
        private static final Pattern WHOLE_CHIPS = Pattern.compile("[0-9.,]*[kKmMbB]?");

        ExactValue(String text, boolean decimal, Predicate<String> save) {
            this(text, decimal ? DECIMAL : INTEGER, decimal ? 32 : 10, save);
        }

        static ExactValue wholeChips(int value, Predicate<String> save) {
            return new ExactValue(Integer.toString(value), WHOLE_CHIPS, 32, save);
        }
    }

    private static final class Target {
        final Rectangle track;
        final Rectangle hit;
        final Rectangle boxHit;
        final Range range;
        final IntConsumer select;
        final ExactValue exact;
        int value;

        Target(Rectangle track, Rectangle hit, Rectangle boxHit, Range range,
                int value, IntConsumer select, ExactValue exact) {
            this.track = track;
            this.hit = hit;
            this.boxHit = boxHit;
            this.range = range;
            this.value = value;
            this.select = select;
            this.exact = exact;
        }

        void select(int next) {
            if (next == value) return;
            select.accept(next);
            value = next;
        }
    }

    private final EnumMap<Control, Target> visible = new EnumMap<>(Control.class);
    private final GdxTextEditState edit = new GdxTextEditState();
    private final GlyphLayout selectionGlyph = new GlyphLayout();
    private Control focused;
    private Control pressed;
    private Control editing;
    private String draft = "";
    private boolean invalid;
    private int pointer = -1;

    void hide() {
        visible.clear();
    }

    void cancel() {
        hide();
        focused = null;
        pressed = null;
        pointer = -1;
        cancelEditing();
    }

    void draw(ShapeRenderer shapes, GdxSettingsLayout.SliderRow row,
            float viewportBottom, float viewportTop, Control control, Range range,
            int value, IntConsumer select, float alpha, ValueFont valueFont) {
        ExactValue exact = new ExactValue(control.isBlind() ? decimalText(value) : Integer.toString(value),
                control.isBlind(), text -> {
                    int number = range.parseExact(text, control.isBlind());
                    if (number < 0) return false;
                    if (number != value) select.accept(number);
                    return true;
                });
        draw(shapes, row, viewportBottom, viewportTop, control, range, value, select, exact, alpha, valueFont);
    }

    void draw(ShapeRenderer shapes, GdxSettingsLayout.SliderRow row,
            float viewportBottom, float viewportTop, Control control, Range range,
            int value, IntConsumer select, ExactValue exact, float alpha, ValueFont valueFont) {
        Rectangle track = row.track();
        float bottom = Math.max(row.bounds().y, viewportBottom);
        float top = Math.min(row.bounds().y + row.bounds().height, viewportTop);
        boolean enabled = select != null && range.max() > range.min();
        GdxSettingsStyle.drawSliderTrack(shapes, track, range.progress(value),
                enabled ? alpha : alpha * 0.42f);
        Rectangle boxHit = drawValueField(shapes, row.value(), bottom, top, control, enabled, alpha, valueFont);
        float center = track.y + track.height / 2f;
        if (enabled && track.width > 0f && center > bottom && center < top) {
            Rectangle hit = new Rectangle(track.x - 8f, bottom,
                    track.width + 16f, top - bottom);
            visible.put(control, new Target(track, hit, boxHit, range, value, select, exact));
        }
    }

    void drawField(ShapeRenderer shapes, Rectangle field, float bottom, float top, Control control,
            ExactValue exact, boolean enabled, float alpha, ValueFont valueFont) {
        Rectangle boxHit = drawValueField(shapes, field, bottom, top, control, enabled, alpha, valueFont);
        if (enabled && boxHit.height > 0f) {
            visible.put(control, new Target(null, null, boxHit, null, 0, null, exact));
        }
    }

    private Rectangle drawValueField(ShapeRenderer shapes, Rectangle field, float bottom, float top,
            Control control, boolean enabled, float alpha, ValueFont valueFont) {
        Rectangle box = new Rectangle(field.x, field.y + field.height / 2f - 20f, field.width, 40f);
        boolean active = enabled && editing == control;
        GdxSettingsStyle.drawRow(shapes, box.x, box.y, box.width, box.height, enabled, active, false, alpha);
        if (active) drawSelection(shapes, box, alpha, valueFont);
        float boxBottom = Math.max(box.y, bottom);
        return new Rectangle(box.x, boxBottom, box.width,
                Math.max(0f, Math.min(box.y + box.height, top) - boxBottom));
    }

    private void drawSelection(ShapeRenderer shapes, Rectangle box, float alpha, ValueFont valueFont) {
        float available = box.width - 8f;
        BitmapFont font = valueFont.fit(draft, available);
        float width = selectionWidth(font, draft.length());
        float scale = width > available ? available / width : 1f;
        float start = box.x + (box.width - width * scale) / 2f;
        int first = edit.selectionStart(draft), last = edit.selectionEnd(draft);
        float left = selectionWidth(font, first) * scale;
        float right = selectionWidth(font, last) * scale;
        shapes.setColor(.33f, .81f, 1f, .34f * alpha);
        shapes.rect(start + left, box.y + 6f, right - left, box.height - 12f);
        Color color = invalid ? Color.SALMON : Color.SKY;
        shapes.setColor(color.r, color.g, color.b, alpha);
        if (invalid) shapes.rect(box.x + 8f, box.y + 3f, box.width - 16f, 2f);
        shapes.rect(start + (edit.caret(draft) == first ? left : right),
                box.y + 6f, 2f, box.height - 12f);
    }

    private float selectionWidth(BitmapFont font, int end) {
        selectionGlyph.setText(font, draft, 0, end, Color.WHITE, 0f, 0, false, null);
        return selectionGlyph.width;
    }

    static String decimalText(int cents) {
        return BigDecimal.valueOf(cents, 2).stripTrailingZeros().toPlainString();
    }

    String text(Control control, String value) {
        return editing == control ? draft : value;
    }

    boolean editing() {
        return editing != null && visible.containsKey(editing);
    }

    boolean begin(float x, float y, int id) {
        if (pointer >= 0) return true;
        focused = null;
        for (var entry : visible.entrySet()) {
            Target target = entry.getValue();
            if (target.boxHit.height <= 0f || !target.boxHit.contains(x, y)) continue;
            if (editing != entry.getKey()) {
                finishEditing();
                editing = entry.getKey();
                draft = target.exact.text();
                edit.focus(editing.name(), draft);
            }
            edit.selectAll(draft);
            return true;
        }
        finishEditing();
        for (var entry : visible.entrySet()) {
            Rectangle hit = entry.getValue().hit;
            if (hit == null || !hit.contains(x, y)) continue;
            focused = pressed = entry.getKey();
            pointer = id;
            drag(x, id);
            return true;
        }
        return false;
    }

    boolean drag(float x, int id) {
        if (pointer < 0) return false;
        if (pointer != id) return true;
        Target target = visible.get(pressed);
        if (target != null) {
            double progress = (x - target.track.x) / (double) target.track.width;
            // Keep exact saved values when pressing their current thumb,
            // including values between steps and large values in the tail.
            float thumb = target.track.x + target.track.width * target.range.progress(target.value);
            if (Math.abs(x - thumb) > 0.5f) target.select(target.range.valueAt(progress));
        }
        return true;
    }

    boolean release(int id) {
        if (pointer != id) return false;
        pressed = null;
        pointer = -1;
        return true;
    }

    boolean keyDown(int keycode) {
        return keyDown(keycode, false, false);
    }

    boolean keyDown(int keycode, boolean control, boolean shift) {
        if (editing()) {
            switch (keycode) {
                case Input.Keys.ESCAPE -> cancelEditing();
                case Input.Keys.ENTER, Input.Keys.NUMPAD_ENTER, Input.Keys.TAB -> {
                    if (saveDraft()) cancelEditing();
                }
                case Input.Keys.LEFT -> edit.left(draft, shift);
                case Input.Keys.RIGHT -> edit.right(draft, shift);
                case Input.Keys.HOME -> edit.home(draft, shift);
                case Input.Keys.END -> edit.end(draft, shift);
                case Input.Keys.BACKSPACE -> change(edit.backspace(draft));
                case Input.Keys.FORWARD_DEL -> change(edit.delete(draft));
                case Input.Keys.A -> { if (control) edit.selectAll(draft); }
                case Input.Keys.C -> {
                    if (control) Gdx.app.getClipboard().setContents(
                            draft.substring(edit.selectionStart(draft), edit.selectionEnd(draft)));
                }
                case Input.Keys.V -> {
                    if (control) {
                        String pasted = Gdx.app.getClipboard().getContents();
                        if (pasted != null) replace(pasted.strip());
                    }
                }
                default -> { }
            }
            return true;
        }
        if (control || shift) return false;
        Target target = visible.get(focused);
        if (target == null) return false;
        int direction = switch (keycode) {
            case Input.Keys.LEFT -> -1;
            case Input.Keys.RIGHT -> 1;
            default -> 0;
        };
        if (direction == 0) return false;
        target.select(target.range.adjust(target.value, direction));
        return true;
    }

    boolean keyTyped(char character) {
        if (!editing()) return false;
        if (!Character.isISOControl(character)) {
            replace(Character.toString(character));
        }
        return true;
    }

    private void replace(String text) {
        Target target = visible.get(editing);
        int limit = target.exact.maxLength();
        if (text.length() > limit) return;
        int start = edit.selectionStart(draft), end = edit.selectionEnd(draft);
        String next = draft.substring(0, start) + text + draft.substring(end);
        if (next.length() <= limit && target.exact.syntax().matcher(next).matches()) {
            change(edit.replaceSelection(draft, text, limit));
        }
    }

    private void change(String value) {
        draft = value;
        invalid = false;
    }

    private boolean saveDraft() {
        Target target = visible.get(editing);
        if (target == null) return false;
        invalid = !target.exact.save().test(draft);
        return !invalid;
    }

    void finishEditing() {
        if (editing()) saveDraft();
        cancelEditing();
    }

    private void cancelEditing() {
        editing = null;
        edit.blur();
        invalid = false;
    }
}
