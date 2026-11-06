/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

/** Shared, resource-free poker-video affordance for every game surface. */
final class PokerVideoIcon {

    static final float SIZE = 58f;
    private static final float RIGHT_MARGIN = 24f;

    enum Placement {
        MENU(92f),
        LOBBY(154f),
        TABLE(92f);

        private final float bottom;

        Placement(float bottom) {
            this.bottom = bottom;
        }
    }

    private PokerVideoIcon() {
    }

    static boolean systemEnabled(boolean platformAvailable,
            boolean animationsEnabled) {
        return platformAvailable && animationsEnabled;
    }

    static boolean visibleOnTable(boolean platformAvailable,
            boolean animationsEnabled, boolean productTable,
            boolean rootSurface, boolean blocked) {
        return systemEnabled(platformAvailable, animationsEnabled)
                && productTable && rootSurface && !blocked;
    }

    static Rectangle bounds(float worldWidth, Placement placement,
            Rectangle result) {
        return result.set(worldWidth - SIZE - RIGHT_MARGIN,
                placement.bottom, SIZE, SIZE);
    }

    static void draw(ShapeRenderer shapes, Rectangle bounds, Vector2 pointer,
            boolean pressed, float alpha) {
        boolean hovered = bounds.contains(pointer);
        // Deliberately no external hover halo or drop shadow: the complete
        // interaction feedback stays clipped to the control silhouette.
        GdxUiButtonStyle.draw(shapes, bounds.x, bounds.y,
                bounds.width, bounds.height,
                GdxUiButtonStyle.Tone.FEATURED, true,
                hovered ? 1f : 0f, pressed, alpha);
        GdxUiIconRenderer.draw(shapes,
                GdxUiIconRenderer.Icon.CONTINUE,
                bounds.x + bounds.width / 2f,
                bounds.y + bounds.height / 2f,
                SIZE * 0.52f,
                GdxUiButtonStyle.labelColor(
                        GdxUiButtonStyle.Tone.FEATURED, true), alpha);
    }
}
