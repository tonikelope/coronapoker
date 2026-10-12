package com.tonikelope.coronapoker.gdx;

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
        Objects.requireNonNull(glyph, "glyph");
        Objects.requireNonNull(tiers, "tiers");
        Objects.requireNonNull(result, "result");
        String text = Objects.requireNonNullElse(value, "");
        float preferredWidth = GdxUiButtonStyle.labelContentWidth(buttonWidth);
        float nativeWidth = GdxUiButtonStyle.nativeLabelContentWidth(buttonWidth);
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
                viewport.getScreenWidth(), viewport.getWorldWidth());
    }

    static float snapY(float worldY, Viewport viewport) {
        return snapWorldCoordinate(worldY, viewport.getScreenY(),
                viewport.getScreenHeight(), viewport.getWorldHeight());
    }

    static float snapWorldCoordinate(float worldCoordinate, float screenOrigin,
            float screenExtent, float worldExtent) {
        if (!Float.isFinite(worldCoordinate) || !Float.isFinite(screenOrigin)
                || !Float.isFinite(screenExtent) || screenExtent <= 0f
                || !Float.isFinite(worldExtent) || worldExtent <= 0f) {
            return worldCoordinate;
        }
        float pixelsPerWorldUnit = screenExtent / worldExtent;
        float physical = screenOrigin + worldCoordinate * pixelsPerWorldUnit;
        return (Math.round(physical) - screenOrigin) / pixelsPerWorldUnit;
    }
}
