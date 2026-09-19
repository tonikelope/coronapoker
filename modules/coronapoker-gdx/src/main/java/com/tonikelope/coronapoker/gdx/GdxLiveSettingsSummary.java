/*
 * Copyright (C) 2020-2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Read-only labels for session settings that Swing exposes as information
 * once a table has started. Keeping this outside the renderer makes it
 * impossible for these rows to mutate the canonical game configuration.
 */
final class GdxLiveSettingsSummary {

    private GdxLiveSettingsSummary() {
    }

    static List<String> timingLabels(
            GameConfigCodecV1.Configuration configuration,
            GdxGameText text) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(text, "text");
        return List.of(
                label(text, "think_time") + "  \u00b7  "
                + (configuration.thinkTimeEnabled()
                        ? configuration.thinkTime() + " S"
                        : value(text, "disabled")),
                label(text, "showdown_time") + "  \u00b7  "
                + configuration.showdownTime() + " S");
    }

    static List<String> purchaseLabels(
            GameConfigCodecV1.Configuration configuration,
            GdxGameText text) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(text, "text");
        return List.of(
                label(text, "initial_buyin") + "  \u00b7  "
                        + configuration.buyin(),
                label(text, "buyin") + "  \u00b7  "
                        + (configuration.fixedBuyin()
                                ? value(text, "fixed")
                                : value(text, "variable")),
                label(text, "buyin_range") + "  \u00b7  "
                        + configuration.buyinMinBb()
                + " \u2013 " + configuration.buyinMaxBb() + " BB",
                label(text, "rebuy") + "  \u00b7  "
                        + (configuration.rebuy()
                                ? value(text, "enabled")
                                : value(text, "disabled_feminine")),
                label(text, "player_limit") + "  \u00b7  "
                + (configuration.rebuyLimit() > 0
                        ? Integer.toString(configuration.rebuyLimit())
                        : value(text, "no_limit")),
                label(text, "rebuy_cap") + "  \u00b7  "
                + (configuration.rebuyCapPolicy() == 0
                        ? "BUY-IN" : value(text, "highest_stack")));
    }

    static List<String> unavailablePurchaseLabels(GdxGameText text) {
        String unavailable = value(text, "unavailable");
        return List.of(
                label(text, "initial_buyin") + "  \u00b7  " + unavailable,
                label(text, "buyin") + "  \u00b7  " + unavailable,
                label(text, "buyin_range") + "  \u00b7  " + unavailable,
                label(text, "rebuy") + "  \u00b7  " + unavailable,
                label(text, "player_limit") + "  \u00b7  " + unavailable,
                label(text, "rebuy_cap") + "  \u00b7  " + unavailable);
    }

    static String purchaseHeading(GdxGameText text) {
        return upper(text, text.translate(
                "gdx.settings.game.summary.purchase_heading"));
    }

    /** Swing's canonical Rabbit Hunting levels, shared by every GDX context. */
    static String rabbitHuntingLabel(int value, GdxGameText text) {
        return switch (value) {
            case 0 -> upper(text, text.translate("menu.off"));
            case 1 -> upper(text, text.translate("menu.free"));
            case 2 -> upper(text, text.translate("menu.free_sb"));
            case 3 -> upper(text, text.translate("menu.free_sb_bb"));
            default -> value(text, "unavailable");
        };
    }

    private static String label(GdxGameText text, String suffix) {
        return upper(text, text.translate(
                "gdx.settings.game.summary.label." + suffix));
    }

    private static String value(GdxGameText text, String suffix) {
        return upper(text, text.translate(
                "gdx.settings.game.summary.value." + suffix));
    }

    private static String upper(GdxGameText text, String value) {
        return value.toUpperCase(Locale.forLanguageTag(text.language()));
    }
}
