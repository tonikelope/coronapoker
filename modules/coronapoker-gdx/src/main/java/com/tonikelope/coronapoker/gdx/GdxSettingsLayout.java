/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Rectangle;

/** Shared modal frame geometry used by menu and live-table settings. */
final class GdxSettingsLayout {

    static final float HORIZONTAL_INSET = 44f;
    /** Breathing room between the content well and every settings row. */
    static final float CONTENT_HORIZONTAL_INSET = 34f;
    /** Left-only inset used by every control governed by another switch. */
    static final float CHILD_ROW_INDENT = 38f;
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
    static final float FOOTER_BOTTOM_INSET = 24f;
    static final float FOOTER_BUTTON_HEIGHT = 58f;
    /** Common row geometry for menu, waiting-room and live-table settings. */
    static final float ROW_HEIGHT = 68f;
    static final float ROW_STRIDE = 70f;
    static final float GAME_COLUMN_GAP = 24f;
    /** Same visible width as the in-game log scrollbar. */
    static final float SCROLLBAR_WIDTH = 14f;
    /** Generous mouse target around the visible bar. */
    static final float SCROLLBAR_HIT_WIDTH = 28f;
    /** Same measured wheel travel used by the lobby and quick-table chats. */
    static final float PIXEL_SCROLL_WHEEL_STEP = 48f;

    private GdxSettingsLayout() {
    }

    static Rectangle panelBounds(float worldWidth, float worldHeight) {
        float panelWidth = Math.min(1600f, worldWidth - 64f);
        float panelHeight = Math.min(920f, worldHeight - 54f);
        return new Rectangle((worldWidth - panelWidth) / 2f,
                (worldHeight - panelHeight) / 2f,
                panelWidth, panelHeight);
    }

    /**
     * Complete frame shared by the startup/lobby and live-table settings
     * entry points.  Keeping these rectangles here prevents a visual fix in
     * one renderer from silently moving its tabs, content or footer away from
     * the other renderer.
     */
    static Frame frame(float worldWidth, float worldHeight, int sectionCount,
            int subpageCount) {
        if (sectionCount < 1 || subpageCount < 1) {
            throw new IllegalArgumentException(
                    "Settings tabs require at least one item");
        }
        Rectangle panel = panelBounds(worldWidth, worldHeight);
        float innerWidth = panel.width - 2f * HORIZONTAL_INSET;
        int subTabRows = (subpageCount + MAX_SUB_TABS_PER_ROW - 1)
                / MAX_SUB_TABS_PER_ROW;
        int subTabColumns = (subpageCount + subTabRows - 1)
                / subTabRows;
        float extraSubTabHeight = (subTabRows - 1) * SUB_TAB_ROW_GAP;
        Rectangle content = new Rectangle(
                panel.x + HORIZONTAL_INSET,
                panel.y + CONTENT_BOTTOM_INSET,
                innerWidth,
                panel.height - CONTENT_TOTAL_VERTICAL_INSET
                - extraSubTabHeight);
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
     * Applies the same content padding and optional hierarchy indentation in
     * the menu and live-table renderers.
     */
    static Rectangle optionRow(float x, float y, float width,
            boolean child) {
        float indent = child ? CHILD_ROW_INDENT : 0f;
        return new Rectangle(x + indent, y,
                width - indent, ROW_HEIGHT);
    }

    /**
     * Shared geometry for every minus/value/plus settings row.  The startup,
     * waiting-room and live-table renderers used to carry slightly different
     * constants, so the painted controls and their hit targets could drift
     * apart as soon as one of them was polished independently.
     */
    static StepperRow stepperRow(float x, float y, float width,
            float height) {
        Rectangle bounds = new Rectangle(x, y, width, height);
        float surfaceY = y + (height - 54f) / 2f;
        float buttonY = y + (height - 48f) / 2f;
        Rectangle controls = new Rectangle(x + width - 432f,
                surfaceY, 420f, 54f);
        Rectangle minus = new Rectangle(x + width - 426f,
                buttonY, 62f, 48f);
        Rectangle value = new Rectangle(x + width - 362f,
                surfaceY, 286f, 54f);
        Rectangle plus = new Rectangle(x + width - 74f,
                buttonY, 62f, 48f);
        Rectangle label = new Rectangle(x + 20f,
                y + (height - 58f) / 2f,
                Math.max(0f, width - 466f), 58f);
        return new StepperRow(bounds, label, controls, minus, value, plus);
    }

    /** Canonical two-column grid used by every dense Game settings page. */
    static GameColumns gameColumns(float rowX, float rowWidth) {
        float columnWidth = (rowWidth - GAME_COLUMN_GAP) / 2f;
        return new GameColumns(rowX,
                rowX + columnWidth + GAME_COLUMN_GAP, columnWidth);
    }

    static Rectangle debugCopyButton(Rectangle content) {
        return new Rectangle(content.x + content.width - 248f,
                content.y + 14f, 230f, 54f);
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
        float viewportHeight = Math.max(0f, viewportTop - viewportBottom);
        float contentHeight = totalRows <= 0 ? 0f
                : ROW_HEIGHT + Math.max(0, totalRows - 1) * ROW_STRIDE;
        float maximum = Math.max(0f, contentHeight - viewportHeight);
        float offset = Math.max(0f, Math.min(maximum, requestedOffset));
        int first = totalRows <= 0 ? 0 : Math.max(0,
                (int) Math.floor(offset / ROW_STRIDE));
        int last = totalRows <= 0 ? 0 : Math.min(totalRows,
                (int) Math.ceil((offset + viewportHeight) / ROW_STRIDE));
        return new PixelRows(firstRowY, viewportBottom, viewportTop,
                totalRows, first, last, offset, maximum, contentHeight);
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

    record PixelRows(float firstRowY, float viewportBottom,
            float viewportTop, int totalRows, int firstIndex,
            int lastExclusive, float offset, float maximum,
            float contentHeight) {

        float viewportHeight() {
            return Math.max(0f, viewportTop - viewportBottom);
        }

        float rowY(int index) {
            return firstRowY - index * ROW_STRIDE + offset;
        }

        boolean scrollable() {
            return maximum > 0f;
        }
    }
}
