/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;

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
