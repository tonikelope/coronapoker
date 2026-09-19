package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GdxFrontendPresetSelectionTest {

    @Test
    void cyclesForwardAndBackwardIncludingTheDefaultProfile() {
        assertEquals(0, GdxFrontendScreen.adjacentPresetIndex(-1, 3, 1));
        assertEquals(1, GdxFrontendScreen.adjacentPresetIndex(0, 3, 1));
        assertEquals(2, GdxFrontendScreen.adjacentPresetIndex(1, 3, 1));
        assertEquals(-1, GdxFrontendScreen.adjacentPresetIndex(2, 3, 1));

        assertEquals(2, GdxFrontendScreen.adjacentPresetIndex(-1, 3, -1));
        assertEquals(-1, GdxFrontendScreen.adjacentPresetIndex(0, 3, -1));
        assertEquals(0, GdxFrontendScreen.adjacentPresetIndex(1, 3, -1));
    }

    @Test
    void remainsOnDefaultWhenThereAreNoSavedProfiles() {
        assertEquals(-1, GdxFrontendScreen.adjacentPresetIndex(-1, 0, 1));
        assertEquals(-1, GdxFrontendScreen.adjacentPresetIndex(-1, 0, -1));
    }

    @Test
    void genericSelectorsWrapInBothDirections() {
        assertEquals(1, GdxFrontendScreen.adjacentIndex(0, 3, 1));
        assertEquals(0, GdxFrontendScreen.adjacentIndex(2, 3, 1));
        assertEquals(2, GdxFrontendScreen.adjacentIndex(0, 3, -1));
        assertEquals(1, GdxFrontendScreen.adjacentIndex(2, 3, -1));
        assertEquals(-1, GdxFrontendScreen.adjacentIndex(0, 0, 1));
    }
}
