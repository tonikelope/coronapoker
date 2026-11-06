/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

/** Shared, resource-free poker-video affordance for the menu and waiting room. */
final class PokerVideoIcon {

    private static final float SOUND_GAP = 12f;

    enum Placement {
        // These are the exact painted sound-button dimensions, not just its
        // texture bounds. The video affordance therefore has the same visual
        // weight and sits immediately to its left on both surfaces.
        MENU(48f, 16f, 31f),
        LOBBY(60f, 61f, 309.5f);

        private final float size;
        private final float bottom;
        private final float soundRightMargin;

        Placement(float size, float bottom, float soundRightMargin) {
            this.size = size;
            this.bottom = bottom;
            this.soundRightMargin = soundRightMargin;
        }
    }

    private PokerVideoIcon() {
    }

    static boolean systemEnabled(boolean platformAvailable,
            boolean animationsEnabled) {
        return platformAvailable && animationsEnabled;
    }

    static Rectangle bounds(float worldWidth, Placement placement,
            Rectangle result) {
        float soundLeft = worldWidth - placement.soundRightMargin
                - placement.size;
        return result.set(soundLeft - SOUND_GAP - placement.size,
                placement.bottom, placement.size, placement.size);
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
                Math.max(12f, bounds.width - 8f),
                GdxUiButtonStyle.labelColor(
                        GdxUiButtonStyle.Tone.FEATURED, true), alpha);
    }
}
