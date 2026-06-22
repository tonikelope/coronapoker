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
 * levels, or none for the default ladder), buy-in (fixed/variable + range),
 * rebuy (+ limit/bots/cap), blind increase, blind cap, hand limit, ante, straddle
 * and bot difficulty. Session-only toggles (rabbit, run-it-twice, voice/TTS) are
 * NOT part of a preset: they are switched in-game, not at table creation.
 *
 * <p>The dialog maps its controls to/from the {@link Settings} carrier; a preset
 * just persists that carrier's {@link Settings#serialize() serialized form}. The
 * carrier never touches {@link GameFrame}: the dialog stages everything in its
 * controls and only commits to GameFrame when the host accepts (presets follow
 * the same rule, so loading one and cancelling is a no-op).
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

    /**
     * The full new-game configuration a preset captures. A plain data carrier with
     * named fields (so the dialog maps its composite controls to/from it without
     * positional-argument mistakes) plus a stable {@code KEY=VALUE#...} storage
     * form. Field defaults mirror the {@link GameFrame} new-game defaults so a
     * partially-populated blob still yields a sane configuration.
     */
    public static final class Settings {

        public double smallBlind = 0.10;
        public double bigBlind = 0.20;
        // Estructura de ciegas elegida: niveles personalizados, o null = escalera por
        // defecto. Es lo que hace que un preset reproduzca la estructura completa.
        public double[][] structure = null;
        public int buyin = 10;
        public boolean fixedBuyin = true;
        public int minBb = BuyinRules.DEFAULT_MIN_BB;
        public int maxBb = BuyinRules.DEFAULT_MAX_BB;
        public boolean rebuy = true;
        public int rebuyLimit = 0;
        public boolean botRebuy = true;
        public int rebuyCapPolicy = GameFrame.REBUY_CAP_BUYIN;
        public int doubleEvery = 0; // 0 = no se doblan las ciegas
        public int doubleType = 1;  // 1 = minutos, 2 = manos
        public double blindCap = 0; // 0 = sin tope
        public int handLimit = -1;  // -1 = sin limite de manos
        public boolean ante = false;
        public boolean straddle = false;
        public Bot.Difficulty difficulty = Bot.Difficulty.MEDIUM;

        /**
         * Serializes to the {@code KEY=VALUE#...} storage form (locale-independent).
         */
        public String serialize() {
            return "SB=" + smallBlind
                    + "#BG=" + bigBlind
                    + "#STRUCT=" + (structure != null ? BlindStructure.levelsToString(structure) : "")
                    + "#BUYIN=" + buyin
                    + "#FIXED=" + (fixedBuyin ? "1" : "0")
                    + "#BMIN=" + minBb
                    + "#BMAX=" + maxBb
                    + "#REBUY=" + (rebuy ? "1" : "0")
                    + "#RLIM=" + rebuyLimit
                    + "#BOTRB=" + (botRebuy ? "1" : "0")
                    + "#RCAP=" + rebuyCapPolicy
                    + "#DBL=" + doubleEvery
                    + "#DTYPE=" + doubleType
                    + "#BCAP=" + blindCap
                    + "#MANOS=" + handLimit
                    + "#ANTE=" + (ante ? "1" : "0")
                    + "#STR=" + (straddle ? "1" : "0")
                    + "#DIFF=" + difficulty.name();
        }

        /**
         * Parses a stored blob back into a Settings. Defensive: an unknown or corrupt
         * entry is skipped (that field keeps its default), never fatal, so a
         * hand-edited or older/newer properties file can never block a preset.
         */
        public static Settings parse(String blob) {
            Settings s = new Settings();
            if (blob == null || blob.isEmpty()) {
                return s;
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
                            s.smallBlind = Double.parseDouble(val);
                            break;
                        case "BG":
                            s.bigBlind = Double.parseDouble(val);
                            break;
                        case "STRUCT":
                            s.structure = val.isEmpty() ? null : BlindStructure.parseValidatedLevels(val);
                            break;
                        case "BUYIN":
                            s.buyin = Integer.parseInt(val);
                            break;
                        case "FIXED":
                            s.fixedBuyin = "1".equals(val);
                            break;
                        case "BMIN":
                            s.minBb = Integer.parseInt(val);
                            break;
                        case "BMAX":
                            s.maxBb = Integer.parseInt(val);
                            break;
                        case "REBUY":
                            s.rebuy = "1".equals(val);
                            break;
                        case "RLIM":
                            s.rebuyLimit = Integer.parseInt(val);
                            break;
                        case "BOTRB":
                            s.botRebuy = "1".equals(val);
                            break;
                        case "RCAP":
                            s.rebuyCapPolicy = Integer.parseInt(val);
                            break;
                        case "DBL":
                            s.doubleEvery = Integer.parseInt(val);
                            break;
                        case "DTYPE":
                            s.doubleType = Integer.parseInt(val);
                            break;
                        case "BCAP":
                            s.blindCap = Double.parseDouble(val);
                            break;
                        case "MANOS":
                            s.handLimit = Integer.parseInt(val);
                            break;
                        case "ANTE":
                            s.ante = "1".equals(val);
                            break;
                        case "STR":
                            s.straddle = "1".equals(val);
                            break;
                        case "DIFF":
                            // "EXPERT" es un valor legacy del esquema de 4 niveles -> HARD.
                            s.difficulty = "EXPERT".equals(val)
                                    ? Bot.Difficulty.HARD : Bot.Difficulty.valueOf(val);
                            break;
                        default:
                            break;
                    }
                } catch (IllegalArgumentException ignore) {
                    LOGGER.log(Level.WARNING, "Skipping unparseable preset entry: {0}", pair);
                }
            }
            return s;
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
