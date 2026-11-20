/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;

/** Hardware state shared by flat cards, projected artwork and the shuffle viewer. */
final class GdxCardSampling {
    private GdxCardSampling() {}

    static float shaderValue(int mode) {
        return switch (mode) {
            case GdxCardSmaaRenderer.RENDERING_BALANCED -> -1f;
            case GdxCardSmaaRenderer.RENDERING_SMOOTH -> 1f;
            default -> 0f;
        };
    }

    static void bind(Texture texture, int unit, int mode) {
        boolean balanced = mode == GdxCardSmaaRenderer.RENDERING_BALANCED;
        texture.bind(unit);
        texture.unsafeSetFilter(balanced ? TextureFilter.MipMapLinearLinear : TextureFilter.MipMapLinearNearest,
                TextureFilter.Linear);
        texture.unsafeSetAnisotropicFilter(balanced ? 16f : 1f);
    }
}
