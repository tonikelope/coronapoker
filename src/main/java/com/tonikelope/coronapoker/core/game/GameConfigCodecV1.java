/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.core.game;

import com.tonikelope.coronapoker.core.BlindStructureRules;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * UI-free owner of the exact strict configuration packet used by INIT and
 * UPDATEBLINDS. The packet format is deliberately unchanged from 24.10.
 */
public final class GameConfigCodecV1 {

    private static final int MAGIC = 0x43504743; // CPGC
    private static final int VERSION = 1;
    private static final int CAPABILITIES = 1;
    private static final int MAX_PACKET_BYTES = 64 * 1024;
    private static final int MAX_SESSION_BYTES = 4096;
    private static final int MAX_STRUCTURE_BYTES = 48 * 1024;

    private GameConfigCodecV1() {
    }

    public static byte[] encode(Configuration configuration) {
        Configuration value = validate(configuration);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(CAPABILITIES);
            out.writeInt(value.buyin());
            out.writeLong(toCents(value.smallBlind()));
            out.writeLong(toCents(value.bigBlind()));
            out.writeInt(value.blindsDouble());
            out.writeInt(value.blindsDoubleType());
            writeBoolean(out, value.recover());
            writeString(out, value.sessionId());
            writeBoolean(out, value.rebuy());
            out.writeInt(value.hands());
            out.writeLong(toCents(value.blindCap()));
            out.writeInt(value.rebuyLimit());
            writeBoolean(out, value.botRebuy());
            writeBoolean(out, value.fixedBuyin());
            out.writeInt(value.buyinMinBb());
            out.writeInt(value.buyinMaxBb());
            out.writeInt(value.rebuyCapPolicy());
            writeBoolean(out, value.ante());
            writeBoolean(out, value.straddle());
            writeBoolean(out, value.iwtsth());
            writeBoolean(out, value.runItTwice());
            out.writeInt(value.rabbitHunting());
            out.writeInt(value.thinkTime());
            writeBoolean(out, value.thinkTimeEnabled());
            out.writeInt(value.showdownTime());
            writeBoolean(out, value.botBalanceToHumans());
            writeString(out, levelsToString(value.blindStructure()));
            out.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > MAX_PACKET_BYTES) {
                throw new IllegalArgumentException("configuration packet too large");
            }
            return encoded;
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("cannot encode validated configuration", failure);
        }
    }

    public static String encodeBase64(Configuration configuration) {
        return Base64.getEncoder().encodeToString(encode(configuration));
    }

    public static Result decodeBase64(String encoded) {
        try {
            if (encoded == null || encoded.length() > MAX_PACKET_BYTES * 2) {
                return Result.error("invalid configuration encoding");
            }
            return decode(Base64.getDecoder().decode(encoded));
        } catch (IllegalArgumentException failure) {
            return Result.error("invalid configuration encoding");
        }
    }

    public static Result decode(byte[] encoded) {
        if (encoded == null || encoded.length > MAX_PACKET_BYTES) {
            return Result.error("invalid configuration packet size");
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded));
            if (in.readInt() != MAGIC || in.readInt() != VERSION
                    || in.readInt() != CAPABILITIES) {
                return Result.error("unsupported configuration version or capabilities");
            }
            Configuration value = new Configuration(
                    in.readInt(), fromCents(in.readLong()), fromCents(in.readLong()),
                    in.readInt(), in.readInt(), readBoolean(in),
                    readString(in, MAX_SESSION_BYTES), readBoolean(in), in.readInt(),
                    fromCents(in.readLong()), in.readInt(), readBoolean(in),
                    readBoolean(in), in.readInt(), in.readInt(), in.readInt(),
                    readBoolean(in), readBoolean(in), readBoolean(in),
                    readBoolean(in), in.readInt(), in.readInt(), readBoolean(in),
                    in.readInt(), readBoolean(in),
                    parseLevels(readString(in, MAX_STRUCTURE_BYTES)));
            if (in.available() != 0) {
                return Result.error("trailing configuration data");
            }
            return Result.ok(validate(value));
        } catch (Exception failure) {
            return Result.error("malformed configuration packet");
        }
    }

    public static Configuration fromSettings(NewGameTableDraft.Settings settings,
            boolean recover, String sessionId) {
        Objects.requireNonNull(settings, "settings");
        NewGameTableDraft.BlindLevel selected = settings.selectedBlindLevel();
        double cap = settings.blindCap()
                ? settings.blindLevels().get(Math.min(settings.blindLevels().size() - 1,
                        settings.blindLevelIndex() + settings.blindCapRaises())).bigBlind()
                : 0d;
        List<BlindLevel> structure = settings.structureName() == null
                ? List.of() : settings.blindLevels().stream()
                        .map(level -> new BlindLevel(level.smallBlind(), level.bigBlind()))
                        .toList();
        return validate(new Configuration(settings.buyin(), selected.smallBlind(),
                selected.bigBlind(), settings.increaseBlinds() ? settings.blindInterval() : 0,
                settings.blindIncreaseType() == NewGameTableDraft.BlindIncreaseType.MINUTES ? 1 : 2,
                recover, sessionId, settings.rebuy(),
                settings.handLimit() ? settings.handLimitCount() : -1,
                cap, settings.rebuy() && settings.rebuyLimit()
                        ? settings.rebuyLimitCount() : 0,
                settings.botRebuy(), settings.fixedBuyin(), settings.minBuyinBb(),
                settings.maxBuyinBb(),
                settings.rebuyCapPolicy() == NewGameTableDraft.RebuyCapPolicy.BUY_IN ? 0 : 1,
                settings.ante(), settings.straddle(), settings.iwtsth(),
                settings.runItTwice(), settings.rabbitHunting().ordinal(),
                settings.thinkSeconds(), settings.thinkTime(), settings.showdownSeconds(),
                settings.botBalanceToHumans(), structure));
    }

    public static byte[] canonicalHash(Configuration configuration) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(encode(configuration));
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    public static Configuration requireValid(Configuration configuration) {
        return validate(configuration);
    }

    private static Configuration validate(Configuration value) {
        Objects.requireNonNull(value, "configuration");
        if (value.buyin() <= 0 || value.sessionId().isBlank()
                || value.sessionId().getBytes(StandardCharsets.UTF_8).length > MAX_SESSION_BYTES) {
            throw new IllegalArgumentException("invalid buy-in or session");
        }
        long sb = toCents(value.smallBlind());
        long bb = toCents(value.bigBlind());
        long cap = toCents(value.blindCap());
        if (BlindStructureRules.validateLevels(new double[][]{{value.smallBlind(), value.bigBlind()}}) != null
                || sb <= 0 || bb < sb || bb > toCents(BlindStructureRules.MAX_BLIND)
                || cap < 0 || cap > toCents(BlindStructureRules.MAX_BLIND)
                || cap % 5 != 0 || (cap != 0 && cap < bb)) {
            throw new IllegalArgumentException("invalid blind range");
        }
        if (value.blindsDouble() < 0
                || (value.blindsDoubleType() != 1 && value.blindsDoubleType() != 2)
                || value.hands() < -1 || value.hands() == 0 || value.rebuyLimit() < 0) {
            throw new IllegalArgumentException("invalid game limits");
        }
        if (value.buyinMinBb() < NewGameTableDraft.MIN_BUYIN_BB
                || value.buyinMaxBb() > NewGameTableDraft.MAX_BUYIN_BB
                || value.buyinMinBb() > value.buyinMaxBb()) {
            throw new IllegalArgumentException("invalid buy-in range");
        }
        if (value.rebuyCapPolicy() < 0 || value.rebuyCapPolicy() > 1) {
            throw new IllegalArgumentException("invalid rebuy cap policy");
        }
        if (value.rabbitHunting() < 0
                || value.rabbitHunting() >= NewGameTableDraft.RabbitHunting.values().length
                || value.thinkTime() < NewGameTableDraft.MIN_THINK_SECONDS
                || value.thinkTime() > NewGameTableDraft.MAX_THINK_SECONDS
                || value.showdownTime() < NewGameTableDraft.MIN_SHOWDOWN_SECONDS
                || value.showdownTime() > NewGameTableDraft.MAX_SHOWDOWN_SECONDS) {
            throw new IllegalArgumentException("invalid timed rule");
        }
        if (!value.blindStructure().isEmpty()) {
            double[][] levels = value.blindStructure().stream()
                    .map(level -> new double[]{level.smallBlind(), level.bigBlind()})
                    .toArray(double[][]::new);
            if (BlindStructureRules.validateLevels(levels) != null) {
                throw new IllegalArgumentException("invalid blind structure");
            }
        }
        return value;
    }

    private static long toCents(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("invalid money");
        }
        try {
            return BigDecimal.valueOf(value).movePointRight(2).longValueExact();
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException("money has sub-cent precision", failure);
        }
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

    private static String levelsToString(List<BlindLevel> levels) {
        StringBuilder result = new StringBuilder();
        for (BlindLevel level : levels) {
            if (!result.isEmpty()) result.append(',');
            result.append(number(level.smallBlind())).append('/').append(number(level.bigBlind()));
        }
        return result.toString();
    }

    private static List<BlindLevel> parseLevels(String encoded) {
        if (encoded.isEmpty()) return List.of();
        List<BlindLevel> result = new ArrayList<>();
        for (String token : encoded.split(",")) {
            String[] pair = token.split("/", -1);
            if (pair.length != 2) throw new IllegalArgumentException("invalid blind structure");
            result.add(new BlindLevel(Double.parseDouble(pair[0]), Double.parseDouble(pair[1])));
        }
        return List.copyOf(result);
    }

    private static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    public record BlindLevel(double smallBlind, double bigBlind) {
    }

    public record Configuration(int buyin, double smallBlind, double bigBlind,
            int blindsDouble, int blindsDoubleType, boolean recover, String sessionId,
            boolean rebuy, int hands, double blindCap, int rebuyLimit,
            boolean botRebuy, boolean fixedBuyin, int buyinMinBb, int buyinMaxBb,
            int rebuyCapPolicy, boolean ante, boolean straddle, boolean iwtsth,
            boolean runItTwice, int rabbitHunting, int thinkTime,
            boolean thinkTimeEnabled, int showdownTime, boolean botBalanceToHumans,
            List<BlindLevel> blindStructure) {
        public Configuration {
            sessionId = Objects.requireNonNull(sessionId, "sessionId");
            blindStructure = List.copyOf(blindStructure);
        }
    }

    public record Result(Configuration value, String error) {
        public boolean isOk() {
            return value != null;
        }

        private static Result ok(Configuration value) {
            return new Result(value, null);
        }

        private static Result error(String error) {
            return new Result(null, error);
        }
    }
}
