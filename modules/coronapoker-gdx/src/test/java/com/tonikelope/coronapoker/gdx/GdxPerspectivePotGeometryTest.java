package com.tonikelope.coronapoker.gdx;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GdxPerspectivePotGeometryTest {
    @Test void restingCompressedStacksEndInWholeChipsAndGrowContinuously() {
        var geometry = new CoronaPokerGdxTable.StackChipRackGeometry(27f, -22f,
                14f, 5f, 3f, 1f, 70f, 50f);
        for (float rise : new float[]{0f, 5f}) {
            float previous = 0f;
            for (int count = 0; count <= 1000; count++) {
                float current = CoronaPokerGdxTable.perspectivePotLevels(geometry, rise, count);
                assertEquals(Math.round(current), current, .00001f,
                        "A settled stack has a fractional top chip");
                assertTrue(current >= previous && current <= 24f);
                if (count > 0) {
                    float near = CoronaPokerGdxTable.perspectivePotLevels(geometry, rise, count - .0001f);
                    assertEquals(current, near, .001f, "A growing stack jumps at an integer count");
                    float midpoint = CoronaPokerGdxTable.perspectivePotLevels(geometry, rise, count - .5f);
                    assertTrue(midpoint >= previous && midpoint <= current);
                }
                previous = current;
            }
        }
    }
}
