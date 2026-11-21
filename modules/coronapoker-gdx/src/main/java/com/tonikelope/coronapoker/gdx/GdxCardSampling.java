/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;

/** Hardware state shared by flat cards, projected artwork and the shuffle viewer. */
final class GdxCardSampling {
    // The 16-tap table filter can reconstruct finer mip levels inside the same pixel.
    static final float TABLE_ARTWORK_LOD_OFFSET = -1.75f;

    private GdxCardSampling() {}

    static boolean smoothArtwork(int mode) {
        return mode == GdxCardSmaaRenderer.RENDERING_SMOOTH || mode == GdxCardSmaaRenderer.RENDERING_SMOOTH_AF;
    }

    static float shaderValue(int mode) {
        if (smoothArtwork(mode)) return 1f;
        return mode == GdxCardSmaaRenderer.RENDERING_BALANCED ? -1f : 0f;
    }

    static void bind(Texture texture, int unit, int mode) {
        boolean trilinear = mode == GdxCardSmaaRenderer.RENDERING_BALANCED
                || mode == GdxCardSmaaRenderer.RENDERING_SMOOTH_AF;
        texture.bind(unit);
        texture.unsafeSetFilter(trilinear ? TextureFilter.MipMapLinearLinear : TextureFilter.MipMapLinearNearest,
                TextureFilter.Linear);
        texture.unsafeSetAnisotropicFilter(trilinear ? 16f : 1f);
    }
}
