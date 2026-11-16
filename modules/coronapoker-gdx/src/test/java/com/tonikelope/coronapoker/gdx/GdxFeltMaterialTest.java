package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.GdxNativesLoader;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class GdxFeltMaterialTest {
    @BeforeAll
    static void natives() {
        GdxNativesLoader.load();
    }

    @ParameterizedTest
    @ValueSource(strings = {"verde", "rojo", "azul", "negro"})
    void bundledClothRepeatsWithEvenToneAndGrain(String name) throws Exception {
        byte[] bytes;
        try (var stream = getClass().getResourceAsStream("/images/tapete_" + name + ".jpg")) {
            assertNotNull(stream);
            bytes = stream.readAllBytes();
        }
        Pixmap source = new Pixmap(bytes, 0, bytes.length);
        Pixmap tile = null;
        try {
            tile = GdxFeltMaterial.repeatingTile(source);
            assertRepeatEdges(tile);
            assertEvenToneAndGrain(source, tile);
        } finally {
            if (tile != null) tile.dispose();
            source.dispose();
        }
    }

    private static void assertRepeatEdges(Pixmap tile) {
        for (boolean vertical : new boolean[]{false, true}) {
            double interior = 0, boundary = 0;
            int across = vertical ? tile.getWidth() : tile.getHeight();
            int length = vertical ? tile.getHeight() : tile.getWidth();
            for (int j = 0; j < across; j++) {
                boundary += difference(pixel(tile, vertical, 0, j),
                        pixel(tile, vertical, length - 1, j));
                for (int i = 1; i < length; i++) {
                    interior += difference(pixel(tile, vertical, i, j),
                            pixel(tile, vertical, i - 1, j));
                }
            }
            interior /= across * (length - 1.0);
            boundary /= across;
            assertTrue(boundary < interior * 1.18,
                    "Exceptional repeat edge: " + boundary + " vs " + interior);
        }
    }

    private static void assertEvenToneAndGrain(Pixmap source, Pixmap tile) {
        // Sample independently of the correction grid, including patches
        // centred on both repeat boundaries.
        int patch = 40;
        int step = 29;
        int samples = ((tile.getWidth() - 1) / step + 1) * ((tile.getHeight() - 1) / step + 1);
        double[] tone = new double[samples], detail = new double[samples];
        int i = 0;
        for (int y = 0; y < tile.getHeight(); y += step) {
            for (int x = 0; x < tile.getWidth(); x += step) {
                double sum = 0, squared = 0;
                for (int dy = -patch / 2; dy < patch / 2; dy++) {
                    int row = Math.floorMod(y + dy, tile.getHeight());
                    for (int dx = -patch / 2; dx < patch / 2; dx++) {
                        int column = Math.floorMod(x + dx, tile.getWidth());
                        double light = lightness(tile.getPixel(column, row));
                        sum += light;
                        squared += light * light;
                    }
                }
                tone[i] = sum / (patch * patch);
                detail[i] = Math.sqrt(squared / (patch * patch) - tone[i] * tone[i]);
                i++;
            }
        }

        double sourceTone = 0;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                sourceTone += lightness(source.getPixel(x, y));
            }
        }
        sourceTone /= source.getHeight() * source.getWidth();
        assertEquals(sourceTone, Arrays.stream(tone).average().orElseThrow(), 0.5,
                "The correction should preserve the overall shade");

        Arrays.sort(tone);
        Arrays.sort(detail);
        double toneSpread = tone[samples * 95 / 100] - tone[samples * 5 / 100];
        double lowDetail = detail[samples * 5 / 100] / detail[samples / 2];
        assertTrue(toneSpread < 1.5, "Visible changes in local brightness: " + toneSpread);
        assertTrue(lowDetail > 0.89, "Soft bands through the weave: " + lowDetail);
        assertTrue(detail[samples / 2] > 5, "Loss of weave detail");
    }

    private static int pixel(Pixmap pixmap, boolean vertical, int i, int j) {
        return vertical ? pixmap.getPixel(j, i) : pixmap.getPixel(i, j);
    }

    private static double lightness(int pixel) {
        return 0.2126 * (pixel >>> 24) + 0.7152 * (pixel >>> 16 & 255)
                + 0.0722 * (pixel >>> 8 & 255);
    }

    private static double difference(int a, int b) {
        return (Math.abs((a >>> 24) - (b >>> 24))
                + Math.abs((a >>> 16 & 255) - (b >>> 16 & 255))
                + Math.abs((a >>> 8 & 255) - (b >>> 8 & 255))) / 3.0;
    }
}
