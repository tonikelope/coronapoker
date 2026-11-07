/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;

/** Shared minimized video button for the menu and waiting room. */
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

    static void drawLogo(SpriteBatch batch, Texture logo, Rectangle bounds,
            boolean hovered, boolean pressed) {
        float feedbackScale = pressed ? 0.94f : hovered ? 1.04f : 1f;
        drawLogo(batch, logo, bounds, feedbackScale, 1f);
    }

    static Rectangle logoBounds(Texture logo, Rectangle bounds, Rectangle result) {
        float scale = fitScale(logo, bounds);
        float width = logo.getWidth() * scale;
        float height = logo.getHeight() * scale;
        return result.set(bounds.x + (bounds.width - width) / 2f,
                bounds.y + (bounds.height - height) / 2f, width, height);
    }

    static void drawLogo(SpriteBatch batch, Texture logo, Rectangle bounds,
            float feedbackScale, float alpha) {
        float scale = fitScale(logo, bounds) * feedbackScale;
        float width = logo.getWidth() * scale;
        float height = logo.getHeight() * scale;
        batch.setColor(1f, 1f, 1f, alpha);
        batch.draw(logo, bounds.x + (bounds.width - width) / 2f,
                bounds.y + (bounds.height - height) / 2f, width, height);
        batch.setColor(Color.WHITE);
    }

    private static float fitScale(Texture logo, Rectangle bounds) {
        return Math.min(bounds.width / logo.getWidth(),
                bounds.height / logo.getHeight());
    }
}
