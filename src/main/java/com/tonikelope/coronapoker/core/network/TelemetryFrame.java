/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.network;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Immutable latency and reconnection snapshot broadcast by the host. */
public class TelemetryFrame {

    public final long serverTimestampMs;
    public final Map<String, int[]> perPeer;

    public TelemetryFrame(long serverTimestampMs, Map<String, int[]> perPeer) {
        this.serverTimestampMs = serverTimestampMs;
        this.perPeer = Collections.unmodifiableMap(new HashMap<>(perPeer));
    }
}
