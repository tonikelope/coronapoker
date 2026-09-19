/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.List;
import java.util.Locale;
import java.util.Properties;

/** Canonical Swing choice sets used by the GDX appearance screen. */
final class GdxAppearanceOptions {

    private static final int LIGHT_MIN = 10;
    private static final int LIGHT_MAX = 90;
    private static final int LIGHT_STEP = 5;

    record Choice(String key, String label, List<String> values,
            List<String> labels, String fallback, boolean numericNearest) {
        Choice {
            if (values.isEmpty() || values.size() != labels.size()) {
                throw new IllegalArgumentException("Invalid appearance choice");
            }
        }

        String label(GdxGameText text) {
            return translatedUpper(text, "gdx.settings.choice." + key,
                    label);
        }
    }

    static final List<Choice> ANIMATION_CHOICES = List.of(
            numeric("reparto_velocidad", "VELOCIDAD DE REPARTO", "100",
                    new String[]{"150", "100", "60"},
                    new String[]{"LENTA", "NORMAL", "RÁPIDA"}),
            numeric("card_flip_duration", "VELOCIDAD DE DESTAPE", "620",
                    new String[]{"1100", "850", "620", "480", "350"},
                    new String[]{"MUY LENTA", "LENTA", "NORMAL", "RÁPIDA",
                        "MUY RÁPIDA"}),
            numeric("card_flip_zoom", "EFECTO AL DESTAPAR", "100",
                    new String[]{"100", "115", "130", "145"},
                    new String[]{"DESACTIVADO", "SUAVE", "NORMAL", "FUERTE"}),
            numeric("swap_velocidad", "VELOCIDAD DE ORDENAR", "320",
                    new String[]{"520", "320", "200"},
                    new String[]{"LENTA", "NORMAL", "RÁPIDA"}),
            exact("swap_arco", "ESTILO AL ORDENAR", "false",
                    new String[]{"true", "false"},
                    new String[]{"ARCO", "HORIZONTAL"}));

    static String selectedLabel(Choice choice, Properties properties) {
        return choice.labels().get(selectedIndex(choice, properties));
    }

    static String selectedLabel(Choice choice, Properties properties,
            GdxGameText text) {
        int index = selectedIndex(choice, properties);
        return translatedUpper(text, "gdx.settings.choice." + choice.key()
                + "." + choice.values().get(index),
                choice.labels().get(index));
    }

    static void cycle(Choice choice, Properties properties) {
        adjust(choice, properties, 1);
    }

    static void adjust(Choice choice, Properties properties, int direction) {
        int next = Math.floorMod(selectedIndex(choice, properties)
                + Integer.signum(direction), choice.values().size());
        properties.setProperty(choice.key(), choice.values().get(next));
    }

    /**
     * A speed or style selector is editable only while its owning animation is
     * enabled. GDX deliberately ignores Swing's global animation master: the
     * renderer's semantic animations cannot all be disabled as a group.
     */
    static boolean enabled(Choice choice, Properties properties) {
        String parent = switch (choice.key()) {
            case "reparto_velocidad" -> "animacion_reparto";
            case "card_flip_duration", "card_flip_zoom" ->
                "animacion_destape";
            case "swap_velocidad", "swap_arco" -> "animacion_swap";
            default -> null;
        };
        return parent == null || Boolean.parseBoolean(properties.getProperty(
                parent, "true"));
    }

    static String lightLevelLabel(Properties properties) {
        return lightLevel(properties) + "%";
    }

    static String deckLabel(String deck) {
        if (deck == null) return "";
        return switch (deck.toLowerCase(java.util.Locale.ROOT)) {
            case "coronapoker" -> "CoronaPoker";
            case "goliat4" -> "Goliat 4 colores";
            case "interstate60" -> "Interstate 60";
            default -> deck;
        };
    }

    static String deckLabel(String deck, GdxGameText text) {
        return deckLabel(deck);
    }

    static String cardBackLabel(String cardBack) {
        return "default".equalsIgnoreCase(cardBack)
                ? "Por defecto" : deckLabel(cardBack);
    }

    static String cardBackLabel(String cardBack, GdxGameText text) {
        return "default".equalsIgnoreCase(cardBack)
                ? translated(text, "gdx.settings.value.default",
                        "Por defecto") : deckLabel(cardBack, text);
    }

    static String feltLabel(String felt) {
        if (felt == null) return "";
        return switch (felt.toLowerCase(java.util.Locale.ROOT)) {
            case "verde" -> "Verde";
            case "azul" -> "Azul";
            case "rojo" -> "Rojo";
            case "negro" -> "Negro";
            case "madera" -> "Sin tapete";
            default -> felt;
        };
    }

    static String feltLabel(String felt, GdxGameText text) {
        if (felt == null) return "";
        String fallback = feltLabel(felt);
        return translated(text, "gdx.settings.felt."
                + felt.toLowerCase(Locale.ROOT), fallback);
    }

    static void cycleLightLevel(Properties properties) {
        int current = lightLevel(properties);
        int next = current + LIGHT_STEP;
        if (next > LIGHT_MAX) next = LIGHT_MIN;
        properties.setProperty("nivel_luz", Integer.toString(next));
    }

    static void adjustLightLevel(Properties properties, int direction) {
        int next = lightLevel(properties)
                + Integer.signum(direction) * LIGHT_STEP;
        properties.setProperty("nivel_luz", Integer.toString(
                Math.max(LIGHT_MIN, Math.min(LIGHT_MAX, next))));
    }

    private static int lightLevel(Properties properties) {
        try {
            int configured = Integer.parseInt(properties.getProperty(
                    "nivel_luz", "50"));
            int clamped = Math.max(LIGHT_MIN, Math.min(LIGHT_MAX, configured));
            return LIGHT_MIN + Math.round((clamped - LIGHT_MIN)
                    / (float) LIGHT_STEP) * LIGHT_STEP;
        } catch (NumberFormatException invalid) {
            return 50;
        }
    }

    static int selectedIndex(Choice choice, Properties properties) {
        String configured = properties.getProperty(choice.key(),
                choice.fallback());
        int exact = choice.values().indexOf(configured);
        if (exact >= 0 || !choice.numericNearest()) return Math.max(0, exact);
        try {
            int number = Integer.parseInt(configured);
            int selected = 0;
            int distance = Integer.MAX_VALUE;
            for (int i = 0; i < choice.values().size(); i++) {
                int candidate = Math.abs(Integer.parseInt(
                        choice.values().get(i)) - number);
                if (candidate < distance) {
                    selected = i;
                    distance = candidate;
                }
            }
            return selected;
        } catch (NumberFormatException invalid) {
            return choice.values().indexOf(choice.fallback());
        }
    }

    private static Choice numeric(String key, String label, String fallback,
            String[] values, String[] labels) {
        return new Choice(key, label, List.of(values), List.of(labels),
                fallback, true);
    }

    private static Choice exact(String key, String label, String fallback,
            String[] values, String[] labels) {
        return new Choice(key, label, List.of(values), List.of(labels),
                fallback, false);
    }

    private static String translated(GdxGameText text, String key,
            String fallback) {
        if (text == null) return fallback;
        String translated = text.translate(key);
        return translated.equals(key) ? fallback : translated;
    }

    private static String translatedUpper(GdxGameText text, String key,
            String fallback) {
        return translated(text, key, fallback).toUpperCase(
                text == null ? Locale.ROOT
                        : Locale.forLanguageTag(text.language()));
    }

    private GdxAppearanceOptions() {
    }
}
