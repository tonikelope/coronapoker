/*
 * Copyright (C) 2026 tonikelope
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A named "new game" preset: a saved snapshot of every NewGameDialog setting so
 * a host can keep favourite table configurations and reapply them in one click,
 * exactly like custom blind structures ({@link BlindStructure}). A preset stores
 * the full new-game config — initial blinds, the CHOSEN blind structure (custom
 * levels, or empty for the default ladder), buy-in (fixed/variable + range),
 * rebuy (+ limit/bots/cap), blind increase, blind cap, hand limit, ante, straddle
 * and bot difficulty. Session-only toggles (rabbit, run-it-twice, voice/TTS) are
 * NOT part of a preset: they are switched in-game, not at table creation.
 *
 * <p>The settings blob is a {@code KEY=VALUE#KEY=VALUE...} string (same shape as
 * {@link GameFrame#serializeRecoverSettings()}); parsing is defensive — an
 * unknown/corrupt entry is skipped, never fatal, so a hand-edited or
 * older/newer properties file can never block applying a preset.
 *
 * <p>The registry lives in the shared {@code coronapoker.properties}, keyed by
 * {@link #PROP_COUNT} + {@link #PROP_PREFIX} (mirrors BlindStructure).
 */
public final class GamePreset {

    private static final Logger LOGGER = Logger.getLogger(GamePreset.class.getName());

    public static final String PROP_COUNT = "game_presets.count";
    public static final String PROP_PREFIX = "game_preset.";
    public static final int MAX_PRESETS = 100;

    private final String name;
    private final String settings;

    public GamePreset(String name, String settings) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("preset name must not be blank");
        }
        this.name = name.trim();
        this.settings = settings != null ? settings : "";
    }

    public String getName() {
        return name;
    }

    public String getSettings() {
        return settings;
    }

    // ----- settings (de)serialization -----------------------------------------

    /**
     * Captures the CURRENT new-game settings (from {@link GameFrame} static fields
     * and {@link Bot#DIFFICULTY}) into a blob. Call after the dialog has pushed the
     * controls into GameFrame, or read the live config directly.
     */
    public static String serializeCurrentSettings() {
        return "SB=" + GameFrame.CIEGA_PEQUEÑA
                + "#BG=" + GameFrame.CIEGA_GRANDE
                // Estructura de ciegas elegida: CSV de niveles (sb/bb) o vacio = escalera
                // por defecto. Imprescindible para que un preset reproduzca la estructura.
                + "#STRUCT=" + (GameFrame.ACTIVE_BLIND_STRUCTURE != null ? BlindStructure.levelsToString(GameFrame.ACTIVE_BLIND_STRUCTURE) : "")
                + "#BUYIN=" + GameFrame.BUYIN
                + "#FIXED=" + (GameFrame.FIXED_BUYIN ? "1" : "0")
                + "#BMIN=" + GameFrame.BUYIN_MIN_BB
                + "#BMAX=" + GameFrame.BUYIN_MAX_BB
                + "#REBUY=" + (GameFrame.REBUY ? "1" : "0")
                + "#RLIM=" + GameFrame.REBUY_LIMIT
                + "#BOTRB=" + (GameFrame.BOT_REBUY ? "1" : "0")
                + "#RCAP=" + GameFrame.REBUY_CAP_POLICY
                + "#DBL=" + GameFrame.CIEGAS_DOUBLE
                + "#DTYPE=" + GameFrame.CIEGAS_DOUBLE_TYPE
                + "#BCAP=" + GameFrame.BLIND_CAP
                + "#MANOS=" + GameFrame.MANOS
                + "#ANTE=" + (GameFrame.ANTE ? "1" : "0")
                + "#STR=" + (GameFrame.STRADDLE ? "1" : "0")
                + "#DIFF=" + Bot.DIFFICULTY.name();
    }

    /**
     * Applies a preset blob to the live {@link GameFrame} new-game settings. Each
     * key is parsed defensively (a bad value leaves that field untouched). After
     * this the NewGameDialog should refresh its controls from GameFrame.
     */
    public void apply() {
        applySettings(this.settings);
    }

    public static void applySettings(String blob) {
        if (blob == null || blob.isEmpty()) {
            return;
        }
        for (String pair : blob.split("#")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = pair.substring(0, eq);
            String val = pair.substring(eq + 1);
            try {
                switch (key) {
                    case "SB":
                        GameFrame.CIEGA_PEQUEÑA = Double.parseDouble(val);
                        break;
                    case "BG":
                        GameFrame.CIEGA_GRANDE = Double.parseDouble(val);
                        break;
                    case "STRUCT":
                        GameFrame.ACTIVE_BLIND_STRUCTURE = (val == null || val.isEmpty())
                                ? null : BlindStructure.parseValidatedLevels(val);
                        break;
                    case "BUYIN":
                        GameFrame.BUYIN = Integer.parseInt(val);
                        break;
                    case "FIXED":
                        GameFrame.FIXED_BUYIN = "1".equals(val);
                        break;
                    case "BMIN":
                        GameFrame.BUYIN_MIN_BB = Integer.parseInt(val);
                        break;
                    case "BMAX":
                        GameFrame.BUYIN_MAX_BB = Integer.parseInt(val);
                        break;
                    case "REBUY":
                        GameFrame.REBUY = "1".equals(val);
                        break;
                    case "RLIM":
                        GameFrame.REBUY_LIMIT = Integer.parseInt(val);
                        break;
                    case "BOTRB":
                        GameFrame.BOT_REBUY = "1".equals(val);
                        break;
                    case "RCAP":
                        GameFrame.REBUY_CAP_POLICY = Integer.parseInt(val);
                        break;
                    case "DBL":
                        GameFrame.CIEGAS_DOUBLE = Integer.parseInt(val);
                        break;
                    case "DTYPE":
                        GameFrame.CIEGAS_DOUBLE_TYPE = Integer.parseInt(val);
                        break;
                    case "BCAP":
                        GameFrame.BLIND_CAP = Double.parseDouble(val);
                        break;
                    case "MANOS":
                        GameFrame.MANOS = Integer.parseInt(val);
                        break;
                    case "ANTE":
                        GameFrame.ANTE = "1".equals(val);
                        break;
                    case "STR":
                        GameFrame.STRADDLE = "1".equals(val);
                        break;
                    case "DIFF":
                        // "EXPERT" es un valor legacy del esquema de 4 niveles -> HARD.
                        Bot.DIFFICULTY = "EXPERT".equals(val)
                                ? Bot.Difficulty.HARD : Bot.Difficulty.valueOf(val);
                        break;
                    default:
                        break;
                }
            } catch (IllegalArgumentException ignore) {
                LOGGER.log(Level.WARNING, "Skipping unparseable preset entry: {0}", pair);
            }
        }
    }

    // ----- registry persistence (mirrors BlindStructure) ----------------------

    /**
     * Reads all presets from the given properties, preserving order. Malformed
     * entries and duplicate names are skipped, never fatal.
     *
     * @return name -&gt; preset, in stored order
     */
    public static LinkedHashMap<String, GamePreset> readFrom(Properties props) {
        LinkedHashMap<String, GamePreset> out = new LinkedHashMap<>();
        if (props == null) {
            return out;
        }
        int count;
        try {
            count = Integer.parseInt(props.getProperty(PROP_COUNT, "0").trim());
        } catch (NumberFormatException ex) {
            return out;
        }
        count = Math.max(0, Math.min(count, MAX_PRESETS));
        for (int i = 0; i < count; i++) {
            String name = props.getProperty(PROP_PREFIX + i + ".name");
            String settings = props.getProperty(PROP_PREFIX + i + ".settings");
            if (name == null || name.trim().isEmpty() || settings == null) {
                continue;
            }
            GamePreset p = new GamePreset(name, settings);
            if (!out.containsKey(p.getName())) {
                out.put(p.getName(), p);
            }
        }
        return out;
    }

    /**
     * Writes the given presets to the properties, replacing any previously stored
     * set (so renames and deletions persist). Does NOT flush to disk.
     */
    public static void writeTo(Properties props, Collection<GamePreset> presets) {
        if (props == null) {
            return;
        }
        ArrayList<String> stale = new ArrayList<>();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith(PROP_PREFIX) || key.equals(PROP_COUNT)) {
                stale.add(key);
            }
        }
        for (String key : stale) {
            props.remove(key);
        }
        int i = 0;
        for (GamePreset p : presets) {
            if (i >= MAX_PRESETS) {
                LOGGER.log(Level.WARNING, "Truncating stored game presets to the {0} cap", MAX_PRESETS);
                break;
            }
            props.setProperty(PROP_PREFIX + i + ".name", p.getName());
            props.setProperty(PROP_PREFIX + i + ".settings", p.getSettings());
            i++;
        }
        props.setProperty(PROP_COUNT, String.valueOf(i));
    }

    /**
     * Loads presets from the shared {@code coronapoker.properties}.
     */
    public static LinkedHashMap<String, GamePreset> loadAll() {
        return readFrom(Helpers.PROPERTIES);
    }

    /**
     * Persists presets to the shared {@code coronapoker.properties} and flushes.
     */
    public static synchronized void saveAll(Collection<GamePreset> presets) {
        writeTo(Helpers.PROPERTIES, presets);
        Helpers.savePropertiesFile();
    }
}
