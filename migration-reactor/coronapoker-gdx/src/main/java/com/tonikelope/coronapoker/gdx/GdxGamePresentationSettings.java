/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.PreferencesService;
import com.tonikelope.coronapoker.core.game.GamePresentationSettings;
import java.util.Objects;
import java.util.Properties;
import java.util.Locale;

/** Reads the same persisted presentation contract used by the classic client. */
final class GdxGamePresentationSettings implements GamePresentationSettings {

    private final Properties properties;

    GdxGamePresentationSettings(PreferencesService preferences) {
        properties = Objects.requireNonNull(preferences, "preferences")
                .properties();
    }

    private boolean bool(String key, boolean fallback) {
        return Boolean.parseBoolean(properties.getProperty(key,
                Boolean.toString(fallback)));
    }

    private int integer(String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key,
                    Integer.toString(fallback)));
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    private float decimal(String key, float fallback) {
        try {
            return Float.parseFloat(properties.getProperty(key,
                    Float.toString(fallback)));
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    private boolean effects() { return bool("sonido_efectos", true); }

    @Override public String language() {
        return properties.getProperty("lenguaje", "es").toLowerCase(Locale.ROOT);
    }
    @Override public String defaultLanguage() { return "es"; }
    @Override public String deck() {
        return properties.getProperty("baraja", "coronapoker");
    }
    @Override public boolean testMode() {
        return Boolean.getBoolean("coronapoker.testMode");
    }
    @Override public boolean sillySounds() { return bool("sonidos_chorra", false); }
    @Override public boolean ambientMusic() { return bool("sonido_ascensor", true); }
    @Override public boolean showCallCost() { return bool("mostrar_coste_igualar", true); }
    @Override public boolean autoActionButtons() {
        return bool("auto_action_buttons", false) && !testMode();
    }
    @Override public boolean autoActionPersist() { return bool("auto_action_persist", true); }
    @Override public boolean autoRebuyOnBroke() { return false; }
    @Override public boolean autoFullscreen() { return bool("auto_fullscreen", true); }
    @Override public boolean cinematics() {
        return bool("cinematicas", true);
    }
    @Override public boolean allInCinematics() {
        return cinematics() && bool("cinematicas_allin", true);
    }
    @Override public boolean gameOverCinematics() {
        return cinematics() && bool("cinematicas_gameover", true);
    }
    @Override public boolean blindDealerAnimation() {
        return true;
    }
    @Override public boolean betAnimation() {
        return true;
    }
    @Override public boolean counterAnimation() {
        return true;
    }
    @Override public boolean shuffleAnimation() {
        return true;
    }
    @Override public boolean dealAnimation() {
        return true;
    }
    @Override public boolean flipAnimation() {
        return true;
    }
    @Override public boolean swapAnimation() {
        return true;
    }
    @Override public boolean callSound() { return effects() && bool("sonido_igualar", true); }
    @Override public boolean betSound() { return effects() && bool("sonido_apostar", true); }
    @Override public boolean blindSound() { return effects() && bool("sonido_ciegas", true); }
    @Override public boolean shuffleSound() { return effects() && bool("sonido_barajado", true); }
    @Override public boolean dealSound() { return effects() && bool("sonido_reparto", true); }
    @Override public boolean flipSound() { return effects() && bool("sonido_destape", true); }
    @Override public boolean cashSound() { return effects() && bool("sonido_caja", true); }
    @Override public boolean iwtsthSound() { return effects() && bool("sonido_iwtsth", true); }
    @Override public boolean startSound() { return effects() && bool("sonido_inicio", true); }
    @Override public boolean warningSound() { return effects() && bool("sonido_aviso", true); }
    @Override public boolean errorSound() { return effects() && bool("sonido_error", true); }
    @Override public String initialStackFillSound() {
        return effects() && bool("sonido_carga_stacks", true)
                ? "misc/balance_count.wav" : null;
    }
    @Override public String cashRegisterSound() {
        return cashSound() ? "misc/cash_register.wav" : null;
    }
    @Override public int compactView() { return integer("vista_compacta", 0) % 4; }
    @Override public int dealSpeed() { return integer("reparto_velocidad", 100); }
    @Override public int swapAnimationDuration() { return integer("swap_velocidad", 320); }
    @Override public boolean swapAnimationArc() { return bool("swap_arco", false); }
    @Override public float zoomFactor() {
        return Math.max(0.05f, 1f + integer("zoom_level", 0) * 0.05f);
    }
    @Override public float dialogZoomFactor() {
        return Math.max(0.5f, Math.min(2f, decimal("dialog_zoom", 1f)));
    }
}
