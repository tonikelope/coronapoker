package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

final class GdxNewGameNavigationTest {

    @Test
    void firstPageIsGeneralAndRemainsAvailableForReturningToIt() {
        assertEquals("gdx.settings.page.general",
                GdxFrontendScreen.NEW_GAME_PAGE_LABEL_KEYS.get(0));
        assertEquals(6, GdxFrontendScreen.NEW_GAME_PAGE_LABEL_KEYS.size());
        assertFalse(GdxFrontendScreen.NEW_GAME_PAGE_LABEL_KEYS.contains(
                "gdx.connection"));
    }
}
