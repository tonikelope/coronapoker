package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Rectangle;
import org.junit.jupiter.api.Test;

final class GdxSettingsLayoutTest {

    @Test
    void sharedSettingsFrameIsCenteredAndStaysInsideTheViewport() {
        Rectangle fullHd = GdxSettingsLayout.panelBounds(1920f, 1080f);
        assertEquals(160f, fullHd.x);
        assertEquals(80f, fullHd.y);
        assertEquals(1600f, fullHd.width);
        assertEquals(920f, fullHd.height);

        Rectangle compact = GdxSettingsLayout.panelBounds(1280f, 720f);
        assertTrue(compact.x >= 0f && compact.y >= 0f);
        assertEquals(64f, 1280f - compact.width);
        assertEquals(54f, 720f - compact.height);
    }

    @Test
    void sharedFrameOwnsTabsContentAndFooterForBothEntryPoints() {
        GdxSettingsLayout.Frame menu = GdxSettingsLayout.frame(
                1920f, 1080f, 4, 6);
        GdxSettingsLayout.Frame table = GdxSettingsLayout.frame(
                1920f, 1080f, 5, 6);

        assertEquals(menu.panel(), table.panel());
        assertEquals(menu.content(), table.content());
        assertEquals(menu.subTab(3), table.subTab(3));
        assertEquals(menu.cancelButton(), table.cancelButton());
        assertEquals(menu.restoreButton(), table.restoreButton());
        assertEquals(menu.saveButton(), table.saveButton());
        assertEquals(menu.content().y + menu.content().height
                - GdxSettingsLayout.CONTENT_ROW_TOP_INSET,
                menu.firstRowY());
        Rectangle firstMainTab = menu.mainTab(0);
        assertTrue(firstMainTab.y + firstMainTab.height
                <= menu.panel().y + menu.panel().height - 148f,
                "the subtitle must have a full line of clearance above tabs");
        Rectangle firstSubTab = menu.subTab(0);
        assertTrue(firstSubTab.y + firstSubTab.height < firstMainTab.y,
                "main and secondary tab labels must never overlap");
    }

    @Test
    void crowdedSubsectionsWrapIntoBalancedRowsWithoutOverlappingContent() {
        GdxSettingsLayout.Frame audio = GdxSettingsLayout.frame(
                1920f, 1080f, 4, 11);

        assertEquals(6, audio.subTabColumns());
        assertEquals(audio.subTab(0).x, audio.subTab(6).x);
        assertEquals(audio.subTab(0).y - GdxSettingsLayout.SUB_TAB_ROW_GAP,
                audio.subTab(6).y);
        assertTrue(audio.subTab(5).x + audio.subTab(5).width
                <= audio.panel().x + audio.panel().width
                - GdxSettingsLayout.HORIZONTAL_INSET);
        Rectangle lowestTab = audio.subTab(10);
        float headingBottom = audio.firstRowY() + 66f;
        float headingTop = headingBottom + 28f;
        assertTrue(headingTop < lowestTab.y,
                "the content heading must stay clear of wrapped tabs");
    }

    @Test
    void sharedFrameRejectsEmptyTabRows() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> GdxSettingsLayout.frame(1920f, 1080f, 0, 1));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> GdxSettingsLayout.frame(1920f, 1080f, 1, 0));
    }

    @Test
    void masterVolumeTextSliderAndButtonsNeverOverlap() {
        GdxSettingsLayout.VolumeRow row = GdxSettingsLayout.volumeRow(
                100f, 200f, 1120f);

        assertTrue(row.label().x + row.label().width
                <= row.percentage().x);
        assertTrue(row.percentage().x + row.percentage().width
                <= row.slider().x);
        assertTrue(row.slider().x + row.slider().width
                <= row.minusButton().x);
        assertTrue(row.minusButton().x + row.minusButton().width
                <= row.plusButton().x);
        assertTrue(row.plusButton().x + row.plusButton().width
                <= row.bounds().x + row.bounds().width);
    }

    @Test
    void denseTogglePagesKeepTheirLastRowInsideTheContentPanel() {
        float contentHeight = GdxSettingsLayout.frame(
                1920f, 1080f, 4, 8).content().height;
        float firstRowY = contentHeight
                - GdxSettingsLayout.CONTENT_ROW_TOP_INSET;
        float stride = GdxSettingsLayout.rowStride(contentHeight, 6);
        float lastRowY = firstRowY - stride * 5f;

        assertTrue(stride < 84f,
                "six rows must close the gaps instead of crossing the footer");
        assertTrue(lastRowY >= 16f,
                "the final settings row must remain inside content bounds");
        assertEquals(84f, GdxSettingsLayout.rowStride(contentHeight, 5));
    }
}
