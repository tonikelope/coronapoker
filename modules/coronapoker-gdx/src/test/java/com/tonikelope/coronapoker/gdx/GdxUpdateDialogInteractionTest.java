package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GdxUpdateDialogInteractionTest {

    @Test
    void blockingModalOwnsPointerBeforeMenuRevealCompletes() {
        assertTrue(GdxFrontendScreen.menuRevealBlocksInteraction(
                true, 0f, false));
        assertFalse(GdxFrontendScreen.menuRevealBlocksInteraction(
                true, 0f, true));
        assertFalse(GdxFrontendScreen.menuRevealBlocksInteraction(
                true, 1f, false));
        assertFalse(GdxFrontendScreen.menuRevealBlocksInteraction(
                false, 0f, false));
    }
}
