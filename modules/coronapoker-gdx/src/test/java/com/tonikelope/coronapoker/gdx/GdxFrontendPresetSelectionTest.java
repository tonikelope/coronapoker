package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test
    void deletingAStoredProfileDoesNotResetTheCurrentTableDraft()
            throws Exception {
        String source = Files.readString(Path.of("src", "main", "java",
                "com", "tonikelope", "coronapoker", "gdx",
                "GdxFrontendScreen.java"), StandardCharsets.UTF_8);
        int start = source.indexOf("private void confirmDeletePreset()");
        int end = source.indexOf("private boolean persistGamePresets(", start);
        assertTrue(start >= 0 && end > start);

        assertFalse(source.substring(start, end).contains(
                "table = new NewGameTableDraft()"),
                "deleting a saved profile must preserve unsaved table controls");
    }
}
