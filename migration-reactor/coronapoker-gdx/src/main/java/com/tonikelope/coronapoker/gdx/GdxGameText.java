/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.game.GameText;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

/** UTF-8 game text lookup that has no dependency on the Swing translator. */
final class GdxGameText implements GameText {

    private static final String DEFAULT_LANGUAGE = "es";

    private final Properties defaults;
    private final Properties selected;
    private final Locale locale;

    GdxGameText(String requestedLanguage) {
        String language = normalizeLanguage(requestedLanguage);
        defaults = load(DEFAULT_LANGUAGE);
        selected = DEFAULT_LANGUAGE.equals(language) ? defaults : load(language);
        locale = Locale.forLanguageTag(language);
    }

    @Override
    public String translate(String key, Object... arguments) {
        Objects.requireNonNull(key, "key");
        String pattern = selected.getProperty(key,
                defaults.getProperty(key, key));
        if (arguments == null || arguments.length == 0) {
            return pattern;
        }
        return new MessageFormat(pattern, locale).format(arguments);
    }

    private static String normalizeLanguage(String language) {
        if (language == null) {
            return DEFAULT_LANGUAGE;
        }
        String normalized = language.strip().toLowerCase(Locale.ROOT);
        return normalized.matches("[a-z]{2}") ? normalized : DEFAULT_LANGUAGE;
    }

    private static Properties load(String language) {
        String resource = "/i18n/messages_" + language + ".properties";
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
}
