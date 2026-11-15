/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/** Inextensible transverse arcs with a small corner lead for a plastic card. */
final class GdxPlasticCard {

    private GdxPlasticCard() { }

    static float thickness(float width) {
        return width * 0.0048f;
    }

    static void flight(Vector3 out, float width, float height, float u, float v, float progress) {
        double bend = 0.17 * Math.sin(2.0 * Math.PI * progress);
        if (Math.abs(bend) < 0.0001f) {
            out.set((u - 0.5f) * width, 0f, (v - 0.5f) * height);
            return;
        }
        double radius = height / bend;
        double angle = (v - 0.5f) * bend;
        out.set((u - 0.5f) * width, (float) (radius * (1.0 - Math.cos(angle))),
                (float) (radius * Math.sin(angle)));
    }

    static void reveal(Vector3 out, float width, float height, float u, float v,
            float progress, float direction) {
        float p = MathUtils.clamp(progress, 0f, 1f);
        if (p == 0f || p == 1f) {
            out.set((u - 0.5f) * width * (p == 0f ? 1f : -1f),
                    0f, (v - 0.5f) * height);
            return;
        }
        float pulse = (float) Math.sin(Math.PI * p);
        float turn = MathUtils.PI * Interpolation.smoother.apply(p);
        // The near corner starts first. The far edge follows through the
        // same continuous surface, rather than turning a rigid rectangle.
        float cornerLead = 0.19f * pulse * (2f * v - 1f);
        double theta = Math.copySign(MathUtils.clamp(turn + cornerLead,
                0f, MathUtils.PI), direction);
        double bend = 0.80 * pulse * (0.75 + 0.25 * v);
        double from = theta - bend * 0.5;
        double to = theta + bend * 0.5;
        double at = from + u * bend;
        double radius = width / bend;
        double x = radius * (Math.sin(at) - (Math.sin(from) + Math.sin(to)) * 0.5);
        // Find the lowest point of the arc, including its interior. This
        // keeps the supported edge on the felt throughout the turn.
        double floor = Math.min(Math.cos(from), Math.cos(to));
        if (from <= -Math.PI && to >= -Math.PI || from <= Math.PI && to >= Math.PI) {
            floor = -1.0;
        }
        double y = radius * (Math.cos(at) - floor);
        out.set((float) x, (float) y, (v - 0.5f) * height);
    }
}
