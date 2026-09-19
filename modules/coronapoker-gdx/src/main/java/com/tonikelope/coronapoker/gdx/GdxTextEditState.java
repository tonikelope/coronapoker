/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.Objects;
import java.util.function.IntToDoubleFunction;
import java.util.function.IntUnaryOperator;

/**
 * Toolkit-independent single-line editing state shared by the native GDX
 * screens. Indices are always valid UTF-16 code-point boundaries so selection,
 * deletion and clipboard edits cannot split surrogate pairs.
 */
final class GdxTextEditState {

    private String fieldId;
    private int anchor;
    private int caret;

    void focus(String id, String value) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(value, "value");
        if (!id.equals(fieldId)) {
            fieldId = id;
            anchor = value.length();
            caret = value.length();
        } else {
            anchor = boundary(value, anchor);
            caret = boundary(value, caret);
        }
    }

    void blur() {
        fieldId = null;
        anchor = 0;
        caret = 0;
    }

    boolean focused(String id) {
        return id != null && id.equals(fieldId);
    }

    int caret(String value) {
        caret = boundary(value, caret);
        return caret;
    }

    int selectionStart(String value) {
        clamp(value);
        return Math.min(anchor, caret);
    }

    int selectionEnd(String value) {
        clamp(value);
        return Math.max(anchor, caret);
    }

    boolean hasSelection(String value) {
        return selectionStart(value) != selectionEnd(value);
    }

    String selectedText(String value) {
        return value.substring(selectionStart(value), selectionEnd(value));
    }

    void selectAll(String value) {
        anchor = 0;
        caret = value.length();
    }

    void home(String value, boolean extend) {
        moveTo(value, 0, extend);
    }

    void end(String value, boolean extend) {
        moveTo(value, value.length(), extend);
    }

    void left(String value, boolean extend) {
        clamp(value);
        int next;
        if (!extend && anchor != caret) {
            next = Math.min(anchor, caret);
        } else {
            next = caret == 0 ? 0 : value.offsetByCodePoints(caret, -1);
        }
        moveTo(value, next, extend);
    }

    void right(String value, boolean extend) {
        clamp(value);
        int next;
        if (!extend && anchor != caret) {
            next = Math.max(anchor, caret);
        } else {
            next = caret == value.length() ? caret
                    : value.offsetByCodePoints(caret, 1);
        }
        moveTo(value, next, extend);
    }

    void setCaret(String value, int index, boolean extend) {
        moveTo(value, index, extend);
    }

    /**
     * Resolves a horizontal pointer position to the nearest legal insertion
     * boundary. Callers provide the visual boundaries and measured prefix
     * widths, which also lets the lobby treat a complete {@code #emoji#}
     * token as one visual glyph.
     */
    static int nearestBoundary(String value, int start, int end, float x,
            IntUnaryOperator nextBoundary,
            IntToDoubleFunction prefixWidth) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(nextBoundary, "nextBoundary");
        Objects.requireNonNull(prefixWidth, "prefixWidth");
        int first = boundary(value, start);
        int last = boundary(value, end);
        if (last < first) {
            int swap = first;
            first = last;
            last = swap;
        }
        if (x <= 0f || first == last) return first;
        int current = first;
        double previousWidth = 0d;
        while (current < last) {
            int requested = nextBoundary.applyAsInt(current);
            int next = boundary(value, requested);
            if (next <= current || next > last) {
                next = value.offsetByCodePoints(current, 1);
                if (next > last) next = last;
            }
            double nextWidth = Math.max(previousWidth,
                    prefixWidth.applyAsDouble(next));
            if (x < (previousWidth + nextWidth) / 2d) return current;
            current = next;
            previousWidth = nextWidth;
        }
        return last;
    }

    String replaceSelection(String value, String replacement,
            int maximumCodePoints) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(replacement, "replacement");
        int start = selectionStart(value);
        int end = selectionEnd(value);
        String base = value.substring(0, start) + value.substring(end);
        int remaining = Math.max(0, maximumCodePoints
                - base.codePointCount(0, base.length()));
        String accepted = firstCodePoints(replacement, remaining);
        String result = value.substring(0, start) + accepted
                + value.substring(end);
        caret = start + accepted.length();
        anchor = caret;
        return result;
    }

    String backspace(String value) {
        if (hasSelection(value)) return replaceSelection(value, "",
                Integer.MAX_VALUE);
        caret(value);
        if (caret == 0) return value;
        int start = value.offsetByCodePoints(caret, -1);
        String result = value.substring(0, start) + value.substring(caret);
        anchor = caret = start;
        return result;
    }

    String delete(String value) {
        if (hasSelection(value)) return replaceSelection(value, "",
                Integer.MAX_VALUE);
        caret(value);
        if (caret == value.length()) return value;
        int end = value.offsetByCodePoints(caret, 1);
        String result = value.substring(0, caret) + value.substring(end);
        anchor = caret;
        return result;
    }

    private void moveTo(String value, int index, boolean extend) {
        int target = boundary(value, index);
        if (!extend) anchor = target;
        caret = target;
    }

    private void clamp(String value) {
        anchor = boundary(value, anchor);
        caret = boundary(value, caret);
    }

    private static int boundary(String value, int index) {
        int result = Math.max(0, Math.min(value.length(), index));
        if (result > 0 && result < value.length()
                && Character.isLowSurrogate(value.charAt(result))
                && Character.isHighSurrogate(value.charAt(result - 1))) {
            result--;
        }
        return result;
    }

    private static String firstCodePoints(String value, int count) {
        if (count <= 0) return "";
        int available = value.codePointCount(0, value.length());
        if (available <= count) return value;
        return value.substring(0, value.offsetByCodePoints(0, count));
    }
}
