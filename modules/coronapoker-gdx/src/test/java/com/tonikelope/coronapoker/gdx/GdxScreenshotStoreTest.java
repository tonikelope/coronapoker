package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.utils.GdxNativesLoader;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GdxScreenshotStoreTest {

    @TempDir
    Path directory;

    @Test
    void savedPngIsOpaqueAndPreservesRenderedRgb() throws Exception {
        GdxNativesLoader.load();
        Path file = directory.resolve("Screenshots")
                .resolve(GdxScreenshotStore.filename(100L));
        byte[] rgba = {
            0x2e, 0x36, 0x44, (byte) 0xc5,
            (byte) 0xe7, 0x14, 0x7f, 0,
            0x00, (byte) 0xa3, (byte) 0xff, 1,
            0x13, 0x38, (byte) 0x91, (byte) 0xff
        };

        GdxScreenshotStore.writeOpaquePng(file, 2, 2, rgba);

        BufferedImage screenshot = ImageIO.read(file.toFile());
        assertEquals(2, screenshot.getWidth());
        assertEquals(2, screenshot.getHeight());
        assertArrayEquals(new int[]{0xff2e3644, 0xffe7147f, 0xff00a3ff, 0xff133891},
                screenshot.getRGB(0, 0, 2, 2, null, 0, 2));
    }

    @Test
    void catalogAcceptsOnlyCoronaPokerPngScreenshots() throws Exception {
        Path first = Files.writeString(directory.resolve(
                "coronapoker_screenshot_100.png"), "first");
        Path second = Files.writeString(directory.resolve(
                "CORONAPOKER_SCREENSHOT_200.PNG"), "second");
        Files.writeString(directory.resolve("unrelated.png"), "other");
        Files.createDirectory(directory.resolve(
                "coronapoker_screenshot_300.png"));

        List<GdxScreenshotStore.Shot> shots =
                GdxScreenshotStore.scan(directory);

        assertEquals(2, shots.size());
        assertTrue(shots.stream().map(GdxScreenshotStore.Shot::file)
                .anyMatch(first::equals));
        assertTrue(shots.stream().map(GdxScreenshotStore.Shot::file)
                .anyMatch(second::equals));
    }

    @Test
    void newestFirstHasStableFilenameTieBreaker() {
        Path old = directory.resolve("coronapoker_screenshot_100.png");
        Path alpha = directory.resolve("coronapoker_screenshot_200.png");
        Path omega = directory.resolve("coronapoker_screenshot_300.png");

        List<GdxScreenshotStore.Shot> sorted =
                GdxScreenshotStore.newestFirst(List.of(
                        new GdxScreenshotStore.Shot(old, 10L),
                        new GdxScreenshotStore.Shot(alpha, 20L),
                        new GdxScreenshotStore.Shot(omega, 20L)));

        assertEquals(List.of(omega, alpha, old), sorted.stream()
                .map(GdxScreenshotStore.Shot::file).toList());
    }

    @Test
    void deletionGuardAcceptsOnlyDirectManagedChildren() {
        Path managed = directory.resolve("coronapoker_screenshot_100.png");
        Path nested = directory.resolve("nested")
                .resolve("coronapoker_screenshot_200.png");

        assertTrue(GdxScreenshotStore.isManaged(directory, managed));
        assertFalse(GdxScreenshotStore.isManaged(directory, nested));
        assertFalse(GdxScreenshotStore.isManaged(directory,
                directory.resolve("other.png")));
    }

    @Test
    void displayTitleIncludesLocalizedDateAndPosition() {
        String title = GdxScreenshotStore.displayTitle(
                new GdxScreenshotStore.Shot(directory.resolve(
                        "coronapoker_screenshot_100.png"), 1_700_000_000_000L),
                1, 3, "es");

        assertTrue(title.contains("( 2 / 3 )"));
        assertTrue(title.length() > "( 2 / 3 )".length());
    }
}
