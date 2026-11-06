/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Rectangle;
import java.util.List;
import java.util.Set;

/** Shared modal frame geometry used by menu and live-table settings. */
final class GdxSettingsLayout {

    /** Canonical lane used by stepper values. */
    private static final float STANDARD_VALUE_WIDTH = 286f;
    /** Wider value lane reserved for potentially long operating-system names. */
    static final float AUDIO_DEVICE_VALUE_WIDTH = 760f;
    /**
     * Canonical lane used by every toggle state label. It is wide enough for
     * "DESACTIVADO *" at the normal body size while preserving the shared
     * stepper centre and a visible gap before the switch.
     */
    static final float TOGGLE_STATUS_WIDTH = 252f;
    /** Geometric clearance; the centred text has additional optical space. */
    private static final float TOGGLE_STATUS_GAP = 4f;
    /** Audio rows also reserve a distinct preview-button lane. */
    private static final float TOGGLE_PREVIEW_GAP = 20f;

    static final float HORIZONTAL_INSET = 44f;
    /** Breathing room between the content well and every settings row. */
    static final float CONTENT_HORIZONTAL_INSET = 34f;
    /** Matching lower viewport inset for the same four-sided rhythm. */
    static final float CONTENT_VERTICAL_INSET = CONTENT_HORIZONTAL_INSET;
    static final float TITLE_BASELINE_TOP_INSET = 48f;
    // There is one heading band only.  Settings used to reserve another band
    // for the redundant "Configuracion de CoronaPoker" subtitle, leaving a
    // conspicuous hole after that subtitle was removed.
    static final float MAIN_TAB_TOP_INSET = 164f;
    static final float SUB_TAB_TOP_INSET = 236f;
    static final int MAX_SUB_TABS_PER_ROW = 7;
    static final float SUB_TAB_ROW_GAP = 42f;
    // Subsection names already live in the tab row. Start the controls directly
    // below it instead of repeating the active tab as a yellow heading.
    /**
     * Leaves the first row four pixels below the content scissor so its
     * rounded outline is never cut at the upper edge. Every settings page
     * uses this same origin, regardless of whether its first control is a
     * toggle, stepper or information row.
     */
    static final float CONTENT_ROW_TOP_INSET = 96f;
    static final float CONTENT_BOTTOM_INSET = 112f;
    static final float CONTENT_TOTAL_VERTICAL_INSET = 364f;
    /** Reclaims the secondary-tab band for primary pages without subpages. */
    static final float NO_SUBPAGE_CONTENT_GAIN = 56f;
    static final float FOOTER_BOTTOM_INSET = 24f;
    static final float FOOTER_BUTTON_HEIGHT = 58f;
    /** Common row geometry for menu, waiting-room and live-table settings. */
    static final float ROW_HEIGHT = 68f;
    static final float ROW_STRIDE = 70f;
    /** Dedicated scrollable header band for contextual notices above a list. */
    static final float STATUS_BAND_HEIGHT = 64f;
    /** Keeps the notice high in its band and leaves air before the first row. */
    static final float STATUS_BAND_TOP_INSET = 18f;
    /** Extra breathing room between adjacent parent/child settings blocks. */
    static final float GROUP_GAP = 12f;
    /** One canonical margin outside and inside every useful group frame. */
    static final float GROUP_FRAME_PADDING = 28f;
    static final float GROUP_DEPTH_INSET = 28f;
    static final float SHORTCUT_ROW_HEIGHT = 62f;
    static final float ACTION_ROW_HEIGHT = 62f;
    static final float ACTION_ROW_GAP = 14f;
    static final float GAME_COLUMN_GAP = 24f;
    static final float GAME_INNER_GAP = 12f;
    /** Same visible width as the in-game log scrollbar. */
    static final float SCROLLBAR_WIDTH = 14f;
    /** Generous mouse target around the visible bar. */
    static final float SCROLLBAR_HIT_WIDTH = 28f;
    /** All categorical popup lists scroll after this many visible choices. */
    static final int POPUP_MAX_VISIBLE_ROWS = 6;
    static final float POPUP_ROW_HEIGHT = 56f;
    static final float POPUP_TEXT_BASELINE = 36f;
    /** Same measured wheel travel used by the lobby and quick-table chats. */
    static final float PIXEL_SCROLL_WHEEL_STEP = 48f;

    static boolean verticalScrollbarContains(Rectangle track, float x,
            float y) {
        float hitWidth = Math.max(track.width, SCROLLBAR_HIT_WIDTH);
        float hitX = track.x - (hitWidth - track.width) / 2f;
        return x >= hitX && x <= hitX + hitWidth
                && y >= track.y && y <= track.y + track.height;
    }

    static boolean horizontalScrollbarContains(Rectangle track, float x,
            float y) {
        float hitHeight = Math.max(track.height, SCROLLBAR_HIT_WIDTH);
        float hitY = track.y - (hitHeight - track.height) / 2f;
        return x >= track.x && x <= track.x + track.width
                && y >= hitY && y <= hitY + hitHeight;
    }
    /** Matches the live game-log row rhythm and monospace baseline. */
    static final float DEBUG_LINE_HEIGHT = 31f;
    static final float DEBUG_TEXT_BASELINE = 23f;
    static final float DEBUG_TEXT_LEFT_INSET = 24f;
    static final float DEBUG_TEXT_RIGHT_INSET = 34f;

    // Canonical baselines for every renderer. Keeping them beside the shared
    // rectangles prevents a visually identical control from drifting by one
    // or two pixels when one settings entry point is adjusted in isolation.
    static final float ROW_TEXT_BASELINE = 43f;
    static final float STEPPER_LABEL_BASELINE = 44f;
    static final float STEPPER_VALUE_BASELINE = 45f;
    static final float STEPPER_SIGN_BASELINE = 48f;
    static final float INFO_LABEL_BASELINE = 44f;
    static final float INFO_VALUE_BASELINE = 45f;
    static final float INFO_VALUE_RIGHT_INSET = 20f;
    static final float VOLUME_TEXT_BASELINE = 41f;
    static final float SHORTCUT_KEY_BASELINE = 38f;
    static final float SHORTCUT_DESCRIPTION_BASELINE = 38f;

    private GdxSettingsLayout() {
    }

    /**
     * Reserves a visible strip above a scrolling settings list without
     * placing its notice outside the content scissor. The row origin and its
     * viewport move together, so rendering, scrolling and hit testing share
     * one coordinate contract. The notice itself scrolls with the rows and
     * therefore cannot remain floating over later entries.
     */
    static StatusBand statusBand(float firstRowY, float viewportTop,
            boolean visible) {
        if (!visible) {
            return new StatusBand(firstRowY, viewportTop, viewportTop);
        }
        return new StatusBand(firstRowY - STATUS_BAND_HEIGHT,
                viewportTop - STATUS_BAND_HEIGHT,
                viewportTop - STATUS_BAND_TOP_INSET);
    }

    static Rectangle panelBounds(float worldWidth, float worldHeight) {
        float panelWidth = Math.min(1600f, worldWidth - 64f);
        // The graphics page now exposes independent scene and card AA rows.
        // At Full HD, 970 keeps all eight canonical 68 px controls and their
        // breathing room visible without overlap or a one-row scrollbar.
        float panelHeight = Math.min(970f, worldHeight - 54f);
        return new Rectangle((worldWidth - panelWidth) / 2f,
                (worldHeight - panelHeight) / 2f,
                panelWidth, panelHeight);
    }

    static float infoValueRight(float x, float width) {
        return x + width - INFO_VALUE_RIGHT_INSET;
    }

    /**
     * Complete frame shared by the startup/lobby and live-table settings
     * entry points.  Keeping these rectangles here prevents a visual fix in
     * one renderer from silently moving its tabs, content or footer away from
     * the other renderer.
     */
    static Frame frame(float worldWidth, float worldHeight, int sectionCount,
            int subpageCount) {
        if (sectionCount < 1 || subpageCount < 0) {
            throw new IllegalArgumentException(
                    "Settings require a primary tab and nonnegative subpages");
        }
        Rectangle panel = panelBounds(worldWidth, worldHeight);
        float innerWidth = panel.width - 2f * HORIZONTAL_INSET;
        int layoutSubpageCount = Math.max(1, subpageCount);
        int subTabRows = (layoutSubpageCount + MAX_SUB_TABS_PER_ROW - 1)
                / MAX_SUB_TABS_PER_ROW;
        int subTabColumns = (layoutSubpageCount + subTabRows - 1)
                / subTabRows;
        float extraSubTabHeight = (subTabRows - 1) * SUB_TAB_ROW_GAP;
        float noSubpageGain = subpageCount == 0
                ? NO_SUBPAGE_CONTENT_GAIN : 0f;
        Rectangle content = new Rectangle(
                panel.x + HORIZONTAL_INSET,
                panel.y + CONTENT_BOTTOM_INSET,
                innerWidth,
                panel.height - CONTENT_TOTAL_VERTICAL_INSET
                - extraSubTabHeight + noSubpageGain);
        return new Frame(panel, content,
                panel.y + panel.height - MAIN_TAB_TOP_INSET,
                innerWidth / sectionCount,
                panel.y + panel.height - SUB_TAB_TOP_INSET,
                innerWidth / subTabColumns,
                subTabColumns,
                content.y + content.height - CONTENT_ROW_TOP_INSET);
    }

    /**
     * Shared geometry for the master-volume row. Text, slider and buttons own
     * disjoint rectangles so font metrics or fractional DPI scaling cannot
     * make the percentage cross the slider.
     */
    static VolumeRow volumeRow(float x, float y, float width) {
        Rectangle bounds = new Rectangle(x, y, width, 66f);
        Rectangle minus = new Rectangle(x + width - 158f, y + 10f,
                62f, 46f);
        Rectangle plus = new Rectangle(x + width - 84f, y + 10f,
                62f, 46f);
        Rectangle label = new Rectangle(x + 18f, y + 10f, 210f, 46f);
        Rectangle percentage = new Rectangle(x + 230f, y + 10f,
                76f, 46f);
        float barX = x + 326f;
        Rectangle slider = new Rectangle(barX, y + 27f,
                Math.max(100f, minus.x - 20f - barX), 12f);
        return new VolumeRow(bounds, label, percentage, slider, minus, plus);
    }

    /**
     * Vertical distance between equally-sized settings rows.  Five-row pages
     * retain the normal breathing room, while denser pages close the gaps just
     * enough to keep their final row above the content footer.
     */
    static float rowStride(float contentHeight, int rowCount) {
        if (rowCount <= 1) return 0f;
        float firstRowBottomInset = CONTENT_ROW_TOP_INSET;
        float lastRowBottomInset = 8f;
        float available = contentHeight - firstRowBottomInset
                - lastRowBottomInset;
        return Math.min(ROW_STRIDE, Math.max(62f,
                available / (rowCount - 1)));
    }

    /**
     * Applies the ordinary row geometry, or the canonical inner margin used
     * by the few legacy parent/child layouts that do not draw a group frame.
     */
    static Rectangle optionRow(float x, float y, float width,
            boolean child) {
        float inset = child ? CONTENT_HORIZONTAL_INSET : 0f;
        return new Rectangle(x + inset, y,
                Math.max(0f, width - 2f * inset), ROW_HEIGHT);
    }

    /**
     * Row lane inside a visible parent/child group. A depth-zero frame is
     * aligned with ordinary rows; its controls receive exactly the same
     * margin that separates the frame from the subpage well.
     */
    static Rectangle groupedRow(float x, float y, float width, int row,
            List<GdxSettingsContract.RowGroup> groups, int totalRows) {
        int levels = 0;
        for (GdxSettingsContract.RowGroup group
                : GdxSettingsContract.visualGroups(groups, totalRows)) {
            if (row >= group.parentRow() && row <= group.lastRow()) {
                levels = Math.max(levels, group.depth() + 1);
            }
        }
        float inset = levels * GROUP_FRAME_PADDING;
        return new Rectangle(x + inset, y,
                Math.max(0f, width - 2f * inset), ROW_HEIGHT);
    }

    /** Right-aligned value lane shared by compact choice rows. */
    static ChoiceRow choiceRow(float x, float y, float width, float height) {
        Rectangle bounds = new Rectangle(x, y, width, height);
        StepperRow reference = stepperRow(x, y, width, height);
        Rectangle arrow = new Rectangle(reference.plusButton());
        float valueCenter = reference.value().x
                + reference.value().width / 2f;
        float availableHalfWidth = Math.max(0f,
                arrow.x - 12f - valueCenter);
        float requestedWidth = Math.min(360f,
                Math.max(190f, width * 0.28f));
        float valueWidth = Math.min(requestedWidth,
                availableHalfWidth * 2f);
        Rectangle value = new Rectangle(valueCenter - valueWidth / 2f,
                y + 5f, valueWidth, Math.max(0f, height - 10f));
        Rectangle label = new Rectangle(x + 22f, y + 5f,
                Math.max(0f, value.x - x - 44f),
                Math.max(0f, height - 10f));
        return new ChoiceRow(bounds, label, value, arrow);
    }

    /**
     * Wider choice geometry for operating-system audio device names.  Reuse
     * the dedicated audio lane, but reclaim the obsolete minus-button space
     * now that categorical settings open a list instead of cycling values.
     */
    static ChoiceRow audioDeviceChoiceRow(float x, float y, float width,
            float height) {
        Rectangle bounds = new Rectangle(x, y, width, height);
        StepperRow reference = audioDeviceStepperRow(x, y, width, height);
        Rectangle arrow = new Rectangle(reference.plusButton());
        float valueX = reference.minusButton().x;
        float valueRight = Math.max(valueX, arrow.x - 10f);
        Rectangle value = new Rectangle(valueX, y + 5f,
                valueRight - valueX, Math.max(0f, height - 10f));
        Rectangle label = new Rectangle(x + 22f, y + 5f,
                Math.max(0f, value.x - x - 44f),
                Math.max(0f, height - 10f));
        return new ChoiceRow(bounds, label, value, arrow);
    }

    /**
     * Places a popup list wholly below or above its anchor whenever either
     * side has enough room. The anchor itself is never used as the first list
     * row, which prevents duplicate labels and preserves the selected value as
     * the stable control that opened the popup.
     */
    static float popupListTop(Rectangle anchor, float listHeight,
            float lowerLimit, float upperLimit) {
        if (anchor == null || listHeight < 0f || upperLimit < lowerLimit) {
            throw new IllegalArgumentException("Invalid popup geometry");
        }
        float gap = 4f;
        float belowTop = anchor.y - gap;
        float belowSpace = belowTop - lowerLimit;
        if (belowSpace >= listHeight) return belowTop;

        float aboveBottom = anchor.y + anchor.height + gap;
        float aboveSpace = upperLimit - aboveBottom;
        if (aboveSpace >= listHeight) return aboveBottom + listHeight;

        // Very small viewports cannot fit the complete list on either side.
        // Keep it inside the available region and use the roomier side.
        return belowSpace >= aboveSpace
                ? lowerLimit + listHeight : upperLimit;
    }

    /**
     * Visible settings content shared by both renderers.  The four pixels
     * above the first row are intentional: they keep its rounded outline out
     * of the scissor edge at every viewport scale.
     */
    static Rectangle contentClip(Rectangle content) {
        return new Rectangle(content.x,
                content.y + CONTENT_VERTICAL_INSET, content.width,
                Math.max(0f, content.height
                        - CONTENT_VERTICAL_INSET - 24f));
    }

    static Rectangle contentClip(Rectangle content, Rectangle destination) {
        return destination.set(content.x,
                content.y + CONTENT_VERTICAL_INSET, content.width,
                Math.max(0f, content.height
                        - CONTENT_VERTICAL_INSET - 24f));
    }

    /** Canonical visible scrollbar position relative to the content well. */
    static float rowScrollbarX(Rectangle content) {
        return rowScrollbarX(content.x, content.width);
    }

    static float rowScrollbarX(float contentX, float contentWidth) {
        return contentX + contentWidth - 24f;
    }

    /** Text lane for ordinary one-line setting labels. */
    static Rectangle rowLabel(float x, float y, float width) {
        return new Rectangle(x + 22f, y + 5f,
                Math.max(0f, width - 132f), 58f);
    }

    /**
     * Shared geometry for every minus/value/plus settings row.  The startup,
     * waiting-room and live-table renderers used to carry slightly different
     * constants, so the painted controls and their hit targets could drift
     * apart as soon as one of them was polished independently.
     */
    static StepperRow stepperRow(float x, float y, float width,
            float height) {
        return stepperRow(x, y, width, height, STANDARD_VALUE_WIDTH);
    }

    /** Device names need more room than ordinary numeric/choice values. */
    static StepperRow audioDeviceStepperRow(float x, float y, float width,
            float height) {
        float valueWidth = Math.min(AUDIO_DEVICE_VALUE_WIDTH,
                Math.max(STANDARD_VALUE_WIDTH, width - 520f));
        return stepperRow(x, y, width, height, valueWidth);
    }

    private static StepperRow stepperRow(float x, float y, float width,
            float height, float requestedValueWidth) {
        Rectangle bounds = new Rectangle(x, y, width, height);
        float surfaceY = y + (height - 54f) / 2f;
        float buttonY = y + (height - 48f) / 2f;
        if (width < 520f) {
            float controlsWidth = Math.max(190f, width * 0.58f);
            float controlsX = x + width - controlsWidth - 12f;
            float buttonWidth = 48f;
            Rectangle controls = new Rectangle(controlsX, surfaceY,
                    controlsWidth, 54f);
            Rectangle minus = new Rectangle(controlsX + 3f, buttonY,
                    buttonWidth, 48f);
            Rectangle value = new Rectangle(controlsX + buttonWidth + 3f,
                    surfaceY, controlsWidth - 2f * buttonWidth - 6f, 54f);
            Rectangle plus = new Rectangle(
                    controlsX + controlsWidth - buttonWidth - 3f, buttonY,
                    buttonWidth, 48f);
            Rectangle label = new Rectangle(x + 20f,
                    y + (height - 58f) / 2f,
                    Math.max(0f, controlsX - x - 30f), 58f);
            return new StepperRow(bounds, label, controls, minus, value, plus);
        }
        float valueWidth = Math.max(STANDARD_VALUE_WIDTH,
                requestedValueWidth);
        float controlsWidth = valueWidth + 134f;
        float controlsX = x + width - controlsWidth - 12f;
        Rectangle controls = new Rectangle(controlsX,
                surfaceY, controlsWidth, 54f);
        Rectangle minus = new Rectangle(controlsX + 6f,
                buttonY, 62f, 48f);
        Rectangle value = new Rectangle(controlsX + 70f,
                surfaceY, valueWidth, 54f);
        Rectangle plus = new Rectangle(value.x + value.width + 2f,
                buttonY, 62f, 48f);
        Rectangle label = new Rectangle(x + 20f,
                y + (height - 58f) / 2f,
                Math.max(0f, controlsX - x - 34f), 58f);
        return new StepperRow(bounds, label, controls, minus, value, plus);
    }

    /**
     * Canonical text geometry for a toggle that also exposes its state.
     * The state shares the horizontal centre used by stepper values, while
     * retaining a safe gap before the switch at the far right.
     */
    static ToggleRow toggleRow(float x, float y, float width,
            float height) {
        return toggleRow(x, y, width, height, 0f);
    }

    static ToggleRow toggleRow(float x, float y, float width,
            float height, float trailingValueReserve) {
        StepperRow stepper = stepperRow(x, y, width, height);
        float switchX = x + width - GdxSettingsStyle.TOGGLE_WIDTH
                - GdxSettingsStyle.TOGGLE_RIGHT_INSET;
        float valueCenter = stepper.value().x + stepper.value().width / 2f;
        float valueWidth = Math.min(TOGGLE_STATUS_WIDTH,
                stepper.value().width);
        float valueGap = trailingValueReserve > 0f
                ? TOGGLE_PREVIEW_GAP : TOGGLE_STATUS_GAP;
        float valueX = Math.min(valueCenter - valueWidth / 2f,
                switchX - valueGap
                        - Math.max(0f, trailingValueReserve)
                        - valueWidth);
        Rectangle value = new Rectangle(valueX, y + 5f,
                valueWidth, height - 10f);
        Rectangle label = new Rectangle(x + 22f, y + 5f,
                Math.max(0f, value.x - x - 34f), height - 10f);
        return new ToggleRow(new Rectangle(x, y, width, height), label,
                value, new Rectangle(switchX,
                        y + GdxSettingsStyle.TOGGLE_BOTTOM_INSET,
                        GdxSettingsStyle.TOGGLE_WIDTH,
                        GdxSettingsStyle.TOGGLE_HEIGHT));
    }

    /** Text bounds reserve one consistent icon lane on every primary tab. */
    static Rectangle mainTabLabel(Rectangle tab) {
        return new Rectangle(tab.x + 42f, tab.y, tab.width - 52f,
                tab.height);
    }

    static Rectangle mainTabIcon(Rectangle tab) {
        return new Rectangle(tab.x + 17f, tab.y + 13f, 22f, 22f);
    }

    /** Canonical two-column grid used by every dense Game settings page. */
    static GameColumns gameColumns(float rowX, float rowWidth) {
        float columnWidth = (rowWidth - GAME_COLUMN_GAP) / 2f;
        return new GameColumns(rowX,
                rowX + columnWidth + GAME_COLUMN_GAP, columnWidth);
    }

    /** Two equal controls inside one Game column, as used by New Game. */
    static SplitRow splitGameRow(Rectangle bounds) {
        float width = (bounds.width - GAME_INNER_GAP) / 2f;
        return new SplitRow(
                new Rectangle(bounds.x, bounds.y, width, bounds.height),
                new Rectangle(bounds.x + width + GAME_INNER_GAP, bounds.y,
                        width, bounds.height));
    }

    /** Paired action buttons used by the voice-note settings subsection. */
    static SplitRow splitActionRow(float x, float y, float width) {
        float buttonWidth = (width - ACTION_ROW_GAP) / 2f;
        return new SplitRow(
                new Rectangle(x, y, buttonWidth, ACTION_ROW_HEIGHT),
                new Rectangle(x + buttonWidth + ACTION_ROW_GAP, y,
                        buttonWidth, ACTION_ROW_HEIGHT));
    }

    /**
     * One shortcut row contract for menu, lobby and live table.  Shapes, text
     * and hit targets must all consume these exact rectangles.
     */
    static ShortcutRow shortcutRow(float x, float y, float width) {
        Rectangle bounds = new Rectangle(x, y, width, SHORTCUT_ROW_HEIGHT);
        Rectangle description = new Rectangle(x + 24f, y + 5f,
                Math.max(0f, width - 276f), 52f);
        Rectangle key = new Rectangle(x + width - 228f, y + 8f,
                216f, 46f);
        return new ShortcutRow(bounds, key, description);
    }

    static float rowTextBaseline(float y) {
        return y + ROW_TEXT_BASELINE;
    }

    static float stepperLabelBaseline(float y) {
        return y + STEPPER_LABEL_BASELINE;
    }

    static float stepperValueBaseline(float y) {
        return y + STEPPER_VALUE_BASELINE;
    }

    static float stepperSignBaseline(float y) {
        return y + STEPPER_SIGN_BASELINE;
    }

    static float infoLabelBaseline(float y) {
        return y + INFO_LABEL_BASELINE;
    }

    static float infoValueBaseline(float y) {
        return y + INFO_VALUE_BASELINE;
    }

    static float volumeTextBaseline(VolumeRow row) {
        return row.bounds().y + VOLUME_TEXT_BASELINE;
    }

    static float shortcutKeyBaseline(ShortcutRow row) {
        return row.bounds().y + SHORTCUT_KEY_BASELINE;
    }

    static float shortcutDescriptionBaseline(ShortcutRow row) {
        return row.bounds().y + SHORTCUT_DESCRIPTION_BASELINE;
    }

    static Rectangle debugCopyButton(Rectangle content) {
        return new Rectangle(content.x + content.width - 248f,
                content.y + 14f, 230f, 54f);
    }

    static Rectangle debugPerformanceRow(Rectangle content) {
        return new Rectangle(content.x + CONTENT_HORIZONTAL_INSET,
                content.y + content.height - ROW_HEIGHT - 28f,
                content.width - 2f * CONTENT_HORIZONTAL_INSET, ROW_HEIGHT);
    }

    /** Complete debug-console geometry shared by menu, lobby and table. */
    static DebugArea debugArea(Rectangle content) {
        Rectangle console = new Rectangle(content.x + 24f,
                content.y + 82f, content.width - 48f,
                Math.max(0f, content.height - ROW_HEIGHT - 132f));
        // The debug stream uses the same inset, text lane and scrollbar lane
        // as the in-game log instead of looking like an unrelated terminal.
        Rectangle viewport = new Rectangle(console.x + 14f,
                console.y + 14f, Math.max(0f, console.width - 44f),
                Math.max(0f, console.height - 28f));
        return new DebugArea(console, viewport,
                console.x + console.width - 24f);
    }

    static float debugTextWidth(Rectangle console) {
        return Math.max(1f, console.width - DEBUG_TEXT_LEFT_INSET
                - DEBUG_TEXT_RIGHT_INSET);
    }

    static float scrollbarThumbHeight(float height, int totalRows,
            int visibleRows) {
        if (totalRows <= 0 || visibleRows <= 0) return height;
        return Math.min(height, Math.max(42f,
                height * visibleRows / (float) totalRows));
    }

    /**
     * Continuous row layout for settings lists.  Unlike the legacy
     * first-row/page model, the offset is expressed in pixels so a wheel tick
     * never has to discard a complete row (or a complete shortcuts page).
     */
    static PixelRows pixelRows(float firstRowY, float viewportBottom,
            float viewportTop, int totalRows, float requestedOffset) {
        return pixelRows(firstRowY, viewportBottom, viewportTop, totalRows,
                requestedOffset, Set.of());
    }

    static PixelRows pixelRows(float firstRowY, float viewportBottom,
            float viewportTop, int totalRows, float requestedOffset,
            Set<Integer> gapsBefore) {
        float viewportHeight = Math.max(0f, viewportTop - viewportBottom);
        float[] rowOffsets = new float[Math.max(0, totalRows)];
        float position = 0f;
        for (int row = 0; row < rowOffsets.length; row++) {
            if (row > 0 && gapsBefore.contains(row)) position += GROUP_GAP;
            rowOffsets[row] = position;
            position += ROW_STRIDE;
        }
        float contentHeight = totalRows <= 0 ? 0f
                : rowOffsets[totalRows - 1] + ROW_HEIGHT;
        float maximum = Math.max(0f, contentHeight - viewportHeight);
        float offset = Math.max(0f, Math.min(maximum, requestedOffset));
        int first = totalRows;
        int last = 0;
        for (int row = 0; row < totalRows; row++) {
            float screenY = firstRowY - rowOffsets[row] + offset;
            if (screenY + ROW_HEIGHT >= viewportBottom
                    && screenY <= viewportTop) {
                first = Math.min(first, row);
                last = row + 1;
            }
        }
        if (first == totalRows) first = 0;
        return new PixelRows(firstRowY, viewportBottom, viewportTop,
                totalRows, first, last, offset, maximum, contentHeight,
                rowOffsets);
    }

    /**
     * Pixel layout that reserves real space for useful group frames. The
     * legacy fixed gap could only separate rows; it could not protect the top
     * and bottom borders or distinguish a group transition from an ordinary
     * gap.
     */
    static PixelRows pixelRows(float firstRowY, float viewportBottom,
            float viewportTop, int totalRows, float requestedOffset,
            List<GdxSettingsContract.RowGroup> groups) {
        List<GdxSettingsContract.RowGroup> visibleGroups =
                GdxSettingsContract.visualGroups(groups, totalRows);
        float viewportHeight = Math.max(0f, viewportTop - viewportBottom);
        float[] rowOffsets = new float[Math.max(0, totalRows)];
        float position = visibleGroups.stream().anyMatch(group ->
                group.parentRow() == 0)
                ? GROUP_FRAME_PADDING
                        + Math.max(0f, CONTENT_HORIZONTAL_INSET
                                - (CONTENT_ROW_TOP_INSET - ROW_HEIGHT))
                : 0f;
        for (int row = 0; row < rowOffsets.length; row++) {
            if (row > 0) {
                int opening = 0;
                int closing = 0;
                for (GdxSettingsContract.RowGroup group : visibleGroups) {
                    if (group.parentRow() == row) opening++;
                    if (group.lastRow() + 1 == row) closing++;
                }
                if (opening + closing > 0) {
                    // ROW_STRIDE already contributes the ordinary two-pixel
                    // row gap, so do not count it twice here.
                    position += (opening + closing) * GROUP_FRAME_PADDING
                            + GROUP_GAP - (ROW_STRIDE - ROW_HEIGHT);
                }
            }
            rowOffsets[row] = position;
            position += ROW_STRIDE;
        }
        int trailingFrames = 0;
        for (GdxSettingsContract.RowGroup group : visibleGroups) {
            if (group.lastRow() == totalRows - 1) trailingFrames++;
        }
        float contentHeight = totalRows <= 0 ? 0f
                : rowOffsets[totalRows - 1] + ROW_HEIGHT
                        + trailingFrames * GROUP_FRAME_PADDING;
        float maximum = Math.max(0f, contentHeight - viewportHeight);
        float offset = Math.max(0f, Math.min(maximum, requestedOffset));
        int first = totalRows;
        int last = 0;
        for (int row = 0; row < totalRows; row++) {
            float screenY = firstRowY - rowOffsets[row] + offset;
            if (screenY + ROW_HEIGHT >= viewportBottom
                    && screenY <= viewportTop) {
                first = Math.min(first, row);
                last = row + 1;
            }
        }
        if (first == totalRows) first = 0;
        return new PixelRows(firstRowY, viewportBottom, viewportTop,
                totalRows, first, last, offset, maximum, contentHeight,
                rowOffsets);
    }

    static Rectangle groupBounds(float rowX, float rowWidth, PixelRows rows,
            GdxSettingsContract.RowGroup group) {
        float inset = group.depth() * GROUP_DEPTH_INSET;
        float bottom = rows.rowY(group.lastRow()) - GROUP_FRAME_PADDING;
        float top = rows.rowY(group.parentRow()) + ROW_HEIGHT
                + GROUP_FRAME_PADDING;
        return new Rectangle(rowX + inset, bottom,
                Math.max(0f, rowWidth - 2f * inset),
                Math.max(1f, top - bottom));
    }

    static void drawGroups(com.badlogic.gdx.graphics.glutils.ShapeRenderer shapes,
            float rowX, float rowWidth, PixelRows rows,
            List<GdxSettingsContract.RowGroup> groups, float alpha) {
        for (GdxSettingsContract.RowGroup group
                : GdxSettingsContract.visualGroups(groups,
                        rows.totalRows())) {
            GdxSettingsStyle.drawGroupFrame(shapes,
                    groupBounds(rowX, rowWidth, rows, group), alpha);
        }
    }

    static void drawCompactGroups(
            com.badlogic.gdx.graphics.glutils.ShapeRenderer shapes,
            float rowX, float rowWidth, float firstRowY,
            List<GdxSettingsContract.RowGroup> groups, float alpha) {
        // Compact Game pages already divide related controls into independent
        // columns. A second outline cannot provide the same inner/outer
        // padding as a scrollable group without shrinking those controls, so
        // it is redundant visual noise rather than useful hierarchy.
    }

    static float pixelScrollAfterWheel(float current, float maximum,
            float amountY) {
        return Math.max(0f, Math.min(Math.max(0f, maximum),
                current + amountY * PIXEL_SCROLL_WHEEL_STEP));
    }

    static float pixelScrollbarThumbHeight(float trackHeight,
            float viewportHeight, float contentHeight) {
        if (contentHeight <= 0f || viewportHeight <= 0f) return trackHeight;
        return Math.min(trackHeight, Math.max(42f,
                trackHeight * Math.min(1f, viewportHeight / contentHeight)));
    }

    /** Row zero is at the top, therefore the zero-offset thumb is at top. */
    static float pixelScrollFromScrollbar(float pointerY, float trackY,
            float trackHeight, float thumbHeight, float maximum) {
        if (maximum <= 0f) return 0f;
        float travel = Math.max(1f, trackHeight - thumbHeight);
        float fromBottom = Math.max(0f, Math.min(1f,
                (pointerY - trackY - thumbHeight / 2f) / travel));
        return (1f - fromBottom) * maximum;
    }

    /**
     * Converts a pointer position on a settings scrollbar into its first
     * visible row. Row zero lives at the top, matching the visual thumb.
     */
    static int firstRowFromScrollbar(float pointerY, float trackY,
            float trackHeight, float thumbHeight, int maximumFirstRow) {
        if (maximumFirstRow <= 0) return 0;
        float travel = Math.max(1f, trackHeight - thumbHeight);
        float progressFromBottom = Math.max(0f, Math.min(1f,
                (pointerY - trackY - thumbHeight / 2f) / travel));
        return Math.round((1f - progressFromBottom) * maximumFirstRow);
    }

    record Frame(Rectangle panel, Rectangle content, float mainTabY,
            float mainTabWidth, float subTabY, float subTabWidth,
            int subTabColumns, float firstRowY) {

        Rectangle mainTab(int index) {
            return new Rectangle(panel.x + HORIZONTAL_INSET
                    + index * mainTabWidth, mainTabY,
                    mainTabWidth - 4f, 48f);
        }

        Rectangle subTab(int index) {
            int row = index / subTabColumns;
            int column = index % subTabColumns;
            return new Rectangle(panel.x + HORIZONTAL_INSET
                    + column * subTabWidth,
                    subTabY - row * SUB_TAB_ROW_GAP,
                    subTabWidth - 3f, 38f);
        }

        Rectangle cancelButton() {
            return new Rectangle(panel.x + 30f,
                    panel.y + FOOTER_BOTTOM_INSET,
                    190f, FOOTER_BUTTON_HEIGHT);
        }

        Rectangle restoreButton() {
            return new Rectangle(panel.x + 240f,
                    panel.y + FOOTER_BOTTOM_INSET,
                    360f, FOOTER_BUTTON_HEIGHT);
        }

        Rectangle saveButton() {
            return new Rectangle(panel.x + panel.width - 250f,
                    panel.y + FOOTER_BOTTOM_INSET,
                    220f, FOOTER_BUTTON_HEIGHT);
        }
    }

    record VolumeRow(Rectangle bounds, Rectangle label,
            Rectangle percentage, Rectangle slider, Rectangle minusButton,
            Rectangle plusButton) {
    }

    record StepperRow(Rectangle bounds, Rectangle label,
            Rectangle controls, Rectangle minusButton, Rectangle value,
            Rectangle plusButton) {

        int directionAt(float pointerX) {
            if (pointerX >= minusButton.x
                    && pointerX <= minusButton.x + minusButton.width) {
                return -1;
            }
            if (pointerX >= plusButton.x
                    && pointerX <= plusButton.x + plusButton.width) {
                return 1;
            }
            return 0;
        }

        boolean valueContains(float pointerX) {
            return pointerX > value.x
                    && pointerX < value.x + value.width;
        }
    }

    record ToggleRow(Rectangle bounds, Rectangle label, Rectangle value,
            Rectangle toggle) {
    }

    record ChoiceRow(Rectangle bounds, Rectangle label, Rectangle value,
            Rectangle arrow) {
    }

    record GameColumns(float leftX, float rightX, float width) {

        Rectangle left(float firstRowY, int row) {
            return new Rectangle(leftX,
                    firstRowY - row * ROW_STRIDE, width, ROW_HEIGHT);
        }

        Rectangle right(float firstRowY, int row) {
            return new Rectangle(rightX,
                    firstRowY - row * ROW_STRIDE, width, ROW_HEIGHT);
        }
    }

    record SplitRow(Rectangle left, Rectangle right) {
    }

    record ShortcutRow(Rectangle bounds, Rectangle key,
            Rectangle description) {
    }

    record DebugArea(Rectangle console, Rectangle viewport,
            float scrollbarX) {
    }

    record StatusBand(float firstRowY, float viewportTop,
            float textCenterY) {

        float scrolledTextCenterY(float scrollOffset) {
            return textCenterY + Math.max(0f, scrollOffset);
        }
    }

    record PixelRows(float firstRowY, float viewportBottom,
            float viewportTop, int totalRows, int firstIndex,
            int lastExclusive, float offset, float maximum,
            float contentHeight, float[] rowOffsets) {

        float viewportHeight() {
            return Math.max(0f, viewportTop - viewportBottom);
        }

        float rowY(int index) {
            return firstRowY - rowOffsets[index] + offset;
        }

        boolean scrollable() {
            return maximum > 0f;
        }
    }
}
