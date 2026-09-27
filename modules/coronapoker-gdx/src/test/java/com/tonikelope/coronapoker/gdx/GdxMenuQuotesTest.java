package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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
    }
}
