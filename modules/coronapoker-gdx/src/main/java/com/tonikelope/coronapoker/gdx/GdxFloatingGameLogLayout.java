/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;

/** Geometry for the movable, resizable in-game GDX log window. */
final class GdxFloatingGameLogLayout {

    static final float SCREEN_MARGIN = 16f;
    static final float MIN_WIDTH = 640f;
    static final float MIN_HEIGHT = 360f;
    private static final float DEFAULT_WIDTH = 1160f;
    private static final float DEFAULT_HEIGHT = 720f;

    private GdxFloatingGameLogLayout() {
    }

    static Rectangle initial(float worldWidth, float worldHeight) {
        float availableWidth = Math.max(1f,
                worldWidth - 2f * SCREEN_MARGIN);
        float availableHeight = Math.max(1f,
                worldHeight - 2f * SCREEN_MARGIN);
        float width = Math.min(DEFAULT_WIDTH, availableWidth);
        float height = Math.min(DEFAULT_HEIGHT, availableHeight);
        return normalize(new Rectangle(
                (worldWidth - width) / 2f,
                (worldHeight - height) / 2f,
                width, height), worldWidth, worldHeight);
    }

    static Rectangle normalize(Rectangle requested, float worldWidth,
            float worldHeight) {
        float availableWidth = Math.max(1f,
                worldWidth - 2f * SCREEN_MARGIN);
        float availableHeight = Math.max(1f,
                worldHeight - 2f * SCREEN_MARGIN);
        float minimumWidth = Math.min(MIN_WIDTH, availableWidth);
        float minimumHeight = Math.min(MIN_HEIGHT, availableHeight);
        float width = MathUtils.clamp(requested.width,
                minimumWidth, availableWidth);
        float height = MathUtils.clamp(requested.height,
                minimumHeight, availableHeight);
        float x = MathUtils.clamp(requested.x, SCREEN_MARGIN,
                Math.max(SCREEN_MARGIN,
                        worldWidth - SCREEN_MARGIN - width));
        float y = MathUtils.clamp(requested.y, SCREEN_MARGIN,
                Math.max(SCREEN_MARGIN,
                        worldHeight - SCREEN_MARGIN - height));
        return new Rectangle(x, y, width, height);
    }

    static Rectangle moved(Rectangle start, float deltaX, float deltaY,
            float worldWidth, float worldHeight) {
        return normalize(new Rectangle(start.x + deltaX,
                start.y + deltaY, start.width, start.height),
                worldWidth, worldHeight);
    }

    /**
     * Resizes the lower-right corner while keeping the top-left corner fixed.
     */
    static Rectangle resizedFromLowerRight(Rectangle start, float deltaX,
            float deltaY, float worldWidth, float worldHeight) {
        float availableWidth = Math.max(1f,
                worldWidth - SCREEN_MARGIN - start.x);
        float availableHeight = Math.max(1f,
                start.y + start.height - SCREEN_MARGIN);
        float width = MathUtils.clamp(start.width + deltaX,
                Math.min(MIN_WIDTH, availableWidth), availableWidth);
        float height = MathUtils.clamp(start.height - deltaY,
                Math.min(MIN_HEIGHT, availableHeight), availableHeight);
        float top = start.y + start.height;
        return normalize(new Rectangle(start.x, top - height,
                width, height), worldWidth, worldHeight);
    }

    static Layout layout(Rectangle panel) {
        Rectangle copy = new Rectangle(panel);
        Rectangle close = new Rectangle(panel.x + panel.width - 62f,
                panel.y + panel.height - 58f, 40f, 36f);
        Rectangle header = new Rectangle(panel.x + 22f,
                panel.y + panel.height - 70f,
                Math.max(1f, panel.width - 100f), 54f);
        Rectangle logFrame = new Rectangle(panel.x + 24f, panel.y + 24f,
                Math.max(1f, panel.width - 48f),
                Math.max(1f, panel.height - 108f));
        Rectangle content = new Rectangle(logFrame.x + 14f,
                logFrame.y + 14f,
                Math.max(1f, logFrame.width - 44f),
                Math.max(1f, logFrame.height - 28f));
        Rectangle scrollbar = new Rectangle(
                logFrame.x + logFrame.width - 24f,
                logFrame.y + 14f, 14f,
                Math.max(1f, logFrame.height - 28f));
        Rectangle resize = new Rectangle(panel.x + panel.width - 30f,
                panel.y + 2f, 26f, 26f);
        return new Layout(copy, header, close, logFrame, content,
                scrollbar, resize);
    }

    record Layout(Rectangle panel, Rectangle headerDrag,
            Rectangle closeButton, Rectangle logFrame, Rectangle content,
            Rectangle scrollbarTrack, Rectangle resizeGrip) {
    }
}
