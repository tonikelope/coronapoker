package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.viewport.Viewport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Native-size button typography and physical-pixel alignment helpers. */
final class GdxCrispButtonText {

    private static final int FIT_WIDTH = 1;
    private static final int FIT_BOX = 2;
    private static final int FIT_WRAPPED = 3;
    private static final int FIT_CACHE_CAPACITY = 64;
    private static final int GROUP_CACHE_CAPACITY = 8;

    private GdxCrispButtonText() {
    }

    /**
     * Font tiers are built only when the adaptive atlases are rebuilt.  Draw
     * calls therefore do not allocate arrays while looking for a native size.
     */
    static final class FontTiers {

        private final BitmapFont[] fonts;
        /*
         * Direct-mapped, allocation-free render cache. FontTiers is rebuilt
         * whenever physical density changes, so cached decisions can never
         * outlive the atlases whose metrics produced them.
         */
        private final String[] cachedText = new String[FIT_CACHE_CAPACITY];
        private final int[] cachedMode = new int[FIT_CACHE_CAPACITY];
        private final int[] cachedFirst = new int[FIT_CACHE_CAPACITY];
        private final int[] cachedSecond = new int[FIT_CACHE_CAPACITY];
        private final int[] cachedAuxiliary = new int[FIT_CACHE_CAPACITY];
        private final BitmapFont[] cachedFont =
                new BitmapFont[FIT_CACHE_CAPACITY];
        private final String[][] cachedGroupText =
                new String[GROUP_CACHE_CAPACITY][];
        private final int[] cachedGroupWidth =
                new int[GROUP_CACHE_CAPACITY];
        private final int[] cachedGroupHeight =
                new int[GROUP_CACHE_CAPACITY];
        private final BitmapFont[] cachedGroupFont =
                new BitmapFont[GROUP_CACHE_CAPACITY];

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

        /**
         * Builds a semantic family headed by {@code preferred}; only smaller
         * native atlases may follow it. The renderer can therefore fit text
         * without ever magnifying or fractionally shrinking glyph textures.
         */
        static FontTiers atOrBelow(BitmapFont preferred,
                BitmapFont... candidates) {
            Objects.requireNonNull(preferred, "preferred");
            List<BitmapFont> smaller = new ArrayList<>();
            Arrays.stream(candidates)
                    .filter(Objects::nonNull)
                    .filter(candidate -> candidate != preferred)
                    .filter(candidate -> candidate.getCapHeight()
                            < preferred.getCapHeight() - 0.01f)
                    .distinct()
                    .sorted(Comparator.comparingDouble(
                            BitmapFont::getCapHeight).reversed())
                    .forEach(smaller::add);
            BitmapFont[] family = new BitmapFont[smaller.size() + 1];
            family[0] = preferred;
            for (int index = 0; index < smaller.size(); index++) {
                family[index + 1] = smaller.get(index);
            }
            return new FontTiers(family);
        }

        BitmapFont cached(int mode, String text, float first, float second,
                int auxiliary) {
            int firstBits = Float.floatToIntBits(first);
            int secondBits = Float.floatToIntBits(second);
            int slot = fitCacheSlot(mode, text, firstBits, secondBits,
                    auxiliary);
            return cachedMode[slot] == mode
                    && cachedFirst[slot] == firstBits
                    && cachedSecond[slot] == secondBits
                    && cachedAuxiliary[slot] == auxiliary
                    && text.equals(cachedText[slot])
                            ? cachedFont[slot] : null;
        }

        void cache(int mode, String text, float first, float second,
                int auxiliary, BitmapFont font) {
            int firstBits = Float.floatToIntBits(first);
            int secondBits = Float.floatToIntBits(second);
            int slot = fitCacheSlot(mode, text, firstBits, secondBits,
                    auxiliary);
            cachedMode[slot] = mode;
            cachedText[slot] = text;
            cachedFirst[slot] = firstBits;
            cachedSecond[slot] = secondBits;
            cachedAuxiliary[slot] = auxiliary;
            cachedFont[slot] = font;
        }

        BitmapFont cachedGroup(String[] values, float width, float height) {
            int widthBits = Float.floatToIntBits(width);
            int heightBits = Float.floatToIntBits(height);
            int slot = groupCacheSlot(values, widthBits, heightBits);
            return cachedGroupWidth[slot] == widthBits
                    && cachedGroupHeight[slot] == heightBits
                    && Arrays.equals(values, cachedGroupText[slot])
                            ? cachedGroupFont[slot] : null;
        }

        void cacheGroup(String[] values, float width, float height,
                BitmapFont font) {
            int widthBits = Float.floatToIntBits(width);
            int heightBits = Float.floatToIntBits(height);
            int slot = groupCacheSlot(values, widthBits, heightBits);
            cachedGroupText[slot] = values.clone();
            cachedGroupWidth[slot] = widthBits;
            cachedGroupHeight[slot] = heightBits;
            cachedGroupFont[slot] = font;
        }

        private static int fitCacheSlot(int mode, String text, int first,
                int second, int auxiliary) {
            int hash = 31 * (31 * (31 * (31 * mode + text.hashCode())
                    + first) + second) + auxiliary;
            hash ^= hash >>> 16;
            return hash & (FIT_CACHE_CAPACITY - 1);
        }

        private static int groupCacheSlot(String[] values, int width,
                int height) {
            int hash = 31 * (31 * Arrays.hashCode(values) + width) + height;
            hash ^= hash >>> 16;
            return hash & (GROUP_CACHE_CAPACITY - 1);
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
     * Text is never texture-scaled or shortened: the smallest native tier is
     * the hard fallback when no larger atlas fits.
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

    /**
     * Selects the largest pre-rasterized tier which fits both dimensions.
     * No BitmapFont scale is changed and the label is never shortened. The
     * caller must provide a native tier small enough for its layout contract.
     */
    static void resolveWithinBox(GlyphLayout glyph, String value,
            float contentWidth, float contentHeight, FontTiers tiers,
            Result result) {
        Objects.requireNonNull(glyph, "glyph");
        Objects.requireNonNull(tiers, "tiers");
        Objects.requireNonNull(result, "result");
        String text = Objects.requireNonNullElse(value, "");
        float width = Math.max(0f, contentWidth);
        float height = Math.max(0f, contentHeight);
        BitmapFont cached = tiers.cached(FIT_BOX, text, width, height, 0);
        if (cached != null) {
            result.font = cached;
            result.text = text;
            return;
        }
        for (BitmapFont font : tiers.fonts) {
            glyph.setText(font, text);
            if (glyph.width <= width && glyph.height <= height) {
                tiers.cache(FIT_BOX, text, width, height, 0, font);
                result.font = font;
                result.text = text;
                return;
            }
        }
        BitmapFont smallest = tiers.fonts[tiers.fonts.length - 1];
        tiers.cache(FIT_BOX, text, width, height, 0, smallest);
        result.font = smallest;
        result.text = text;
    }

    /**
     * Chooses one native tier shared by a group of labels. This keeps paired
     * captions optically identical without scaling either atlas.
     */
    static BitmapFont resolveAllWithinBox(GlyphLayout glyph, String[] values,
            float contentWidth, float contentHeight, FontTiers tiers) {
        Objects.requireNonNull(glyph, "glyph");
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(tiers, "tiers");
        float width = Math.max(0f, contentWidth);
        float height = Math.max(0f, contentHeight);
        BitmapFont cached = tiers.cachedGroup(values, width, height);
        if (cached != null) return cached;
        for (BitmapFont font : tiers.fonts) {
            boolean fits = true;
            for (String value : values) {
                glyph.setText(font, Objects.requireNonNullElse(value, ""));
                if (glyph.width > width || glyph.height > height) {
                    fits = false;
                    break;
                }
            }
            if (fits) {
                tiers.cacheGroup(values, width, height, font);
                return font;
            }
        }
        BitmapFont smallest = tiers.fonts[tiers.fonts.length - 1];
        tiers.cacheGroup(values, width, height, smallest);
        return smallest;
    }

    /** Native-tier equivalent of wrapped text fitting. */
    static void resolveWrapped(GlyphLayout glyph, String value, Color color,
            float contentWidth, float contentHeight, int alignment,
            FontTiers tiers, Result result) {
        Objects.requireNonNull(glyph, "glyph");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(tiers, "tiers");
        Objects.requireNonNull(result, "result");
        String text = Objects.requireNonNullElse(value, "");
        float width = Math.max(1f, contentWidth);
        float height = Math.max(0f, contentHeight);
        BitmapFont cached = tiers.cached(FIT_WRAPPED, text, width, height,
                alignment);
        if (cached != null) {
            result.font = cached;
            result.text = text;
            return;
        }
        for (BitmapFont font : tiers.fonts) {
            glyph.setText(font, text, color, width, alignment, true);
            if (glyph.height <= height) {
                tiers.cache(FIT_WRAPPED, text, width, height, alignment, font);
                result.font = font;
                result.text = text;
                return;
            }
        }
        BitmapFont smallest = tiers.fonts[tiers.fonts.length - 1];
        tiers.cache(FIT_WRAPPED, text, width, height, alignment, smallest);
        result.font = smallest;
        result.text = text;
    }

    private static void resolveAgainstWidths(GlyphLayout glyph, String value,
            float preferredWidth, float nativeWidth, FontTiers tiers,
            Result result) {
        Objects.requireNonNull(glyph, "glyph");
        Objects.requireNonNull(tiers, "tiers");
        Objects.requireNonNull(result, "result");
        String text = Objects.requireNonNullElse(value, "");
        BitmapFont cached = tiers.cached(FIT_WIDTH, text, preferredWidth,
                nativeWidth, 0);
        if (cached != null) {
            result.font = cached;
            result.text = text;
            return;
        }
        for (BitmapFont font : tiers.fonts) {
            glyph.setText(font, text);
            if (glyph.width <= preferredWidth || glyph.width <= nativeWidth) {
                tiers.cache(FIT_WIDTH, text, preferredWidth, nativeWidth, 0,
                        font);
                result.font = font;
                result.text = text;
                return;
            }
        }
        BitmapFont smallest = tiers.fonts[tiers.fonts.length - 1];
        tiers.cache(FIT_WIDTH, text, preferredWidth, nativeWidth, 0,
                smallest);
        result.font = smallest;
        result.text = text;
    }

    static float snapX(float worldX, Viewport viewport) {
        return GdxPixelText.snapX(worldX, viewport);
    }

    static float snapY(float worldY, Viewport viewport) {
        return GdxPixelText.snapY(worldY, viewport);
    }

    static float snapWorldCoordinate(float worldCoordinate, float screenOrigin,
            float screenExtent, float worldExtent) {
        return GdxPixelText.snapWorldCoordinate(worldCoordinate, screenOrigin,
                screenExtent, worldExtent);
    }

    static float snapWorldCoordinate(float worldCoordinate, float screenOrigin,
            float screenExtent, float worldExtent, float physicalPixelScale) {
        return GdxPixelText.snapWorldCoordinate(worldCoordinate, screenOrigin,
                screenExtent, worldExtent, physicalPixelScale);
    }
}
