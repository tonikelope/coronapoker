package com.tonikelope.coronapoker.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GamePresetCatalogTest {

    @TempDir
    Path temporary;

    @Test
    void roundTripsOrderedEntriesAndRemovesStaleSlots() {
        Properties properties = new Properties();
        properties.setProperty("game_preset.8.name", "stale");
        properties.setProperty("game_preset.8.settings", "old");
        GamePresetCatalog.writeTo(properties, List.of(
                new GamePresetCatalog.Entry("Cash", "SB=0.1"),
                new GamePresetCatalog.Entry("Torneo", "SB=1")));

        LinkedHashMap<String, GamePresetCatalog.Entry> loaded =
                GamePresetCatalog.readFrom(properties);
        assertEquals(List.of("Cash", "Torneo"),
                List.copyOf(loaded.keySet()));
        assertEquals("SB=0.1", loaded.get("Cash").settings());
        assertEquals("2", properties.getProperty(GamePresetCatalog.PROP_COUNT));
        assertEquals(null, properties.getProperty("game_preset.8.name"));
    }

    @Test
    void skipsInvalidAndDuplicateStoredNamesWithoutLosingFollowingEntries() {
        Properties properties = new Properties();
        properties.setProperty(GamePresetCatalog.PROP_COUNT, "4");
        properties.setProperty("game_preset.0.name", "");
        properties.setProperty("game_preset.0.settings", "bad");
        properties.setProperty("game_preset.1.name", "Cash");
        properties.setProperty("game_preset.1.settings", "first");
        properties.setProperty("game_preset.2.name", "Cash");
        properties.setProperty("game_preset.2.settings", "duplicate");
        properties.setProperty("game_preset.3.name", "Torneo");
        properties.setProperty("game_preset.3.settings", "second");

        LinkedHashMap<String, GamePresetCatalog.Entry> loaded =
                GamePresetCatalog.readFrom(properties);
        assertEquals(List.of("Cash", "Torneo"),
                List.copyOf(loaded.keySet()));
        assertEquals("first", loaded.get("Cash").settings());
    }

    @Test
    void normalizesNamesExactlyOnceAtTheSharedBoundary() {
        assertEquals("Mesa", GamePresetCatalog.normalizeName("  Mesa  "));
        assertEquals(40, GamePresetCatalog.normalizeName("x".repeat(50)).length());
        assertThrows(IllegalArgumentException.class,
                () -> GamePresetCatalog.normalizeName("   "));
    }

    @Test
    void catalogueSurvivesTheSameDeferredDiskSaveUsedByGdx() throws Exception {
        Path file = temporary.resolve("coronapoker.properties");
        PreferencesService writer = new PreferencesService(file);
        writer.start();
        GamePresetCatalog.writeTo(writer.properties(), List.of(
                new GamePresetCatalog.Entry("Amigos", "SB=0.1#BG=0.2")));
        writer.saveDeferred();
        writer.close();

        PreferencesService reader = new PreferencesService(file);
        LinkedHashMap<String, GamePresetCatalog.Entry> restored =
                GamePresetCatalog.readFrom(reader.properties());
        assertEquals(List.of("Amigos"), List.copyOf(restored.keySet()));
        assertEquals("SB=0.1#BG=0.2", restored.get("Amigos").settings());
        reader.close();
    }

    @Test
    void explicitProfileSaveIsDurableBeforeTheUiReportsSuccess()
            throws Exception {
        Path file = temporary.resolve("immediate.properties");
        PreferencesService writer = new PreferencesService(file);
        writer.start();
        NewGameTableDraft draft = new NewGameTableDraft();
        draft.setAnte(true);
        draft.setStraddle(true);
        draft.setRunItTwice(true);
        NewGameTableDraft.Settings expected = draft.snapshot();
        GamePresetCatalog.writeTo(writer.properties(), List.of(
                new GamePresetCatalog.Entry("Viernes",
                        expected.serializeForWire())));

        writer.save();

        PreferencesService concurrentReader = new PreferencesService(file);
        GamePresetCatalog.Entry restored = GamePresetCatalog.readFrom(
                concurrentReader.properties()).get("Viernes");
        assertEquals(expected, NewGameTableDraft.Settings.parseWire(
                restored.settings()));
        concurrentReader.close();
        writer.close();
    }
}
