package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.utils.viewport.Viewport;

/** Physical-pixel placement for native-size rasterized text. */
final class GdxPixelText {

    private static float framePhysicalPixelScaleX = 1f;
    private static float framePhysicalPixelScaleY = 1f;

    private GdxPixelText() {
    }

    /**
     * Captures the logical-to-physical transform once for the current frame.
     * Window and monitor changes are observed on the following frame without
     * making every individual text draw query the graphics backend again.
     */
    static void beginFrame() {
        framePhysicalPixelScaleX = physicalPixelScale(
                Gdx.graphics.getWidth(), Gdx.graphics.getBackBufferWidth());
        framePhysicalPixelScaleY = physicalPixelScale(
                Gdx.graphics.getHeight(), Gdx.graphics.getBackBufferHeight());
    }

    static float snapX(float worldX, Viewport viewport) {
        return snapWorldCoordinate(worldX, viewport.getScreenX(),
                viewport.getScreenWidth(), viewport.getWorldWidth(),
                physicalPixelScaleX());
    }

    static float snapY(float worldY, Viewport viewport) {
        return snapWorldCoordinate(worldY, viewport.getScreenY(),
                viewport.getScreenHeight(), viewport.getWorldHeight(),
                physicalPixelScaleY());
    }

    static float snapWorldCoordinate(float worldCoordinate, float screenOrigin,
            float screenExtent, float worldExtent) {
        return snapWorldCoordinate(worldCoordinate, screenOrigin,
                screenExtent, worldExtent, 1f);
    }

    static float snapWorldCoordinate(float worldCoordinate, float screenOrigin,
            float screenExtent, float worldExtent, float physicalPixelScale) {
        if (!Float.isFinite(worldCoordinate) || !Float.isFinite(screenOrigin)
                || !Float.isFinite(screenExtent) || screenExtent <= 0f
                || !Float.isFinite(worldExtent) || worldExtent <= 0f
                || !Float.isFinite(physicalPixelScale)
                || physicalPixelScale <= 0f) {
            return worldCoordinate;
        }
        float logicalPixelsPerWorldUnit = screenExtent / worldExtent;
        float physicalOrigin = screenOrigin * physicalPixelScale;
        float physicalPixelsPerWorldUnit = logicalPixelsPerWorldUnit
                * physicalPixelScale;
        float physical = physicalOrigin
                + worldCoordinate * physicalPixelsPerWorldUnit;
        return (Math.round(physical) - physicalOrigin)
                / physicalPixelsPerWorldUnit;
    }

    static void draw(BitmapFont font, Batch batch, CharSequence text,
            float x, float y, Viewport viewport) {
        boolean integerPositions = font.usesIntegerPositions();
        font.setUseIntegerPositions(false);
        try {
            font.draw(batch, text, snapX(x, viewport), snapY(y, viewport));
        } finally {
            font.setUseIntegerPositions(integerPositions);
        }
    }

    static void draw(BitmapFont font, Batch batch, GlyphLayout glyph,
            float x, float y, Viewport viewport) {
        boolean integerPositions = font.usesIntegerPositions();
        font.setUseIntegerPositions(false);
        try {
            font.draw(batch, glyph, snapX(x, viewport), snapY(y, viewport));
        } finally {
            font.setUseIntegerPositions(integerPositions);
        }
    }

    static void draw(BitmapFont font, Batch batch, CharSequence text,
            float x, float y, float targetWidth, int horizontalAlignment,
            boolean wrap, Viewport viewport) {
        boolean integerPositions = font.usesIntegerPositions();
        font.setUseIntegerPositions(false);
        try {
            font.draw(batch, text, snapX(x, viewport), snapY(y, viewport),
                    targetWidth, horizontalAlignment, wrap);
        } finally {
            font.setUseIntegerPositions(integerPositions);
        }
    }

    private static float physicalPixelScaleX() {
        return framePhysicalPixelScaleX;
    }

    private static float physicalPixelScaleY() {
        return framePhysicalPixelScaleY;
    }

    static float physicalPixelScale(int logicalExtent, int physicalExtent) {
        return logicalExtent > 0 && physicalExtent > 0
                ? (float) physicalExtent / logicalExtent : 1f;
    }
}
