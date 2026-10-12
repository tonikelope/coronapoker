/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

/**
 * Shared native-style scrollbar dragging geometry.
 *
 * <p>Pressing the thumb keeps the exact point that was grabbed, while
 * pressing the empty track moves the thumb under the pointer and starts a
 * drag from its centre.  The captured offset remains valid outside the track;
 * callers only need to clamp through {@link #ratio} until release.</p>
 */
final class GdxScrollbarDrag {

    record Capture(float grabOffset, float ratio) { }

    private GdxScrollbarDrag() { }

    static Capture begin(float pointer, float trackStart, float trackLength,
            float thumbLength, float currentRatio, boolean inverted) {
        float safeTrack = Math.max(0f, trackLength);
        float safeThumb = clamp(thumbLength, 0f, safeTrack);
        float travel = safeTrack - safeThumb;
        if (travel <= 0f) return new Capture(0f, 0f);

        float logicalRatio = clamp(currentRatio, 0f, 1f);
        float visualRatio = inverted ? 1f - logicalRatio : logicalRatio;
        float thumbStart = trackStart + travel * visualRatio;
        boolean onThumb = pointer >= thumbStart
                && pointer <= thumbStart + safeThumb;
        float grabOffset = onThumb
                ? pointer - thumbStart : safeThumb / 2f;
        return new Capture(grabOffset, ratio(pointer, trackStart,
                safeTrack, safeThumb, grabOffset, inverted));
    }

    static float ratio(float pointer, float trackStart, float trackLength,
            float thumbLength, float grabOffset, boolean inverted) {
        float safeTrack = Math.max(0f, trackLength);
        float safeThumb = clamp(thumbLength, 0f, safeTrack);
        float travel = safeTrack - safeThumb;
        if (travel <= 0f) return 0f;
        float visualRatio = clamp((pointer - trackStart
                - clamp(grabOffset, 0f, safeThumb)) / travel, 0f, 1f);
        return inverted ? 1f - visualRatio : visualRatio;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
