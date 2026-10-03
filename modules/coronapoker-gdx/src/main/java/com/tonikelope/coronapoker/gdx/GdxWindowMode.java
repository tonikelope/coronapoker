/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.Locale;
import java.util.Properties;

/** Startup window mode owned only by the native GDX frontend. */
enum GdxWindowMode {
    EXCLUSIVE("exclusive", "COMPLETA EXCLUSIVA"),
    BORDERLESS("borderless", "COMPLETA SIN BORDES"),
    WINDOWED("windowed", "VENTANA");

    static final String PREFERENCE_KEY = "gdx_window_mode";

    private final String persistedValue;
    private final String label;

    GdxWindowMode(String persistedValue, String label) {
        this.persistedValue = persistedValue;
        this.label = label;
    }

    String persistedValue() {
        return persistedValue;
    }

    String label() {
        return label;
    }

    String label(GdxGameText text) {
        if (text == null) return label;
        String key = "gdx.settings.window_mode." + persistedValue;
        String translated = text.translate(key);
        return translated.equals(key) ? label : translated.toUpperCase(
                Locale.forLanguageTag(text.language()));
    }

    static GdxWindowMode configured(Properties properties) {
        String configured = properties.getProperty(PREFERENCE_KEY, "")
                .trim().toLowerCase(Locale.ROOT);
        for (GdxWindowMode mode : values()) {
            if (mode.persistedValue.equals(configured)) return mode;
        }
        // GDX owns its display preference independently from legacy Swing.
        // Borderless is the native frontend default: instant desktop switching
        // with the same renderer/frame pacing and no forced restart.
        return BORDERLESS;
    }

    static GdxWindowMode requested(String[] arguments,
            Properties properties) {
        for (String argument : arguments) {
            if ("--windowed".equalsIgnoreCase(argument)) return WINDOWED;
            if ("--borderless".equalsIgnoreCase(argument)) return BORDERLESS;
            if ("--exclusive".equalsIgnoreCase(argument)
                    || "--fullscreen".equalsIgnoreCase(argument)) {
                return EXCLUSIVE;
            }
        }
        return configured(properties);
    }

    static GdxWindowMode cycle(Properties properties) {
        return adjust(properties, 1);
    }

    static GdxWindowMode adjust(Properties properties, int direction) {
        GdxWindowMode next = adjusted(properties, direction);
        persist(properties, next);
        return next;
    }

    static GdxWindowMode adjusted(Properties properties, int direction) {
        GdxWindowMode current = configured(properties);
        int size = values().length;
        int index = Math.floorMod(current.ordinal()
                + Integer.signum(direction), size);
        return values()[index];
    }

    static void persist(Properties properties, GdxWindowMode mode) {
        if (properties == null || mode == null) return;
        properties.setProperty(PREFERENCE_KEY, mode.persistedValue);
    }
}
