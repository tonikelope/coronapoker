/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.PreferencesService;
import com.tonikelope.coronapoker.core.media.ModMediaCatalog;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GdxGameAudioResourcesTest {

    @Test
    void documentedBackgroundMusicPathOverridesFrontendAndTableAudio(
            @TempDir Path temporary) throws Exception {
        Path installation = Files.createDirectories(temporary.resolve("app"));
        Path mod = Files.createDirectories(installation.resolve(
                "mod/sounds/misc"));
        Files.writeString(installation.resolve("mod/mod.xml"),
                "<mod><name>Audio test</name><version>1</version></mod>");
        Path music = Files.writeString(mod.resolve("background_music.mp3"),
                "external music").toAbsolutePath().normalize();
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.start();
        try {
            GdxGamePresentationSettings settings =
                    new GdxGamePresentationSettings(preferences,
                            ModMediaCatalog.discover(installation));

            assertEquals(music, GdxGameAudioResources.modOverride(settings,
                    "sounds/misc/background_music.mp3").orElseThrow());
            assertEquals(music, GdxGameAudioResources.modOverride(settings,
                    "misc/background_music.mp3").orElseThrow());
            assertTrue(GdxGameAudioResources.modOverride(settings,
                    "../outside.mp3").isEmpty());
        } finally {
            preferences.close();
        }
    }

    @Test
    void frontendAndTableShareTheModAwareResolver() throws Exception {
        Path sourceRoot = Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx");
        String frontend = Files.readString(sourceRoot.resolve(
                "GdxFrontendScreen.java"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        String table = Files.readString(sourceRoot.resolve(
                "CoronaPokerGdxTable.java"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");

        assertTrue(frontend.contains(
                "GdxGameAudioResources.resolve(\n"
                + "                presentationSettings, path)"));
        assertTrue(table.contains(
                "return GdxGameAudioResources.resolve("
                + "presentationSettings, resource);"));
        assertFalse(frontend.contains(
                "newMusic(Gdx.files.internal(path))"));
    }
}
