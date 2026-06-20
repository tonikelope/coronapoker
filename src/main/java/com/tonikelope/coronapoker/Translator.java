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
 * Sistema de internacionalización (i18n) basado en ficheros .properties.
 *
 * Uso: Translator.translate("clave.semantica")
 *
 * Los ficheros de traducción se encuentran en /i18n/messages_XX.properties
 * donde XX es el código de idioma (es, en, ...).
 *
 * El idioma por defecto es el español (es). Si una clave no se encuentra en el
 * idioma seleccionado, se usa el valor del idioma por defecto. Si tampoco
 * existe en el idioma por defecto, se devuelve la propia clave.
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

        // Sincronizar: Cargar siempre el idioma por defecto (español) como fallback
        try (InputStream is = Translator.class.getResourceAsStream("/i18n/messages_es.properties")) {
            if (is != null) {
                DEFAULT_PROPS.load(new InputStreamReader(is, StandardCharsets.UTF_8));
            }
        } catch (Exception ex) {
            Logger.getLogger(Translator.class.getName()).log(Level.SEVERE, "Error loading default language", ex);
        }

        // Cargar siempre el inglés para poder forzarlo en logs
        try (InputStream is = Translator.class.getResourceAsStream("/i18n/messages_en.properties")) {
            if (is != null) {
                EN_PROPS.load(new InputStreamReader(is, StandardCharsets.UTF_8));
            }
        } catch (Exception ex) {
            Logger.getLogger(Translator.class.getName()).log(Level.SEVERE, "Error loading English properties", ex);
        }

        // Cargar idioma seleccionado (si no es el por defecto ni inglés)
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

    public static String translate(String key) {
        return _translate(key, false);
    }

    public static String translate(String key, Object... args) {
        return translate(key, false, args);
    }

    public static String translate(String key, boolean forceEn) {
        return _translate(key, forceEn);
    }

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

        // Recargar si cambió el idioma
        if (!LANG.equals(GameFrame.LANGUAGE)) {
            LANG = GameFrame.LANGUAGE;
            loadLanguage(LANG);
        }

        return CACHE.computeIfAbsent(key + (forceEn ? "#forceEn" : ""), k -> {
            // 1. Si se fuerza inglés, probar primero en las propiedades de inglés
            if (forceEn) {
                String valEn = EN_PROPS.getProperty(key);
                if (valEn != null) {
                    return valEn;
                }
            }

            // 2. Probar como clave directa en el idioma actual
            String val = LANG_PROPS.getProperty(key);
            if (val != null) {
                return val;
            }
            // 3. Probar como clave directa en el idioma por defecto
            val = DEFAULT_PROPS.getProperty(key);
            if (val != null) {
                return val;
            }
            // 4. Si no se encuentra, devolver la propia clave
            return key;
        });
    }

}
