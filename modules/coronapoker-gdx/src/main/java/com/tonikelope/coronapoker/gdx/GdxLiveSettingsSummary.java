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
import java.util.Objects;

/**
 * Read-only labels for session settings that Swing exposes as information
 * once a table has started. Keeping this outside the renderer makes it
 * impossible for these rows to mutate the canonical game configuration.
 */
final class GdxLiveSettingsSummary {

    private static final String PURCHASE_HEADING
            = "FIJADO AL CREAR LA TIMBA  \u00b7  SOLO LECTURA";

    private static final List<String> UNAVAILABLE_PURCHASE_LABELS = List.of(
            "COMPRA INICIAL  \u00b7  NO DISPONIBLE",
            "BUY-IN  \u00b7  NO DISPONIBLE",
            "RANGO DE COMPRA  \u00b7  NO DISPONIBLE",
            "RECOMPRA  \u00b7  NO DISPONIBLE",
            "L\u00cdMITE POR JUGADOR  \u00b7  NO DISPONIBLE",
            "TOPE DE RECOMPRA  \u00b7  NO DISPONIBLE");

    private GdxLiveSettingsSummary() {
    }

    static List<String> timingLabels(
            GameConfigCodecV1.Configuration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        return List.of(
                "TIEMPO DE PENSAR  \u00b7  "
                + (configuration.thinkTimeEnabled()
                        ? configuration.thinkTime() + " S"
                        : "DESACTIVADO"),
                "TIEMPO DE SHOWDOWN  \u00b7  "
                + configuration.showdownTime() + " S");
    }

    static List<String> purchaseLabels(
            GameConfigCodecV1.Configuration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        return List.of(
                "COMPRA INICIAL  \u00b7  " + configuration.buyin(),
                "BUY-IN  \u00b7  " + (configuration.fixedBuyin()
                        ? "FIJO" : "VARIABLE"),
                "RANGO DE COMPRA  \u00b7  " + configuration.buyinMinBb()
                + " \u2013 " + configuration.buyinMaxBb() + " BB",
                "RECOMPRA  \u00b7  " + (configuration.rebuy()
                        ? "ACTIVADA" : "DESACTIVADA"),
                "L\u00cdMITE POR JUGADOR  \u00b7  "
                + (configuration.rebuyLimit() > 0
                        ? Integer.toString(configuration.rebuyLimit())
                        : "SIN L\u00cdMITE"),
                "TOPE DE RECOMPRA  \u00b7  "
                + (configuration.rebuyCapPolicy() == 0
                        ? "BUY-IN" : "STACK M\u00c1S ALTO"));
    }

    static List<String> unavailablePurchaseLabels() {
        return UNAVAILABLE_PURCHASE_LABELS;
    }

    static String purchaseHeading() {
        return PURCHASE_HEADING;
    }

    /** Swing's canonical Rabbit Hunting levels, shared by every GDX context. */
    static String rabbitHuntingLabel(int value) {
        return switch (value) {
            case 0 -> "DESACTIVADO";
            case 1 -> "GRATIS";
            case 2 -> "GRATIS + SB";
            case 3 -> "GRATIS + SB + BB";
            default -> "NO DISPONIBLE";
        };
    }
}
