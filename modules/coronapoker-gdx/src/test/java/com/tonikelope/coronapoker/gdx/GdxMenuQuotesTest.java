package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GdxMenuQuotesTest {

    @Test
    void usesSwingFormattingAndRotatesEveryEightSeconds() {
        GdxMenuQuotes quotes = new GdxMenuQuotes(new SecureRandom(new byte[]{1}),
                List.of("Primera#Autor", "Segunda#Otra"),
                List.of("First#Author", "Second#Other"));

        String first = quotes.update("es", 0f);
        assertEquals(first, quotes.update("es", 7.99f));
        assertNotEquals(first, quotes.update("es", 0.02f));
        String english = quotes.update("en", 0f);
        org.junit.jupiter.api.Assertions.assertTrue(
                english.equals("\"First\" (Author)")
                || english.equals("\"Second\" (Other)"));
    }

    @Test
    void formatsQuoteAndAuthorLikeTheHistoricalMenu() {
        assertEquals("\"El póker\" (Autor)",
                GdxMenuQuotes.format(" El póker # Autor "));
        assertEquals("\"El póker\" (Autor)",
                GdxMenuQuotes.format(" \"El póker\" # Autor "));
        assertEquals("\"El póker\" (Autor)",
                GdxMenuQuotes.format(" «El póker» # Autor "));
    }

    @Test
    void bundledCatalogsStayAlignedAndStructurallyClean() throws Exception {
        List<String> spanish = resource("/quotes_ES.txt");
        List<String> english = resource("/quotes_EN.txt");

        assertEquals(english.size(), spanish.size());
        assertFalse(spanish.isEmpty());
        for (int index = 0; index < spanish.size(); index++) {
            assertCleanEntry(spanish.get(index), "ES", index);
            assertCleanEntry(english.get(index), "EN", index);
        }
        assertTrue(spanish.stream().anyMatch(line -> line.endsWith(
                "#Mike Sexton")));
        assertTrue(spanish.stream().anyMatch(line -> line.endsWith(
                "#David A. Daniel")));
        assertTrue(spanish.stream().anyMatch(line -> line.endsWith(
                "#W. Somerset Maugham")));
        assertTrue(spanish.stream().anyMatch(line -> line.endsWith(
                "#R. E. Shay")));
    }

    private static void assertCleanEntry(String line, String language,
            int index) {
        String context = language + " quote " + (index + 1);
        assertFalse(line.contains("\ufffd"), context + " has replacement data");
        assertEquals(1, line.chars().filter(value -> value == '#').count(),
                context + " must contain exactly one separator");
        String[] parts = line.split("#", -1);
        assertFalse(parts[0].isBlank(), context + " has no quote");
        assertFalse(parts[1].isBlank(), context + " has no author");
        assertFalse(parts[0].startsWith("\"") && parts[0].endsWith("\""),
                context + " duplicates the renderer's quotation marks");
    }

    private static List<String> resource(String path) throws Exception {
        try (InputStream input = GdxMenuQuotesTest.class
                .getResourceAsStream(path)) {
            assertTrue(input != null, path + " must exist");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                return reader.lines().filter(line -> !line.isBlank()).toList();
            }
        }
    }
}
