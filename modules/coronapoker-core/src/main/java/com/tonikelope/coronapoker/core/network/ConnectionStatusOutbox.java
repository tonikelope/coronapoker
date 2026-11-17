/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.network;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;

/** Bounded latest-value notices. Never carries a poker or cryptographic operation. */
public final class ConnectionStatusOutbox {
    record Entry(long generation, String command, int bytes) { }

    private final int maxElements;
    private final long maxBytes;
    private final LinkedHashMap<String, Entry> pending = new LinkedHashMap<>();
    private long queuedBytes;

    ConnectionStatusOutbox(int maxElements, long maxBytes) {
        if (maxElements <= 0 || maxBytes <= 0L) throw new IllegalArgumentException("positive limits required");
        this.maxElements = maxElements;
        this.maxBytes = maxBytes;
    }

    public static boolean isStatus(String command) {
        return command != null && (command.startsWith("TIMEOUT#") || command.startsWith("TELEMETRY#"));
    }

    private static String key(String command) {
        if (!isStatus(command)) throw new IllegalArgumentException("only connection status notices are allowed");
        if (command.startsWith("TELEMETRY#")) {
            if (TelemetryCodec.decode(command.substring("TELEMETRY#".length())) == null) {
                throw new IllegalArgumentException("invalid telemetry snapshot");
            }
            return "TELEMETRY";
        }
        try {
            byte[] nickname = Base64.getDecoder().decode(command.substring("TIMEOUT#".length()));
            if (StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(nickname)).toString().isBlank()) {
                throw new IllegalArgumentException("empty timeout nickname");
            }
            return "TIMEOUT#" + Base64.getEncoder().encodeToString(nickname);
        } catch (java.nio.charset.CharacterCodingException invalidUtf8) {
            throw new IllegalArgumentException("invalid timeout nickname", invalidUtf8);
        }
    }

    synchronized boolean offer(long generation, String command) {
        String key = key(command);
        int bytes = command.getBytes(StandardCharsets.UTF_8).length;
        Entry previous = pending.get(key);
        long replacedBytes = previous == null ? 0L : previous.bytes();
        if ((previous == null && pending.size() >= maxElements)
                || bytes > maxBytes - queuedBytes + replacedBytes) return false;
        pending.put(key, new Entry(generation, command, bytes));
        queuedBytes += bytes - replacedBytes;
        return true;
    }

    synchronized Entry poll() {
        var iterator = pending.entrySet().iterator();
        if (!iterator.hasNext()) return null;
        Entry entry = iterator.next().getValue();
        iterator.remove();
        queuedBytes -= entry.bytes();
        return entry;
    }

    synchronized boolean isEmpty() { return pending.isEmpty(); }
    synchronized int size() { return pending.size(); }
    synchronized long queuedBytes() { return queuedBytes; }
    synchronized void clear() { pending.clear(); queuedBytes = 0L; }
}
