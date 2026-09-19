package com.tonikelope.coronapoker.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.media.ModMediaCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ModAwareGameCinematicAssetsTest {

    @Test
    void replacementModeExposesOnlyExternalCinematicsAndCompanionAudio(
            @TempDir Path installation) throws Exception {
        Path folder = Files.createDirectories(installation.resolve(
                "mod/cinematics/allin"));
        Files.writeString(installation.resolve("mod/mod.xml"), """
                <mod fusion_cinematics="false">
                  <name>Test</name><version>1</version>
                </mod>
                """);
        Files.write(folder.resolve("custom.gif"), new byte[]{1});
        Files.write(folder.resolve("custom.wav"), new byte[]{1});
        GameCinematicAssets bundled = bundledStub();

        ModAwareGameCinematicAssets assets = new ModAwareGameCinematicAssets(
                ModMediaCatalog.discover(installation), bundled,
                List.of("rounders.gif"));

        assertEquals(List.of("custom.gif"), assets.filenames());
        assertTrue(assets.hasCinematic("custom.gif"));
        assertTrue(assets.hasCompanionAudio("custom.gif"));
        assertFalse(assets.hasCinematic("rounders.gif"));
    }

    @Test
    void fusionModeAppendsBundledCatalogWithoutDuplicatingNames(
            @TempDir Path installation) throws Exception {
        Path folder = Files.createDirectories(installation.resolve(
                "mod/cinematics/allin"));
        Files.writeString(installation.resolve("mod/mod.xml"), """
                <mod fusion_cinematics="true">
                  <name>Test</name><version>1</version>
                </mod>
                """);
        Files.write(folder.resolve("custom.gif"), new byte[]{1});

        ModAwareGameCinematicAssets assets = new ModAwareGameCinematicAssets(
                ModMediaCatalog.discover(installation), bundledStub(),
                List.of("rounders.gif"));

        assertEquals(List.of("custom.gif", "rounders.gif"),
                assets.filenames());
        assertTrue(assets.hasCinematic("rounders.gif"));
    }

    private static GameCinematicAssets bundledStub() {
        return new GameCinematicAssets() {
            @Override public long durationMillis(String filename) {
                return "rounders.gif".equals(filename) ? 1_000L : 0L;
            }
            @Override public boolean hasCinematic(String filename) {
                return "rounders.gif".equals(filename);
            }
            @Override public boolean hasCompanionAudio(String filename) {
                return false;
            }
        };
    }
}
