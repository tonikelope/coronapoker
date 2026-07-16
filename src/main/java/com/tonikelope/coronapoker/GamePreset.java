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
 * rebuy (+ limit/bots/cap), blind increase, blind cap, hand limit, ante, straddle,
 * the game rules (IWTSTH, run-it-twice, rabbit hunting) and bot difficulty. The
 * host's global voice/TTS toggles are NOT part of a preset (they are audio session
 * preferences, switched in-game, not table-creation config).
 *
 * <p>The dialog maps its controls to/from the {@link Settings} carrier; a preset
 * just persists that carrier's {@link Settings#serialize() serialized form}. As a
 * staging carrier it does not commit to GameFrame on its own — the dialog stages
 * everything in its controls and only writes to GameFrame when the host accepts
 * (presets follow the same rule, so loading one and cancelling is a no-op). The two
 * explicit bridges {@link Settings#fromGameFrame()} and
 * {@link Settings#applyToGameFrame(boolean)} are the controlled exceptions, used by
 * the waiting-room "Partida" tab (and its peer-to-peer mirror) to read/commit the
 * static GameFrame config when there is no running game/Crupier.
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
        public boolean iwtsth = false;        // regla "Quiero ver la mano"
        public boolean runItTwice = false;    // ALL-IN run it twice
        public int rabbit = 0;                // 0=off 1=free 2=free+sb 3=free+sb+bb
        public int thinkTime = GameFrame.DEFAULT_THINK_TIME; // tiempo de pensar en segundos
        public boolean thinkTimeEnabled = true;       // false = sin limite de tiempo
        public int showdownTime = GameFrame.DEFAULT_SHOWDOWN_TIME; // pausa del showdown en segundos
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
                    + "#IWTSTH=" + (iwtsth ? "1" : "0")
                    + "#RIT=" + (runItTwice ? "1" : "0")
                    + "#RABBIT=" + rabbit
                    + "#THINKT=" + thinkTime
                    + "#THINKON=" + (thinkTimeEnabled ? "1" : "0")
                    + "#SHOWDOWN=" + showdownTime
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
                        case "IWTSTH":
                            s.iwtsth = "1".equals(val);
                            break;
                        case "RIT":
                            s.runItTwice = "1".equals(val);
                            break;
                        case "RABBIT":
                            s.rabbit = Integer.parseInt(val);
                            break;
                        case "THINKT":
                            s.thinkTime = Integer.parseInt(val);
                            break;
                        case "THINKON":
                            s.thinkTimeEnabled = "1".equals(val);
                            break;
                        case "SHOWDOWN":
                            s.showdownTime = Integer.parseInt(val);
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

        /**
         * Reads the current static {@link GameFrame} game config into a Settings. Used
         * by the waiting-room "Partida" tab (server side) and by the peer-to-peer mirror
         * to ship the full config to clients. Only READS GameFrame; never mutates it.
         */
        public static Settings fromGameFrame() {
            Settings s = new Settings();
            s.smallBlind = GameFrame.CIEGA_PEQUEÑA;
            s.bigBlind = GameFrame.CIEGA_GRANDE;
            s.structure = GameFrame.ACTIVE_BLIND_STRUCTURE;
            s.buyin = GameFrame.BUYIN;
            s.fixedBuyin = GameFrame.FIXED_BUYIN;
            s.minBb = GameFrame.BUYIN_MIN_BB;
            s.maxBb = GameFrame.BUYIN_MAX_BB;
            s.rebuy = GameFrame.REBUY;
            s.rebuyLimit = GameFrame.REBUY_LIMIT;
            s.botRebuy = GameFrame.BOT_REBUY;
            s.rebuyCapPolicy = GameFrame.REBUY_CAP_POLICY;
            s.doubleEvery = GameFrame.CIEGAS_DOUBLE;
            s.doubleType = GameFrame.CIEGAS_DOUBLE_TYPE;
            s.blindCap = GameFrame.BLIND_CAP;
            s.handLimit = GameFrame.MANOS;
            s.ante = GameFrame.ANTE;
            s.straddle = GameFrame.STRADDLE;
            s.iwtsth = GameFrame.IWTSTH_RULE;
            s.runItTwice = GameFrame.RUN_IT_TWICE;
            s.rabbit = GameFrame.RABBIT_HUNTING;
            s.thinkTime = GameFrame.THINK_TIME;
            s.thinkTimeEnabled = GameFrame.THINK_TIME_ENABLED;
            s.showdownTime = GameFrame.SHOWDOWN_TIME;
            s.difficulty = Bot.DIFFICULTY;
            return s;
        }

        /**
         * Commits this Settings into the static {@link GameFrame} game config. Mirror of
         * the old {@code NewGameDialog} UPDATE branch: direct static assignments (NOT the
         * in-game setters {@code setIwtsthRule/...}, which assume a live Crupier/broadcast).
         * Bot difficulty is server-local, so it is only applied when {@code partida_local}.
         * Called ONLY on the host's GUARDAR in the waiting room (never from a client).
         */
        public void applyToGameFrame(boolean partida_local) {
            GameFrame.MANOS = handLimit;
            GameFrame.REBUY = rebuy;
            GameFrame.ANTE = ante;
            GameFrame.STRADDLE = straddle;
            GameFrame.IWTSTH_RULE = iwtsth;
            GameFrame.RUN_IT_TWICE = runItTwice;
            GameFrame.RABBIT_HUNTING = rabbit;
            // Clamp defensivo: un preset hand-editado o un blob antiguo/corrupto no debe
            // meter un tiempo de pensar fuera de rango (el spinner ya acota 10-120).
            GameFrame.THINK_TIME = Math.max(GameFrame.THINK_TIME_MIN, Math.min(GameFrame.THINK_TIME_MAX, thinkTime));
            GameFrame.THINK_TIME_ENABLED = thinkTimeEnabled;
            // Clamp defensivo: un preset hand-editado o un blob antiguo/corrupto no debe meter una
            // pausa de showdown fuera de rango (el spinner ya acota 5-30).
            GameFrame.SHOWDOWN_TIME = Math.max(GameFrame.SHOWDOWN_TIME_MIN, Math.min(GameFrame.SHOWDOWN_TIME_MAX, showdownTime));
            GameFrame.BOT_REBUY = botRebuy;
            GameFrame.REBUY_LIMIT = rebuyLimit;
            GameFrame.BLIND_CAP = blindCap;
            GameFrame.BUYIN = buyin;
            GameFrame.FIXED_BUYIN = fixedBuyin;
            GameFrame.BUYIN_MIN_BB = minBb;
            GameFrame.BUYIN_MAX_BB = maxBb;
            GameFrame.REBUY_CAP_POLICY = rebuyCapPolicy;
            GameFrame.CIEGA_GRANDE = bigBlind;
            GameFrame.CIEGA_PEQUEÑA = smallBlind;
            GameFrame.ACTIVE_BLIND_STRUCTURE = structure;
            GameFrame.CIEGAS_DOUBLE = doubleEvery;
            GameFrame.CIEGAS_DOUBLE_TYPE = doubleType;
            if (partida_local) {
                Bot.DIFFICULTY = difficulty;
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
