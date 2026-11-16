/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FileTextureData;
import com.badlogic.gdx.math.MathUtils;

/** Shared physical scale and repeat preparation for menu and table cloth. */
final class GdxFeltMaterial {
    private static final float CLOTH_SOURCE_SPAN = 512f;
    private static final float WEAVE_SAMPLE_SPAN = 24f;

    private GdxFeltMaterial() { }

    static Texture load(String name) {
        String felt = GdxGamePresentationSettings.normalizedFelt(name);
        FileHandle file = Gdx.files.internal("images/tapete_" + felt + ".jpg");
        Texture texture = "madera".equals(felt)
                ? new Texture(file, true) : new Texture(new ClothData(file));
        texture.setFilter(minificationFilter(), Texture.TextureFilter.Linear);
        texture.setAnisotropicFilter(8f);
        texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        return texture;
    }

    static float tileSpan(Texture texture) {
        if (texture.getTextureData() instanceof ClothData data) {
            // Cropping the overlap must not enlarge the approved weave.
            return CLOTH_SOURCE_SPAN * data.getWidth() / data.sourceWidth();
        }
        return texture.getWidth();
    }

    static Texture.TextureFilter minificationFilter() {
        return Texture.TextureFilter.MipMapLinearLinear;
    }

    /** Managed reloads repeat the same preparation after a GL context loss. */
    private static final class ClothData extends FileTextureData {
        ClothData(FileHandle file) { super(file, null, Pixmap.Format.RGB888, true); }
        int sourceWidth() { return super.getWidth(); }
        @Override public int getWidth() { return super.getWidth() - overlap(super.getWidth()); }
        @Override public int getHeight() { return super.getHeight() - overlap(super.getHeight()); }
        @Override public Pixmap consumePixmap() {
            Pixmap source = super.consumePixmap();
            try { return repeatingTile(source); }
            finally { source.dispose(); }
        }
    }

    private static int overlap(int size) { return Math.min(size / 2, Math.max(2, size / 8)); }

    /**
     * Join an overlapping strip, rather than asking GL_REPEAT to join two
     * unrelated cut edges. At the wrap the samples are neighbours from the
     * original cloth. Smooth endpoint weights keep the join and its slope
     * continuous. This runs once at load, never in the render loop.
     */
    static Pixmap repeatingTile(Pixmap source) {
        int mx = overlap(source.getWidth()), my = overlap(source.getHeight());
        int width = source.getWidth() - mx, height = source.getHeight() - my;
        if (mx < 2 || my < 2) throw new IllegalArgumentException("Cloth image too small");
        Pixmap horizontal = new Pixmap(width, source.getHeight(), Pixmap.Format.RGB888);
        Pixmap tile = new Pixmap(width, height, Pixmap.Format.RGB888);
        try {
            for (int x = 0; x < width; x++) {
                float weight = x < mx ? smooth(x / (float)(mx - 1)) : 1f;
                for (int y = 0; y < source.getHeight(); y++) {
                    int pixel = source.getPixel(x, y);
                    horizontal.drawPixel(x, y, x < mx
                            ? mix(source.getPixel(width + x, y), pixel, weight) : pixel);
                }
            }
            for (int y = 0; y < height; y++) {
                float weight = y < my ? smooth(y / (float)(my - 1)) : 1f;
                for (int x = 0; x < width; x++) {
                    int pixel = horizontal.getPixel(x, y);
                    tile.drawPixel(x, y, y < my
                            ? mix(horizontal.getPixel(x, height + y), pixel, weight) : pixel);
                }
            }
            evenOutWeave(tile, mx, my);
            return tile;
        } catch (RuntimeException | Error failure) {
            tile.dispose();
            throw failure;
        } finally {
            horizontal.dispose();
        }
    }

    /**
     * Remove baked lighting and restore the detail lost in the overlap blend.
     * Interpolate the correction between sample centres, including across the
     * repeat boundary, so it cannot draw a second grid or seam.
     */
    private static void evenOutWeave(Pixmap tile, int overlapX, int overlapY) {
        int width = tile.getWidth(), height = tile.getHeight();
        int columns = Math.max(1, Math.round(width / WEAVE_SAMPLE_SPAN));
        int rows = Math.max(1, Math.round(height / WEAVE_SAMPLE_SPAN));
        ClothSample[][] samples = new ClothSample[rows][columns];
        float red = 0, green = 0, blue = 0, variance = 0;
        int detailSamples = 0;
        for (int row = 0; row < rows; row++) {
            int top = row * height / rows, bottom = (row + 1) * height / rows;
            for (int column = 0; column < columns; column++) {
                int left = column * width / columns, right = (column + 1) * width / columns;
                ClothSample sample = measure(tile, left, top, right, bottom);
                samples[row][column] = sample;
                red += sample.red();
                green += sample.green();
                blue += sample.blue();
                if (left >= overlapX && top >= overlapY) {
                    variance += sample.variance();
                    detailSamples++;
                }
            }
        }
        int sampleCount = rows * columns;
        float baseRed = red / sampleCount;
        float baseGreen = green / sampleCount;
        float baseBlue = blue / sampleCount;
        float baseVariance = variance / Math.max(1, detailSamples);

        ToneCorrection[][] corrections = new ToneCorrection[rows][columns];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                ClothSample local = samples[row][column];
                float gain = MathUtils.clamp((float)Math.sqrt(baseVariance
                        / Math.max(1f, local.variance())), 0.65f, 1.85f);
                corrections[row][column] = new ToneCorrection(gain, baseRed - gain * local.red(),
                        baseGreen - gain * local.green(), baseBlue - gain * local.blue());
            }
        }

        ToneCorrection[] scanline = new ToneCorrection[columns];
        for (int y = 0; y < height; y++) {
            float gridY = (y + 0.5f) * rows / height - 0.5f;
            int row = MathUtils.floor(gridY);
            ToneCorrection[] top = corrections[Math.floorMod(row, rows)];
            ToneCorrection[] bottom = corrections[(row + 1) % rows];
            float ty = gridY - row;
            for (int column = 0; column < columns; column++) {
                scanline[column] = top[column].interpolate(bottom[column], ty);
            }
            for (int x = 0; x < width; x++) {
                float gridX = (x + 0.5f) * columns / width - 0.5f;
                int column = MathUtils.floor(gridX);
                ToneCorrection left = scanline[Math.floorMod(column, columns)];
                ToneCorrection right = scanline[(column + 1) % columns];
                float tx = gridX - column;
                float gain = MathUtils.lerp(left.gain(), right.gain(), tx);
                int pixel = tile.getPixel(x, y);
                int r = tone(pixel >>> 24, gain, MathUtils.lerp(left.red(), right.red(), tx));
                int g = tone(pixel >>> 16 & 255, gain, MathUtils.lerp(left.green(), right.green(), tx));
                int b = tone(pixel >>> 8 & 255, gain, MathUtils.lerp(left.blue(), right.blue(), tx));
                tile.drawPixel(x, y, r << 24 | g << 16 | b << 8 | 255);
            }
        }
    }

    private static ClothSample measure(Pixmap tile, int left, int top, int right, int bottom) {
        int red = 0, green = 0, blue = 0;
        double lightSquared = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                int pixel = tile.getPixel(x, y);
                int r = pixel >>> 24, g = pixel >>> 16 & 255, b = pixel >>> 8 & 255;
                red += r;
                green += g;
                blue += b;
                float light = luma(r, g, b);
                lightSquared += light * light;
            }
        }
        float count = (right - left) * (bottom - top);
        float r = red / count, g = green / count, b = blue / count;
        float mean = luma(r, g, b);
        return new ClothSample(r, g, b, (float)(lightSquared / count) - mean * mean);
    }

    private static float luma(float r, float g, float b) {
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    private static int tone(int value, float gain, float offset) {
        return MathUtils.clamp(Math.round(value * gain + offset), 0, 255);
    }

    private record ClothSample(float red, float green, float blue, float variance) { }

    private record ToneCorrection(float gain, float red, float green, float blue) {
        ToneCorrection interpolate(ToneCorrection other, float t) {
            return new ToneCorrection(MathUtils.lerp(gain, other.gain, t),
                    MathUtils.lerp(red, other.red, t), MathUtils.lerp(green, other.green, t),
                    MathUtils.lerp(blue, other.blue, t));
        }
    }

    private static float smooth(float t) { return t*t*t*(t*(t*6f-15f)+10f); }

    private static int mix(int a, int b, float t) {
        int r = Math.round((a >>> 24) + ((b >>> 24) - (a >>> 24)) * t);
        int g = Math.round((a >>> 16 & 255) + ((b >>> 16 & 255) - (a >>> 16 & 255)) * t);
        int blue = Math.round((a >>> 8 & 255) + ((b >>> 8 & 255) - (a >>> 8 & 255)) * t);
        return r << 24 | g << 16 | blue << 8 | 255;
    }
}
