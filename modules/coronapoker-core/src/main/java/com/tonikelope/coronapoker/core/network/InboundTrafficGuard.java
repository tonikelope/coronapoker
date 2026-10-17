/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.network;

/**
 * Passive per-authenticated-peer traffic monitor.
 *
 * <p>Crossing an observation threshold never discards a valid frame and never
 * expels a player.  The host remains the only authority that can turn a
 * sustained observation into an expulsion.  Structural protocol limits live
 * in the codecs and bounded queues instead.</p>
 */
final class InboundTrafficGuard {

    private static final int CONSECUTIVE_EXCESSIVE_WINDOWS_TO_ALERT = 3;

    private final int maxFrames;
    private final long maxBytes;
    private final long frameWindowMillis;
    private final int maxVoiceOperations;
    private final long featureWindowMillis;
    private long frameWindowStartedAt;
    private int frames;
    private long bytes;
    private boolean frameWindowExceeded;
    private int consecutiveExceededFrameWindows;
    private long featureWindowStartedAt;
    private int voiceOperations;

    InboundTrafficGuard(int maxFrames, long maxBytes, long frameWindowMillis,
            int maxVoiceOperations, long featureWindowMillis) {
        if (maxFrames <= 0 || maxBytes <= 0L || frameWindowMillis <= 0L
                || maxVoiceOperations <= 0
                || featureWindowMillis <= 0L) {
            throw new IllegalArgumentException("Invalid inbound traffic limits");
        }
        this.maxFrames = maxFrames;
        this.maxBytes = maxBytes;
        this.frameWindowMillis = frameWindowMillis;
        this.maxVoiceOperations = maxVoiceOperations;
        this.featureWindowMillis = featureWindowMillis;
    }

    /** Returns true only after excessive traffic persists for three windows. */
    synchronized boolean observeFrame(long byteCount, long nowMillis) {
        if (byteCount < 0L) throw new IllegalArgumentException(
                "Negative frame size");
        if (frameWindowStartedAt == 0L
                || nowMillis - frameWindowStartedAt >= frameWindowMillis) {
            long elapsed = frameWindowStartedAt == 0L ? 0L
                    : nowMillis - frameWindowStartedAt;
            if (elapsed >= frameWindowMillis * 2L) {
                consecutiveExceededFrameWindows = 0;
            } else if (frameWindowStartedAt != 0L) {
                consecutiveExceededFrameWindows = frameWindowExceeded
                        ? consecutiveExceededFrameWindows + 1 : 0;
            }
            frameWindowStartedAt = nowMillis;
            frames = 0;
            bytes = 0L;
            frameWindowExceeded = false;
        }
        boolean excessive = frames >= maxFrames
                || byteCount > maxBytes - Math.min(bytes, maxBytes);
        frames = frames == Integer.MAX_VALUE ? frames : frames + 1;
        bytes = byteCount > Long.MAX_VALUE - bytes
                ? Long.MAX_VALUE : bytes + byteCount;
        if (excessive) {
            frameWindowExceeded = true;
            return consecutiveExceededFrameWindows
                    >= CONSECUTIVE_EXCESSIVE_WINDOWS_TO_ALERT - 1;
        }
        return false;
    }

    /** Returns true when voice traffic is excessive; it is never rejected. */
    synchronized boolean observeVoiceOperation(long nowMillis) {
        rotateFeatureWindow(nowMillis);
        boolean excessive = voiceOperations >= maxVoiceOperations;
        if (voiceOperations < Integer.MAX_VALUE) voiceOperations++;
        return excessive;
    }

    /** Starts a fresh observation window after the host forgives an alert. */
    synchronized void reset() {
        frameWindowStartedAt = 0L;
        frames = 0;
        bytes = 0L;
        frameWindowExceeded = false;
        consecutiveExceededFrameWindows = 0;
        featureWindowStartedAt = 0L;
        voiceOperations = 0;
    }

    private void rotateFeatureWindow(long nowMillis) {
        if (featureWindowStartedAt == 0L
                || nowMillis - featureWindowStartedAt >= featureWindowMillis) {
            featureWindowStartedAt = nowMillis;
            voiceOperations = 0;
        }
    }
}
