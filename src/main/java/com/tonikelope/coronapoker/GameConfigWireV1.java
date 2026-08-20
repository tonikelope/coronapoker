/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Complete, immutable table configuration sent by the host. There is exactly
 * one accepted wire version: peers running another CoronaPoker version cannot
 * join the table and no legacy/default-filled parser exists.
 */
public final class GameConfigWireV1 {

    private static final int MAGIC = 0x43504743; // CPGC
    private static final int VERSION = 1;
    private static final int CAPABILITIES = 1;
    private static final int MAX_PACKET_BYTES = 64 * 1024;
    private static final int MAX_SESSION_BYTES = 4096;
    private static final int MAX_STRUCTURE_BYTES = 48 * 1024;

    private final int buyin;
    private final double smallBlind;
    private final double bigBlind;
    private final int blindsDouble;
    private final int blindsDoubleType;
    private final boolean recover;
    private final String sessionId;
    private final boolean rebuy;
    private final int hands;
    private final double blindCap;
    private final int rebuyLimit;
    private final boolean botRebuy;
    private final boolean fixedBuyin;
    private final int buyinMinBb;
    private final int buyinMaxBb;
    private final int rebuyCapPolicy;
    private final boolean ante;
    private final boolean straddle;
    private final boolean iwtsth;
    private final boolean runItTwice;
    private final int rabbitHunting;
    private final int thinkTime;
    private final boolean thinkTimeEnabled;
    private final int showdownTime;
    private final boolean botBalanceToHumans;
    private final double[][] blindStructure;

    private GameConfigWireV1(Builder b) {
        buyin = b.buyin;
        smallBlind = b.smallBlind;
        bigBlind = b.bigBlind;
        blindsDouble = b.blindsDouble;
        blindsDoubleType = b.blindsDoubleType;
        recover = b.recover;
        sessionId = b.sessionId;
        rebuy = b.rebuy;
        hands = b.hands;
        blindCap = b.blindCap;
        rebuyLimit = b.rebuyLimit;
        botRebuy = b.botRebuy;
        fixedBuyin = b.fixedBuyin;
        buyinMinBb = b.buyinMinBb;
        buyinMaxBb = b.buyinMaxBb;
        rebuyCapPolicy = b.rebuyCapPolicy;
        ante = b.ante;
        straddle = b.straddle;
        iwtsth = b.iwtsth;
        runItTwice = b.runItTwice;
        rabbitHunting = b.rabbitHunting;
        thinkTime = b.thinkTime;
        thinkTimeEnabled = b.thinkTimeEnabled;
        showdownTime = b.showdownTime;
        botBalanceToHumans = b.botBalanceToHumans;
        blindStructure = copyStructure(b.blindStructure);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Result fromGlobals() {
        return builder()
                .buyin(GameFrame.BUYIN)
                .smallBlind(GameFrame.CIEGA_PEQUEÑA)
                .bigBlind(GameFrame.CIEGA_GRANDE)
                .blindsDouble(GameFrame.CIEGAS_DOUBLE, GameFrame.CIEGAS_DOUBLE_TYPE)
                .recover(GameFrame.RECOVER)
                .sessionId(GameFrame.UGI)
                .rebuy(GameFrame.REBUY)
                .hands(GameFrame.MANOS)
                .blindCap(GameFrame.BLIND_CAP)
                .rebuyLimit(GameFrame.REBUY_LIMIT)
                .botRebuy(GameFrame.BOT_REBUY)
                .fixedBuyin(GameFrame.FIXED_BUYIN)
                .buyinRangeBb(GameFrame.BUYIN_MIN_BB, GameFrame.BUYIN_MAX_BB)
                .rebuyCapPolicy(GameFrame.REBUY_CAP_POLICY)
                .ante(GameFrame.ANTE)
                .straddle(GameFrame.STRADDLE)
                .iwtsth(GameFrame.IWTSTH_RULE)
                .runItTwice(GameFrame.RUN_IT_TWICE)
                .rabbitHunting(GameFrame.RABBIT_HUNTING)
                .thinkTime(GameFrame.THINK_TIME, GameFrame.THINK_TIME_ENABLED)
                .showdownTime(GameFrame.SHOWDOWN_TIME)
                .botBalanceToHumans(GameFrame.BOT_BALANCE_TO_HUMANS)
                .blindStructure(GameFrame.ACTIVE_BLIND_STRUCTURE)
                .build();
    }

    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(CAPABILITIES);
            out.writeInt(buyin);
            out.writeLong(toCents(smallBlind));
            out.writeLong(toCents(bigBlind));
            out.writeInt(blindsDouble);
            out.writeInt(blindsDoubleType);
            writeBoolean(out, recover);
            writeString(out, sessionId);
            writeBoolean(out, rebuy);
            out.writeInt(hands);
            out.writeLong(toCents(blindCap));
            out.writeInt(rebuyLimit);
            writeBoolean(out, botRebuy);
            writeBoolean(out, fixedBuyin);
            out.writeInt(buyinMinBb);
            out.writeInt(buyinMaxBb);
            out.writeInt(rebuyCapPolicy);
            writeBoolean(out, ante);
            writeBoolean(out, straddle);
            writeBoolean(out, iwtsth);
            writeBoolean(out, runItTwice);
            out.writeInt(rabbitHunting);
            out.writeInt(thinkTime);
            writeBoolean(out, thinkTimeEnabled);
            out.writeInt(showdownTime);
            writeBoolean(out, botBalanceToHumans);
            writeString(out, blindStructure == null ? "" : BlindStructure.levelsToString(blindStructure));
            out.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > MAX_PACKET_BYTES) {
                throw new IllegalStateException("configuration packet too large");
            }
            return encoded;
        } catch (Exception ex) {
            throw new IllegalStateException("cannot encode validated configuration", ex);
        }
    }

    public String encodeBase64() {
        return Base64.getEncoder().encodeToString(encode());
    }

    public static Result decodeBase64(String encoded) {
        try {
            if (encoded == null || encoded.length() > MAX_PACKET_BYTES * 2) {
                return Result.error("invalid configuration encoding");
            }
            return decode(Base64.getDecoder().decode(encoded));
        } catch (IllegalArgumentException ex) {
            return Result.error("invalid configuration encoding");
        }
    }

    public static Result decode(byte[] encoded) {
        if (encoded == null || encoded.length > MAX_PACKET_BYTES) {
            return Result.error("invalid configuration packet size");
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded));
            if (in.readInt() != MAGIC || in.readInt() != VERSION || in.readInt() != CAPABILITIES) {
                return Result.error("unsupported configuration version or capabilities");
            }
            Builder b = builder()
                    .buyin(in.readInt())
                    .smallBlind(fromCents(in.readLong()))
                    .bigBlind(fromCents(in.readLong()))
                    .blindsDouble(in.readInt(), in.readInt())
                    .recover(readBoolean(in))
                    .sessionId(readString(in, MAX_SESSION_BYTES))
                    .rebuy(readBoolean(in))
                    .hands(in.readInt())
                    .blindCap(fromCents(in.readLong()))
                    .rebuyLimit(in.readInt())
                    .botRebuy(readBoolean(in))
                    .fixedBuyin(readBoolean(in))
                    .buyinRangeBb(in.readInt(), in.readInt())
                    .rebuyCapPolicy(in.readInt())
                    .ante(readBoolean(in))
                    .straddle(readBoolean(in))
                    .iwtsth(readBoolean(in))
                    .runItTwice(readBoolean(in))
                    .rabbitHunting(in.readInt())
                    .thinkTime(in.readInt(), readBoolean(in))
                    .showdownTime(in.readInt())
                    .botBalanceToHumans(readBoolean(in));
            String structure = readString(in, MAX_STRUCTURE_BYTES);
            if (!structure.isEmpty()) {
                b.blindStructure(BlindStructure.parseValidatedLevels(structure));
            }
            if (in.available() != 0) {
                return Result.error("trailing configuration data");
            }
            return b.build();
        } catch (Exception ex) {
            return Result.error("malformed configuration packet");
        }
    }

    public byte[] canonicalHash() {
        try {
            return MessageDigest.getInstance("SHA-256").digest(encode());
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public static boolean publish(Result result, AtomicReference<GameConfigWireV1> target) {
        if (result == null || !result.isOk() || target == null) {
            return false;
        }
        target.set(result.value());
        return true;
    }

    public static boolean decodeAndPublish(byte[] encoded, AtomicReference<GameConfigWireV1> target) {
        return publish(decode(encoded), target);
    }

    /** Apply only an already validated, complete snapshot. */
    public void applyToGlobals() {
        synchronized (GameConfigWireV1.class) {
            GameFrame.BUYIN = buyin;
            GameFrame.CIEGA_PEQUEÑA = smallBlind;
            GameFrame.CIEGA_GRANDE = bigBlind;
            GameFrame.CIEGAS_DOUBLE = blindsDouble;
            GameFrame.CIEGAS_DOUBLE_TYPE = blindsDoubleType;
            GameFrame.RECOVER = recover;
            GameFrame.UGI = sessionId;
            GameFrame.REBUY = rebuy;
            GameFrame.MANOS = hands;
            GameFrame.BLIND_CAP = blindCap;
            GameFrame.REBUY_LIMIT = rebuyLimit;
            GameFrame.BOT_REBUY = botRebuy;
            GameFrame.FIXED_BUYIN = fixedBuyin;
            GameFrame.BUYIN_MIN_BB = buyinMinBb;
            GameFrame.BUYIN_MAX_BB = buyinMaxBb;
            GameFrame.REBUY_CAP_POLICY = rebuyCapPolicy;
            GameFrame.ANTE = ante;
            GameFrame.STRADDLE = straddle;
            GameFrame.IWTSTH_RULE = iwtsth;
            GameFrame.RUN_IT_TWICE = runItTwice;
            GameFrame.RABBIT_HUNTING = rabbitHunting;
            GameFrame.THINK_TIME = thinkTime;
            GameFrame.THINK_TIME_ENABLED = thinkTimeEnabled;
            GameFrame.SHOWDOWN_TIME = showdownTime;
            GameFrame.BOT_BALANCE_TO_HUMANS = botBalanceToHumans;
            GameFrame.ACTIVE_BLIND_STRUCTURE = copyStructure(blindStructure);
            GameFrame.TABLE_CONFIG = this;
        }
    }

    /** Apply the complete validated subset carried by UPDATEBLINDS. */
    public void applyBlindUpdateToGlobals() {
        synchronized (GameConfigWireV1.class) {
            GameFrame.CIEGA_PEQUEÑA = smallBlind;
            GameFrame.CIEGA_GRANDE = bigBlind;
            GameFrame.CIEGAS_DOUBLE = blindsDouble;
            GameFrame.CIEGAS_DOUBLE_TYPE = blindsDoubleType;
            GameFrame.BLIND_CAP = blindCap;
            GameFrame.ANTE = ante;
            GameFrame.STRADDLE = straddle;
            GameFrame.ACTIVE_BLIND_STRUCTURE = copyStructure(blindStructure);
            GameFrame.TABLE_CONFIG = this;
        }
    }

    public double smallBlind() { return smallBlind; }
    public double bigBlind() { return bigBlind; }
    public int blindsDouble() { return blindsDouble; }
    public int blindsDoubleType() { return blindsDoubleType; }
    public boolean ante() { return ante; }
    public boolean straddle() { return straddle; }

    private static long toCents(double value) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("non-finite money");
        }
        return BigDecimal.valueOf(value).movePointRight(2).longValueExact();
    }

    private static double fromCents(long cents) {
        return BigDecimal.valueOf(cents, 2).doubleValue();
    }

    private static void writeBoolean(DataOutputStream out, boolean value) throws Exception {
        out.writeByte(value ? 1 : 0);
    }

    private static boolean readBoolean(DataInputStream in) throws Exception {
        int value = in.readUnsignedByte();
        if (value != 0 && value != 1) {
            throw new IllegalArgumentException("invalid boolean");
        }
        return value == 1;
    }

    private static void writeString(DataOutputStream out, String value) throws Exception {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in, int maximum) throws Exception {
        int length = in.readInt();
        if (length < 0 || length > maximum || length > in.available()) {
            throw new IllegalArgumentException("invalid string length");
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        String decoded = new String(bytes, StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(bytes, decoded.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("invalid UTF-8");
        }
        return decoded;
    }

    private static double[][] copyStructure(double[][] source) {
        if (source == null) {
            return null;
        }
        double[][] copy = new double[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }

    public static final class Result {
        private final GameConfigWireV1 value;
        private final String error;

        private Result(GameConfigWireV1 value, String error) {
            this.value = value;
            this.error = error;
        }

        public boolean isOk() { return value != null; }
        public GameConfigWireV1 value() { return value; }
        public String error() { return error; }
        private static Result ok(GameConfigWireV1 value) { return new Result(value, null); }
        private static Result error(String error) { return new Result(null, error); }
    }

    public static final class Builder {
        private int buyin = 10;
        private double smallBlind = 0.10;
        private double bigBlind = 0.20;
        private int blindsDouble = 60;
        private int blindsDoubleType = 1;
        private boolean recover;
        private String sessionId = "test-session";
        private boolean rebuy = true;
        private int hands = -1;
        private double blindCap;
        private int rebuyLimit;
        private boolean botRebuy = true;
        private boolean fixedBuyin = true;
        private int buyinMinBb = BuyinRules.DEFAULT_MIN_BB;
        private int buyinMaxBb = BuyinRules.DEFAULT_MAX_BB;
        private int rebuyCapPolicy = GameFrame.REBUY_CAP_BUYIN;
        private boolean ante;
        private boolean straddle;
        private boolean iwtsth;
        private boolean runItTwice;
        private int rabbitHunting;
        private int thinkTime = GameFrame.DEFAULT_THINK_TIME;
        private boolean thinkTimeEnabled = true;
        private int showdownTime = GameFrame.DEFAULT_SHOWDOWN_TIME;
        private boolean botBalanceToHumans;
        private double[][] blindStructure;

        public Builder buyin(int v) { buyin = v; return this; }
        public Builder smallBlind(double v) { smallBlind = v; return this; }
        public Builder bigBlind(double v) { bigBlind = v; return this; }
        public Builder blindsDouble(int v, int type) { blindsDouble = v; blindsDoubleType = type; return this; }
        public Builder recover(boolean v) { recover = v; return this; }
        public Builder sessionId(String v) { sessionId = v; return this; }
        public Builder rebuy(boolean v) { rebuy = v; return this; }
        public Builder hands(int v) { hands = v; return this; }
        public Builder blindCap(double v) { blindCap = v; return this; }
        public Builder rebuyLimit(int v) { rebuyLimit = v; return this; }
        public Builder botRebuy(boolean v) { botRebuy = v; return this; }
        public Builder fixedBuyin(boolean v) { fixedBuyin = v; return this; }
        public Builder buyinRangeBb(int min, int max) { buyinMinBb = min; buyinMaxBb = max; return this; }
        public Builder rebuyCapPolicy(int v) { rebuyCapPolicy = v; return this; }
        public Builder ante(boolean v) { ante = v; return this; }
        public Builder straddle(boolean v) { straddle = v; return this; }
        public Builder iwtsth(boolean v) { iwtsth = v; return this; }
        public Builder runItTwice(boolean v) { runItTwice = v; return this; }
        public Builder rabbitHunting(int v) { rabbitHunting = v; return this; }
        public Builder thinkTime(int v, boolean enabled) { thinkTime = v; thinkTimeEnabled = enabled; return this; }
        public Builder showdownTime(int v) { showdownTime = v; return this; }
        public Builder botBalanceToHumans(boolean v) { botBalanceToHumans = v; return this; }
        public Builder blindStructure(double[][] v) { blindStructure = copyStructure(v); return this; }

        public Result build() {
            try {
                if (buyin <= 0 || sessionId == null || sessionId.trim().isEmpty()
                        || sessionId.getBytes(StandardCharsets.UTF_8).length > MAX_SESSION_BYTES) {
                    return Result.error("invalid buy-in or session");
                }
                long sb = toCents(smallBlind);
                long bb = toCents(bigBlind);
                long cap = toCents(blindCap);
                long maxBlind = toCents(BlindStructure.MAX_BLIND);
                if (BlindStructure.validateLevels(new double[][]{{smallBlind, bigBlind}}) != null
                        || sb <= 0 || bb < sb || bb > maxBlind || cap < 0 || cap > maxBlind
                        || cap % 5 != 0
                        || (cap != 0 && cap < bb)) {
                    return Result.error("invalid blind range");
                }
                if (blindsDouble < 0 || (blindsDoubleType != 1 && blindsDoubleType != 2)
                        || hands < -1 || hands == 0 || rebuyLimit < 0) {
                    return Result.error("invalid game limits");
                }
                if (buyinMinBb < BuyinRules.FLOOR_MIN_BB || buyinMaxBb > BuyinRules.CEIL_MAX_BB
                        || buyinMinBb > buyinMaxBb) {
                    return Result.error("invalid buy-in range");
                }
                if (rebuyCapPolicy != GameFrame.REBUY_CAP_BUYIN
                        && rebuyCapPolicy != GameFrame.REBUY_CAP_HIGHEST_STACK) {
                    return Result.error("invalid rebuy cap policy");
                }
                if (rabbitHunting < 0 || rabbitHunting > 3
                        || thinkTime < GameFrame.THINK_TIME_MIN || thinkTime > GameFrame.THINK_TIME_MAX
                        || showdownTime < GameFrame.SHOWDOWN_TIME_MIN || showdownTime > GameFrame.SHOWDOWN_TIME_MAX) {
                    return Result.error("invalid timed rule");
                }
                if (blindStructure != null && BlindStructure.validateLevels(blindStructure) != null) {
                    return Result.error("invalid blind structure");
                }
                GameConfigWireV1 value = new GameConfigWireV1(this);
                if (value.encode().length > MAX_PACKET_BYTES) {
                    return Result.error("configuration packet too large");
                }
                return Result.ok(value);
            } catch (Exception ex) {
                return Result.error("invalid configuration");
            }
        }
    }
}
