package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Guards vertical ownership of the statistics header, selector and content. */
class GdxStatsLayoutTest {

    @Test
    void viewSelectorFitsBetweenHeaderAndContentWithoutOverlap() {
        float selectorTop = GdxFrontendScreen.STATS_MODE_SELECTOR_Y
                + GdxFrontendScreen.STATS_MODE_SELECTOR_HEIGHT;
        assertTrue(selectorTop
                        <= GdxFrontendScreen.STATS_VIEW_HEADER_SEPARATOR_Y - 4f,
                "the view selector must stay below the panel header");
        assertTrue(GdxFrontendScreen.STATS_MODE_SELECTOR_Y
                        >= GdxFrontendScreen.STATS_CONTENT_TOP + 4f,
                "the view selector must stay above charts and tables");
    }
}
