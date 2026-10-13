/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.Texture.TextureFilter;
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
}
