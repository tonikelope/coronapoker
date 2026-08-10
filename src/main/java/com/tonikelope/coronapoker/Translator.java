/*
 * Copyright (C) 2020 tonikelope
 _              _ _        _                  
| |_ ___  _ __ (_) | _____| | ___  _ __   ___ 
| __/ _ \| '_ \| | |/ / _ \ |/ _ \| '_ \ / _ \
| || (_) | | | | |   <  __/ | (_) | |_) |  __/
 \__\___/|_| |_|_|_|\_\___|_|\___/| .__/ \___|
 ____    ___  ____    ___  
|___ \  / _ \|___ \  / _ \ 
  __) || | | | __) || | | |
 / __/ | |_| |/ __/ | |_| |
|_____| \___/|_____| \___/ 

https://github.com/tonikelope/coronapoker
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tonikelope.coronapoker;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple i18n system backed by {@code .properties} resource files.
 * <p>
 * Usage: {@code Translator.translate("some.key")}.
 * <p>
 * Translation files live under {@code /i18n/messages_XX.properties}, where XX is the
 * language code (es, en, ...). Spanish (es) is the default language; if a key is missing
 * from the active language it falls back to the default, and finally to the key itself.
 *
 * @author tonikelope
 */
public class Translator {

    private static volatile String LANG = "";

    private static final Properties DEFAULT_PROPS = new Properties();
    private static final Properties LANG_PROPS = new Properties();
    private static final Properties EN_PROPS = new Properties();
    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();

    private static synchronized void loadLanguage(String lang) {

        DEFAULT_PROPS.clear();
        LANG_PROPS.clear();
        EN_PROPS.clear();
        CACHE.clear();

        // Always load the default language (Spanish) as a fallback
        try (InputStream is = Translator.class.getResourceAsStream("/i18n/messages_es.properties")) {
            if (is != null) {
                DEFAULT_PROPS.load(new InputStreamReader(is, StandardCharsets.UTF_8));
            }
        } catch (Exception ex) {
            Logger.getLogger(Translator.class.getName()).log(Level.SEVERE, "Error loading default language", ex);
        }

        // Always load English too, so callers can force it (e.g. for logs)
        try (InputStream is = Translator.class.getResourceAsStream("/i18n/messages_en.properties")) {
            if (is != null) {
                EN_PROPS.load(new InputStreamReader(is, StandardCharsets.UTF_8));
            }
        } catch (Exception ex) {
            Logger.getLogger(Translator.class.getName()).log(Level.SEVERE, "Error loading English properties", ex);
        }

        // Load the selected language (skipped only when it's the default, Spanish)
        if (!"es".equals(lang)) {
            try (InputStream is = Translator.class.getResourceAsStream("/i18n/messages_" + lang + ".properties")) {
                if (is != null) {
                    LANG_PROPS.load(new InputStreamReader(is, StandardCharsets.UTF_8));
                }
            } catch (Exception ex) {
                Logger.getLogger(Translator.class.getName()).log(Level.SEVERE, "Error loading language: " + lang, ex);
            }
        }
    }

    /**
     * Translates a key using the active language.
     *
     * @param key i18n key
     * @return translated string, or {@code key} itself if not found
     */
    public static String translate(String key) {
        return _translate(key, false);
    }

    /**
     * Translates a key and substitutes {@code args} via {@link java.text.MessageFormat}.
     *
     * @param key  i18n key
     * @param args format arguments
     * @return formatted, translated string
     */
    public static String translate(String key, Object... args) {
        return translate(key, false, args);
    }

    /**
     * Translates a key, optionally forcing English regardless of the active language.
     *
     * @param key     i18n key
     * @param forceEn {@code true} to try the English properties first
     * @return translated string, or {@code key} itself if not found
     */
    public static String translate(String key, boolean forceEn) {
        return _translate(key, forceEn);
    }

    /**
     * Translates a key, optionally forcing English, and substitutes {@code args} via
     * {@link java.text.MessageFormat}.
     *
     * @param key     i18n key
     * @param forceEn {@code true} to try the English properties first
     * @param args    format arguments
     * @return formatted, translated string
     */
    public static String translate(String key, boolean forceEn, Object... args) {
        String val = _translate(key, forceEn);
        if (val != null && args != null && args.length > 0) {
            try {
                return java.text.MessageFormat.format(val, args);
            } catch (Exception ex) {
                Logger.getLogger(Translator.class.getName()).log(Level.WARNING, "Error formatting i18n key: " + key, ex);
            }
        }
        return val;
    }

    private static String _translate(String key, final boolean forceEn) {

        if (key == null) {
            return null;
        }

        // Reload if the active language changed
        if (!LANG.equals(GameFrame.LANGUAGE)) {
            LANG = GameFrame.LANGUAGE;
            loadLanguage(LANG);
        }

        // Reuse `key` directly in the dominant forceEn=false case instead of building `key + ""`
        // (a fresh String) on every translate() call — this method is called pervasively.
        String cacheKey = forceEn ? key + "#forceEn" : key;
        return CACHE.computeIfAbsent(cacheKey, k -> {
            // 1. If English is forced, try the English properties first
            if (forceEn) {
                String valEn = EN_PROPS.getProperty(key);
                if (valEn != null) {
                    return valEn;
                }
            }

            // 2. Try as a direct key in the current language
            String val = LANG_PROPS.getProperty(key);
            if (val != null) {
                return val;
            }
            // 3. Try as a direct key in the default language
            val = DEFAULT_PROPS.getProperty(key);
            if (val != null) {
                return val;
            }
            // 4. Fall back to the key itself if nothing matched
            return key;
        });
    }

}
