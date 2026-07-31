/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.smoke;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Structural checks on the translation bundles.
 *
 * Two ways a message has silently broken in the past, both of them reaching the
 * repository unnoticed because nothing looked at the shape of these files:
 *
 * <ul>
 *   <li>a key used from the code that exists in no bundle: the lookup returns
 *       the key itself, so the dialog showed the user a raw key;</li>
 *   <li>a multi-line message written with real line breaks instead of escaped
 *       ones: a value ends at the end of the line, so the message was truncated
 *       AND the leftover prose became a key of its own.</li>
 * </ul>
 *
 * These read the bundles STRAIGHT FROM THE SOURCE TREE, not from the classpath.
 * Off the classpath they would come from whatever jar was last installed, so a
 * broken bundle would still pass until someone reinstalled the artifact, which
 * is exactly the moment the check is meant to fire.
 */
class I18nBundleIntegritySmoke {

    /** Every key in the bundle: lowercase, dotted, no prose. */
    private static final Pattern KEY_SHAPE = Pattern.compile("^[a-z][a-z0-9_.]*$");

    private static final Pattern ARGUMENT = Pattern.compile("\\{(\\d+)\\}");

    /** A literal key handed to the translator, e.g. translate("ui.enviar"). */
    private static final Pattern TRANSLATE_CALL = Pattern.compile("translate\\(\\s*\"([^\"]+)\"");

    private static final String[] LANGUAGES = {"es", "en"};

    /** Walks up from the working directory until the project root shows up. */
    private static Path projectRoot() {
        Path dir = Paths.get("").toAbsolutePath();

        while (dir != null) {
            if (Files.isDirectory(dir.resolve("src/main/resources/i18n"))) {
                return dir;
            }
            dir = dir.getParent();
        }

        return fail("could not find the project root from " + Paths.get("").toAbsolutePath());
    }

    private static Path bundleFile(String language) {
        return projectRoot().resolve("src/main/resources/i18n/messages_" + language + ".properties");
    }

    private static Properties bundle(String language) throws IOException {
        Properties loaded = new Properties();

        try (InputStreamReader in = new InputStreamReader(
                Files.newInputStream(bundleFile(language)), StandardCharsets.UTF_8)) {
            // Same reader the game uses, on the same file the game ships.
            loaded.load(in);
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
    void everyKeyLooksLikeAKey() throws IOException {
        for (String language : LANGUAGES) {
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
    void bothBundlesDefineTheSameKeys() throws IOException {
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
    void placeholdersMatchAcrossLanguages() throws IOException {
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

    @Test
    @DisplayName("No key is defined twice: the second one wins in silence")
    void noKeyIsDefinedTwice() throws IOException {
        for (String language : LANGUAGES) {
            TreeMap<String, Integer> seen = new TreeMap<>();

            for (String line : Files.readAllLines(bundleFile(language), StandardCharsets.UTF_8)) {
                String trimmed = line.trim();

                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                    continue;
                }

                int separator = trimmed.indexOf('=');

                if (separator > 0) {
                    seen.merge(trimmed.substring(0, separator), 1, Integer::sum);
                }
            }

            TreeSet<String> repeated = new TreeSet<>();
            seen.forEach((key, count) -> {
                if (count > 1) {
                    repeated.add(key);
                }
            });

            assertEquals(new TreeSet<String>(), repeated,
                    "these keys are defined more than once in the " + language
                    + " bundle, and the file silently keeps the last one");
        }
    }

    @Test
    @DisplayName("Every key asked for from the code exists in both bundles")
    void everyKeyUsedInCodeIsDefined() throws IOException {
        Properties spanish = bundle("es");
        Properties english = bundle("en");
        Path sources = projectRoot().resolve("src/main/java");
        TreeSet<String> missing = new TreeSet<>();

        try (Stream<Path> tree = Files.walk(sources)) {
            List<Path> files = tree.filter(p -> p.toString().endsWith(".java")).toList();

            for (Path file : files) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();

                    // Skip comments: javadoc spells out example keys that do not exist.
                    if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
                        continue;
                    }

                    Matcher matcher = TRANSLATE_CALL.matcher(line);

                    while (matcher.find()) {
                        String key = matcher.group(1);

                        if (spanish.getProperty(key) == null || english.getProperty(key) == null) {
                            missing.add(key + "  (" + file.getFileName() + ")");
                        }
                    }
                }
            }
        }

        assertEquals(new TreeSet<String>(), missing,
                "the code asks for these keys and no bundle defines them, so the user is shown the raw key");
    }
}
