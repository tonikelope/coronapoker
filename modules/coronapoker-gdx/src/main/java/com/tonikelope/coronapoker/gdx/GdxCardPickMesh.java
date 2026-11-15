/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.FloatArray;

/** Picking uses the same closed triangles as the visible plastic shell. */
final class GdxCardPickMesh {
    final FloatArray triangles = new FloatArray();

    void append(FloatArray vertices) {
        for (int at = 0; at < vertices.size; at += 9) {
            triangles.add(vertices.items[at], vertices.items[at + 1], vertices.items[at + 2]);
        }
    }

    float distance(Vector3 origin, Vector3 direction) {
        float best = Float.POSITIVE_INFINITY;
        float[] p = triangles.items;
        for (int at = 0; at < triangles.size; at += 9) {
            float ax = p[at + 3] - p[at], ay = p[at + 4] - p[at + 1], az = p[at + 5] - p[at + 2];
            float bx = p[at + 6] - p[at], by = p[at + 7] - p[at + 1], bz = p[at + 8] - p[at + 2];
            float hx = direction.y * bz - direction.z * by;
            float hy = direction.z * bx - direction.x * bz;
            float hz = direction.x * by - direction.y * bx;
            float det = ax * hx + ay * hy + az * hz;
            if (Math.abs(det) < 1e-7f) continue;
            float sx = origin.x - p[at], sy = origin.y - p[at + 1], sz = origin.z - p[at + 2];
            float u = (sx * hx + sy * hy + sz * hz) / det;
            if (u < 0f || u > 1f) continue;
            float qx = sy * az - sz * ay, qy = sz * ax - sx * az, qz = sx * ay - sy * ax;
            float v = (direction.x * qx + direction.y * qy + direction.z * qz) / det;
            if (v < 0f || u + v > 1f) continue;
            float t = (bx * qx + by * qy + bz * qz) / det;
            if (t >= 0f) best = Math.min(best, t);
        }
        return best;
    }
}
