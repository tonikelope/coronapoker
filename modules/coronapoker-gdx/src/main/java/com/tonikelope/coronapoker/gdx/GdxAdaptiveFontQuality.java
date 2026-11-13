package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
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
        return generateForCharacters(generator, size, border, color,
                borderColor, charactersWith(extraCharacters), profile);
    }

    static String charactersWith(String extraCharacters) {
        return FreeTypeFontGenerator.DEFAULT_CHARS
                + Objects.requireNonNullElse(extraCharacters, "");
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
        FreeTypeFontParameter parameter = parameters(size, rasterSize, border,
                color, borderColor, characters);
        BitmapFont result = generator.generateFont(parameter);
        // Map the native raster density back to logical units.
        result.getData().setScale((float) size / rasterSize);
        return result;
    }

    private static FreeTypeFontParameter parameters(int size, int rasterSize,
            float border, Color color, Color borderColor, String characters) {
        FreeTypeFontParameter parameter = new FreeTypeFontParameter();
        parameter.size = rasterSize;
        parameter.color = color;
        parameter.borderColor = borderColor;
        parameter.borderWidth = border * ((float) rasterSize / size);
        parameter.hinting = FreeTypeFontGenerator.Hinting.Full;
        parameter.kerning = true;
        parameter.genMipMaps = false;
        parameter.minFilter = TextureFilter.Linear;
        parameter.magFilter = TextureFilter.Linear;
        parameter.characters = Objects.requireNonNull(characters,
                "characters");
        return parameter;
    }

    /** Rasterize without GL. The caller transfers the result to the render thread. */
    static PreparedFont prepare(FreeTypeFontGenerator generator, int size,
            float border, Color color, Color borderColor, String characters,
            Profile profile) {
        int rasterSize = rasterSizeFor(size, profile);
        FreeTypeFontParameter parameter = parameters(size, rasterSize, border,
                color, borderColor, characters);
        int limit = FreeTypeFontGenerator.getMaxTextureSize();
        int estimate = MathUtils.nextPowerOfTwo((int) Math.ceil(
                (rasterSize + 2 * parameter.borderWidth + 2)
                * Math.sqrt(characters.length())));
        int pageSize = limit > 0 ? Math.min(limit, estimate) : estimate;
        PixmapPacker packer = new PixmapPacker(pageSize, pageSize,
                Pixmap.Format.RGBA8888, 1, false,
                new PixmapPacker.SkylineStrategy());
        packer.setTransparentColor(border > 0f ? borderColor : color);
        packer.getTransparentColor().a = 0f;
        parameter.packer = packer;
        try {
            return new PreparedFont(generator.generateData(parameter), packer,
                    (float) size / rasterSize);
        } catch (RuntimeException | Error failure) {
            packer.dispose();
            throw failure;
        }
    }

    static final class PreparedFont implements AutoCloseable {

        private final BitmapFont.BitmapFontData data;
        private final PixmapPacker packer;
        private final float scale;
        private final Array<TextureRegion> regions = new Array<>();
        private boolean transferred;

        private PreparedFont(BitmapFont.BitmapFontData data,
                PixmapPacker packer, float scale) {
            this.data = data;
            this.packer = packer;
            this.scale = scale;
        }

        /** Upload at most one atlas page per frame. */
        boolean uploadNextPage() {
            var page = packer.getPages().get(regions.size);
            page.updateTexture(TextureFilter.Linear, TextureFilter.Linear, false);
            regions.add(new TextureRegion(page.getTexture()));
            return regions.size == packer.getPages().size;
        }

        BitmapFont createFont() {
            BitmapFont font = new BitmapFont(data, regions, true);
            font.setOwnsTexture(true);
            font.getData().setScale(scale);
            transferred = true;
            return font;
        }

        @Override
        public void close() {
            packer.dispose();
            if (!transferred) {
                for (var page : packer.getPages()) {
                    if (page.getTexture() != null) page.getTexture().dispose();
                }
            }
        }
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
