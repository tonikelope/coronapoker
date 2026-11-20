/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GdxShufflePlacementTest {
    @Test
    void theDeckAspectChangesOnlyTheShortEdgeDuringEveryPhase() {
        var reference = new GdxShuffleGeometry();
        var modded = new GdxShuffleGeometry();
        int stride = GdxShuffleGeometry.FLOATS_PER_VERTEX;
        for (float time : new float[] {0f, .46f, .92f, 1.6f, 2.6f, 3.2f, 4.46f}) {
            reference.update(time, GdxShuffleGeometry.Motion.MID_BRIDGE, .75f);
            modded.update(time, GdxShuffleGeometry.Motion.MID_BRIDGE, .60f);
            float low = Float.POSITIVE_INFINITY, high = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < reference.vertices.length; i += stride) {
                assertEquals(reference.vertices[i], modded.vertices[i]);
                assertEquals(reference.vertices[i+1], modded.vertices[i+1]);
                assertEquals(reference.vertices[i+2] * .8f, modded.vertices[i+2], .000001f);
                low = Math.min(low, modded.vertices[i+1]);
                high = Math.max(high, modded.vertices[i+1]);
            }
            if (time == 0f || time == 4.46f) assertEquals(52f * .32f, (high - low) * 88f, .0001f);
        }
    }

    @Test
    void rotatedDeckKeepsTheCommunityCardDimensionsOnTheFelt() {
        GdxNativesLoader.load();
        var table = new GdxPerspectiveTable(4);
        var mesh = new GdxShuffleGeometry();
        float aspect = 874f / 1176f;
        mesh.update(0f, GdxShuffleGeometry.Motion.MID_BRIDGE, aspect);
        var projection = new Matrix4();
        var point = new Vector3();
        for (int[] size : new int[][] {{1280, 720}, {1024, 768}, {1920, 1080}, {3840, 2160}}) {
            float width = size[0], height = size[1], centerY = height * .52f;
            float cardWidth = 140f, cardLength = cardWidth / aspect;
            table.layout(width, height, centerY + 27f);
            for (float transition : new float[] {0f, .5f, 1f}) {
                table.moveCamera(transition);
                table.shuffleProjection(projection, centerY, cardLength, aspect, GdxShuffleAngle.DEFAULT);
                float left = width, right = 0f, bottom = height, top = 0f;
                for (int i = 0; i < mesh.vertices.length; i += GdxShuffleGeometry.FLOATS_PER_VERTEX) {
                    point.set(mesh.vertices[i], mesh.vertices[i + 1], mesh.vertices[i + 2]).prj(projection);
                    float x = (1f + point.x) * width * .5f;
                    float y = (1f + point.y) * height * .5f;
                    left = Math.min(left, x); right = Math.max(right, x);
                    bottom = Math.min(bottom, y); top = Math.max(top, y);
                }
                assertEquals(width * .5f, (left + right) * .5f, .15f);
                assertEquals(centerY, (bottom + top) * .5f, .15f);
                for (int angle : new int[]{-180, 0, GdxShuffleAngle.DEFAULT, 90, 180}) {
                    table.shuffleProjection(projection, centerY, cardLength, aspect, angle);
                    Vector3 middle = onFelt(table, projection, 0, 0, width, height);
                    assertEquals(cardLength, 2f * middle.dst(onFelt(table, projection, .5f, 0, width, height)), .1f);
                    assertEquals(cardWidth, 2f * middle.dst(onFelt(table, projection, 0, aspect * .5f, width, height)), .1f);
                }
                // Unproject a bottom corner onto the real board and recover its depth.
                // A flat overlay or an elevated mesh would have a different projected z.
                point.set(.45f, 0f, .30f).prj(projection);
                float depth = point.z;
                Vector3 contact = table.boardAt((point.x + 1f) * width * .5f, (point.y + 1f) * height * .5f);
                point.set(contact.x, contact.y, 0f).prj(table.boardProjection());
                assertEquals(depth, point.z, .00001f);
            }
        }
    }
    private static Vector3 onFelt(GdxPerspectiveTable table, Matrix4 projection,
            float x, float z, float width, float height) {
        Vector3 p = new Vector3(x, 0f, z).prj(projection);
        return new Vector3(table.boardAt((p.x + 1f) * width * .5f, (p.y + 1f) * height * .5f));
    }

}
