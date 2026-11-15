/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.utils.FloatArray;
import java.util.Arrays;

/** Closed rounded sheet topology, split at every triangle of the bending grid. */
final class GdxCardContour {
    static final float MARGIN = 1.065f;
    static final float RADIUS = 0.075f;
    private static final int CORNER_STEPS = 24;
    final FloatArray faces = new FloatArray();
    final FloatArray rim = new FloatArray();
    private final FloatArray hull = new FloatArray();
    private final double[] first = new double[256];
    private final double[] second = new double[256];
    private final float aspect;

    GdxCardContour(int columns, int rows, float aspect) {
        if (columns < 1 || rows < 1 || !Float.isFinite(aspect) || aspect <= 0f) {
            throw new IllegalArgumentException("Invalid card surface grid");
        }
        this.aspect = aspect;
        for (int corner = 0; corner < 4; corner++) {
            for (int step = 0; step <= CORNER_STEPS; step++) {
                double angle = -Math.PI / 2.0 + (corner + step / (double) CORNER_STEPS) * Math.PI / 2.0;
                float u = (corner < 2 ? 1f - RADIUS : RADIUS) + RADIUS * (float) Math.cos(angle);
                float v = (corner == 0 || corner == 3 ? RADIUS / aspect : 1f - RADIUS / aspect)
                        + RADIUS / aspect * (float) Math.sin(angle);
                hull.add((u - 0.5f) / MARGIN + 0.5f, (v - 0.5f) / MARGIN + 0.5f);
            }
        }
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                double u0 = column / (double) columns, u1 = (column + 1.0) / columns;
                double v0 = row / (double) rows, v1 = (row + 1.0) / rows;
                clip(u0, v0, u0, v1, u1, v0);
                clip(u1, v0, u0, v1, u1, v1);
            }
        }
        // Straight UV edges are curved in world space. Split at grid lines
        // AND diagonals, so the rim follows exactly the same face triangles.
        double[] cuts = new double[2 * (columns + rows) + 12];
        for (int edge = 0; edge < hull.size; edge += 2) {
            int next = (edge + 2) % hull.size;
            double u = hull.items[edge], v = hull.items[edge + 1];
            double du = hull.items[next] - u, dv = hull.items[next + 1] - v;
            int count = 0;
            cuts[count++] = 0.0;
            cuts[count++] = 1.0;
            count = intersections(cuts, count, u * columns, du * columns, columns);
            count = intersections(cuts, count, v * rows, dv * rows, rows);
            count = intersections(cuts, count, u * columns + v * rows,
                    du * columns + dv * rows, columns + rows);
            Arrays.sort(cuts, 0, count);
            double previous = -1.0;
            for (int i = 0; i < count; i++) {
                double t = cuts[i];
                if (t >= 1.0 - 1e-8 || t - previous < 1e-8) continue;
                rim.add((float) (u + du * t), (float) (v + dv * t));
                previous = t;
            }
        }
    }

    private static int intersections(double[] cuts, int count, double origin, double delta, int lines) {
        if (Math.abs(delta) < 1e-12) return count;
        for (int line = 0; line <= lines; line++) {
            double t = (line - origin) / delta;
            if (t > 1e-8 && t < 1.0 - 1e-8) cuts[count++] = t;
        }
        return count;
    }

    private boolean deepInside(double u, double v) {
        double x = (Math.abs((u - 0.5) * MARGIN) - 0.5) + RADIUS;
        double y = (Math.abs((v - 0.5) * MARGIN) - 0.5) * aspect + RADIUS;
        return Math.hypot(Math.max(x, 0.0), Math.max(y, 0.0))
                + Math.min(Math.max(x, y), 0.0) - RADIUS < -0.0003;
    }

    private void clip(double u0, double v0, double u1, double v1, double u2, double v2) {
        if (deepInside(u0, v0) && deepInside(u1, v1) && deepInside(u2, v2)) {
            faces.add((float) u0, (float) v0, (float) u1, (float) v1);
            faces.add((float) u2, (float) v2);
            return;
        }
        double[] input = first, output = second;
        input[0] = u0; input[1] = v0;
        input[2] = u1; input[3] = v1;
        input[4] = u2; input[5] = v2;
        int count = 6;
        for (int edge = 0; edge < hull.size && count >= 6; edge += 2) {
            int next = (edge + 2) % hull.size;
            double ax = hull.items[edge], ay = hull.items[edge + 1];
            double dx = hull.items[next] - ax, dy = hull.items[next + 1] - ay;
            int written = 0;
            double px = input[count - 2], py = input[count - 1];
            double pd = dx * (py - ay) - dy * (px - ax);
            for (int i = 0; i < count; i += 2) {
                double x = input[i], y = input[i + 1];
                double d = dx * (y - ay) - dy * (x - ax);
                if ((d >= 0.0) != (pd >= 0.0)) {
                    double t = pd / (pd - d);
                    output[written++] = px + (x - px) * t;
                    output[written++] = py + (y - py) * t;
                }
                if (d >= 0.0) {
                    output[written++] = x;
                    output[written++] = y;
                }
                px = x; py = y; pd = d;
            }
            double[] swap = input; input = output; output = swap;
            count = written;
        }
        for (int i = 2; i + 3 < count; i += 2) {
            double area = (input[i] - input[0]) * (input[i + 3] - input[1])
                    - (input[i + 1] - input[1]) * (input[i + 2] - input[0]);
            if (Math.abs(area) < 1e-12) continue;
            faces.add((float) input[0], (float) input[1], (float) input[i], (float) input[i + 1]);
            faces.add((float) input[i + 2], (float) input[i + 3]);
        }
    }
}
