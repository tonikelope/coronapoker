/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Strict, current-version decoder for the critical POSITIONS command. */
public final class PositionWireV1 {

    private static final int MAX_NICK_BYTES = 256;

    private PositionWireV1() {
    }

    public static Result parse(String frame) {
        String[] parts = frame == null ? new String[0] : frame.split("#", -1);
        if (parts.length != 9 || !"POSITIONS".equals(parts[2])) {
            return Result.error("WRONG_SCHEMA");
        }
        try {
            String utg = decodeNick(parts[3]);
            String bigBlind = decodeNick(parts[4]);
            String smallBlind = decodeNick(parts[5]);
            String dealer = decodeNick(parts[6]);
            long playTime = Long.parseLong(parts[7]);
            if (playTime < 0 || !("0".equals(parts[8]) || "1".equals(parts[8]))) {
                return Result.error("BAD_VALUE");
            }
            return Result.ok(new Value(utg, bigBlind, smallBlind, dealer,
                    playTime, "1".equals(parts[8])));
        } catch (Exception ex) {
            return Result.error("BAD_VALUE");
        }
    }

    private static String decodeNick(String encoded) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(encoded);
        if (bytes.length == 0 || bytes.length > MAX_NICK_BYTES) {
            throw new IllegalArgumentException("invalid nick length");
        }
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
    }

    public static final class Value {
        private final String utg;
        private final String bigBlind;
        private final String smallBlind;
        private final String dealer;
        private final long playTime;
        private final boolean doubleBlinds;

        private Value(String utg, String bigBlind, String smallBlind, String dealer,
                long playTime, boolean doubleBlinds) {
            this.utg = utg;
            this.bigBlind = bigBlind;
            this.smallBlind = smallBlind;
            this.dealer = dealer;
            this.playTime = playTime;
            this.doubleBlinds = doubleBlinds;
        }

        public String utg() {
            return utg;
        }

        public String bigBlind() {
            return bigBlind;
        }

        public String smallBlind() {
            return smallBlind;
        }

        public String dealer() {
            return dealer;
        }

        public long playTime() {
            return playTime;
        }

        public boolean doubleBlinds() {
            return doubleBlinds;
        }
    }

    public static final class Result {
        private final Value value;
        private final String error;

        private Result(Value value, String error) {
            this.value = value;
            this.error = error;
        }

        private static Result ok(Value value) {
            return new Result(value, null);
        }

        private static Result error(String error) {
            return new Result(null, error);
        }

        public boolean isOk() {
            return value != null;
        }

        public Value value() {
            return value;
        }

        public String error() {
            return error;
        }
    }
}
