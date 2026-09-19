package com.tonikelope.coronapoker.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Complete staged table configuration used by NewGameDialog frontends. */
public final class NewGameTableDraft {

    public static final int MIN_BUYIN_BB = 10;
    public static final int MAX_BUYIN_BB = 500;
    public static final int MIN_THINK_SECONDS = 10;
    public static final int MAX_THINK_SECONDS = 120;
    public static final int MIN_SHOWDOWN_SECONDS = 5;
    public static final int MAX_SHOWDOWN_SECONDS = 30;

    public enum BlindIncreaseType { MINUTES, HANDS }
    public enum RebuyCapPolicy { BUY_IN, HIGHEST_STACK }
    public enum RabbitHunting { OFF, FREE, FREE_SMALL_BLIND, FREE_SMALL_AND_BIG_BLIND }
    public enum BotDifficulty { EASY, MEDIUM, HARD }

    public record BlindLevel(double smallBlind, double bigBlind) {
        public BlindLevel {
            String error = BlindStructureRules.validateLevels(
                    new double[][]{{smallBlind, bigBlind}});
            if (error != null) {
                throw new IllegalArgumentException(error);
            }
        }
    }

    private String structureName;
    private List<BlindLevel> blindLevels;
    private int blindLevelIndex;
    private boolean increaseBlinds;
    private BlindIncreaseType blindIncreaseType = BlindIncreaseType.MINUTES;
    private int blindInterval = 60;
    private boolean blindCap;
    private int blindCapRaises = 5;
    private boolean fixedBuyin = true;
    private int buyin = 10;
    private int minBuyinBb = 10;
    private int maxBuyinBb = 100;
    private boolean rebuy = true;
    private boolean rebuyLimit;
    private int rebuyLimitCount = 3;
    private boolean botRebuy = true;
    private boolean botBalanceToHumans;
    private RebuyCapPolicy rebuyCapPolicy = RebuyCapPolicy.BUY_IN;
    private boolean handLimit;
    private int handLimitCount = 100;
    private boolean thinkTime = true;
    private int thinkSeconds = 40;
    private int showdownSeconds = 10;
    private boolean ante;
    private boolean straddle;
    private boolean iwtsth;
    private boolean runItTwice;
    private RabbitHunting rabbitHunting = RabbitHunting.OFF;
    private BotDifficulty botDifficulty = BotDifficulty.MEDIUM;
    private boolean economyLocked;

    public NewGameTableDraft() {
        setBlindStructure(null, levels(BlindStructureRules.defaultLevels()), 0);
    }

    /** Rehydrates an editable draft from an immutable frontend/core handoff. */
    public static NewGameTableDraft from(Settings settings) {
        Objects.requireNonNull(settings, "settings");
        NewGameTableDraft draft = new NewGameTableDraft();
        draft.setBlindStructure(settings.structureName(), settings.blindLevels(),
                settings.blindLevelIndex());
        draft.setIncreaseBlinds(settings.increaseBlinds());
        draft.setBlindIncreaseType(settings.blindIncreaseType());
        draft.setBlindInterval(settings.blindInterval());
        draft.setBlindCap(settings.blindCap());
        draft.setBlindCapRaises(settings.blindCapRaises());
        draft.setMaxBuyinBb(settings.maxBuyinBb());
        draft.setMinBuyinBb(settings.minBuyinBb());
        draft.setFixedBuyin(settings.fixedBuyin());
        draft.setBuyin(settings.buyin());
        draft.setRebuy(settings.rebuy());
        draft.setRebuyLimit(settings.rebuyLimit());
        draft.setRebuyLimitCount(settings.rebuyLimitCount());
        draft.setBotRebuy(settings.botRebuy());
        draft.setBotBalanceToHumans(settings.botBalanceToHumans());
        draft.setRebuyCapPolicy(settings.rebuyCapPolicy());
        draft.setHandLimit(settings.handLimit());
        draft.setHandLimitCount(settings.handLimitCount());
        draft.setThinkTime(settings.thinkTime());
        draft.setThinkSeconds(settings.thinkSeconds());
        draft.setShowdownSeconds(settings.showdownSeconds());
        draft.setAnte(settings.ante());
        draft.setStraddle(settings.straddle());
        draft.setIwtsth(settings.iwtsth());
        draft.setRunItTwice(settings.runItTwice());
        draft.setRabbitHunting(settings.rabbitHunting());
        draft.setBotDifficulty(settings.botDifficulty());
        return draft;
    }

    public String structureName() { return structureName; }
    public List<BlindLevel> blindLevels() { return blindLevels; }
    public int blindLevelIndex() { return blindLevelIndex; }
    public BlindLevel blindLevel() { return blindLevels.get(blindLevelIndex); }

    public void setBlindStructure(String name, List<BlindLevel> levels, int selectedIndex) {
        requireEconomyEditable();
        Objects.requireNonNull(levels, "levels");
        double[][] raw = new double[levels.size()][2];
        for (int i = 0; i < levels.size(); i++) {
            BlindLevel level = Objects.requireNonNull(levels.get(i), "blind level");
            raw[i][0] = level.smallBlind();
            raw[i][1] = level.bigBlind();
        }
        String error = BlindStructureRules.validateLevels(raw);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        structureName = name == null || name.isBlank() ? null : name.trim();
        blindLevels = List.copyOf(levels);
        blindLevelIndex = clamp(selectedIndex, 0, blindLevels.size() - 1);
        clampDependentValues();
    }

    public void setBlindLevelIndex(int value) {
        requireEconomyEditable();
        blindLevelIndex = clamp(value, 0, blindLevels.size() - 1);
        clampDependentValues();
    }

    public boolean increaseBlinds() { return increaseBlinds; }
    public void setIncreaseBlinds(boolean value) { requireEconomyEditable(); increaseBlinds = value; }
    public BlindIncreaseType blindIncreaseType() { return blindIncreaseType; }
    public void setBlindIncreaseType(BlindIncreaseType value) { requireEconomyEditable(); blindIncreaseType = Objects.requireNonNull(value); }
    public int blindInterval() { return blindInterval; }
    public void setBlindInterval(int value) { requireEconomyEditable(); blindInterval = Math.max(1, value); }
    public boolean blindCap() { return blindCap; }
    public void setBlindCap(boolean value) { requireEconomyEditable(); blindCap = value; }
    public int blindCapRaises() { return blindCapRaises; }
    public void setBlindCapRaises(int value) { requireEconomyEditable(); blindCapRaises = clamp(value, 1, maxBlindCapRaises()); }
    public int maxBlindCapRaises() { return Math.max(1, blindLevels.size() - 1 - blindLevelIndex); }
    public BlindLevel blindCapLevel() { return blindLevels.get(Math.min(blindLevels.size() - 1, blindLevelIndex + blindCapRaises)); }
    public boolean blindCapControlEnabled() { return increaseBlinds; }
    public boolean blindCapRaisesEnabled() { return increaseBlinds && blindCap; }

    public boolean fixedBuyin() { return fixedBuyin; }
    public void setFixedBuyin(boolean value) { requireEconomyEditable(); fixedBuyin = value; }
    public int buyin() { return buyin; }
    public void setBuyin(int value) { requireEconomyEditable(); buyin = clamp(value, minimumBuyin(), maximumBuyin()); }
    public int minBuyinBb() { return minBuyinBb; }
    public void setMinBuyinBb(int value) {
        requireEconomyEditable();
        minBuyinBb = clamp(value, MIN_BUYIN_BB, maxBuyinBb);
        clampDependentValues();
    }
    public int maxBuyinBb() { return maxBuyinBb; }
    public void setMaxBuyinBb(int value) {
        requireEconomyEditable();
        maxBuyinBb = clamp(value, minBuyinBb, MAX_BUYIN_BB);
        clampDependentValues();
    }
    public int minimumBuyin() { return wholeUnitsCeiling(blindLevel().bigBlind(), minBuyinBb); }
    public int maximumBuyin() { return wholeUnitsFloor(blindLevel().bigBlind(), maxBuyinBb); }

    public boolean rebuy() { return rebuy; }
    public void setRebuy(boolean value) { rebuy = value; }
    public boolean rebuyLimit() { return rebuyLimit; }
    public void setRebuyLimit(boolean value) { rebuyLimit = value; }
    public int rebuyLimitCount() { return rebuyLimitCount; }
    public void setRebuyLimitCount(int value) { rebuyLimitCount = Math.max(1, value); }
    public boolean botRebuy() { return botRebuy; }
    public void setBotRebuy(boolean value) { botRebuy = value; }
    public boolean botBalanceToHumans() { return botBalanceToHumans; }
    public void setBotBalanceToHumans(boolean value) { botBalanceToHumans = value; }
    public RebuyCapPolicy rebuyCapPolicy() { return rebuyCapPolicy; }
    public void setRebuyCapPolicy(RebuyCapPolicy value) { rebuyCapPolicy = Objects.requireNonNull(value); }
    public boolean rebuyLimitEnabled() { return rebuy; }
    public boolean rebuyLimitCountEnabled() { return rebuy && rebuyLimit; }
    public boolean botRebuyEnabled() { return rebuy; }

    public boolean handLimit() { return handLimit; }
    public void setHandLimit(boolean value) { handLimit = value; }
    public int handLimitCount() { return handLimitCount; }
    public void setHandLimitCount(int value) { handLimitCount = Math.max(1, value); }
    public boolean thinkTime() { return thinkTime; }
    public void setThinkTime(boolean value) { thinkTime = value; }
    public int thinkSeconds() { return thinkSeconds; }
    public void setThinkSeconds(int value) { thinkSeconds = clamp(value, MIN_THINK_SECONDS, MAX_THINK_SECONDS); }
    public int showdownSeconds() { return showdownSeconds; }
    public void setShowdownSeconds(int value) { showdownSeconds = clamp(value, MIN_SHOWDOWN_SECONDS, MAX_SHOWDOWN_SECONDS); }
    public boolean ante() { return ante; }
    public void setAnte(boolean value) { requireEconomyEditable(); ante = value; }
    public boolean straddle() { return straddle; }
    public void setStraddle(boolean value) { requireEconomyEditable(); straddle = value; }
    public boolean iwtsth() { return iwtsth; }
    public void setIwtsth(boolean value) { iwtsth = value; }
    public boolean runItTwice() { return runItTwice; }
    public void setRunItTwice(boolean value) { runItTwice = value; }
    public RabbitHunting rabbitHunting() { return rabbitHunting; }
    public void setRabbitHunting(RabbitHunting value) { rabbitHunting = Objects.requireNonNull(value); }
    public BotDifficulty botDifficulty() { return botDifficulty; }
    public void setBotDifficulty(BotDifficulty value) { botDifficulty = Objects.requireNonNull(value); }

    public boolean economyLocked() { return economyLocked; }
    public void setEconomyLocked(boolean value) { economyLocked = value; }

    public Settings snapshot() {
        return new Settings(structureName, blindLevels, blindLevelIndex,
                increaseBlinds, blindIncreaseType, blindInterval, blindCap,
                blindCapRaises, fixedBuyin, buyin, minBuyinBb, maxBuyinBb,
                rebuy, rebuyLimit, rebuyLimitCount, botRebuy,
                botBalanceToHumans, rebuyCapPolicy, handLimit, handLimitCount,
                thinkTime, thinkSeconds, showdownSeconds, ante, straddle,
                iwtsth, runItTwice, rabbitHunting, botDifficulty);
    }

    private void clampDependentValues() {
        blindCapRaises = clamp(blindCapRaises, 1, maxBlindCapRaises());
        buyin = clamp(buyin, minimumBuyin(), maximumBuyin());
    }

    private void requireEconomyEditable() {
        if (economyLocked) {
            throw new IllegalStateException("Recovered game economy is locked");
        }
    }

    private static int wholeUnitsCeiling(double bigBlind, int bigBlinds) {
        long cents = Math.round(bigBlind * 100d) * bigBlinds;
        return Math.toIntExact((cents + 99L) / 100L);
    }

    private static int wholeUnitsFloor(double bigBlind, int bigBlinds) {
        long cents = Math.round(bigBlind * 100d) * bigBlinds;
        return Math.toIntExact(cents / 100L);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static List<BlindLevel> levels(double[][] raw) {
        List<BlindLevel> result = new ArrayList<>(raw.length);
        for (double[] level : raw) {
            result.add(new BlindLevel(level[0], level[1]));
        }
        return result;
    }

    public record Settings(String structureName, List<BlindLevel> blindLevels,
            int blindLevelIndex, boolean increaseBlinds,
            BlindIncreaseType blindIncreaseType, int blindInterval,
            boolean blindCap, int blindCapRaises, boolean fixedBuyin, int buyin,
            int minBuyinBb, int maxBuyinBb, boolean rebuy, boolean rebuyLimit,
            int rebuyLimitCount, boolean botRebuy, boolean botBalanceToHumans,
            RebuyCapPolicy rebuyCapPolicy, boolean handLimit, int handLimitCount,
            boolean thinkTime, int thinkSeconds, int showdownSeconds,
            boolean ante, boolean straddle, boolean iwtsth, boolean runItTwice,
            RabbitHunting rabbitHunting, BotDifficulty botDifficulty) {
        public Settings {
            blindLevels = List.copyOf(blindLevels);
        }

        public BlindLevel selectedBlindLevel() {
            return blindLevels.get(blindLevelIndex);
        }

        /** Exact KEY=VALUE mirror consumed by the classic waiting-room client. */
        public String serializeForWire() {
            BlindLevel selected = selectedBlindLevel();
            String structure = structureName == null ? "" : serializeLevels(blindLevels);
            int doubleEvery = increaseBlinds ? blindInterval : 0;
            int doubleType = blindIncreaseType == BlindIncreaseType.MINUTES ? 1 : 2;
            double blindCapValue = blindCap
                    ? blindLevels.get(Math.min(blindLevels.size() - 1,
                            blindLevelIndex + blindCapRaises)).bigBlind() : 0d;
            int handLimitValue = handLimit ? handLimitCount : -1;
            int rebuyLimitValue = rebuy && rebuyLimit ? rebuyLimitCount : 0;
            return "SB=" + selected.smallBlind()
                    + "#BG=" + selected.bigBlind()
                    + "#STRUCT=" + structure
                    + (structureName == null ? "" : "#SNAME="
                            + Base64.getUrlEncoder().withoutPadding()
                                    .encodeToString(structureName.getBytes(
                                            StandardCharsets.UTF_8)))
                    + "#BUYIN=" + buyin
                    + "#FIXED=" + bool(fixedBuyin)
                    + "#BMIN=" + minBuyinBb
                    + "#BMAX=" + maxBuyinBb
                    + "#REBUY=" + bool(rebuy)
                    + "#RLIM=" + rebuyLimitValue
                    + "#BOTRB=" + bool(botRebuy)
                    + "#BOTBAL=" + bool(botBalanceToHumans)
                    + "#RCAP=" + (rebuyCapPolicy == RebuyCapPolicy.BUY_IN ? 0 : 1)
                    + "#DBL=" + doubleEvery
                    + "#DTYPE=" + doubleType
                    + "#BCAP=" + blindCapValue
                    + "#MANOS=" + handLimitValue
                    + "#ANTE=" + bool(ante)
                    + "#STR=" + bool(straddle)
                    + "#IWTSTH=" + bool(iwtsth)
                    + "#RIT=" + bool(runItTwice)
                    + "#RABBIT=" + rabbitHunting.ordinal()
                    + "#THINKT=" + thinkSeconds
                    + "#THINKON=" + bool(thinkTime)
                    + "#SHOWDOWN=" + showdownSeconds
                    + "#DIFF=" + botDifficulty.name();
        }

        /** Reads the classic KEY=VALUE mirror into the neutral settings model. */
        public static Settings parseWire(String wire) {
            if (wire == null || wire.isBlank()) {
                throw new IllegalArgumentException("Empty table configuration");
            }
            Map<String, String> values = new HashMap<>();
            for (String pair : wire.split("#")) {
                int separator = pair.indexOf('=');
                if (separator > 0) values.put(pair.substring(0, separator), pair.substring(separator + 1));
            }
            try {
                NewGameTableDraft draft = new NewGameTableDraft();
                String structure = values.getOrDefault("STRUCT", "");
                if (!structure.isEmpty()) {
                    String encodedName = values.get("SNAME");
                    String structureName = encodedName == null
                            ? "wire" : new String(Base64.getUrlDecoder()
                                    .decode(encodedName), StandardCharsets.UTF_8);
                    draft.setBlindStructure(structureName,
                            parseLevels(structure), 0);
                }
                double small = Double.parseDouble(required(values, "SB"));
                double big = Double.parseDouble(required(values, "BG"));
                int selected = 0;
                for (int i = 0; i < draft.blindLevels().size(); i++) {
                    BlindLevel level = draft.blindLevels().get(i);
                    if (Double.compare(level.smallBlind(), small) == 0
                            && Double.compare(level.bigBlind(), big) == 0) {
                        selected = i;
                        break;
                    }
                }
                draft.setBlindLevelIndex(selected);
                draft.setMaxBuyinBb(Integer.parseInt(required(values, "BMAX")));
                draft.setMinBuyinBb(Integer.parseInt(required(values, "BMIN")));
                draft.setFixedBuyin(one(values, "FIXED"));
                draft.setBuyin(Integer.parseInt(required(values, "BUYIN")));
                draft.setRebuy(one(values, "REBUY"));
                int rebuyLimit = Integer.parseInt(required(values, "RLIM"));
                draft.setRebuyLimit(rebuyLimit > 0);
                if (rebuyLimit > 0) draft.setRebuyLimitCount(rebuyLimit);
                draft.setBotRebuy(one(values, "BOTRB"));
                draft.setBotBalanceToHumans(one(values, "BOTBAL"));
                draft.setRebuyCapPolicy("1".equals(values.get("RCAP"))
                        ? RebuyCapPolicy.HIGHEST_STACK : RebuyCapPolicy.BUY_IN);
                int interval = Integer.parseInt(required(values, "DBL"));
                draft.setIncreaseBlinds(interval > 0);
                if (interval > 0) draft.setBlindInterval(interval);
                draft.setBlindIncreaseType("2".equals(values.get("DTYPE"))
                        ? BlindIncreaseType.HANDS : BlindIncreaseType.MINUTES);
                double cap = Double.parseDouble(required(values, "BCAP"));
                if (cap > 0d) {
                    draft.setBlindCap(true);
                    int raises = 1;
                    for (int i = selected + 1; i < draft.blindLevels().size(); i++) {
                        if (Double.compare(draft.blindLevels().get(i).bigBlind(), cap) == 0) {
                            raises = i - selected;
                            break;
                        }
                    }
                    draft.setBlindCapRaises(raises);
                }
                int hands = Integer.parseInt(required(values, "MANOS"));
                draft.setHandLimit(hands >= 0);
                if (hands >= 0) draft.setHandLimitCount(hands);
                draft.setAnte(one(values, "ANTE"));
                draft.setStraddle(one(values, "STR"));
                draft.setIwtsth(one(values, "IWTSTH"));
                draft.setRunItTwice(one(values, "RIT"));
                draft.setRabbitHunting(RabbitHunting.values()[Integer.parseInt(required(values, "RABBIT"))]);
                draft.setThinkSeconds(Integer.parseInt(required(values, "THINKT")));
                draft.setThinkTime(one(values, "THINKON"));
                draft.setShowdownSeconds(Integer.parseInt(required(values, "SHOWDOWN")));
                draft.setBotDifficulty(BotDifficulty.valueOf(required(values, "DIFF")));
                return draft.snapshot();
            } catch (RuntimeException invalid) {
                throw new IllegalArgumentException("Malformed table configuration", invalid);
            }
        }

        /** Compact lobby summary used by the current protocol's NICKOK packet. */
        public String gameInfoForWire() {
            BlindLevel selected = selectedBlindLevel();
            String buyinText = fixedBuyin ? Integer.toString(buyin) + (rebuy ? "" : "*") : "--";
            String blindsText = displayMoney(selected.smallBlind()) + " / " + displayMoney(selected.bigBlind());
            if (increaseBlinds) {
                blindsText += " @ " + blindInterval
                        + (blindIncreaseType == BlindIncreaseType.MINUTES ? "'" : "*");
            }
            return buyinText + "|" + blindsText + (handLimit ? "|" + handLimitCount : "");
        }

        private static String serializeLevels(List<BlindLevel> levels) {
            StringBuilder result = new StringBuilder();
            for (BlindLevel level : levels) {
                if (!result.isEmpty()) result.append(',');
                result.append(number(level.smallBlind())).append('/').append(number(level.bigBlind()));
            }
            return result.toString();
        }

        private static List<BlindLevel> parseLevels(String wire) {
            List<BlindLevel> levels = new ArrayList<>();
            for (String token : wire.split(",")) {
                String[] pair = token.split("/", -1);
                if (pair.length != 2) throw new IllegalArgumentException("Malformed blind structure");
                levels.add(new BlindLevel(Double.parseDouble(pair[0]), Double.parseDouble(pair[1])));
            }
            return levels;
        }

        private static String required(Map<String, String> values, String key) {
            String value = values.get(key);
            if (value == null) throw new IllegalArgumentException("Missing " + key);
            return value;
        }

        private static boolean one(Map<String, String> values, String key) {
            return "1".equals(required(values, key));
        }

        private static String number(double value) {
            return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
        }

        private static String displayMoney(double value) {
            String formatted = String.format(java.util.Locale.ROOT, "%.2f", value);
            return formatted.endsWith(".00") ? formatted.substring(0, formatted.length() - 3) : formatted;
        }

        private static int bool(boolean value) {
            return value ? 1 : 0;
        }
    }
}
