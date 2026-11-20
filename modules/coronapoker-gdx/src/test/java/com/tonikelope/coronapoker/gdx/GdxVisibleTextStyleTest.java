/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GdxVisibleTextStyleTest {

    private static final List<String> VISIBLE_TEXT_RESOURCES = List.of(
            "i18n/messages_es.properties",
            "i18n/messages_en.properties",
            "quotes_ES.txt",
            "quotes_EN.txt");

    @Test
    void visibleTextAvoidsDisallowedPunctuation() throws IOException {
        for (String resource : VISIBLE_TEXT_RESOURCES) {
            String content = read(resource);
            assertFalse(content.contains(";"), resource + " contains ;");
            assertFalse(content.contains("·"), resource + " contains ·");
            assertFalse(content.contains("—"), resource + " contains —");
            assertFalse(content.contains("–"), resource + " contains –");
        }
    }

    @Test
    void everyLanguageAvoidsTheMiddleDot() throws IOException {
        for (var language : GdxGameText.languages()) {
            String resource = "i18n/messages_" + language.tag().replace('-', '_') + ".properties";
            String catalog = read(resource);
            assertFalse(catalog.indexOf(0xb7) >= 0, resource);
            assertFalse(catalog.toLowerCase(java.util.Locale.ROOT).contains("\\u00b7"), resource);
        }
    }

    private static String read(String resource) throws IOException {
        try (InputStream input = GdxVisibleTextStyleTest.class
                .getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
