/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Historical Swing poker quotes, rotated without repeating a cycle. */
final class GdxMenuQuotes {

    static final float ROTATION_SECONDS = 8f;

    private final SecureRandom random;
    private final List<String> spanish;
    private final List<String> english;
    private List<String> cycle = List.of();
    private String language = "";
    private int index;
    private float remaining;
    private String current = "";

    GdxMenuQuotes(SecureRandom random) {
        this(random, load("/quotes_ES.txt"), load("/quotes_EN.txt"));
    }

    GdxMenuQuotes(SecureRandom random, List<String> spanish,
            List<String> english) {
        this.random = Objects.requireNonNull(random, "random");
        this.spanish = List.copyOf(spanish);
        this.english = List.copyOf(english);
    }

    String update(String requestedLanguage, float deltaSeconds) {
        String normalized = "en".equalsIgnoreCase(requestedLanguage)
                ? "en" : "es";
        if (!normalized.equals(language)) {
            language = normalized;
            startCycle();
        } else {
            remaining -= Math.max(0f, deltaSeconds);
            if (remaining <= 0f) advance();
        }
        return current;
    }

    private void startCycle() {
        cycle = new ArrayList<>("en".equals(language) ? english : spanish);
        Collections.shuffle(cycle, random);
        index = 0;
        showCurrent();
    }

    private void advance() {
        index++;
        if (index >= cycle.size()) startCycle();
        else showCurrent();
    }

    private void showCurrent() {
        current = cycle.isEmpty() ? "" : format(cycle.get(index));
        remaining = ROTATION_SECONDS;
    }

    static String format(String source) {
        String[] parts = Objects.requireNonNullElse(source, "").trim()
                .split("#", 2);
        String quote = parts[0].trim();
        String author = parts.length == 2 ? parts[1].trim() : "";
        return author.isEmpty() ? '"' + quote + '"'
                : '"' + quote + "\" (" + author + ')';
    }

    private static List<String> load(String resource) {
        InputStream input = GdxMenuQuotes.class.getResourceAsStream(resource);
        if (input == null) return List.of();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                input, StandardCharsets.UTF_8))) {
            return reader.lines().map(String::trim)
                    .filter(line -> !line.isEmpty()).toList();
        } catch (Exception failure) {
            throw new IllegalStateException(String.format(Locale.ROOT,
                    "Cannot load menu quotes from %s", resource), failure);
        }
    }
}
