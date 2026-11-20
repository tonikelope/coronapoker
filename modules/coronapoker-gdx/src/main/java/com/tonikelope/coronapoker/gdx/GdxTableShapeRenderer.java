/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/** Emits the table HUD's existing triangle strips and fans with one batch check. */
final class GdxTableShapeRenderer extends ShapeRenderer {
    GdxTableShapeRenderer(int capacity) {
        super(capacity);
    }

    void filledQuad(float x0, float y0, Color c0, float x1, float y1, Color c1,
            float x2, float y2, Color c2, float x3, float y3, Color c3) {
        check(ShapeType.Filled, ShapeType.Line, 6);
        ImmediateModeRenderer renderer = getRenderer();
        float a = c0.toFloatBits(), b = c1.toFloatBits(), c = c2.toFloatBits(), d = c3.toFloatBits();
        vertex(renderer, x0, y0, a);
        vertex(renderer, x1, y1, b);
        vertex(renderer, x2, y2, c);
        vertex(renderer, x0, y0, a);
        vertex(renderer, x2, y2, c);
        vertex(renderer, x3, y3, d);
    }

    void filledFan(float cx, float cy, float radius, float[] unitX, float[] unitY, Color color) {
        check(ShapeType.Filled, ShapeType.Line, (unitX.length - 1) * 3);
        ImmediateModeRenderer renderer = getRenderer();
        float packed = color.toFloatBits();
        float previousX = cx + unitX[0] * radius, previousY = cy + unitY[0] * radius;
        for (int step = 1; step < unitX.length; step++) {
            float x = cx + unitX[step] * radius, y = cy + unitY[step] * radius;
            vertex(renderer, cx, cy, packed);
            vertex(renderer, previousX, previousY, packed);
            vertex(renderer, x, y, packed);
            previousX = x;
            previousY = y;
        }
    }

    void filledBand(float cx, float cy, float outerRadius, float innerRadius,
            float[] unitX, float[] unitY, Color outer, Color inner) {
        check(ShapeType.Filled, ShapeType.Line, (unitX.length - 1) * 6);
        ImmediateModeRenderer renderer = getRenderer();
        float outerColor = outer.toFloatBits();
        float innerColor = inner.toFloatBits();
        float outerPreviousX = cx + unitX[0] * outerRadius;
        float outerPreviousY = cy + unitY[0] * outerRadius;
        float innerPreviousX = cx + unitX[0] * innerRadius;
        float innerPreviousY = cy + unitY[0] * innerRadius;
        for (int step = 1; step < unitX.length; step++) {
            float outerX = cx + unitX[step] * outerRadius;
            float outerY = cy + unitY[step] * outerRadius;
            float innerX = cx + unitX[step] * innerRadius;
            float innerY = cy + unitY[step] * innerRadius;
            vertex(renderer, outerPreviousX, outerPreviousY, outerColor);
            vertex(renderer, outerX, outerY, outerColor);
            vertex(renderer, innerX, innerY, innerColor);
            vertex(renderer, outerPreviousX, outerPreviousY, outerColor);
            vertex(renderer, innerX, innerY, innerColor);
            vertex(renderer, innerPreviousX, innerPreviousY, innerColor);
            outerPreviousX = outerX;
            outerPreviousY = outerY;
            innerPreviousX = innerX;
            innerPreviousY = innerY;
        }
    }

    private static void vertex(ImmediateModeRenderer renderer, float x, float y, float packedColor) {
        renderer.color(packedColor);
        renderer.vertex(x, y, 0f);
    }
}
