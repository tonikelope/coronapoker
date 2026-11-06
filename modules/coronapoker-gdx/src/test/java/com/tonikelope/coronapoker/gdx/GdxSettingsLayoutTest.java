package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Rectangle;
import org.junit.jupiter.api.Test;

final class GdxSettingsLayoutTest {

    @Test
    void popupListStaysOffItsAnchorAndUsesAvailableSide() {
        assertEquals(6, GdxSettingsLayout.POPUP_MAX_VISIBLE_ROWS);
        assertEquals(56f, GdxSettingsLayout.POPUP_ROW_HEIGHT);
        assertEquals(36f, GdxSettingsLayout.POPUP_TEXT_BASELINE);
        Rectangle highAnchor = new Rectangle(100f, 600f, 300f, 68f);
        assertEquals(596f, GdxSettingsLayout.popupListTop(
                highAnchor, 280f, 100f, 900f));

        Rectangle lowAnchor = new Rectangle(100f, 180f, 300f, 68f);
        assertEquals(532f, GdxSettingsLayout.popupListTop(
                lowAnchor, 280f, 100f, 900f));
    }

    @Test
    void popupListRemainsBoundedWhenNeitherSideFits() {
        Rectangle anchor = new Rectangle(100f, 260f, 300f, 68f);
        float top = GdxSettingsLayout.popupListTop(
                anchor, 300f, 100f, 600f);
        assertEquals(600f, top);
        assertTrue(top - 300f >= 100f);
    }

    @Test
    void debugPerformanceRowOwnsASeparateBandAboveTheConsole() {
        Rectangle content = new Rectangle(100f, 80f, 1400f, 720f);
        Rectangle row = GdxSettingsLayout.debugPerformanceRow(content);
        GdxSettingsLayout.DebugArea debug =
                GdxSettingsLayout.debugArea(content);
        Rectangle copy = GdxSettingsLayout.debugCopyButton(content);
        Rectangle clip = GdxSettingsLayout.contentClip(content);

        assertTrue(row.y >= debug.console().y + debug.console().height);
        assertTrue(debug.console().y >= copy.y + copy.height);
        assertTrue(content.contains(row));
        assertTrue(clip.contains(row),
                "the complete rounded outline must stay inside the scissor");
        assertTrue(clip.y + clip.height - (row.y + row.height) >= 4f,
                "the top outline needs visible antialiasing clearance");
        assertTrue(content.contains(debug.console()));
    }

    @Test
    void sharedSettingsFrameIsCenteredAndStaysInsideTheViewport() {
        Rectangle fullHd = GdxSettingsLayout.panelBounds(1920f, 1080f);
        assertEquals(160f, fullHd.x);
        assertEquals(55f, fullHd.y);
        assertEquals(1600f, fullHd.width);
        assertEquals(970f, fullHd.height);

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
                <= menu.panel().y + menu.panel().height - 114f,
                "the title must have a full line of clearance above tabs");
        float titleBaseline = menu.panel().y + menu.panel().height
                - GdxSettingsLayout.TITLE_BASELINE_TOP_INSET;
        assertTrue(titleBaseline
                - (firstMainTab.y + firstMainTab.height) >= 68f,
                "the title needs deliberate breathing room above tabs");
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
    void sharedFrameRequiresPrimaryTabsButAllowsAParentOnlyPage() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> GdxSettingsLayout.frame(1920f, 1080f, 0, 1));
        GdxSettingsLayout.Frame withSubpage = GdxSettingsLayout.frame(
                1920f, 1080f, 1, 1);
        GdxSettingsLayout.Frame parentOnly = GdxSettingsLayout.frame(
                1920f, 1080f, 1, 0);
        assertEquals(withSubpage.content().height
                        + GdxSettingsLayout.NO_SUBPAGE_CONTENT_GAIN,
                parentOnly.content().height);
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
    void contentClipAndScrollbarUseOneContractAtEveryEntryPoint() {
        Rectangle content = GdxSettingsLayout.frame(
                1920f, 1080f, 5, 6).content();
        Rectangle clip = GdxSettingsLayout.contentClip(content);

        assertEquals(content.x, clip.x);
        assertEquals(content.y + GdxSettingsLayout.CONTENT_VERTICAL_INSET,
                clip.y);
        assertEquals(content.width, clip.width);
        assertEquals(content.height
                - GdxSettingsLayout.CONTENT_VERTICAL_INSET - 24f,
                clip.height);
        assertEquals(content.x + content.width - 24f,
                GdxSettingsLayout.rowScrollbarX(content));
        assertEquals(GdxSettingsLayout.rowScrollbarX(content),
                GdxSettingsLayout.rowScrollbarX(content.x, content.width));
    }

    @Test
    void voiceActionsUseEqualCanonicalButtons() {
        GdxSettingsLayout.SplitRow actions =
                GdxSettingsLayout.splitActionRow(100f, 200f, 1120f);

        assertEquals(actions.left().width, actions.right().width);
        assertEquals(GdxSettingsLayout.ACTION_ROW_HEIGHT,
                actions.left().height);
        assertEquals(GdxSettingsLayout.ACTION_ROW_GAP,
                actions.right().x
                - (actions.left().x + actions.left().width));
    }

    @Test
    void shortcutsSharePaintingTextAndPointerGeometry() {
        GdxSettingsLayout.ShortcutRow row =
                GdxSettingsLayout.shortcutRow(100f, 200f, 1120f);

        assertEquals(GdxSettingsLayout.SHORTCUT_ROW_HEIGHT,
                row.bounds().height);
        assertTrue(row.bounds().contains(row.key()));
        assertTrue(row.bounds().contains(row.description()));
        assertTrue(row.description().x + row.description().width
                < row.key().x);
        assertEquals(row.bounds().x + row.bounds().width - 12f,
                row.key().x + row.key().width);
        assertEquals(238f, GdxSettingsLayout.shortcutKeyBaseline(row));
        assertEquals(238f,
                GdxSettingsLayout.shortcutDescriptionBaseline(row));
    }

    @Test
    void canonicalTextBaselinesCannotDriftBetweenSettingsRenderers() {
        assertEquals(243f, GdxSettingsLayout.rowTextBaseline(200f));
        assertEquals(244f, GdxSettingsLayout.stepperLabelBaseline(200f));
        assertEquals(245f, GdxSettingsLayout.stepperValueBaseline(200f));
        assertEquals(248f, GdxSettingsLayout.stepperSignBaseline(200f));
        assertEquals(244f, GdxSettingsLayout.infoLabelBaseline(200f));
        assertEquals(245f, GdxSettingsLayout.infoValueBaseline(200f));
        assertEquals(241f, GdxSettingsLayout.volumeTextBaseline(
                GdxSettingsLayout.volumeRow(100f, 200f, 1_120f)));
    }

    @Test
    void informationValuesAreAnchoredToTheCanonicalRightEdge() {
        assertEquals(1_200f,
                GdxSettingsLayout.infoValueRight(100f, 1_120f));
        assertEquals(20f, GdxSettingsLayout.INFO_VALUE_RIGHT_INSET);
    }

    @Test
    void ordinaryRowLabelUsesTheSameSafeLaneAsFrontendToggles() {
        Rectangle label = GdxSettingsLayout.rowLabel(100f, 200f, 1120f);

        assertEquals(122f, label.x);
        assertEquals(205f, label.y);
        assertEquals(988f, label.width);
        assertEquals(58f, label.height);
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
    void audioDeviceStepperWidensNamesWithoutOverlappingButtons() {
        GdxSettingsLayout.StepperRow standard =
                GdxSettingsLayout.stepperRow(100f, 200f, 1_500f,
                        GdxSettingsLayout.ROW_HEIGHT);
        GdxSettingsLayout.StepperRow device =
                GdxSettingsLayout.audioDeviceStepperRow(100f, 200f, 1_500f,
                        GdxSettingsLayout.ROW_HEIGHT);

        assertEquals(GdxSettingsLayout.AUDIO_DEVICE_VALUE_WIDTH,
                device.value().width);
        assertTrue(device.value().width > standard.value().width);
        assertTrue(device.label().x + device.label().width
                <= device.controls().x);
        assertTrue(device.minusButton().x + device.minusButton().width
                <= device.value().x);
        assertTrue(device.value().x + device.value().width
                <= device.plusButton().x);
        assertTrue(device.plusButton().x + device.plusButton().width
                <= device.bounds().x + device.bounds().width);
    }

    @Test
    void audioDeviceChoiceReclaimsStepperSpaceForLongNamesAndPopup() {
        GdxSettingsLayout.ChoiceRow standard =
                GdxSettingsLayout.choiceRow(100f, 200f, 1_500f,
                        GdxSettingsLayout.ROW_HEIGHT);
        GdxSettingsLayout.ChoiceRow device =
                GdxSettingsLayout.audioDeviceChoiceRow(100f, 200f, 1_500f,
                        GdxSettingsLayout.ROW_HEIGHT);

        assertTrue(device.value().width > standard.value().width * 2f);
        assertTrue(device.label().x + device.label().width
                <= device.value().x);
        assertTrue(device.value().x + device.value().width
                <= device.arrow().x);
        assertTrue(device.arrow().x + device.arrow().width
                <= device.bounds().x + device.bounds().width);
    }

    @Test
    void toggleStateAlignsWithStepperValuesWithoutTouchingTheSwitch() {
        GdxSettingsLayout.StepperRow stepper = GdxSettingsLayout.stepperRow(
                100f, 200f, 1_120f, GdxSettingsLayout.ROW_HEIGHT);
        GdxSettingsLayout.ToggleRow toggle = GdxSettingsLayout.toggleRow(
                100f, 200f, 1_120f, GdxSettingsLayout.ROW_HEIGHT);

        assertEquals(stepper.value().x + stepper.value().width / 2f,
                toggle.value().x + toggle.value().width / 2f);
        assertEquals(GdxSettingsLayout.TOGGLE_STATUS_WIDTH,
                toggle.value().width,
                "toggle states need one canonical full-size text lane");
        assertTrue(toggle.label().x + toggle.label().width
                < toggle.value().x);
        assertTrue(toggle.value().x + toggle.value().width
                < toggle.toggle().x);
    }

    @Test
    void audioToggleStatusReservesThePreviewButtonLane() {
        float x = 100f;
        float width = 1_120f;
        GdxSettingsLayout.ToggleRow toggle = GdxSettingsLayout.toggleRow(
                x, 200f, width, GdxSettingsLayout.ROW_HEIGHT, 94f);
        float previewX = x + width - 190f;

        assertTrue(toggle.value().x + toggle.value().width < previewX,
                "the status text must not sit below the audio preview button");
        assertTrue(previewX + 46f < toggle.toggle().x,
                "the audio preview and switch need separate hit lanes");
        assertEquals(GdxSettingsLayout.TOGGLE_STATUS_WIDTH,
                toggle.value().width,
                "preview rows must not shrink disabled state labels");
    }

    @Test
    void primaryTabIconAndLabelOwnSeparateSafeLanes() {
        Rectangle tab = GdxSettingsLayout.frame(
                1920f, 1080f, 4, 6).mainTab(0);
        Rectangle icon = GdxSettingsLayout.mainTabIcon(tab);
        Rectangle label = GdxSettingsLayout.mainTabLabel(tab);

        assertTrue(tab.contains(icon));
        assertTrue(label.x >= tab.x);
        assertTrue(label.y >= tab.y);
        assertTrue(label.x + label.width <= tab.x + tab.width);
        assertTrue(label.y + label.height <= tab.y + tab.height);
        assertTrue(icon.x + icon.width < label.x);
    }

    @Test
    void canonicalFirstRowKeepsItsOutlineBelowTheSettingsClipTop() {
        GdxSettingsLayout.Frame frame = GdxSettingsLayout.frame(
                1920f, 1080f, 4, 5);
        Rectangle content = frame.content();
        float clipTop = content.y + content.height - 24f;

        assertEquals(4f, clipTop
                - (frame.firstRowY() + GdxSettingsLayout.ROW_HEIGHT),
                "every first-row outline needs the same visible clearance");
    }

    @Test
    void gamePagesUseOneCanonicalTwoColumnGridWithoutOverlap() {
        GdxSettingsLayout.Frame frame = GdxSettingsLayout.frame(
                1920f, 1080f, 5, 6);
        Rectangle content = frame.content();
        float rowX = content.x + GdxSettingsLayout.CONTENT_HORIZONTAL_INSET;
        float rowWidth = content.width
                - 2f * GdxSettingsLayout.CONTENT_HORIZONTAL_INSET;
        GdxSettingsLayout.GameColumns columns =
                GdxSettingsLayout.gameColumns(rowX, rowWidth);

        Rectangle leftFirst = columns.left(frame.firstRowY(), 0);
        Rectangle rightFirst = columns.right(frame.firstRowY(), 0);
        Rectangle leftLast = columns.left(frame.firstRowY(), 4);
        Rectangle rightLast = columns.right(frame.firstRowY(), 4);

        assertEquals(GdxSettingsLayout.ROW_HEIGHT, leftFirst.height);
        assertEquals(GdxSettingsLayout.ROW_HEIGHT, rightFirst.height);
        assertEquals(GdxSettingsLayout.GAME_COLUMN_GAP,
                rightFirst.x - (leftFirst.x + leftFirst.width));
        assertEquals(leftFirst.y - 4f * GdxSettingsLayout.ROW_STRIDE,
                leftLast.y);
        assertEquals(rightFirst.y - 4f * GdxSettingsLayout.ROW_STRIDE,
                rightLast.y);
        assertTrue(leftLast.y >= content.y);
        assertTrue(rightLast.y >= content.y);
        assertTrue(rightFirst.x + rightFirst.width
                <= content.x + content.width
                - GdxSettingsLayout.CONTENT_HORIZONTAL_INSET);
    }

    @Test
    void compactGamePagesDoNotAddRedundantFrames() {
        assertTrue(GdxSettingsContract.visualGroups(java.util.List.of(
                new GdxSettingsContract.RowGroup(0, 4, 0)), 5).isEmpty());
    }

    @Test
    void newGameStyleSplitRowsKeepCompactSteppersInsideEachHalf() {
        Rectangle column = new Rectangle(100f, 400f, 740f,
                GdxSettingsLayout.ROW_HEIGHT);
        GdxSettingsLayout.SplitRow split =
                GdxSettingsLayout.splitGameRow(column);
        GdxSettingsLayout.StepperRow left = GdxSettingsLayout.stepperRow(
                split.left().x, split.left().y, split.left().width,
                split.left().height);
        GdxSettingsLayout.StepperRow right = GdxSettingsLayout.stepperRow(
                split.right().x, split.right().y, split.right().width,
                split.right().height);

        assertEquals(GdxSettingsLayout.GAME_INNER_GAP,
                split.right().x - (split.left().x + split.left().width));
        assertTrue(left.label().x + left.label().width <= left.controls().x);
        assertTrue(left.plusButton().x + left.plusButton().width
                <= split.left().x + split.left().width);
        assertTrue(right.label().x + right.label().width
                <= right.controls().x);
        assertTrue(right.plusButton().x + right.plusButton().width
                <= split.right().x + split.right().width);
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
    void debugConsoleUsesTheSameResponsiveGeometryEverywhere() {
        Rectangle content = GdxSettingsLayout.frame(
                1920f, 1080f, 5, 1).content();
        GdxSettingsLayout.DebugArea debug =
                GdxSettingsLayout.debugArea(content);

        assertEquals(content.x + 24f, debug.console().x);
        assertEquals(content.y + 82f, debug.console().y);
        assertEquals(content.width - 48f, debug.console().width);
        assertEquals(content.height - GdxSettingsLayout.ROW_HEIGHT - 132f,
                debug.console().height);
        assertEquals(debug.console().x + 14f, debug.viewport().x);
        assertEquals(debug.console().y + 14f, debug.viewport().y);
        assertEquals(debug.console().width - 44f,
                debug.viewport().width);
        assertEquals(debug.console().height - 28f,
                debug.viewport().height);
        assertEquals(debug.console().x + debug.console().width - 24f,
                debug.scrollbarX());
        assertEquals(debug.console().width - 58f,
                GdxSettingsLayout.debugTextWidth(debug.console()));
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
    void appearanceTableAndGraphicsPagesKeepCanonicalRowSpacing() {
        float contentHeight = GdxSettingsLayout.frame(
                1920f, 1080f, 4,
                GdxSettingsContract.APPEARANCE_PAGES.size() + 2)
                .content().height;
        float firstRowY = contentHeight
                - GdxSettingsLayout.CONTENT_ROW_TOP_INSET;
        float stride = GdxSettingsLayout.rowStride(contentHeight,
                GdxSettingsContract.APPEARANCE_PRIMARY_ROW_COUNT);
        float lastRowY = firstRowY - stride
                * (GdxSettingsContract.APPEARANCE_PRIMARY_ROW_COUNT - 1);

        assertEquals(GdxSettingsLayout.ROW_STRIDE, stride,
                "table rows must retain the canonical gap");
        assertTrue(lastRowY >= GdxSettingsLayout.ROW_HEIGHT,
                "the last table row must remain wholly inside content");

        float graphicsStride = GdxSettingsLayout.rowStride(contentHeight,
                GdxSettingsContract.APPEARANCE_GRAPHICS_ROW_COUNT);
        float graphicsLastY = firstRowY - graphicsStride
                * (GdxSettingsContract.APPEARANCE_GRAPHICS_ROW_COUNT - 1);
        assertEquals(GdxSettingsLayout.ROW_STRIDE, graphicsStride,
                "graphics rows must retain the canonical gap");
        assertTrue(graphicsLastY >= 8f,
                "renderer information must remain inside content");
    }

    @Test
    void legacyDependentRowsUseTheCanonicalInnerMargin() {
        Rectangle parent = GdxSettingsLayout.optionRow(
                100f, 500f, 1_000f, false);
        Rectangle child = GdxSettingsLayout.optionRow(
                100f, 430f, 1_000f, true);
        assertEquals(GdxSettingsLayout.CONTENT_HORIZONTAL_INSET,
                child.x - parent.x);
        assertEquals(GdxSettingsLayout.CONTENT_HORIZONTAL_INSET,
                parent.x + parent.width - child.x - child.width);
        assertEquals(parent.y - GdxSettingsLayout.ROW_STRIDE, child.y);
    }

    @Test
    void choiceRowsReserveAnIndependentRightAlignedValueLane() {
        GdxSettingsLayout.ChoiceRow row = GdxSettingsLayout.choiceRow(
                100f, 430f, 1_000f, GdxSettingsLayout.ROW_HEIGHT);
        GdxSettingsLayout.StepperRow stepper = GdxSettingsLayout.stepperRow(
                100f, 430f, 1_000f, GdxSettingsLayout.ROW_HEIGHT);

        assertTrue(row.value().x > row.label().x + row.label().width);
        assertTrue(row.arrow().x > row.value().x + row.value().width);
        assertEquals(stepper.plusButton(), row.arrow(),
                "the choice arrow must use the canonical embedded button");
        assertEquals(stepper.value().x + stepper.value().width / 2f,
                row.value().x + row.value().width / 2f,
                "choice summaries must share the canonical value axis");
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
    void everyScrollbarGetsTheSameMinimumMouseHitThickness() {
        Rectangle vertical = new Rectangle(100f, 200f, 7f, 300f);
        assertTrue(GdxSettingsLayout.verticalScrollbarContains(
                vertical, 89.5f, 200f));
        assertTrue(GdxSettingsLayout.verticalScrollbarContains(
                vertical, 117.5f, 500f));
        assertFalse(GdxSettingsLayout.verticalScrollbarContains(
                vertical, 89.4f, 350f));
        assertFalse(GdxSettingsLayout.verticalScrollbarContains(
                vertical, 103f, 500.1f));

        Rectangle horizontal = new Rectangle(40f, 80f, 320f, 8f);
        assertTrue(GdxSettingsLayout.horizontalScrollbarContains(
                horizontal, 40f, 70f));
        assertTrue(GdxSettingsLayout.horizontalScrollbarContains(
                horizontal, 360f, 98f));
        assertFalse(GdxSettingsLayout.horizontalScrollbarContains(
                horizontal, 39.9f, 84f));
        assertFalse(GdxSettingsLayout.horizontalScrollbarContains(
                horizontal, 200f, 98.1f));
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
    void contextualStatusBandStaysInsideTheClipAndAboveTheRows() {
        float firstRowY = 600f;
        float viewportTop = firstRowY + GdxSettingsLayout.ROW_HEIGHT;

        GdxSettingsLayout.StatusBand visible =
                GdxSettingsLayout.statusBand(firstRowY, viewportTop, true);

        assertEquals(firstRowY - GdxSettingsLayout.STATUS_BAND_HEIGHT,
                visible.firstRowY());
        assertEquals(viewportTop - GdxSettingsLayout.STATUS_BAND_HEIGHT,
                visible.viewportTop());
        assertTrue(visible.textCenterY() > visible.viewportTop());
        assertTrue(visible.textCenterY() < viewportTop,
                "the status text must remain inside the content scissor");
        assertTrue(visible.textCenterY() - visible.viewportTop() >= 40f,
                "the notice needs clear air before the first setting row");
        assertTrue(visible.scrolledTextCenterY(80f) > viewportTop,
                "the notice must scroll out instead of floating over rows");

        GdxSettingsLayout.StatusBand hidden =
                GdxSettingsLayout.statusBand(firstRowY, viewportTop, false);
        assertEquals(firstRowY, hidden.firstRowY());
        assertEquals(viewportTop, hidden.viewportTop());
    }

    @Test
    void parentGroupsUseTheSameOuterAndInnerMarginOnEverySide() {
        GdxSettingsLayout.PixelRows rows = GdxSettingsLayout.pixelRows(
                600f, 182f, 668f, 6, 0f, java.util.List.of(
                        new GdxSettingsContract.RowGroup(0, 3, 0)));

        assertEquals(GdxSettingsLayout.GROUP_GAP,
                rows.rowY(3) - GdxSettingsLayout.GROUP_FRAME_PADDING
                - (rows.rowY(4) + GdxSettingsLayout.ROW_HEIGHT));
        Rectangle group = GdxSettingsLayout.groupBounds(100f, 1_120f,
                rows, new GdxSettingsContract.RowGroup(0, 3, 0));
        assertEquals(100f, group.x);
        assertEquals(1_120f, group.width);
        assertEquals(rows.rowY(3) - GdxSettingsLayout.GROUP_FRAME_PADDING,
                group.y);
        assertEquals(rows.rowY(0) + GdxSettingsLayout.ROW_HEIGHT
                + GdxSettingsLayout.GROUP_FRAME_PADDING,
                group.y + group.height);
        Rectangle first = GdxSettingsLayout.groupedRow(100f, rows.rowY(0),
                1_120f, 0, java.util.List.of(
                        new GdxSettingsContract.RowGroup(0, 3, 0)), 6);
        Rectangle last = GdxSettingsLayout.groupedRow(100f, rows.rowY(3),
                1_120f, 3, java.util.List.of(
                        new GdxSettingsContract.RowGroup(0, 3, 0)), 6);
        assertEquals(GdxSettingsLayout.GROUP_FRAME_PADDING,
                first.x - group.x);
        assertEquals(GdxSettingsLayout.GROUP_FRAME_PADDING,
                group.x + group.width - first.x - first.width);
        assertEquals(GdxSettingsLayout.GROUP_FRAME_PADDING,
                group.y + group.height - first.y - first.height);
        assertEquals(GdxSettingsLayout.GROUP_FRAME_PADDING,
                last.y - group.y);
    }

    @Test
    void graphicsFullscreenGroupAlignsWithStandaloneGraphicsRows() {
        java.util.List<GdxSettingsContract.RowGroup> groups =
                GdxSettingsContract.appearanceGraphicsGroups();
        GdxSettingsLayout.PixelRows rows = GdxSettingsLayout.pixelRows(
                600f, 182f, 668f,
                GdxSettingsContract.APPEARANCE_GRAPHICS_ROW_COUNT, 0f,
                groups);
        Rectangle frame = GdxSettingsLayout.groupBounds(100f, 1_120f,
                rows, groups.get(0));
        Rectangle parent = GdxSettingsLayout.groupedRow(100f, rows.rowY(0),
                1_120f, 0, groups,
                GdxSettingsContract.APPEARANCE_GRAPHICS_ROW_COUNT);
        Rectangle child = GdxSettingsLayout.groupedRow(100f, rows.rowY(1),
                1_120f, 1, groups,
                GdxSettingsContract.APPEARANCE_GRAPHICS_ROW_COUNT);
        Rectangle standalone = GdxSettingsLayout.groupedRow(100f,
                rows.rowY(2), 1_120f, 2, groups,
                GdxSettingsContract.APPEARANCE_GRAPHICS_ROW_COUNT);

        assertEquals(GdxSettingsLayout.GROUP_FRAME_PADDING,
                parent.x - frame.x);
        assertEquals(parent.x, child.x,
                "the fullscreen child uses the canonical unindented row");
        assertEquals(parent.width, child.width);
        assertEquals(100f, standalone.x,
                "standalone graphics rows align with the group frame");
        assertEquals(1_120f, standalone.width);
        assertEquals(GdxSettingsLayout.GROUP_GAP,
                rows.rowY(1) - GdxSettingsLayout.GROUP_FRAME_PADDING
                - (rows.rowY(2) + GdxSettingsLayout.ROW_HEIGHT));
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
