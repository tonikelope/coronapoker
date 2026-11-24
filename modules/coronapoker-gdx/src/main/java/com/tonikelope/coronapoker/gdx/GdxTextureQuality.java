/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import org.lwjgl.stb.STBImageResize;

/** Sampling policy for artwork that is displayed at several UI sizes. */
final class GdxTextureQuality {

    private GdxTextureQuality() {
    }

    static Texture loadUi(FileHandle file) {
        Texture texture = new Texture(file, uiMipMapsEnabled());
        texture.setFilter(uiMinificationFilter(), uiMagnificationFilter());
        return texture;
    }

    static Texture loadAlphaEdgeUi(FileHandle file) {
        Texture texture = loadUi(file);
        texture.setWrap(alphaEdgeWrap(), alphaEdgeWrap());
        return texture;
    }

    static Texture loadDetailedAlphaEdgeUi(FileHandle file) {
        Texture texture = loadAlphaEdgeUi(file);
        // Blend the mip levels while the perspective changes.
        texture.setFilter(TextureFilter.MipMapLinearLinear, uiMagnificationFilter());
        // LibGDX clamps to the device limit. Inclined badges then retain
        // detail along the less compressed axis of the projection.
        texture.setAnisotropicFilter(16f);
        return texture;
    }

    static Pixmap resizePixmap(Pixmap source, int width, int height) {
        Pixmap resized = new Pixmap(width, height, source.getFormat());
        try {
            int layout = switch (source.getFormat()) {
                case RGB888 -> STBImageResize.STBIR_RGB;
                case RGBA8888 -> STBImageResize.STBIR_RGBA;
                default -> 0;
            };
            if (layout == 0 || STBImageResize.stbir_resize_uint8_linear(source.getPixels(),
                    source.getWidth(), source.getHeight(), 0, resized.getPixels(),
                    width, height, 0, layout) == null) {
                resized.setBlending(Pixmap.Blending.None);
                resized.setFilter(Pixmap.Filter.BiLinear);
                resized.drawPixmap(source, 0, 0, source.getWidth(), source.getHeight(),
                        0, 0, width, height);
            }
            return resized;
        } catch (RuntimeException | Error failure) {
            resized.dispose();
            throw failure;
        }
    }

    static boolean uiMipMapsEnabled() {
        return true;
    }

    static TextureFilter uiMinificationFilter() {
        // Pick the nearest mip level, then filter within that level. This
        // removes aliasing from large position chips rendered near 70 px
        // without the cross-level softness of trilinear filtering.
        return TextureFilter.MipMapLinearNearest;
    }

    static TextureFilter uiMagnificationFilter() {
        return TextureFilter.Linear;
    }

    static TextureWrap alphaEdgeWrap() {
        return TextureWrap.ClampToEdge;
    }
}
