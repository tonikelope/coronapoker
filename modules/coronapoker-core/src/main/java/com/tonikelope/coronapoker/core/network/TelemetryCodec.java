/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.network;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** Canonical TELEMETRY wire encoder and defensive decoder. */
public final class TelemetryCodec {

    private TelemetryCodec() {
    }

    public static String encode(TelemetryFrame frame) {
        if (frame == null) {
            throw new IllegalArgumentException("frame must not be null");
        }
        StringBuilder wire = new StringBuilder(64 + frame.perPeer.size() * 32);
        wire.append(frame.serverTimestampMs).append('#');
        boolean first = true;
        for (Map.Entry<String, int[]> entry : frame.perPeer.entrySet()) {
            int[] values = entry.getValue();
            if (values == null || values.length < 3) {
                continue;
            }
            if (!first) {
                wire.append('@');
            }
            first = false;
            wire.append(Base64.getEncoder().encodeToString(
                    entry.getKey().getBytes(StandardCharsets.UTF_8)));
            wire.append('|').append(values[0]).append('/').append(values[1])
                    .append('/').append(values[2]);
        }
        return wire.toString();
    }

    public static TelemetryFrame decode(String payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        int firstHash = payload.indexOf('#');
        long timestamp;
        if (firstHash < 0) {
            try {
                timestamp = Long.parseLong(payload);
            } catch (NumberFormatException invalidTimestamp) {
                return null;
            }
            return new TelemetryFrame(timestamp, Map.of());
        }
        try {
            timestamp = Long.parseLong(payload.substring(0, firstHash));
        } catch (NumberFormatException invalidTimestamp) {
            return null;
        }
        Map<String, int[]> valuesByPeer = new HashMap<>();
        String entries = payload.substring(firstHash + 1);
        if (!entries.isEmpty()) {
            for (String tuple : entries.split("@")) {
                int pipe = tuple.indexOf('|');
                if (pipe <= 0 || pipe >= tuple.length() - 1) {
                    continue;
                }
                String[] values = tuple.substring(pipe + 1).split("/");
                if (values.length < 3) {
                    continue;
                }
                try {
                    String nickname = new String(Base64.getDecoder().decode(
                            tuple.substring(0, pipe)), StandardCharsets.UTF_8);
                    if (!nickname.isEmpty()) {
                        valuesByPeer.put(nickname, new int[]{
                            Integer.parseInt(values[0]), Integer.parseInt(values[1]),
                            Integer.parseInt(values[2])});
                    }
                } catch (IllegalArgumentException malformedEntry) {
                    // Best-effort telemetry: discard only the malformed tuple.
                }
            }
        }
        return new TelemetryFrame(timestamp, valuesByPeer);
    }
}
