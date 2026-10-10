/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;

/** Shared geometry and time formatting for both host blocklist entry points. */
final class GdxNetworkBlockDialogLayout {

    static final float PANEL_WIDTH = 1_080f;
    static final float PANEL_HEIGHT = 700f;
    static final float ROW_HEIGHT = 76f;
    static final float ROW_GAP = 10f;
    static final int VISIBLE_ROWS = 6;

    record Layout(Rectangle panel, Rectangle rows, Rectangle closeButton,
            Rectangle scrollbar) { }

    private GdxNetworkBlockDialogLayout() { }

    static Layout layout(float worldWidth, float worldHeight) {
        float width = Math.min(PANEL_WIDTH, worldWidth - 80f);
        float height = Math.min(PANEL_HEIGHT, worldHeight - 70f);
        float x = (worldWidth - width) / 2f;
        float y = (worldHeight - height) / 2f;
        Rectangle panel = new Rectangle(x, y, width, height);
        Rectangle rows = new Rectangle(x + 44f, y + 128f,
                width - 88f, height - 244f);
        return new Layout(panel, rows,
                new Rectangle(x + width - 252f, y + 34f, 208f, 64f),
                new Rectangle(x + width - 30f, rows.y, 10f, rows.height));
    }

    static float rowStride() {
        return ROW_HEIGHT + ROW_GAP;
    }

    static float maximumScroll(int rowCount, Rectangle rows) {
        return Math.max(0f, rowCount * rowStride() - ROW_GAP - rows.height);
    }

    static float clampScroll(float scroll, int rowCount, Rectangle rows) {
        return MathUtils.clamp(scroll, 0f, maximumScroll(rowCount, rows));
    }

    static Rectangle row(Rectangle rows, int index, float scroll) {
        return new Rectangle(rows.x,
                rows.y + rows.height - ROW_HEIGHT
                        - index * rowStride() + scroll,
                rows.width - 24f, ROW_HEIGHT);
    }

    static String remaining(long millis) {
        long seconds = Math.max(0L, (millis + 999L) / 1_000L);
        long minutes = seconds / 60L;
        long remainder = seconds % 60L;
        return minutes > 0L ? minutes + " min " + remainder + " s"
                : remainder + " s";
    }
}
