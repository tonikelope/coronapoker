package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GdxModalBackdropBlurContractTest {

    @Test
    void nativeBackdropUsesWideThirtyThreeTapGaussianWithoutSparseScaling()
            throws Exception {
        String source = Files.readString(source("GdxModalBackdropBlur.java"));

        assertTrue(source.contains("0.06206181"),
                "the normalized Gaussian center weight must remain present");
        assertTrue(source.contains("1.49112519")
                && source.contains("3.47930178")
                && source.contains("5.46750150")
                && source.contains("7.45573747")
                && source.contains("9.44402263")
                && source.contains("11.43236969")
                && source.contains("13.42079112")
                && source.contains("15.40929903"),
                "the optimized 33-tap Gaussian offsets must remain present");
        assertFalse(source.contains("BLUR_RADIUS_SCALE"),
                "native blur must not widen sparse samples into visible echoes");
        assertTrue(source.contains(
                "shader.setUniformf(\"u_direction\", directionX, directionY)"),
                "each pass must sample one native-pixel Gaussian axis");

        double normalizedWeight = 0.06206181d + 2d * (0.12052410d
                + 0.10714751d + 0.08669949d + 0.06385230d
                + 0.04280173d + 0.02611378d + 0.01450108d
                + 0.00732911d);
        assertEquals(1d, normalizedWeight, 0.000001d,
                "the Gaussian kernel must preserve backdrop luminance");
    }

    @Test
    void tableSettingsCannotBypassTheSharedModalBlurBySubpage()
            throws Exception {
        String source = Files.readString(source("CoronaPokerGdxTable.java"));

        assertFalse(source.contains("sharpGraphicsPreview"));
        assertFalse(source.contains("settingsUsesSharpTablePreview"));
        assertTrue(source.contains(
                "return preparationModal || uiLayerUsesBackdropBlur(uiLayer,")
                && source.contains("chatImageMode)"),
                "every settings subpage must use the same modal blur path");
    }

    private static Path source(String fileName) {
        Path direct = Path.of("src/main/java/com/tonikelope/coronapoker/gdx",
                fileName);
        if (Files.exists(direct)) return direct;
        return Path.of("modules/coronapoker-gdx/src/main/java/com/tonikelope/"
                + "coronapoker/gdx", fileName);
    }
}
