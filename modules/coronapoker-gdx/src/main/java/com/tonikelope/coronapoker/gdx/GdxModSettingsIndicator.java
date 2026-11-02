/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

/**
 * Canonical provenance marker for settings whose selected media comes from
 * the active MOD.
 */
final class GdxModSettingsIndicator {

    static final String SUFFIX = " (MOD)";

    private GdxModSettingsIndicator() {
    }

    static String label(String value, boolean overridden) {
        if (!overridden || value == null || value.endsWith(SUFFIX)) {
            return value;
        }
        return value + SUFFIX;
    }

    static boolean marked(String value) {
        return value != null && value.contains(SUFFIX);
    }

    static boolean deck(GdxGamePresentationSettings settings, String deck) {
        return settings != null && settings.modActive()
                && settings.modDeck(deck);
    }

    static boolean cardBack(GdxGamePresentationSettings settings,
            String cardBack) {
        return deck(settings, cardBack);
    }

    static boolean option(GdxGamePresentationSettings settings,
            String key) {
        if (settings == null || !settings.modActive() || key == null) {
            return false;
        }
        return switch (key) {
            case "cinematicas_allin" ->
                !settings.modFiles("cinematics/allin", ".gif").isEmpty();
            case "cinematicas_gameover" ->
                settings.modAsset("cinematics/misc/game_over.gif").isPresent()
                || settings.modAsset("cinematics/misc/game_over_zero.gif")
                        .isPresent();
            case "animacion_barajado" ->
                settings.modAsset("decks/" + settings.deck()
                        + "/gif/shuffle.gif").isPresent();
            default -> false;
        };
    }
}
