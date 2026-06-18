/*
 * Money resolution of Helpers.floatClean / float1DSecureCompare.
 *
 * The engine chip is the cent (0.01). The critical safety property for the
 * 0.1 -> 0.01 migration: any value already on the 0.1 grid (every legacy game)
 * is numerically UNCHANGED, so existing games behave identically; only finer
 * (cent) resolution is added.
 */
package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class HelpersMoneyTest {

    @Test
    void tenthGridValuesAreUnchanged() {
        // Safety property: floatClean is a no-op on any 0.1 multiple.
        float[] tenths = {0f, 0.1f, 0.2f, 0.3f, 0.5f, 0.9f, 1.0f, 2.5f, 10.3f, 999.9f, 5000.0f};
        for (float v : tenths) {
            assertEquals(v, Helpers.floatClean(v), 0f, "tenth-grid value must be unchanged: " + v);
        }
    }

    @Test
    void centGridValuesArePreserved() {
        float[] cents = {0.01f, 0.05f, 0.25f, 0.35f, 0.37f, 1.99f, 12.34f};
        for (float v : cents) {
            assertEquals(v, Helpers.floatClean(v), 0f, "cent-grid value must be preserved: " + v);
        }
    }

    @Test
    void subCentIsRoundedToTheCent() {
        assertEquals(0.12f, Helpers.floatClean(0.123f), 0f);
        assertEquals(0.13f, Helpers.floatClean(0.125f), 0f); // HALF_UP
        assertEquals(1.00f, Helpers.floatClean(0.999f), 0f);
    }

    @Test
    void compareIsAtCentResolution() {
        assertEquals(0, Helpers.float1DSecureCompare(0.25f, 0.25f));
        assertEquals(0, Helpers.float1DSecureCompare(0.250001f, 0.25f)); // same cent
        assertTrue(Helpers.float1DSecureCompare(0.10f, 0.20f) < 0);
        assertTrue(Helpers.float1DSecureCompare(0.25f, 0.20f) > 0); // distinct at cents
    }
}
