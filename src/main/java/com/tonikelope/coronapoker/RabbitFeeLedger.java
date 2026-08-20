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
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** Per-hand authoritative Rabbit request/fee reducer and strict V1 wire. */
public final class RabbitFeeLedger {

    private static final int REQUEST_MAGIC = 0x52425251; // RBRQ
    private static final int AUTH_MAGIC = 0x52424155; // RBAU
    private static final int VERSION = 1;
    public static final int HAND_BYTES = 32;
    public static final int NONCE_BYTES = 16;
    private static final int MAX_PLAYER_BYTES = 1024;
    private static final int MAX_WIRE_BYTES = 2048;

    private final byte[] handId;
    private final int mode;
    private final long smallBlindCents;
    private final long bigBlindCents;
    private final Map<String, Integer> authorizedCounts = new HashMap<>();
    private final Map<String, Authorization> authorizationsByRequest = new HashMap<>();
    private final Map<String, Integer> appliedCounts = new HashMap<>();
    private final Map<String, Authorization> appliedByRequest = new HashMap<>();

    public RabbitFeeLedger(byte[] handId, int mode, long smallBlindCents, long bigBlindCents) {
        if (handId == null || handId.length != HAND_BYTES || mode < 0 || mode > 3
                || smallBlindCents < 0 || bigBlindCents < smallBlindCents) {
            throw new IllegalArgumentException("invalid Rabbit ledger parameters");
        }
        this.handId = handId.clone();
        this.mode = mode;
        this.smallBlindCents = smallBlindCents;
        this.bigBlindCents = bigBlindCents;
    }

    /** Host-only transition: assigns the next sequence; the request has no count/fee. */
    public synchronized Result<Authorization> authorize(Request request) {
        if (request == null || !Arrays.equals(handId, request.handId())) {
            return Result.error("request belongs to another hand");
        }
        String key = requestKey(request.playerId(), request.nonce());
        Authorization cached = authorizationsByRequest.get(key);
        if (cached != null) {
            return Result.ok(cached);
        }
        int count = authorizedCounts.getOrDefault(request.playerId(), 0) + 1;
        Authorization authorization = new Authorization(request, count, feeFor(count));
        authorizedCounts.put(request.playerId(), count);
        authorizationsByRequest.put(key, authorization);
        return Result.ok(authorization);
    }

    /** Every peer transition: exact sequence, fee and hand binding or no mutation. */
    public synchronized Acceptance accept(Authorization authorization) {
        if (authorization == null || !Arrays.equals(handId, authorization.request().handId())) {
            return Acceptance.REJECTED;
        }
        Request request = authorization.request();
        String key = requestKey(request.playerId(), request.nonce());
        Authorization applied = appliedByRequest.get(key);
        if (applied != null) {
            return Arrays.equals(applied.encode(), authorization.encode())
                    ? Acceptance.DUPLICATE : Acceptance.REJECTED;
        }
        int expected = appliedCounts.getOrDefault(request.playerId(), 0) + 1;
        if (authorization.count() != expected || authorization.feeCents() != feeFor(expected)) {
            return Acceptance.REJECTED;
        }
        appliedCounts.put(request.playerId(), expected);
        appliedByRequest.put(key, authorization);
        return Acceptance.ACCEPTED;
    }

    private long feeFor(int count) {
        if (mode == 2 && count > 1) {
            return smallBlindCents;
        }
        if (mode == 3 && count == 2) {
            return smallBlindCents;
        }
        if (mode == 3 && count > 2) {
            return bigBlindCents;
        }
        return 0L;
    }

    private static String requestKey(String playerId, byte[] nonce) {
        return playerId + "\0" + Base64.getEncoder().encodeToString(nonce);
    }

    public enum Acceptance {
        ACCEPTED, DUPLICATE, REJECTED;
        public boolean isAccepted() { return this == ACCEPTED; }
    }

    public static final class Request {
        private final byte[] handId;
        private final String playerId;
        private final byte[] nonce;

        public Request(byte[] handId, String playerId, byte[] nonce) {
            if (handId == null || handId.length != HAND_BYTES || nonce == null || nonce.length != NONCE_BYTES
                    || playerId == null || playerId.isEmpty()) {
                throw new IllegalArgumentException("invalid Rabbit request");
            }
            String normalized = Normalizer.normalize(playerId, Normalizer.Form.NFC);
            if (!normalized.equals(playerId)
                    || playerId.getBytes(StandardCharsets.UTF_8).length > MAX_PLAYER_BYTES) {
                throw new IllegalArgumentException("invalid Rabbit player id");
            }
            this.handId = handId.clone();
            this.playerId = playerId;
            this.nonce = nonce.clone();
        }

        public byte[] handId() { return handId.clone(); }
        public String playerId() { return playerId; }
        public byte[] nonce() { return nonce.clone(); }

        public byte[] encode() {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes);
                out.writeInt(REQUEST_MAGIC);
                out.writeInt(VERSION);
                out.write(handId);
                writeString(out, playerId);
                out.write(nonce);
                out.flush();
                return bytes.toByteArray();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        public static Result<Request> decode(byte[] wire) {
            if (wire == null || wire.length > MAX_WIRE_BYTES) {
                return Result.error("invalid request size");
            }
            try {
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(wire));
                if (in.readInt() != REQUEST_MAGIC || in.readInt() != VERSION) {
                    return Result.error("unsupported Rabbit request version");
                }
                byte[] hand = new byte[HAND_BYTES];
                in.readFully(hand);
                String player = readString(in);
                byte[] nonce = new byte[NONCE_BYTES];
                in.readFully(nonce);
                if (in.available() != 0) {
                    return Result.error("trailing Rabbit request data");
                }
                return Result.ok(new Request(hand, player, nonce));
            } catch (Exception ex) {
                return Result.error("malformed Rabbit request");
            }
        }
    }

    public static final class Authorization {
        private final Request request;
        private final int count;
        private final long feeCents;

        private Authorization(Request request, int count, long feeCents) {
            if (request == null || count <= 0 || feeCents < 0) {
                throw new IllegalArgumentException("invalid Rabbit authorization");
            }
            this.request = request;
            this.count = count;
            this.feeCents = feeCents;
        }

        public Request request() { return request; }
        public int count() { return count; }
        public long feeCents() { return feeCents; }

        public byte[] encode() {
            try {
                byte[] requestWire = request.encode();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes);
                out.writeInt(AUTH_MAGIC);
                out.writeInt(VERSION);
                out.writeInt(requestWire.length);
                out.write(requestWire);
                out.writeInt(count);
                out.writeLong(feeCents);
                out.flush();
                return bytes.toByteArray();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        public static Result<Authorization> decode(byte[] wire) {
            if (wire == null || wire.length > MAX_WIRE_BYTES) {
                return Result.error("invalid authorization size");
            }
            try {
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(wire));
                if (in.readInt() != AUTH_MAGIC || in.readInt() != VERSION) {
                    return Result.error("unsupported Rabbit authorization version");
                }
                int requestLength = in.readInt();
                if (requestLength <= 0 || requestLength > MAX_WIRE_BYTES || requestLength > in.available() - 12) {
                    return Result.error("invalid embedded request length");
                }
                byte[] requestWire = new byte[requestLength];
                in.readFully(requestWire);
                Result<Request> decodedRequest = Request.decode(requestWire);
                if (!decodedRequest.isOk()) {
                    return Result.error(decodedRequest.error());
                }
                int count = in.readInt();
                long fee = in.readLong();
                if (in.available() != 0) {
                    return Result.error("trailing Rabbit authorization data");
                }
                return Result.ok(new Authorization(decodedRequest.value(), count, fee));
            } catch (Exception ex) {
                return Result.error("malformed Rabbit authorization");
            }
        }
    }

    public static final class Result<T> {
        private final T value;
        private final String error;
        private Result(T value, String error) { this.value = value; this.error = error; }
        public boolean isOk() { return value != null; }
        public T value() { return value; }
        public String error() { return error; }
        private static <T> Result<T> ok(T value) { return new Result<>(value, null); }
        private static <T> Result<T> error(String error) { return new Result<>(null, error); }
    }

    private static void writeString(DataOutputStream out, String value) throws Exception {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws Exception {
        int length = in.readInt();
        if (length <= 0 || length > MAX_PLAYER_BYTES || length > in.available()) {
            throw new IllegalArgumentException("invalid player length");
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        String decoded = new String(bytes, StandardCharsets.UTF_8);
        if (!Arrays.equals(bytes, decoded.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("invalid UTF-8");
        }
        return decoded;
    }
}
