/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

/** Draws a dashed marking on the table's surface, with one phase around corners. */
final class GdxFeltMarking {
    private GdxFeltMarking() {}

    static void rounded(ShapeRenderer shapes, float left, float bottom, float right,
            float top, float radius) {
        if (right <= left || top <= bottom) return;
        radius = Math.min(radius, Math.min(right - left, top - bottom) * .5f);
        float previousX = right, previousY = bottom + radius;
        float phase = 0f;
        for (int corner = 0; corner < 4; corner++) {
            float cx = corner == 0 || corner == 3 ? right - radius : left + radius;
            float cy = corner <= 1 ? top - radius : bottom + radius;
            for (int point = 0; point <= 8; point++) {
                float angle = (corner * 90f + point * 90f / 8f);
                float x = cx + radius * MathUtils.cosDeg(angle);
                float y = cy + radius * MathUtils.sinDeg(angle);
                phase = dashedSegment(shapes, previousX, previousY, x, y, phase);
                previousX = x;
                previousY = y;
            }
        }
    }

    private static float dashedSegment(ShapeRenderer shapes, float x1, float y1, float x2,
            float y2, float phase) {
        float length = (float) Math.hypot(x2 - x1, y2 - y1);
        float dash = 10f, period = 18f;
        for (float from = -phase; from < length; from += period) {
            float start = Math.max(0f, from), end = Math.min(length, from + dash);
            if (end <= start) continue;
            shapes.rectLine(MathUtils.lerp(x1, x2, start / length), MathUtils.lerp(y1, y2, start / length),
                    MathUtils.lerp(x1, x2, end / length), MathUtils.lerp(y1, y2, end / length), 1.8f);
        }
        return (phase + length) % period;
    }
}
