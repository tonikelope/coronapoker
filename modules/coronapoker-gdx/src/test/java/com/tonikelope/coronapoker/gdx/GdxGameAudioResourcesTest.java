/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.PreferencesService;
import com.tonikelope.coronapoker.core.media.ModMediaCatalog;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GdxGameAudioResourcesTest {

    @Test
    void bundledBotManagerMusicIsAvailable() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/sounds/misc/bots_manager.mp3")) {
            assertNotNull(input, "bot manager music resource");
            assertEquals(4, input.readNBytes(4).length);
        }
    }

    @Test
    void documentedMusicSlotsOverrideFrontendAndTableAudio(
            @TempDir Path temporary) throws Exception {
        Path installation = Files.createDirectories(temporary.resolve("app"));
        Path mod = Files.createDirectories(installation.resolve(
                "mod/sounds/misc"));
        Files.writeString(installation.resolve("mod/mod.xml"),
                "<mod><name>Audio test</name><version>1</version></mod>");
        String[] musicSlots = {
            "background_music.mp3", "waiting_room.mp3", "about_music.mp3",
            "stats_music.mp3", "bots_manager.mp3"
        };
        for (String slot : musicSlots) {
            Files.writeString(mod.resolve(slot), "external music");
        }
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.start();
        try {
            GdxGamePresentationSettings settings =
                    new GdxGamePresentationSettings(preferences,
                            ModMediaCatalog.discover(installation));

            for (String slot : musicSlots) {
                Path music = mod.resolve(slot).toAbsolutePath().normalize();
                assertEquals(music,
                        GdxGameAudioResources.modOverride(settings,
                                "sounds/misc/" + slot).orElseThrow());
            }
            Path background = mod.resolve("background_music.mp3")
                    .toAbsolutePath().normalize();
            assertEquals(background,
                    GdxGameAudioResources.modOverride(settings,
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
