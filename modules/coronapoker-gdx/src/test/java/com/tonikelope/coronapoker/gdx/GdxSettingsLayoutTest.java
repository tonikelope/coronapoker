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
        assertTrue(firstMainTab.y
                - (firstSubTab.y + firstSubTab.height) >= 30f,
                "the shared separator needs visible space on both sides");
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
        float firstRowTop = audio.firstRowY()
                + GdxSettingsLayout.ROW_HEIGHT;
        assertTrue(firstRowTop < lowestTab.y,
                "the first control must stay clear of wrapped tabs");
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
    void sharedStepperKeepsPaintingAndHitTargetsDisjoint() {
        GdxSettingsLayout.StepperRow row = GdxSettingsLayout.stepperRow(
                100f, 200f, 1_120f, GdxSettingsLayout.ROW_HEIGHT);

        assertTrue(row.label().x + row.label().width
                <= row.controls().x);
        assertTrue(row.minusButton().x + row.minusButton().width
                <= row.value().x);
        assertTrue(row.value().x + row.value().width
                <= row.plusButton().x);
        assertTrue(row.plusButton().x + row.plusButton().width
                <= row.bounds().x + row.bounds().width);
        assertEquals(-1, row.directionAt(row.minusButton().x + 1f));
        assertEquals(1, row.directionAt(row.plusButton().x + 1f));
        assertEquals(0, row.directionAt(row.value().x + 1f));
        assertTrue(row.valueContains(row.value().x + 1f));
    }

    @Test
    void debugCopyButtonStaysInsideTheSharedContentFooter() {
        Rectangle content = GdxSettingsLayout.frame(
                1920f, 1080f, 5, 1).content();
        Rectangle button = GdxSettingsLayout.debugCopyButton(content);

        assertTrue(button.x >= content.x && button.y >= content.y);
        assertTrue(button.x + button.width
                <= content.x + content.width);
        assertTrue(button.y + button.height
                <= content.y + content.height);
        assertEquals(content.x + content.width - 18f,
                button.x + button.width);
        assertEquals(content.y + 14f, button.y);
    }

    @Test
    void denseTogglePagesKeepTheirLastRowInsideTheContentPanel() {
        float contentHeight = GdxSettingsLayout.frame(
                1920f, 1080f, 4, 8).content().height;
        float firstRowY = contentHeight
                - GdxSettingsLayout.CONTENT_ROW_TOP_INSET;
        float stride = GdxSettingsLayout.rowStride(contentHeight, 6);
        float lastRowY = firstRowY - stride * 5f;

        assertEquals(GdxSettingsLayout.ROW_STRIDE, stride,
                "both settings renderers must use the same row rhythm");
        assertTrue(lastRowY >= 8f,
                "the final settings row must remain inside content bounds");
        assertEquals(GdxSettingsLayout.ROW_STRIDE,
                GdxSettingsLayout.rowStride(contentHeight, 5));
    }

    @Test
    void childRowsAreInsetOnlyFromTheLeft() {
        Rectangle parent = GdxSettingsLayout.optionRow(
                100f, 500f, 1_000f, false);
        Rectangle child = GdxSettingsLayout.optionRow(
                100f, 430f, 1_000f, true);
        assertTrue(child.x > parent.x);
        assertEquals(parent.x + parent.width, child.x + child.width);
        assertEquals(GdxSettingsLayout.CHILD_ROW_INDENT,
                child.x - parent.x);
        assertEquals(parent.y - GdxSettingsLayout.ROW_STRIDE, child.y);
    }

    @Test
    void settingsScrollbarMapsMouseDragFromTopToFirstRows() {
        float height = 420f;
        float thumb = GdxSettingsLayout.scrollbarThumbHeight(height, 12, 6);

        assertEquals(0, GdxSettingsLayout.firstRowFromScrollbar(
                100f + height - thumb / 2f, 100f, height, thumb, 6));
        assertEquals(3, GdxSettingsLayout.firstRowFromScrollbar(
                100f + height / 2f, 100f, height, thumb, 6));
        assertEquals(6, GdxSettingsLayout.firstRowFromScrollbar(
                100f + thumb / 2f, 100f, height, thumb, 6));
        assertEquals(14f, GdxSettingsLayout.SCROLLBAR_WIDTH);
        assertTrue(GdxSettingsLayout.SCROLLBAR_HIT_WIDTH
                > GdxSettingsLayout.SCROLLBAR_WIDTH);
    }

    @Test
    void settingsListsScrollContinuouslyAndUseAllAvailableHeight() {
        GdxSettingsLayout.PixelRows initial = GdxSettingsLayout.pixelRows(
                600f, 182f, 668f, 20, 0f);

        assertEquals(0f, initial.offset());
        assertEquals(0, initial.firstIndex());
        assertEquals(7, initial.lastExclusive(),
                "shortcuts must fill the viewport instead of stopping at "
                + "the old five-row page boundary");
        assertEquals(912f, initial.maximum());

        float afterWheel = GdxSettingsLayout.pixelScrollAfterWheel(
                initial.offset(), initial.maximum(), 1f);
        GdxSettingsLayout.PixelRows scrolled = GdxSettingsLayout.pixelRows(
                600f, 182f, 668f, 20, afterWheel);
        assertEquals(48f, scrolled.offset());
        assertEquals(648f, scrolled.rowY(0),
                "a wheel tick must move rows by pixels, not one full row");
    }

    @Test
    void pixelScrollbarDragCoversTheWholeContinuousRange() {
        float trackY = 100f;
        float trackHeight = 486f;
        float contentHeight = 1_398f;
        float maximum = contentHeight - trackHeight;
        float thumb = GdxSettingsLayout.pixelScrollbarThumbHeight(
                trackHeight, trackHeight, contentHeight);

        assertEquals(0f, GdxSettingsLayout.pixelScrollFromScrollbar(
                trackY + trackHeight - thumb / 2f, trackY, trackHeight,
                thumb, maximum), 0.001f);
        assertEquals(maximum, GdxSettingsLayout.pixelScrollFromScrollbar(
                trackY + thumb / 2f, trackY, trackHeight, thumb, maximum),
                0.001f);
        assertTrue(thumb > 42f && thumb < trackHeight);
    }
}
