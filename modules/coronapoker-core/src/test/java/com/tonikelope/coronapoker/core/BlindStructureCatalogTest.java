package com.tonikelope.coronapoker.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

final class BlindStructureCatalogTest {

    @Test
    void readsTheClassicOrderedRegistryAndSkipsBrokenEntries() {
        Properties properties = new Properties();
        properties.setProperty("blind_structures.count", "5");
        properties.setProperty("blind_structure.0.name", "Turbo");
        properties.setProperty("blind_structure.0.levels",
                "0.10/0.20,0.20/0.40");
        properties.setProperty("blind_structure.1.name", "Broken");
        properties.setProperty("blind_structure.1.levels", "0.10/nope");
        properties.setProperty("blind_structure.2.name", "Turbo");
        properties.setProperty("blind_structure.2.levels", "1/2,2/4");
        properties.setProperty("blind_structure.3.name", "Deep");
        properties.setProperty("blind_structure.3.levels", "0.25/0.50,1/2");

        List<BlindStructureCatalog.Entry> entries =
                BlindStructureCatalog.read(properties);

        assertEquals(List.of("Turbo", "Deep"), entries.stream()
                .map(BlindStructureCatalog.Entry::name).toList());
        assertEquals(0.25d, entries.get(1).levels().get(0).smallBlind());
        assertEquals(2d, entries.get(1).levels().get(1).bigBlind());
    }

    @Test
    void malformedCountProducesAnEmptyCatalog() {
        Properties properties = new Properties();
        properties.setProperty("blind_structures.count", "invalid");

        assertEquals(List.of(), BlindStructureCatalog.read(properties));
    }

    @Test
    void writesClassicFormatAndRemovesStaleRegistryKeys() {
        Properties properties = new Properties();
        properties.setProperty("unrelated", "kept");
        properties.setProperty("blind_structures.count", "3");
        properties.setProperty("blind_structure.2.name", "stale");
        properties.setProperty("blind_structure.2.levels", "1/2");

        BlindStructureCatalog.writeTo(properties, List.of(
                entry("Turbo", 0.1, 0.2, 0.2, 0.4),
                entry("Deep", 1, 2, 2, 4)));

        assertEquals("2", properties.getProperty("blind_structures.count"));
        assertEquals("Turbo",
                properties.getProperty("blind_structure.0.name"));
        assertEquals("0.1/0.2,0.2/0.4",
                properties.getProperty("blind_structure.0.levels"));
        assertEquals("1/2,2/4",
                properties.getProperty("blind_structure.1.levels"));
        assertEquals(null,
                properties.getProperty("blind_structure.2.name"));
        assertEquals("kept", properties.getProperty("unrelated"));
        assertEquals(List.of("Turbo", "Deep"),
                BlindStructureCatalog.read(properties).stream()
                        .map(BlindStructureCatalog.Entry::name).toList());
    }

    @Test
    void rejectsInvalidCatalogWithoutMutatingProperties() {
        Properties properties = new Properties();
        properties.setProperty("blind_structures.count", "1");
        properties.setProperty("blind_structure.0.name", "Original");
        properties.setProperty("blind_structure.0.levels", "1/2");
        Properties before = new Properties();
        before.putAll(properties);

        assertThrows(IllegalArgumentException.class,
                () -> BlindStructureCatalog.writeTo(properties, List.of(
                        entry("Duplicada", 1, 2),
                        entry("Duplicada", 2, 4))));

        assertEquals(before, properties);
    }

    @Test
    void validatesNamesWithTheGoldLengthAndControlCharacterRules() {
        assertTrue(BlindStructureCatalog.isValidName("Casa"));
        assertTrue(BlindStructureCatalog.isValidName("  Turbo 6-max  "));
        assertFalse(BlindStructureCatalog.isValidName(null));
        assertFalse(BlindStructureCatalog.isValidName(""));
        assertFalse(BlindStructureCatalog.isValidName("   "));
        assertFalse(BlindStructureCatalog.isValidName("bad\nname"));
        assertFalse(BlindStructureCatalog.isValidName(
                "x".repeat(BlindStructureCatalog.MAX_NAME_LENGTH + 1)));
    }

    @Test
    void roundTripsFractionalWholeAndLargeBlindValues() {
        Properties properties = new Properties();
        BlindStructureCatalog.writeTo(properties, List.of(
                entry("Mixed", 0.05, 0.10, 0.5, 1, 1_000, 2_000)));

        assertEquals("0.05/0.1,0.5/1,1000/2000",
                properties.getProperty("blind_structure.0.levels"));
        BlindStructureCatalog.Entry restored =
                BlindStructureCatalog.read(properties).get(0);
        assertEquals(List.of(
                new BlindStructureCatalog.BlindLevel(0.05, 0.10),
                new BlindStructureCatalog.BlindLevel(0.5, 1),
                new BlindStructureCatalog.BlindLevel(1_000, 2_000)),
                restored.levels());
    }

    @Test
    void clampsAbsurdCountsAndKeepsTheFirstDuplicate() {
        Properties properties = new Properties();
        properties.setProperty(BlindStructureCatalog.COUNT_KEY, "100000");
        properties.setProperty(BlindStructureCatalog.PREFIX + "0.name", "Casa");
        properties.setProperty(BlindStructureCatalog.PREFIX + "0.levels", "25/50");
        properties.setProperty(BlindStructureCatalog.PREFIX + "1.name", "Casa");
        properties.setProperty(BlindStructureCatalog.PREFIX + "1.levels", "50/100");

        List<BlindStructureCatalog.Entry> entries =
                BlindStructureCatalog.read(properties);

        assertEquals(1, entries.size());
        assertEquals("Casa", entries.get(0).name());
        assertEquals(25, entries.get(0).levels().get(0).smallBlind());
    }

    private static BlindStructureCatalog.Entry entry(String name,
            double... amounts) {
        java.util.ArrayList<BlindStructureCatalog.BlindLevel> levels =
                new java.util.ArrayList<>();
        for (int index = 0; index < amounts.length; index += 2) {
            levels.add(new BlindStructureCatalog.BlindLevel(
                    amounts[index], amounts[index + 1]));
        }
        return new BlindStructureCatalog.Entry(name, levels);
    }
}
