/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Rectangle;

/** Shared modal frame geometry used by menu and live-table settings. */
final class GdxSettingsLayout {

    static final float HORIZONTAL_INSET = 44f;
    // The subtitle baseline sits 116 px below the panel top.  The old tab row
    // ended only four pixels below it, so real font ascenders/descenders at
    // 125% Windows scaling visibly crossed the tab chrome.  Reserve a full
    // text line between the header and the first row of controls.
    static final float MAIN_TAB_TOP_INSET = 196f;
    static final float SUB_TAB_TOP_INSET = 254f;
    static final float FIRST_ROW_TOP_INSET = 330f;
    static final float CONTENT_BOTTOM_INSET = 112f;
    static final float CONTENT_TOTAL_VERTICAL_INSET = 362f;
    static final float FOOTER_BOTTOM_INSET = 24f;
    static final float FOOTER_BUTTON_HEIGHT = 58f;

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
        Rectangle content = new Rectangle(
                panel.x + HORIZONTAL_INSET,
                panel.y + CONTENT_BOTTOM_INSET,
                innerWidth,
                panel.height - CONTENT_TOTAL_VERTICAL_INSET);
        return new Frame(panel, content,
                panel.y + panel.height - MAIN_TAB_TOP_INSET,
                innerWidth / sectionCount,
                panel.y + panel.height - SUB_TAB_TOP_INSET,
                innerWidth / subpageCount,
                panel.y + panel.height - FIRST_ROW_TOP_INSET);
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
        float firstRowBottomInset = 158f;
        float lastRowBottomInset = 16f;
        float available = contentHeight - firstRowBottomInset
                - lastRowBottomInset;
        return Math.min(84f, Math.max(70f,
                available / (rowCount - 1)));
    }

    record Frame(Rectangle panel, Rectangle content, float mainTabY,
            float mainTabWidth, float subTabY, float subTabWidth,
            float firstRowY) {

        Rectangle mainTab(int index) {
            return new Rectangle(panel.x + HORIZONTAL_INSET
                    + index * mainTabWidth, mainTabY,
                    mainTabWidth - 4f, 48f);
        }

        Rectangle subTab(int index) {
            return new Rectangle(panel.x + HORIZONTAL_INSET
                    + index * subTabWidth, subTabY,
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
}
