/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.utils.FloatArray;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GdxCardContourTest {
    @Test void sharedSurfaceSamplesPreserveAllClippedFaceAndRimCoordinates() {
        for (int[] grid : new int[][]{{1, 1}, {28, 16}, {32, 48}}) {
            var contour = new GdxCardContour(grid[0], grid[1], 1.4f);
            assertMappings(contour.faces, contour.faceSamples, contour.sampleUvs);
            assertMappings(contour.rim, contour.rimSamples, contour.sampleUvs);
            int faceCount = contour.faceSamples.length;
            assertEquals(2 * faceCount, contour.faceIndices.length);
            for (int i = 0; i < faceCount; i += 3) {
                for (int corner = 0; corner < 3; corner++) {
                    int sample = contour.faceSamples[i + corner];
                    assertEquals(2 * sample, Short.toUnsignedInt(contour.faceIndices[i + corner]));
                    int reverse = (3 - corner) % 3;
                    assertEquals(2 * sample + 1,
                            Short.toUnsignedInt(contour.faceIndices[faceCount + i + reverse]));
                }
            }
            assertTrue(contour.sampleUvs.size < contour.faces.size + contour.rim.size);
        }
    }

    private static void assertMappings(FloatArray coords, int[] indices, FloatArray samples) {
        assertEquals(coords.size, 2 * indices.length);
        for (int i = 0; i < indices.length; i++) {
            for (int axis = 0; axis < 2; axis++) {
                assertEquals(Float.floatToRawIntBits(coords.items[2 * i + axis]),
                        Float.floatToRawIntBits(samples.items[2 * indices[i] + axis]));
            }
        }
    }

    @Test void roundedFacesFillTheirClosedRimWithoutHolesOrOverlappingTriangles() {
        for (int[] grid : new int[][]{{1, 1}, {28, 16}, {32, 48}}) {
            for (float aspect : new float[]{1.36f, 1.4f, 1.53f}) {
                var contour = new GdxCardContour(grid[0], grid[1], aspect);
                double area = 0.0;
                for (int at = 0; at < contour.faces.size; at += 6) {
                    float[] f = contour.faces.items;
                    double cross = (f[at + 2] - f[at]) * (double) (f[at + 5] - f[at + 1])
                            - (f[at + 4] - f[at]) * (double) (f[at + 3] - f[at + 1]);
                    assertTrue(cross < 0.0, "Degenerate or reversed face triangle");
                    area -= cross * 0.5;
                }
                double rimArea = 0.0;
                for (int at = 0; at < contour.rim.size; at += 2) {
                    int next = (at + 2) % contour.rim.size;
                    rimArea += contour.rim.items[at] * (double) contour.rim.items[next + 1]
                            - contour.rim.items[next] * (double) contour.rim.items[at + 1];
                }
                assertEquals(rimArea * 0.5, area, 0.000001,
                        "The face and the shell outline leave a gap or overlap");
                double expected = (1.0 - (4.0 - Math.PI) * GdxCardContour.RADIUS
                        * GdxCardContour.RADIUS / aspect)
                        / (GdxCardContour.MARGIN * GdxCardContour.MARGIN);
                assertEquals(expected, area, 0.000015);
            }
        }
    }

    @Test void theRimFollowsEveryFaceTriangleInsteadOfBridgingTheBentEdge() {
        for (int[] grid : new int[][]{{1, 1}, {28, 16}, {32, 48}}) {
            var contour = new GdxCardContour(grid[0], grid[1], 1.4f);
            for (int at = 0; at < contour.rim.size; at += 2) {
                int next = (at + 2) % contour.rim.size;
                float u = contour.rim.items[at], v = contour.rim.items[at + 1];
                float un = contour.rim.items[next], vn = contour.rim.items[next + 1];
                assertNoGridLineBetween(u * grid[0], un * grid[0]);
                assertNoGridLineBetween(v * grid[1], vn * grid[1]);
                assertNoGridLineBetween(u * grid[0] + v * grid[1],
                        un * grid[0] + vn * grid[1]);
            }
            assertTrue(contour.rim.size > 100);
        }
    }

    @Test void transparentRectangleCornersAreAbsentFromBothSurfaces() {
        var contour = new GdxCardContour(32, 48, 1.4f);
        for (FloatArray geometry : new FloatArray[]{contour.faces, contour.rim}) {
            for (int at = 0; at < geometry.size; at += 2) {
                double x = (Math.abs((geometry.items[at] - 0.5) * GdxCardContour.MARGIN) - 0.5)
                        + GdxCardContour.RADIUS;
                double y = (Math.abs((geometry.items[at + 1] - 0.5) * GdxCardContour.MARGIN) - 0.5)
                        * 1.4 + GdxCardContour.RADIUS;
                double distance = Math.hypot(Math.max(x, 0.0), Math.max(y, 0.0))
                        + Math.min(Math.max(x, y), 0.0) - GdxCardContour.RADIUS;
                assertTrue(distance <= 0.000001, "Geometry writes depth outside the rounded card");
            }
        }
    }

    private static void assertNoGridLineBetween(double first, double second) {
        double low = Math.min(first, second), high = Math.max(first, second);
        assertTrue(Math.ceil(low + 0.00001) >= high - 0.00001,
                () -> "An uncut edge crosses a bending triangle: " + first + " to " + second);
    }
}
