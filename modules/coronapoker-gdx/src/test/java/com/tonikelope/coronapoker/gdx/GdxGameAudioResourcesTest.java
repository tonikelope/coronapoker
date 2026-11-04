/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.PreferencesService;
import com.tonikelope.coronapoker.core.game.GamePresentationSettings;
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
    void settingsIdentifyOnlyAudioActuallySuppliedByTheMod(
            @TempDir Path temporary) throws Exception {
        Path installation = Files.createDirectories(temporary.resolve("app"));
        Path music = Files.createDirectories(installation.resolve(
                "mod/sounds/misc"));
        Path jokes = Files.createDirectories(installation.resolve(
                "mod/sounds/joke/es/winner"));
        Files.writeString(installation.resolve("mod/mod.xml"),
                "<mod><name>Audio test</name><version>1</version></mod>");
        Files.writeString(music.resolve("background_music.mp3"), "music");
        Files.writeString(jokes.resolve("custom.wav"), "joke");
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.start();
        try {
            GdxGamePresentationSettings settings =
                    new GdxGamePresentationSettings(preferences,
                            ModMediaCatalog.discover(installation));
            GdxSettingsContract.ToggleOption background =
                    option("sonido_ascensor");
            GdxSettingsContract.ToggleOption jokesOption =
                    option("sonidos_chorra");
            GdxSettingsContract.ToggleOption shuffle =
                    option("sonido_barajado");

            assertTrue(GdxGameAudioResources.modOverridesSetting(
                    settings, background, "es"));
            assertTrue(GdxGameAudioResources.modOverridesSetting(
                    settings, jokesOption, "es"));
            assertFalse(GdxGameAudioResources.modOverridesSetting(
                    settings, shuffle, "es"));
        } finally {
            preferences.close();
        }
    }

    @Test
    void everyPreviewableAudioSettingDetectsItsExactModResource(
            @TempDir Path temporary) throws Exception {
        Path installation = Files.createDirectories(temporary.resolve("app"));
        Files.createDirectories(installation.resolve("mod"));
        Files.writeString(installation.resolve("mod/mod.xml"),
                "<mod><name>Audio wiring</name><version>1</version></mod>");
        java.util.List<GdxSettingsContract.ToggleOption> previewable =
                GdxSettingsContract.AUDIO_PAGES.stream()
                        .flatMap(page -> page.options().stream())
                        .filter(option -> GdxSettingsContract.audioPreview(
                                option, "es") != null)
                        .toList();
        for (GdxSettingsContract.ToggleOption option : previewable) {
            for (String resource : GdxGameAudioResources.settingResources(
                    option.key(), "es")) {
                Path target = installation.resolve("mod/sounds")
                        .resolve(resource);
                Files.createDirectories(target.getParent());
                Files.writeString(target, option.key());
            }
        }
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.start();
        try {
            GdxGamePresentationSettings settings =
                    new GdxGamePresentationSettings(preferences,
                            ModMediaCatalog.discover(installation));
            previewable.forEach(option -> assertTrue(
                    GdxGameAudioResources.modOverridesSetting(
                            settings, option, "es"), option.key()));
        } finally {
            preferences.close();
        }
    }

    @Test
    void switchSettingDetectsEitherOfItsTwoModSounds(
            @TempDir Path temporary) throws Exception {
        Path installation = Files.createDirectories(temporary.resolve("app"));
        Path misc = Files.createDirectories(installation.resolve(
                "mod/sounds/misc"));
        Files.writeString(installation.resolve("mod/mod.xml"),
                "<mod><name>Switch audio</name><version>1</version></mod>");
        Files.writeString(misc.resolve("button_off.wav"), "off");
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.start();
        try {
            GdxGamePresentationSettings settings =
                    new GdxGamePresentationSettings(preferences,
                            ModMediaCatalog.discover(installation));
            assertTrue(GdxGameAudioResources.modOverridesSetting(settings,
                    option("sonido_interruptor"), "es"));
            assertEquals(java.util.List.of("misc/button_on.wav",
                    "misc/button_off.wav"),
                    GdxGameAudioResources.settingResources(
                            "sonido_interruptor", "es"));
        } finally {
            preferences.close();
        }
    }

    @Test
    void everyFunnySoundSettingDetectsItsOwnModOverride(
            @TempDir Path temporary) throws Exception {
        Path installation = Files.createDirectories(temporary.resolve("app"));
        Path modSounds = Files.createDirectories(installation.resolve(
                "mod/sounds"));
        Files.writeString(installation.resolve("mod/mod.xml"),
                "<mod><name>Funny audio</name><version>1</version></mod>");
        for (String category : java.util.List.of(
                "allin", "fold", "showdown", "winner", "loser")) {
            Path directory = Files.createDirectories(modSounds.resolve(
                    "joke/es/" + category));
            Files.writeString(directory.resolve("custom.wav"), category);
        }
        Path misc = Files.createDirectories(modSounds.resolve("misc"));
        for (String sound : java.util.List.of(
                "raise.wav", "norebuy.wav", "indivisible.wav",
                "lastcard.wav", "badbeat.wav", "youarelucky.wav")) {
            Files.writeString(misc.resolve(sound), sound);
        }
        Path deck = Files.createDirectories(modSounds.resolve(
                "decks/goliat"));
        Files.writeString(deck.resolve("AS.wav"), "special card");
        Path cinematics = Files.createDirectories(installation.resolve(
                "mod/cinematics/allin"));
        Files.writeString(cinematics.resolve("custom.gif"), "cinematic");
        Files.writeString(cinematics.resolve("custom.wav"), "companion");

        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.start();
        try {
            GdxGamePresentationSettings settings =
                    new GdxGamePresentationSettings(preferences,
                            ModMediaCatalog.discover(installation));

            for (String key : java.util.List.of(
                    "sonidos_chorra", "sonido_chorra_allin",
                    "sonido_chorra_fold", "sonido_chorra_showdown",
                    "sonido_chorra_winner", "sonido_chorra_loser",
                    "sonido_chorra_raise", "sonido_chorra_norebuy",
                    "sonido_chorra_indivisible",
                    "sonido_chorra_lastcard", "sonido_chorra_badbeat",
                    "sonido_chorra_lucky",
                    "sonido_chorra_special_card")) {
                assertTrue(GdxGameAudioResources.modOverridesSetting(
                        settings, option(key), "es"), key);
            }
            assertFalse(GdxGameAudioResources.modOverridesSetting(
                    settings, option("sonido_apostar"), "es"));
            assertTrue(GdxGameAudioResources.modOverridesSetting(
                    settings, option("sonido_cinematicas"), "es"));
        } finally {
            preferences.close();
        }
    }

    @Test
    void jokeCatalogueUsesSelectedLanguageAndStableFilenameOrder(
            @TempDir Path temporary) throws Exception {
        Path installation = Files.createDirectories(temporary.resolve("app"));
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.start();
        try {
            preferences.properties().setProperty("lenguaje", "es");
            GdxGamePresentationSettings settings =
                    new GdxGamePresentationSettings(preferences,
                            ModMediaCatalog.discover(installation));

            java.util.List<String> allIn = settings.jokeSoundResources(
                    "allin");
            assertEquals(12, allIn.size());
            assertTrue(allIn.stream().allMatch(resource ->
                    resource.startsWith("joke/es/allin/")));
            assertEquals(allIn.stream().sorted(
                    String.CASE_INSENSITIVE_ORDER).toList(), allIn);

            GdxSettingsContract.TogglePage page =
                    GdxGameAudioResources.jokeSoundsPage(settings, "es",
                            GdxSettingsContract.AUDIO_PAGES.get(3));
            java.util.List<String> childResources = page.options().stream()
                    .map(GdxGameAudioResources::jokeResource)
                    .filter(java.util.Objects::nonNull)
                    .filter(resource -> resource.contains("/allin/"))
                    .toList();
            assertEquals(allIn, childResources);
            assertTrue(page.options().stream()
                    .filter(item -> GdxGameAudioResources.jokeResource(item)
                            != null)
                    .allMatch(item -> item.label().matches(
                            "[A-ZÁÉÍÓÚÑ-]+-\\d+: [^/\\\\]+\\.wav")));
            assertTrue(page.options().stream()
                    .filter(item -> GdxGameAudioResources.jokeResource(item)
                            != null)
                    .allMatch(item -> item.parentKey() != null));
            String firstAllInFilename = GdxGameAudioResources.jokeFilename(
                    allIn.get(0));
            assertEquals("ALL-IN-1: " + firstAllInFilename,
                    page.options().stream()
                    .filter(item -> allIn.get(0).equals(
                            GdxGameAudioResources.jokeResource(item)))
                    .findFirst().orElseThrow().label());
            assertFalse(firstAllInFilename.contains("/"));
            assertEquals("SUBIDA: raise.wav", page.options().stream()
                    .filter(item -> "sonido_chorra_raise".equals(item.key()))
                    .findFirst().orElseThrow().label());
            assertEquals("SIN RECOMPRA: norebuy.wav",
                    page.options().stream()
                            .filter(item -> "sonido_chorra_norebuy".equals(
                                    item.key()))
                            .findFirst().orElseThrow().label());
            java.util.Map<String, String> individualFilenames = java.util.Map.of(
                    "sonido_chorra_raise", "raise.wav",
                    "sonido_chorra_norebuy", "norebuy.wav",
                    "sonido_chorra_indivisible", "indivisible.wav",
                    "sonido_chorra_lastcard", "lastcard.wav",
                    "sonido_chorra_badbeat", "badbeat.wav",
                    "sonido_chorra_lucky", "youarelucky.wav");
            individualFilenames.forEach((key, filename) -> assertTrue(
                    page.options().stream()
                            .filter(item -> key.equals(item.key()))
                            .findFirst().orElseThrow().label()
                            .endsWith(": " + filename), key));
            assertEquals(allIn.get(0),
                    GamePresentationSettings.jokeSoundResource(
                            page.options().stream()
                                    .filter(item -> allIn.get(0).equals(
                                            GdxGameAudioResources.jokeResource(
                                                    item)))
                                    .findFirst().orElseThrow().key()));

            preferences.properties().setProperty("lenguaje", "en");
            assertTrue(settings.jokeSoundResources("allin").isEmpty());
            assertEquals(java.util.List.of("joke/en/winner/applause.wav"),
                    settings.jokeSoundResources("winner"));
        } finally {
            preferences.close();
        }
    }

    @Test
    void everyExternalJokeEntryUsesTheModMediaAndIndicator(
            @TempDir Path temporary) throws Exception {
        Path installation = Files.createDirectories(temporary.resolve("app"));
        Path allIn = Files.createDirectories(installation.resolve(
                "mod/sounds/joke/es/allin"));
        Files.writeString(installation.resolve("mod/mod.xml"),
                "<mod fusion_sounds=\"false\"><name>Audio test</name>"
                + "<version>1</version></mod>");
        Files.writeString(allIn.resolve("primero.wav"), "first");
        Files.writeString(allIn.resolve("segundo.wav"), "second");
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.start();
        try {
            preferences.properties().setProperty("lenguaje", "es");
            GdxGamePresentationSettings settings =
                    new GdxGamePresentationSettings(preferences,
                            ModMediaCatalog.discover(installation));
            GdxSettingsContract.TogglePage page =
                    GdxGameAudioResources.jokeSoundsPage(settings, "es",
                            GdxSettingsContract.AUDIO_PAGES.get(3));
            java.util.List<GdxSettingsContract.ToggleOption> entries =
                    page.options().stream()
                            .filter(item -> {
                                String resource = GdxGameAudioResources
                                        .jokeResource(item);
                                return resource != null
                                        && resource.contains("/allin/");
                            }).toList();

            assertEquals(2, entries.size());
            assertTrue(entries.stream().allMatch(item ->
                    GdxGameAudioResources.modOverridesSetting(
                            settings, item, "es")));
            assertTrue(entries.stream().allMatch(item ->
                    GdxGameAudioResources.modOverride(settings,
                            GdxGameAudioResources.jokeResource(item))
                            .orElseThrow().startsWith(allIn)));
        } finally {
            preferences.close();
        }
    }

    @Test
    void onlyCategoriesActuallyPresentInTheModUseTheModIndicator(
            @TempDir Path temporary) throws Exception {
        Path installation = Files.createDirectories(temporary.resolve("app"));
        Path fold = Files.createDirectories(installation.resolve(
                "mod/sounds/joke/es/fold"));
        Path cinematics = Files.createDirectories(installation.resolve(
                "mod/cinematics/allin"));
        Files.writeString(installation.resolve("mod/mod.xml"),
                "<mod fusion_sounds=\"false\"><name>Partial audio</name>"
                + "<version>1</version></mod>");
        Files.writeString(fold.resolve("nombre-libre.wav"), "fold");
        Files.writeString(cinematics.resolve("scene.gif"), "gif");
        Files.writeString(cinematics.resolve("scene.wav"), "companion");
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.start();
        try {
            preferences.properties().setProperty("lenguaje", "es");
            GdxGamePresentationSettings settings =
                    new GdxGamePresentationSettings(preferences,
                            ModMediaCatalog.discover(installation));
            GdxSettingsContract.TogglePage page =
                    GdxGameAudioResources.jokeSoundsPage(settings, "es",
                            GdxSettingsContract.AUDIO_PAGES.get(3));

            GdxSettingsContract.ToggleOption allInParent = page.options()
                    .stream().filter(item -> "sonido_chorra_allin".equals(
                            item.key())).findFirst().orElseThrow();
            GdxSettingsContract.ToggleOption foldParent = page.options()
                    .stream().filter(item -> "sonido_chorra_fold".equals(
                            item.key())).findFirst().orElseThrow();
            java.util.List<GdxSettingsContract.ToggleOption> allInChildren =
                    categoryEntries(page, "/allin/");
            java.util.List<GdxSettingsContract.ToggleOption> foldChildren =
                    categoryEntries(page, "/fold/");

            assertFalse(GdxGameAudioResources.modOverridesSetting(settings,
                    allInParent, "es"));
            assertTrue(allInChildren.stream().noneMatch(item ->
                    GdxGameAudioResources.modOverridesSetting(settings, item,
                            "es")));
            assertTrue(GdxGameAudioResources.modOverridesSetting(settings,
                    foldParent, "es"));
            assertEquals(1, foldChildren.size());
            assertTrue(foldChildren.stream().allMatch(item ->
                    GdxGameAudioResources.modOverridesSetting(settings, item,
                            "es")));
        } finally {
            preferences.close();
        }
    }

    @Test
    void jokeModStatusReportsTheRealLanguageAndFusionMode(
            @TempDir Path temporary) throws Exception {
        Path exclusiveRoot = Files.createDirectories(
                temporary.resolve("exclusive"));
        Path exclusiveBank = Files.createDirectories(exclusiveRoot.resolve(
                "mod/sounds/joke/es/winner"));
        Files.writeString(exclusiveRoot.resolve("mod/mod.xml"),
                "<mod fusion_sounds=\"false\"><name>Test pack</name>"
                + "<version>2</version></mod>");
        Files.writeString(exclusiveBank.resolve("victoria.wav"), "winner");

        Path fusedRoot = Files.createDirectories(temporary.resolve("fused"));
        Path fusedBank = Files.createDirectories(fusedRoot.resolve(
                "mod/sounds/joke/es/allin"));
        Files.writeString(fusedRoot.resolve("mod/mod.xml"),
                "<mod fusion_sounds=\"true\"><name>Fusion pack</name>"
                + "<version>3</version></mod>");
        Files.writeString(fusedBank.resolve("libre.wav"), "all-in");

        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.start();
        try {
            GdxGamePresentationSettings exclusive =
                    new GdxGamePresentationSettings(preferences,
                            ModMediaCatalog.discover(exclusiveRoot));
            GdxGamePresentationSettings fused =
                    new GdxGamePresentationSettings(preferences,
                            ModMediaCatalog.discover(fusedRoot));

            assertEquals(GdxGameAudioResources.JokeBankMode.MOD_ONLY,
                    GdxGameAudioResources.jokeBanksMode(exclusive, "es"));
            assertTrue(GdxGameAudioResources.jokeModStatus(exclusive, "es",
                    new GdxGameText("es")).contains(
                            "Test pack v2 (modo exclusivo)"));
            assertEquals(GdxGameAudioResources.JokeBankMode.OFFICIAL,
                    GdxGameAudioResources.jokeBanksMode(exclusive, "en"));
            assertTrue(GdxGameAudioResources.jokeModStatus(exclusive, "en",
                    new GdxGameText("en")).isBlank());

            assertEquals(GdxGameAudioResources.JokeBankMode.FUSED,
                    GdxGameAudioResources.jokeBanksMode(fused, "es"));
            assertTrue(GdxGameAudioResources.jokeModStatus(fused, "es",
                    new GdxGameText("es")).contains(
                            "Fusion pack v3 (modo fusión)"));
        } finally {
            preferences.close();
        }
    }

    @Test
    void fusedJokeCatalogueMarksOnlyFilesActuallySuppliedByTheMod(
            @TempDir Path temporary) throws Exception {
        Path installation = Files.createDirectories(temporary.resolve("app"));
        Path allIn = Files.createDirectories(installation.resolve(
                "mod/sounds/joke/es/allin"));
        Files.writeString(installation.resolve("mod/mod.xml"),
                "<mod fusion_sounds=\"true\"><name>Fused audio</name>"
                + "<version>1</version></mod>");
        Files.writeString(allIn.resolve("external.wav"), "external");
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.start();
        try {
            preferences.properties().setProperty("lenguaje", "es");
            GdxGamePresentationSettings settings =
                    new GdxGamePresentationSettings(preferences,
                            ModMediaCatalog.discover(installation));
            GdxSettingsContract.TogglePage page =
                    GdxGameAudioResources.jokeSoundsPage(settings, "es",
                            GdxSettingsContract.AUDIO_PAGES.get(3));
            java.util.List<GdxSettingsContract.ToggleOption> entries =
                    page.options().stream()
                            .filter(item -> {
                                String resource = GdxGameAudioResources
                                        .jokeResource(item);
                                return resource != null
                                        && resource.contains("/allin/");
                            }).toList();
            GdxSettingsContract.ToggleOption external = entries.stream()
                    .filter(item -> GdxGameAudioResources.jokeResource(item)
                            .endsWith("/external.wav"))
                    .findFirst().orElseThrow();

            assertTrue(entries.size() > 1);
            assertTrue(GdxGameAudioResources.modOverridesSetting(settings,
                    external, "es"));
            assertTrue(entries.stream()
                    .filter(item -> item != external)
                    .noneMatch(item -> GdxGameAudioResources
                            .modOverridesSetting(settings, item, "es")));
        } finally {
            preferences.close();
        }
    }

    @Test
    void bothSettingsSurfacesUseCentralModOverridePalette() throws Exception {
        Path sourceRoot = Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx");
        for (String sourceName : java.util.List.of(
                "GdxFrontendScreen.java", "CoronaPokerGdxTable.java")) {
            String source = Files.readString(sourceRoot.resolve(sourceName),
                    StandardCharsets.UTF_8).replace("\r\n", "\n");
            assertTrue(source.contains(
                    "GdxSettingsStyle.MOD_OVERRIDE_RGBA"),
                    sourceName);
            assertTrue(source.contains(
                    "GdxSettingsStyle.MOD_OVERRIDE_DISABLED_RGBA"),
                    sourceName);
            assertTrue(source.contains(
                    "enabled ? MOD_OVERRIDE : MOD_OVERRIDE_DISABLED"),
                    sourceName);
            assertTrue(source.contains(
                    "GdxGameAudioResources.jokeModStatus("),
                    sourceName);
            assertTrue(source.contains("GdxSettingsLayout.statusBand("),
                    sourceName);
            assertTrue(source.contains(
                    "statusBand.scrolledTextCenterY(rows.offset())"),
                    sourceName + " must scroll the notice with its rows");
            assertFalse(source.contains(
                    "firstY + GdxSettingsLayout.ROW_HEIGHT + 14f"),
                    sourceName);
            assertFalse(source.contains(
                    "rowY + GdxSettingsLayout.ROW_HEIGHT + 20f"),
                    sourceName);
        }
        String table = Files.readString(sourceRoot.resolve(
                "CoronaPokerGdxTable.java"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        assertTrue(table.contains("settingsAudioRowsViewport("));
        assertTrue(countOccurrences(table, "settingsAudioRows(") >= 5,
                "table audio shapes, text and input must share row geometry");
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
                + "                presentationSettings, resource)"));
        assertTrue(table.contains(
                "return GdxGameAudioResources.resolve("
                + "presentationSettings, resource);"));
        assertFalse(frontend.contains(
                "newMusic(Gdx.files.internal(path))"));
        assertFalse(frontend.contains(
                "Gdx.files.internal(\"sounds/"));
        assertTrue(frontend.contains(
                "this::loadModAwareSound"));
    }

    private static GdxSettingsContract.ToggleOption option(String key) {
        return GdxSettingsContract.AUDIO_PAGES.stream()
                .flatMap(page -> page.options().stream())
                .filter(option -> key.equals(option.key()))
                .findFirst().orElseThrow();
    }

    private static java.util.List<GdxSettingsContract.ToggleOption>
            categoryEntries(GdxSettingsContract.TogglePage page,
                    String categoryMarker) {
        return page.options().stream().filter(item -> {
            String resource = GdxGameAudioResources.jokeResource(item);
            return resource != null && resource.contains(categoryMarker);
        }).toList();
    }

    private static int countOccurrences(String source, String fragment) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(fragment, index)) >= 0) {
            count++;
            index += fragment.length();
        }
        return count;
    }
}
