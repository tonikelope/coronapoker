/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.game.GameText;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.text.MessageFormat;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.Objects;
import java.util.Properties;

/** UTF-8 game text lookup that has no dependency on the Swing translator. */
final class GdxGameText implements GameText {

    private static final String FALLBACK_LANGUAGE = "en";

    private static final List<String> LANGUAGE_TAGS = List.of(
            "es", "en", "af-ZA", "bg-BG", "ca-ES", "cs-CZ",
            "da-DK", "de-DE", "fr-FR", "gd-GB", "gl-ES", "el-GR",
            "it-IT", "ja-JP", "lt-LT", "hu-HU", "nl-NL", "nb-NO",
            "pl-PL", "pt-BR", "pt-PT", "ru-RU", "zh-CN", "sk-SK",
            "fi-FI", "sv-SE", "ta-IN", "tr-TR", "uk-UA", "vi-VN");

    private final Properties defaults;
    private volatile Selection selection;

    GdxGameText(String requestedLanguage) {
        defaults = load(FALLBACK_LANGUAGE);
        setLanguage(requestedLanguage);
    }

    synchronized String setLanguage(String requestedLanguage) {
        String language = resolveLanguage(requestedLanguage);
        Properties selected = FALLBACK_LANGUAGE.equals(language)
                ? defaults : load(language);
        selection = new Selection(language, selected,
                Locale.forLanguageTag(language));
        return language;
    }

    String language() {
        return selection.language();
    }

    @Override
    public String translate(String key, Object... arguments) {
        Objects.requireNonNull(key, "key");
        Selection current = selection;
        String pattern = current.properties().getProperty(key,
                defaults.getProperty(key, key));
        if (arguments == null || arguments.length == 0) {
            return pattern;
        }
        return new MessageFormat(pattern, current.locale()).format(arguments);
    }

    Map<String, String> messagesWithPrefix(String prefix) {
        Selection current = selection;
        Map<String, String> messages = new TreeMap<>();
        for (String key : defaults.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                messages.put(key.substring(prefix.length()), current.properties()
                        .getProperty(key, defaults.getProperty(key)));
            }
        }
        return messages;
    }

    static String resolveLanguage(String requestedLanguage) {
        Locale locale = requestedLanguage == null || requestedLanguage.isBlank()
                ? Locale.getDefault()
                : Locale.forLanguageTag(requestedLanguage.strip().replace('_', '-'));
        return supportedLanguage(locale);
    }

    static String supportedLanguage(Locale locale) {
        String tag = locale.stripExtensions().toLanguageTag();
        for (String option : LANGUAGE_TAGS) {
            if (option.equalsIgnoreCase(tag)) {
                return option;
            }
        }
        String language = locale.getLanguage();
        if ("no".equals(language)) {
            return "nb-NO";
        }
        if ("zh".equals(language) && ("Hant".equals(locale.getScript())
                || List.of("TW", "HK", "MO").contains(locale.getCountry()))) {
            return FALLBACK_LANGUAGE;
        }
        for (String option : LANGUAGE_TAGS) {
            if (option.equals(language) || option.startsWith(language + "-")) {
                return option;
            }
        }
        return FALLBACK_LANGUAGE;
    }

    String catalogCharacters() {
        Selection current = selection;
        var characters = new LinkedHashSet<Integer>();
        for (String key : current.properties().stringPropertyNames()) {
            // Chromium loads its own fonts and never uses the game atlas.
            if (key.startsWith("gdx.stats.web.") || key.startsWith("gdx.web.")) continue;
            String value = current.properties().getProperty(key);
            value.codePoints().forEach(characters::add);
            value.toUpperCase(current.locale()).codePoints().forEach(characters::add);
        }
        var unique = new StringBuilder(characters.size());
        characters.forEach(unique::appendCodePoint);
        return unique.toString();
    }

    int languageIndex() {
        String active = language();
        for (int index = 0; index < languages().size(); index++) {
            if (languages().get(index).tag().equals(active)) {
                return index;
            }
        }
        throw new IllegalStateException("Unsupported active language: " + active);
    }

    static List<Language> languages() {
        return LanguageSelector.LANGUAGES;
    }

    static List<String> languageLabels() {
        return LanguageSelector.LABELS;
    }

    record Language(String tag, String label) { }

    // Only the selector needs the names from the other catalogs.
    private static final class LanguageSelector {
        private static final List<Language> LANGUAGES = orderedLanguages();
        private static final List<String> LABELS = LANGUAGES.stream().map(Language::label).toList();

        private static List<Language> orderedLanguages() {
            var alphabet = Collator.getInstance(Locale.ROOT);
            return LANGUAGE_TAGS.stream()
                    .map(tag -> new Language(tag, load(tag).getProperty("gdx.language_name", tag)))
                    .sorted(Comparator.comparing(Language::label, alphabet).thenComparing(Language::tag))
                    .toList();
        }
    }

    private static Properties load(String language) {
        String resource = "/i18n/messages_" + language.replace('-', '_')
                + ".properties";
        Properties properties = new Properties();
        try (InputStream input = GdxGameText.class.getResourceAsStream(resource)) {
            if (input == null) {
                return properties;
            }
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return properties;
        } catch (Exception failure) {
            throw new IllegalStateException("No se pudo cargar " + resource,
                    failure);
        }
    }

    private record Selection(String language, Properties properties,
            Locale locale) {
    }
}
