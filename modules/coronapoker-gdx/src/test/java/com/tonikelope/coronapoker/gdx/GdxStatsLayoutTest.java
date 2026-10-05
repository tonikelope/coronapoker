package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Test
    void pickerUsesPixelScrollingInsteadOfPages() {
        float maximum = GdxFrontendScreen.statsPickerMaximumScroll(207);
        assertTrue(maximum > 10_000f);
        assertEquals(0f, GdxFrontendScreen.statsPickerScrollAfterWheel(
                0f, maximum, -1f));
        float moved = GdxFrontendScreen.statsPickerScrollAfterWheel(
                0f, maximum, 1f);
        assertTrue(moved > GdxFrontendScreen.STATS_PICKER_ROW_STRIDE);
        assertEquals(maximum, GdxFrontendScreen.statsPickerScrollAfterWheel(
                maximum - 1f, maximum, 10f));
    }

    @Test
    void pickerCanCenterASelectedEntryAndPreservesRowSpacing() {
        float maximum = GdxFrontendScreen.statsPickerMaximumScroll(207);
        float scroll = GdxFrontendScreen.statsPickerScrollForSelection(
                120, maximum);
        assertTrue(scroll > 0f && scroll < maximum);
        assertEquals(GdxFrontendScreen.STATS_PICKER_ROW_STRIDE,
                GdxFrontendScreen.statsPickerRowY(7, scroll)
                        - GdxFrontendScreen.statsPickerRowY(8, scroll));
    }

    @Test
    void longResultTablesScrollWithoutTruncatingRows() {
        float maximum = GdxFrontendScreen.statsResultMaximumScroll(1000,
                48f);
        assertEquals(0f, GdxFrontendScreen.statsResultMaximumScroll(
                GdxFrontendScreen.STATS_RESULT_VISIBLE_ROWS, 48f));
        assertTrue(maximum > 40_000f);
        assertEquals(0f, GdxFrontendScreen.statsResultScrollAfterWheel(
                0f, maximum, 48f, -1f));
        assertEquals(maximum,
                GdxFrontendScreen.statsResultScrollAfterWheel(
                        maximum - 1f, maximum, 48f, 10f));
        assertEquals(48f, GdxFrontendScreen.statsResultRowY(
                593f, 7, 48f, 125f)
                        - GdxFrontendScreen.statsResultRowY(
                                593f, 8, 48f, 125f));
    }

    @Test
    void statsUnitsCardsAndPlayerListsRetainTheirInformation() {
        assertEquals("SEGUNDOS", GdxFrontendScreen.statsUnitHeader(
                "(SEGUNDOS)"));
        GdxFrontendScreen.StatsCardGlyph heart =
                GdxFrontendScreen.decodeStatsCard("10_C");
        assertEquals("10", heart.rank());
        assertEquals("♥", heart.suit());
        assertTrue(heart.red());
        GdxFrontendScreen.StatsCardGlyph spade =
                GdxFrontendScreen.decodeStatsCard("AP");
        assertEquals("A", spade.rank());
        assertEquals("♠", spade.suit());
        assertTrue(!spade.red());
        assertEquals(0f, GdxFrontendScreen.statsSummaryPlayersMaximum(2));
        assertTrue(GdxFrontendScreen.statsSummaryPlayersMaximum(10) > 0f);
    }

    @Test
    void chartZoomChangesGeometryAndPanReachesBothCanvasEdges() {
        assertEquals(50f, GdxFrontendScreen.statsChartCanvasCoordinate(
                50f, 100f, 1f, 0.5f));
        assertEquals(0f, GdxFrontendScreen.statsChartCanvasCoordinate(
                0f, 100f, 2f, 0f));
        assertEquals(100f, GdxFrontendScreen.statsChartCanvasCoordinate(
                100f, 100f, 2f, 1f));
        assertEquals(25f, GdxFrontendScreen.statsChartCanvasCoordinate(
                0f, 100f, 0.5f, 0f));
    }

    @Test
    void verticalChartScrollUsesConventionalTopToBottomDirection() {
        assertEquals(100f,
                GdxFrontendScreen.statsChartCanvasVerticalCoordinate(
                        100f, 100f, 2f, 0f));
        assertEquals(0f,
                GdxFrontendScreen.statsChartCanvasVerticalCoordinate(
                        0f, 100f, 2f, 1f));
    }
}
