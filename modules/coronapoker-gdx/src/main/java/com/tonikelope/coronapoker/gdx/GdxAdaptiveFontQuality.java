package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import java.util.Objects;

/**
 * Rasterizes FreeType glyphs at their final physical output density.
 *
 * <p>The GDX scenes use a stable 1920x1080 design space. Extra atlas
 * supersampling forces the GPU to reduce every glyph again at draw time, which
 * can soften strokes or create uneven edges. This profile instead generates
 * each font at the number of physical pixels it will occupy on the current
 * framebuffer. Logical font sizes and layout metrics remain unchanged.</p>
 */
final class GdxAdaptiveFontQuality {

    // Covers practical Windows framebuffers from very small diagnostic
    // windows through 8K without turning this into a fixed resolution table.
    private static final float MIN_PHYSICAL_SCALE = 0.125f;
    private static final float MAX_PHYSICAL_SCALE = 4f;

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
            return new Profile(1f);
        }
        float physicalPixelsPerWorldUnit = Math.min(
                backBufferWidth / worldWidth,
                backBufferHeight / worldHeight);
        return new Profile(Math.max(MIN_PHYSICAL_SCALE,
                Math.min(MAX_PHYSICAL_SCALE, physicalPixelsPerWorldUnit)));
    }

    static int rasterSizeFor(int logicalSize, Profile profile) {
        return Math.max(1, Math.round(logicalSize
                * profile.physicalPixelsPerWorldUnit()));
    }

    static BitmapFont generate(FreeTypeFontGenerator generator, int size,
            float border, Color color, Color borderColor, String extraCharacters,
            Profile profile) {
        String characters = FreeTypeFontGenerator.DEFAULT_CHARS;
        if (extraCharacters != null && !extraCharacters.isEmpty()) {
            characters += extraCharacters;
        }
        return generateForCharacters(generator, size, border, color,
                borderColor, characters, profile);
    }

    /**
     * Generates a native-density atlas with an exact semantic repertoire.
     * Dense size families use this to avoid duplicating unrelated lowercase,
     * card-suit and box-drawing glyphs in every texture.
     */
    static BitmapFont generateForCharacters(FreeTypeFontGenerator generator,
            int size, float border, Color color, Color borderColor,
            String characters, Profile profile) {
        int rasterSize = rasterSizeFor(size, profile);
        float actualRasterScale = (float) rasterSize / size;
        FreeTypeFontParameter parameter = new FreeTypeFontParameter();
        parameter.size = rasterSize;
        parameter.color = color;
        parameter.borderColor = borderColor;
        parameter.borderWidth = border * actualRasterScale;
        parameter.hinting = FreeTypeFontGenerator.Hinting.Full;
        parameter.kerning = true;
        parameter.genMipMaps = false;
        parameter.minFilter = TextureFilter.Linear;
        parameter.magFilter = TextureFilter.Linear;
        parameter.characters = Objects.requireNonNull(characters,
                "characters");
        BitmapFont result = generator.generateFont(parameter);
        // This is coordinate normalization, not post-raster fitting: FreeType
        // has already generated one texel density for the physical framebuffer.
        // The inverse density maps those native texels back to world units so
        // one atlas pixel still lands on one physical output pixel.
        result.getData().setScale((float) size / rasterSize);
        return result;
    }

    record Profile(float physicalPixelsPerWorldUnit) {

        Profile {
            if (!Float.isFinite(physicalPixelsPerWorldUnit)
                    || physicalPixelsPerWorldUnit < MIN_PHYSICAL_SCALE
                    || physicalPixelsPerWorldUnit > MAX_PHYSICAL_SCALE) {
                throw new IllegalArgumentException("Invalid font quality profile");
            }
        }

        float rasterScaleFor(int logicalSize) {
            return physicalPixelsPerWorldUnit;
        }
    }
}
