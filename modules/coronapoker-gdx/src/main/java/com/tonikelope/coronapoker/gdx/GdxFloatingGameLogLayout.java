/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;

/** Geometry for the movable, resizable in-game GDX log window. */
final class GdxFloatingGameLogLayout {

    static final float SCREEN_MARGIN = 16f;
    static final float MIN_WIDTH = 640f;
    static final float MIN_HEIGHT = 360f;
    static final float RESIZE_EDGE = 14f;
    static final float RESIZE_CORNER = 20f;
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
        return resized(start, ResizeHandle.BOTTOM_RIGHT, deltaX, deltaY,
                worldWidth, worldHeight);
    }

    static Rectangle resized(Rectangle start, ResizeHandle handle,
            float deltaX, float deltaY, float worldWidth,
            float worldHeight) {
        if (handle == null || handle == ResizeHandle.NONE) {
            return normalize(new Rectangle(start), worldWidth, worldHeight);
        }
        float minimumWidth = Math.min(MIN_WIDTH,
                Math.max(1f, worldWidth - 2f * SCREEN_MARGIN));
        float minimumHeight = Math.min(MIN_HEIGHT,
                Math.max(1f, worldHeight - 2f * SCREEN_MARGIN));
        float left = start.x;
        float right = start.x + start.width;
        float bottom = start.y;
        float top = start.y + start.height;
        if (handle.left) {
            left = MathUtils.clamp(left + deltaX, SCREEN_MARGIN,
                    right - minimumWidth);
        } else if (handle.right) {
            right = MathUtils.clamp(right + deltaX,
                    left + minimumWidth, worldWidth - SCREEN_MARGIN);
        }
        if (handle.bottom) {
            bottom = MathUtils.clamp(bottom + deltaY, SCREEN_MARGIN,
                    top - minimumHeight);
        } else if (handle.top) {
            top = MathUtils.clamp(top + deltaY,
                    bottom + minimumHeight, worldHeight - SCREEN_MARGIN);
        }
        return normalize(new Rectangle(left, bottom,
                right - left, top - bottom), worldWidth, worldHeight);
    }

    static ResizeHandle resizeHandleAt(Rectangle panel, float x, float y) {
        if (!panel.contains(x, y)) return ResizeHandle.NONE;
        float right = panel.x + panel.width;
        float top = panel.y + panel.height;
        boolean leftCorner = x <= panel.x + RESIZE_CORNER;
        boolean rightCorner = x >= right - RESIZE_CORNER;
        boolean bottomCorner = y <= panel.y + RESIZE_CORNER;
        boolean topCorner = y >= top - RESIZE_CORNER;
        if (leftCorner && topCorner) return ResizeHandle.TOP_LEFT;
        if (rightCorner && topCorner) return ResizeHandle.TOP_RIGHT;
        if (leftCorner && bottomCorner) return ResizeHandle.BOTTOM_LEFT;
        if (rightCorner && bottomCorner) return ResizeHandle.BOTTOM_RIGHT;
        if (x <= panel.x + RESIZE_EDGE) return ResizeHandle.LEFT;
        if (x >= right - RESIZE_EDGE) return ResizeHandle.RIGHT;
        if (y <= panel.y + RESIZE_EDGE) return ResizeHandle.BOTTOM;
        if (y >= top - RESIZE_EDGE) return ResizeHandle.TOP;
        return ResizeHandle.NONE;
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
        return new Layout(copy, header, close, logFrame, content,
                scrollbar);
    }

    record Layout(Rectangle panel, Rectangle headerDrag,
            Rectangle closeButton, Rectangle logFrame, Rectangle content,
            Rectangle scrollbarTrack) {
    }

    enum ResizeHandle {
        NONE(false, false, false, false),
        LEFT(true, false, false, false),
        RIGHT(false, true, false, false),
        TOP(false, false, true, false),
        BOTTOM(false, false, false, true),
        TOP_LEFT(true, false, true, false),
        TOP_RIGHT(false, true, true, false),
        BOTTOM_LEFT(true, false, false, true),
        BOTTOM_RIGHT(false, true, false, true);

        private final boolean left;
        private final boolean right;
        private final boolean top;
        private final boolean bottom;

        ResizeHandle(boolean left, boolean right, boolean top,
                boolean bottom) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
        }
    }
}
