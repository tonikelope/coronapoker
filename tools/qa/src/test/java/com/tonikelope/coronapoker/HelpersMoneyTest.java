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

    // ----- float -> double money migration safety net -------------------------

    @Test
    void doubleCleanMatchesFloatCleanOnTheLegacyGridBelowCeiling() {
        // Byte-identity guarantee: the double money cleaner agrees with the legacy
        // float cleaner for every value a normal game can produce (tenth- and
        // cent-grid, up to the float ceiling). So migrated normal games are
        // numerically unchanged.
        double[] grid = {0, 0.1, 0.2, 0.3, 0.5, 0.9, 1.0, 2.5, 10.3, 999.9, 5000.0,
            0.01, 0.05, 0.25, 0.35, 0.37, 1.99, 12.34, 50000.55, 131072.00};
        for (double v : grid) {
            assertEquals(Helpers.floatClean((float) v), (float) Helpers.doubleClean(v), 0f,
                    "double/float cleaners must agree below the ceiling: " + v);
        }
    }

    @Test
    void doubleCleanUsesThePreciseBigDecimalConstructor() {
        // LOCK the constructor decision: new BigDecimal(double) reproduces the
        // legacy float rounding at exact half-cent inputs; BigDecimal.valueOf would
        // diverge (2.675 -> 2.68 instead of 2.67) and silently shift cent rounding.
        // If this fails, someone switched doubleClean to valueOf — revert it.
        assertEquals(2.67, Helpers.doubleClean(2.675), 0.0);
        assertEquals(0.14, Helpers.doubleClean(0.145), 0.0);
        assertEquals(1.00, Helpers.doubleClean(1.005), 0.0);
        // And it agrees with the legacy float path at those same boundaries.
        assertEquals(Helpers.floatClean(2.675f), (float) Helpers.doubleClean(2.675), 0f);
    }

    @Test
    void doubleCleanIsExactAboveTheFloatCeiling() {
        // The point of the migration: cents stay exact above ~131072 chips, where
        // float32 can no longer represent every cent. double does not drift.
        assertEquals(200000.07, Helpers.doubleClean(200000.07), 0.0);
        assertEquals(1000000.55, Helpers.doubleClean(1000000.55), 0.0);
        assertEquals(9999999.99, Helpers.doubleClean(9999999.99), 0.0);
    }

    @Test
    void doubleCompareIsAtCentResolution() {
        assertEquals(0, Helpers.doubleSecureCompare(0.25, 0.25));
        assertEquals(0, Helpers.doubleSecureCompare(0.250001, 0.25)); // same cent
        assertTrue(Helpers.doubleSecureCompare(0.10, 0.20) < 0);
        assertTrue(Helpers.doubleSecureCompare(200000.25, 200000.20) > 0); // distinct cents above ceiling
    }
}
