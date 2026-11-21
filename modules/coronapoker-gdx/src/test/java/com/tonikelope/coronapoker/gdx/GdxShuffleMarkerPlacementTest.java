/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GdxShuffleMarkerPlacementTest {
    @Test void roundMarkerFitsBesideADiagonalDeckEdge() {
        var placement = new GdxShuffleMarkerPlacement();
        var projection = new Matrix4();
        projection.val[Matrix4.M00] = .3f;
        projection.val[Matrix4.M10] = .3f;
        projection.val[Matrix4.M11] = 0f;
        projection.val[Matrix4.M02] = -.2f;
        projection.val[Matrix4.M12] = .2f;
        float aspect = .7f;
        placement.project(projection, aspect, 200f, 200f);
        var empty = new Rectangle();
        // The deck edge lies on y = x - 20 * aspect. The circle clears it by just over one pixel.
        var anchor = new Vector2(100f, 100f - 20f * aspect - 16f);
        assertEquals(anchor, placement.place(anchor.x, anchor.y, 10f, 10f, empty, empty, empty, true));

        var centre = new Vector2(100f, 100f);
        assertNotEquals(centre, placement.place(100f, 100f, 10f, 10f, empty, empty, empty, true));
        assertEquals(centre, placement.place(100f, 100f, 10f, 10f, empty, empty, empty, false));
        var bank = new Rectangle(104f, 80f, 50f, 40f);
        assertEquals(new Vector2(94f, 100f), placement.place(100f, 100f, 10f, 10f, empty, bank, empty, false));
    }
}
