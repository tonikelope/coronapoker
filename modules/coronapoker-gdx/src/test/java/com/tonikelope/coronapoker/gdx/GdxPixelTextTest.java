package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class GdxPixelTextTest {

    @Test
    void scaleDownQuantizationNeverBreaksTheFitConstraint() {
        assertEquals(19f / 24f,
                GdxPixelText.quantizePhysicalHeight(24f, 0.83f, true),
                0.0001f);
    }

    @Test
    void unconstrainedScaleUsesTheNearestWholePhysicalHeight() {
        assertEquals(20f / 24f,
                GdxPixelText.quantizePhysicalHeight(24f, 0.83f, false),
                0.0001f);
    }

    @Test
    void nativeScaleIsPreservedExactly() {
        assertEquals(1f,
                GdxPixelText.quantizePhysicalHeight(21.4f, 1f, true));
        assertEquals(1f,
                GdxPixelText.quantizePhysicalHeight(21.4f, 1f, false));
    }

    @Test
    void invalidPhysicalMetricsFailSoftWithoutChangingTheRequest() {
        assertEquals(0.72f,
                GdxPixelText.quantizePhysicalHeight(0f, 0.72f, true));
        assertEquals(0.72f,
                GdxPixelText.quantizePhysicalHeight(Float.NaN, 0.72f,
                        false));
    }

    @Test
    void tableAndFrontendCannotBypassPhysicalPixelComposition()
            throws IOException {
        Path source = Path.of("src", "main", "java", "com", "tonikelope",
                "coronapoker", "gdx");
        for (String file : new String[]{"CoronaPokerGdxTable.java",
            "GdxFrontendScreen.java", "GdxFrameRateOverlayStyle.java"}) {
            assertFalse(Files.readString(source.resolve(file))
                    .contains(".draw(batch"), file);
        }
    }
}
