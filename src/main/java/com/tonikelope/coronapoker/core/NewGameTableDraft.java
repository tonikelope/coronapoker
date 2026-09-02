package com.tonikelope.coronapoker.core;

import java.util.ArrayList;
import java.util.List;
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
    }
}
