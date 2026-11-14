/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.game.GameText;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

/** UTF-8 game text lookup that has no dependency on the Swing translator. */
final class GdxGameText implements GameText {

    private static final String FALLBACK_LANGUAGE = "en";

    // Use names that the existing selector font can display for every locale.
    static final List<Language> LANGUAGES = List.of(
            new Language("es", "Español"),
            new Language("en", "English"),
            new Language("af-ZA", "Afrikaans"),
            new Language("bg-BG", "Bulgarian"),
            new Language("ca-ES", "Català"),
            new Language("cs-CZ", "Czech"),
            new Language("da-DK", "Dansk"),
            new Language("de-DE", "Deutsch"),
            new Language("el-GR", "Greek"),
            new Language("fi-FI", "Suomi"),
            new Language("fr-FR", "Français"),
            new Language("gd-GB", "Gàidhlig"),
            new Language("gl-ES", "Galego"),
            new Language("hu-HU", "Magyar"),
            new Language("it-IT", "Italiano"),
            new Language("ja-JP", "Japanese"),
            new Language("lt-LT", "Lithuanian"),
            new Language("nb-NO", "Norsk bokmål"),
            new Language("nl-NL", "Nederlands"),
            new Language("pl-PL", "Polski"),
            new Language("pt-BR", "Português (Brasil)"),
            new Language("pt-PT", "Português (Portugal)"),
            new Language("ru-RU", "Russian"),
            new Language("sk-SK", "Slovak"),
            new Language("sv-SE", "Svenska"),
            new Language("ta-IN", "Tamil"),
            new Language("tr-TR", "Turkish"),
            new Language("uk-UA", "Ukrainian"),
            new Language("vi-VN", "Vietnamese"),
            new Language("zh-CN", "Simplified Chinese"));
    static final List<String> LANGUAGE_LABELS = LANGUAGES.stream()
            .map(Language::label).toList();

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

    static String resolveLanguage(String requestedLanguage) {
        Locale locale = requestedLanguage == null || requestedLanguage.isBlank()
                ? Locale.getDefault()
                : Locale.forLanguageTag(requestedLanguage.strip().replace('_', '-'));
        return supportedLanguage(locale);
    }

    static String supportedLanguage(Locale locale) {
        String tag = locale.stripExtensions().toLanguageTag();
        for (Language option : LANGUAGES) {
            if (option.tag().equalsIgnoreCase(tag)) {
                return option.tag();
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
        for (Language option : LANGUAGES) {
            if (option.tag().equals(language) || option.tag().startsWith(language + "-")) {
                return option.tag();
            }
        }
        return FALLBACK_LANGUAGE;
    }

    String catalogCharacters() {
        Selection current = selection;
        var characters = new LinkedHashSet<Integer>();
        for (String key : current.properties().stringPropertyNames()) {
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
        for (int index = 0; index < LANGUAGES.size(); index++) {
            if (LANGUAGES.get(index).tag().equals(active)) {
                return index;
            }
        }
        throw new IllegalStateException("Unsupported active language: " + active);
    }

    record Language(String tag, String label) { }

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
