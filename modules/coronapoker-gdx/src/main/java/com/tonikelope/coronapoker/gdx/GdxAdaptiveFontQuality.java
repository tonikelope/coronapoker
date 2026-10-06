package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;

/**
 * Keeps FreeType atlases matched to the physical framebuffer density.
 *
 * <p>The GDX scenes use a stable 1920x1080 design space. Generating every
 * glyph at a fixed 2x resolution made a 720p display minify text by roughly
 * 3:1, while large display fonts were magnified on 1440p and 4K screens. Both
 * paths lose definition. This profile keeps a bounded, predictable amount of
 * supersampling at every supported output density without changing font
 * families, logical sizes or layout metrics.</p>
 */
final class GdxAdaptiveFontQuality {

    static final int LARGE_FONT_THRESHOLD = 76;
    private static final float SCALE_STEP = 0.25f;
    // 1.5x is the current proven 1440p sampling ratio (2x atlas rendered at
    // 1.333 physical pixels per world unit). Preserve that exact reference
    // instead of making already-clean text softer through excess minification.
    private static final float NORMAL_SUPERSAMPLING = 1.50f;
    private static final float LARGE_SUPERSAMPLING = 1.10f;
    private static final float MAX_NORMAL_RASTER_SCALE = 4f;
    private static final float MAX_LARGE_RASTER_SCALE = 3f;

    private GdxAdaptiveFontQuality() {
    }

    static Profile current(float worldWidth, float worldHeight) {
        return forBackBuffer(Gdx.graphics.getBackBufferWidth(),
                Gdx.graphics.getBackBufferHeight(), worldWidth, worldHeight);
    }

    static Profile forBackBuffer(int backBufferWidth, int backBufferHeight,
            float worldWidth, float worldHeight) {
        if (backBufferWidth <= 0 || backBufferHeight <= 0
                || !Float.isFinite(worldWidth) || worldWidth <= 0f
                || !Float.isFinite(worldHeight) || worldHeight <= 0f) {
            return new Profile(2f, 1f);
        }
        float physicalPixelsPerWorldUnit = Math.min(
                backBufferWidth / worldWidth,
                backBufferHeight / worldHeight);
        float normal = quantizeUp(physicalPixelsPerWorldUnit
                * NORMAL_SUPERSAMPLING, 1f, MAX_NORMAL_RASTER_SCALE);
        float large = quantizeUp(physicalPixelsPerWorldUnit
                * LARGE_SUPERSAMPLING, 1f, MAX_LARGE_RASTER_SCALE);
        return new Profile(normal, large);
    }

    private static float quantizeUp(float value, float minimum,
            float maximum) {
        float bounded = Math.max(minimum, Math.min(maximum, value));
        return Math.min(maximum,
                (float) Math.ceil(bounded / SCALE_STEP) * SCALE_STEP);
    }

    static BitmapFont generate(FreeTypeFontGenerator generator, int size,
            float border, Color color, Color borderColor, String extraCharacters,
            Profile profile) {
        float rasterScale = profile.rasterScaleFor(size);
        int rasterSize = Math.max(1, Math.round(size * rasterScale));
        FreeTypeFontParameter parameter = new FreeTypeFontParameter();
        parameter.size = rasterSize;
        parameter.color = color;
        parameter.borderColor = borderColor;
        parameter.borderWidth = border * rasterScale;
        parameter.hinting = FreeTypeFontGenerator.Hinting.Full;
        parameter.kerning = true;
        parameter.genMipMaps = false;
        parameter.minFilter = TextureFilter.Linear;
        parameter.magFilter = TextureFilter.Linear;
        if (extraCharacters != null && !extraCharacters.isEmpty()) {
            parameter.characters = FreeTypeFontGenerator.DEFAULT_CHARS
                    + extraCharacters;
        }
        BitmapFont result = generator.generateFont(parameter);
        // Use the real rounded raster size so logical metrics remain bit-for-bit
        // stable even for quarter-step profiles and odd point sizes.
        result.getData().setScale((float) size / rasterSize);
        return result;
    }

    record Profile(float normalRasterScale, float largeRasterScale) {

        Profile {
            if (!Float.isFinite(normalRasterScale) || normalRasterScale < 1f
                    || !Float.isFinite(largeRasterScale)
                    || largeRasterScale < 1f) {
                throw new IllegalArgumentException("Invalid font quality profile");
            }
        }

        float rasterScaleFor(int logicalSize) {
            return logicalSize <= LARGE_FONT_THRESHOLD
                    ? normalRasterScale : largeRasterScale;
        }
    }
}
