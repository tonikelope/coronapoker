/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import org.junit.jupiter.api.Test;

final class GdxTextureQualityTest {

    @Test
    void uiArtworkUsesOneBilinearMipLevelWithoutTrilinearBlur() {
        assertTrue(GdxTextureQuality.uiMipMapsEnabled());
        assertEquals(TextureFilter.MipMapLinearNearest,
                GdxTextureQuality.uiMinificationFilter());
        assertEquals(TextureFilter.Linear,
                GdxTextureQuality.uiMagnificationFilter());
    }

    @Test
    void alphaEdgeArtworkClampsItsSubpixelCoverageSamples() {
        assertEquals(TextureWrap.ClampToEdge,
                GdxTextureQuality.alphaEdgeWrap());
        assertEquals(4, GdxAlphaEdgeRenderer.COVERAGE_SAMPLE_COUNT);
        assertEquals(5, GdxAlphaEdgeRenderer.FRAGMENT_SHADER
                .split("texture2D", -1).length - 1,
                "one sharp colour lookup plus four coverage samples");
        assertTrue(GdxAlphaEdgeRenderer.FRAGMENT_SHADER.contains(
                "coverage < 0.999 ? edgeColor : center.rgb"),
                "opaque artwork must keep its unfiltered centre colour");
    }
}
