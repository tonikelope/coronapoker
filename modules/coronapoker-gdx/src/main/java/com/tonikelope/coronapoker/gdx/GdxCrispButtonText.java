package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.utils.viewport.Viewport;
import java.util.Arrays;
import java.util.Objects;

/** Native-size button typography and physical-pixel alignment helpers. */
final class GdxCrispButtonText {

    private GdxCrispButtonText() {
    }

    /**
     * Font tiers are built only when the adaptive atlases are rebuilt.  Draw
     * calls therefore do not allocate arrays while looking for a native size.
     */
    static final class FontTiers {

        private final BitmapFont[] fonts;

        FontTiers(BitmapFont... candidates) {
            Objects.requireNonNull(candidates, "candidates");
            fonts = Arrays.stream(candidates)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toArray(BitmapFont[]::new);
            if (fonts.length == 0) {
                throw new IllegalArgumentException("At least one font is required");
            }
        }
    }

    /** Mutable scratch result, owned by one renderer and reused every frame. */
    static final class Result {

        private BitmapFont font;
        private String text = "";

        BitmapFont font() {
            return font;
        }

        String text() {
            return text;
        }
    }

    /**
     * Selects a pre-rasterized tier at scale 1.  The preferred safe area may
     * relax to its hard minimum before the next smaller atlas is selected.
     * If even the smallest native tier cannot fit, the label is ellipsized;
     * it is never texture-scaled to a fractional size.
     */
    static void resolve(GlyphLayout glyph, String value, float buttonWidth,
            FontTiers tiers, Result result) {
        float preferredWidth = GdxUiButtonStyle.labelContentWidth(buttonWidth);
        float nativeWidth = GdxUiButtonStyle.nativeLabelContentWidth(buttonWidth);
        resolveAgainstWidths(glyph, value, preferredWidth, nativeWidth,
                tiers, result);
    }

    /** Uses a content box whose icon and outer padding are already removed. */
    static void resolveWithinContentWidth(GlyphLayout glyph, String value,
            float contentWidth, FontTiers tiers, Result result) {
        float available = Math.max(0f, contentWidth);
        resolveAgainstWidths(glyph, value, available, available,
                tiers, result);
    }

    private static void resolveAgainstWidths(GlyphLayout glyph, String value,
            float preferredWidth, float nativeWidth, FontTiers tiers,
            Result result) {
        Objects.requireNonNull(glyph, "glyph");
        Objects.requireNonNull(tiers, "tiers");
        Objects.requireNonNull(result, "result");
        String text = Objects.requireNonNullElse(value, "");
        for (BitmapFont font : tiers.fonts) {
            glyph.setText(font, text);
            if (glyph.width <= preferredWidth || glyph.width <= nativeWidth) {
                result.font = font;
                result.text = text;
                return;
            }
        }
        BitmapFont smallest = tiers.fonts[tiers.fonts.length - 1];
        result.font = smallest;
        result.text = ellipsize(glyph, smallest, text, nativeWidth);
    }

    private static String ellipsize(GlyphLayout glyph, BitmapFont font,
            String value, float maxWidth) {
        glyph.setText(font, value);
        if (glyph.width <= maxWidth) return value;
        // McLaren does not expose a visible ellipsis glyph on every generated
        // atlas. Three ASCII stops guarantee that an exceptional truncation
        // is visible instead of looking like a silently clipped label.
        String suffix = "...";
        glyph.setText(font, suffix);
        if (glyph.width > maxWidth) return "";
        int end = value.length();
        while (end > 0) {
            end = value.offsetByCodePoints(end, -1);
            String candidate = value.substring(0, end).stripTrailing() + suffix;
            glyph.setText(font, candidate);
            if (glyph.width <= maxWidth) return candidate;
        }
        return suffix;
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
