package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GdxGlassBevelTest {

    @Test
    void everyGradientBandSharesItsBoundaryWithTheNextOne() {
        float height = 127.3f;
        int bands = GdxGlassBevel.bandCount(height);

        for (int band = 0; band < bands - 1; band++) {
            float currentTop = GdxGlassBevel.bandBoundary(height,
                    band + 1, bands);
            float nextBottom = GdxGlassBevel.bandBoundary(height,
                    band + 1, bands);
            assertEquals(currentTop, nextBottom);
        }
        assertEquals(0f, GdxGlassBevel.bandBoundary(height, 0, bands));
        assertEquals(height,
                GdxGlassBevel.bandBoundary(height, bands, bands));
    }

    @Test
    void roundedSilhouetteIsSymmetricAndMeetsItsStraightRuns() {
        float height = 80f;
        float radius = 14f;

        assertEquals(radius,
                GdxGlassBevel.roundedInset(0f, height, radius));
        assertEquals(0f,
                GdxGlassBevel.roundedInset(radius, height, radius));
        assertEquals(0f, GdxGlassBevel.roundedInset(
                height - radius, height, radius));
        assertEquals(radius,
                GdxGlassBevel.roundedInset(height, height, radius));
        for (int y = 0; y <= 40; y++) {
            assertEquals(GdxGlassBevel.roundedInset(y, height, radius),
                    GdxGlassBevel.roundedInset(height - y, height, radius));
        }
    }

    @Test
    void highlightAndShadeFadeWithoutChangingTheBaseMaterial() {
        assertTrue(GdxGlassBevel.layerAlpha(0f, 0.2f, 1f, false) > 0f);
        assertEquals(0f,
                GdxGlassBevel.layerAlpha(1f, 0.2f, 1f, false));
        assertEquals(0f,
                GdxGlassBevel.layerAlpha(0f, 0.2f, 1f, true));
        assertEquals(0.2f,
                GdxGlassBevel.layerAlpha(1f, 0.2f, 1f, true));
    }
}
