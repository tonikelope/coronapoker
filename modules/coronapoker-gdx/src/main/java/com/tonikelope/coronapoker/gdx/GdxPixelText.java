package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.utils.viewport.Viewport;

/** Physical-pixel placement and scale quantization for rasterized text. */
final class GdxPixelText {

    private static final float IDENTITY_EPSILON = 0.0001f;

    private GdxPixelText() {
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

    /**
     * Quantizes a requested uniform scale to a whole physical-pixel cap
     * height. Scale-down fitting never grows past its layout constraint.
     */
    static float quantizeScaleDown(BitmapFont font, float requestedScale,
            Viewport viewport) {
        return quantizeScale(font, requestedScale, viewport, true);
    }

    /** Quantizes an unconstrained visual scale to the nearest physical pixel. */
    static float quantizeScale(BitmapFont font, float requestedScale,
            Viewport viewport) {
        return quantizeScale(font, requestedScale, viewport, false);
    }

    private static float quantizeScale(BitmapFont font, float requestedScale,
            Viewport viewport, boolean neverExceed) {
        if (!Float.isFinite(requestedScale) || requestedScale <= 0f
                || Math.abs(requestedScale - 1f) <= IDENTITY_EPSILON) {
            return requestedScale;
        }
        float physicalCapHeight = font.getCapHeight()
                * physicalPixelsPerWorldUnitY(viewport);
        return quantizePhysicalHeight(physicalCapHeight, requestedScale,
                neverExceed);
    }

    static float quantizePhysicalHeight(float physicalBaseHeight,
            float requestedScale, boolean neverExceed) {
        if (!Float.isFinite(physicalBaseHeight) || physicalBaseHeight <= 0f
                || !Float.isFinite(requestedScale) || requestedScale <= 0f
                || Math.abs(requestedScale - 1f) <= IDENTITY_EPSILON) {
            return requestedScale;
        }
        float requestedPixels = physicalBaseHeight * requestedScale;
        float wholePixels = neverExceed
                ? (float) Math.floor(requestedPixels + IDENTITY_EPSILON)
                : Math.round(requestedPixels);
        if (wholePixels < 1f) wholePixels = 1f;
        float quantized = wholePixels / physicalBaseHeight;
        return neverExceed ? Math.min(requestedScale, quantized) : quantized;
    }

    static float applyScaleDown(BitmapFont font, float originalScaleX,
            float originalScaleY, float requestedScale, Viewport viewport) {
        float quantized = quantizeScaleDown(font, requestedScale, viewport);
        font.getData().setScale(originalScaleX * quantized,
                originalScaleY * quantized);
        return quantized;
    }

    static float applyScale(BitmapFont font, float originalScaleX,
            float originalScaleY, float requestedScale, Viewport viewport) {
        float quantized = quantizeScale(font, requestedScale, viewport);
        font.getData().setScale(originalScaleX * quantized,
                originalScaleY * quantized);
        return quantized;
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

    private static float physicalPixelsPerWorldUnitY(Viewport viewport) {
        float worldHeight = viewport.getWorldHeight();
        if (!Float.isFinite(worldHeight) || worldHeight <= 0f
                || viewport.getScreenHeight() <= 0) return 1f;
        return viewport.getScreenHeight() / worldHeight
                * physicalPixelScaleY();
    }

    private static float physicalPixelScaleX() {
        int logicalWidth = Gdx.graphics.getWidth();
        return logicalWidth > 0
                ? (float) Gdx.graphics.getBackBufferWidth() / logicalWidth
                : 1f;
    }

    private static float physicalPixelScaleY() {
        int logicalHeight = Gdx.graphics.getHeight();
        return logicalHeight > 0
                ? (float) Gdx.graphics.getBackBufferHeight() / logicalHeight
                : 1f;
    }
}
