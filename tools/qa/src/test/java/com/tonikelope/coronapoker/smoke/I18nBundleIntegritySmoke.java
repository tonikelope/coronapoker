/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.smoke;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural checks on the translation bundles.
 *
 * A multi-line message has to be written with escaped newlines in the value: in
 * a properties file a value ends at the end of the line. Writing a real line
 * break instead silently truncates the message AND turns the leftover prose
 * into a key of its own, so the user is shown half a warning and the bundle
 * grows a junk entry. Nothing in the build or the test suite used to look at
 * the shape of these files, so that mistake reached the repository unnoticed.
 *
 * The rules pinned here are the ones the whole bundle already follows: every
 * key is lowercase and dotted, both languages define exactly the same keys, and
 * a message takes the same arguments in either language.
 */
class I18nBundleIntegritySmoke {

    /** Every key in the bundle: lowercase, dotted, no prose. */
    private static final Pattern KEY_SHAPE = Pattern.compile("^[a-z][a-z0-9_.]*$");

    private static final Pattern ARGUMENT = Pattern.compile("\\{(\\d+)\\}");

    private static Properties bundle(String language) throws Exception {
        Properties loaded = new Properties();

        try (InputStream in = I18nBundleIntegritySmoke.class
                .getResourceAsStream("/i18n/messages_" + language + ".properties")) {
            assertNotNull(in, "the " + language + " bundle is not on the classpath");
            // Same reader the game uses, so this sees exactly what it sees.
            loaded.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        return loaded;
    }

    private static Set<String> argumentsOf(String message) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = ARGUMENT.matcher(message);

        while (matcher.find()) {
            found.add(matcher.group(1));
        }

        return found;
    }

    @Test
    @DisplayName("No key is a runaway line of prose")
    void everyKeyLooksLikeAKey() throws Exception {
        for (String language : new String[]{"es", "en"}) {
            Properties loaded = bundle(language);
            assertTrue(loaded.size() > 1000, "the " + language + " bundle came back nearly empty");

            for (String key : loaded.stringPropertyNames()) {
                assertTrue(KEY_SHAPE.matcher(key).matches(),
                        "the " + language + " bundle has a key that reads like prose: '" + key
                        + "': a message was split across lines instead of using an escaped newline");
                assertTrue(key.indexOf('.') > 0,
                        "the " + language + " bundle has a key with no namespace: '" + key
                        + "': most likely the tail of a message split across lines");
            }
        }
    }

    @Test
    @DisplayName("Both languages define exactly the same keys")
    void bothBundlesDefineTheSameKeys() throws Exception {
        Set<String> spanish = new TreeSet<>(bundle("es").stringPropertyNames());
        Set<String> english = new TreeSet<>(bundle("en").stringPropertyNames());

        Set<String> onlySpanish = new TreeSet<>(spanish);
        onlySpanish.removeAll(english);
        Set<String> onlyEnglish = new TreeSet<>(english);
        onlyEnglish.removeAll(spanish);

        assertEquals(new TreeSet<String>(), onlySpanish, "these keys are missing from the english bundle");
        assertEquals(new TreeSet<String>(), onlyEnglish, "these keys are missing from the spanish bundle");
    }

    @Test
    @DisplayName("A message takes the same arguments in either language")
    void placeholdersMatchAcrossLanguages() throws Exception {
        Properties spanish = bundle("es");
        Properties english = bundle("en");

        for (String key : new TreeSet<>(spanish.stringPropertyNames())) {
            String translated = english.getProperty(key);

            if (translated != null) {
                assertEquals(argumentsOf(spanish.getProperty(key)), argumentsOf(translated),
                        "'" + key + "' does not take the same arguments in both languages");
            }
        }
    }
}
