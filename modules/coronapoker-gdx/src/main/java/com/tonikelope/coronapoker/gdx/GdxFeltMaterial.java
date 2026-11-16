/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FileTextureData;

/** Shared physical scale and repeat preparation for menu and table cloth. */
final class GdxFeltMaterial {
    private static final float CLOTH_SOURCE_SPAN = 512f;

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
        if (width < mx || height < my) throw new IllegalArgumentException("Cloth image too small");
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
            return tile;
        } catch (RuntimeException | Error failure) {
            tile.dispose();
            throw failure;
        } finally {
            horizontal.dispose();
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
