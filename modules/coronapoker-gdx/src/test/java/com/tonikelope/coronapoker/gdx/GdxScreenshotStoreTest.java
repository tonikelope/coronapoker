package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GdxScreenshotStoreTest {

    @TempDir
    Path directory;

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
