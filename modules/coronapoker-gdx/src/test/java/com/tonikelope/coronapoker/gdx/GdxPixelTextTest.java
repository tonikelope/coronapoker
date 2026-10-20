package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

final class GdxPixelTextTest {

    @Test
    void logicalCoordinatesSnapToPhysicalPixels() {
        assertEquals(10f, GdxPixelText.snapWorldCoordinate(
                10.3f, 0f, 100f, 100f), 0.0001f);
        assertEquals(10.5f, GdxPixelText.snapWorldCoordinate(
                10.3f, 0f, 100f, 100f, 2f), 0.0001f);
    }

    @Test
    void invalidPhysicalMetricsFailSoftWithoutMovingTheCoordinate() {
        assertEquals(10.3f, GdxPixelText.snapWorldCoordinate(
                10.3f, 0f, 0f, 100f, 2f), 0.0001f);
        assertEquals(10.3f, GdxPixelText.snapWorldCoordinate(
                10.3f, 0f, 100f, Float.NaN, 2f), 0.0001f);
    }

    @Test
    void physicalScaleIsResolvedOnceFromValidFrameDimensions() {
        assertEquals(2f, GdxPixelText.physicalPixelScale(100, 200), 0.0001f);
        assertEquals(1.5f, GdxPixelText.physicalPixelScale(200, 300),
                0.0001f);
        assertEquals(1f, GdxPixelText.physicalPixelScale(0, 200), 0.0001f);
        assertEquals(1f, GdxPixelText.physicalPixelScale(100, 0), 0.0001f);
    }

    @Test
    void frontendAndTableCapturePhysicalScaleAtFrameBoundary()
            throws IOException {
        Path source = Path.of("src", "main", "java", "com", "tonikelope",
                "coronapoker", "gdx");
        String frontend = Files.readString(source.resolve(
                "GdxFrontendScreen.java"));
        String table = Files.readString(source.resolve(
                "CoronaPokerGdxTable.java"));

        assertTrueBefore(frontend, "public void render() {",
                "GdxPixelText.beginFrame();", "refreshFontQualityIfDue();");
        assertTrueBefore(table, "public void render() {",
                "GdxPixelText.beginFrame();", "refreshFontQualityIfDue();");
    }

    @Test
    void everyGdxRendererUsesPhysicalPixelCompositionWithoutRasterScaling()
            throws IOException {
        Path source = Path.of("src", "main", "java", "com", "tonikelope",
                "coronapoker", "gdx");
        List<Path> javaSources;
        try (var paths = Files.walk(source)) {
            javaSources = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .endsWith(".java"))
                    .toList();
        }
        for (Path file : javaSources) {
            String name = file.getFileName().toString();
            String contents = Files.readString(file);
            if (!name.equals("GdxPixelText.java")) {
                assertFalse(contents.contains(".draw(batch"), name);
            }
            assertFalse(contents.contains("GdxPixelText.applyScale"), name);
            if (!name.equals("GdxAdaptiveFontQuality.java")) {
                assertFalse(contents.contains("getData().setScale"), name);
            }
        }
    }

    @Test
    void nativeFittingNeverAbbreviatesGameLabels() throws IOException {
        Path source = Path.of("src", "main", "java", "com", "tonikelope",
                "coronapoker", "gdx");
        String fitting = Files.readString(
                source.resolve("GdxCrispButtonText.java"));
        String frameRate = Files.readString(
                source.resolve("GdxFrameRateOverlayStyle.java"));
        assertFalse(fitting.contains("ellipsize"));
        assertFalse(fitting.contains("\"...\""));
        assertFalse(frameRate.contains("ellipsize"));
        assertFalse(frameRate.contains("\"...\""));
    }

    private static void assertTrueBefore(String source, String scope,
            String first, String second) {
        int start = source.indexOf(scope);
        int firstIndex = source.indexOf(first, start);
        int secondIndex = source.indexOf(second, start);
        org.junit.jupiter.api.Assertions.assertTrue(start >= 0
                && firstIndex > start && secondIndex > firstIndex);
    }
}
