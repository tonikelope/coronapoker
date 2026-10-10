/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.network;

/** Per-authenticated-peer flood guard; deliberately independent of UI/game state. */
final class InboundTrafficGuard {

    enum FrameDecision { ALLOW, DROP, DISCONNECT }

    private static final int CONSECUTIVE_ABUSIVE_WINDOWS_TO_DISCONNECT = 3;

    private final int maxFrames;
    private final long maxBytes;
    private final long frameWindowMillis;
    private final int maxChats;
    private final int maxBinaryOperations;
    private final long featureWindowMillis;
    private long frameWindowStartedAt;
    private int frames;
    private long bytes;
    private boolean frameWindowExceeded;
    private int consecutiveExceededFrameWindows;
    private long featureWindowStartedAt;
    private int chats;
    private int binaryOperations;

    InboundTrafficGuard(int maxFrames, long maxBytes, long frameWindowMillis,
            int maxChats, int maxBinaryOperations, long featureWindowMillis) {
        if (maxFrames <= 0 || maxBytes <= 0L || frameWindowMillis <= 0L
                || maxChats <= 0 || maxBinaryOperations <= 0
                || featureWindowMillis <= 0L) {
            throw new IllegalArgumentException("Invalid inbound traffic limits");
        }
        this.maxFrames = maxFrames;
        this.maxBytes = maxBytes;
        this.frameWindowMillis = frameWindowMillis;
        this.maxChats = maxChats;
        this.maxBinaryOperations = maxBinaryOperations;
        this.featureWindowMillis = featureWindowMillis;
    }

    synchronized FrameDecision frameDecision(long byteCount, long nowMillis) {
        if (byteCount < 0L || byteCount > maxBytes) {
            return FrameDecision.DISCONNECT;
        }
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
        if (frames >= maxFrames || byteCount > maxBytes - bytes) {
            frameWindowExceeded = true;
            return consecutiveExceededFrameWindows
                    >= CONSECUTIVE_ABUSIVE_WINDOWS_TO_DISCONNECT - 1
                            ? FrameDecision.DISCONNECT : FrameDecision.DROP;
        }
        frames++;
        bytes += byteCount;
        return FrameDecision.ALLOW;
    }

    synchronized boolean allowChat(long nowMillis) {
        rotateFeatureWindow(nowMillis);
        if (chats >= maxChats) return false;
        chats++;
        return true;
    }

    synchronized boolean allowBinaryOperation(long nowMillis) {
        rotateFeatureWindow(nowMillis);
        if (binaryOperations >= maxBinaryOperations) return false;
        binaryOperations++;
        return true;
    }

    private void rotateFeatureWindow(long nowMillis) {
        if (featureWindowStartedAt == 0L
                || nowMillis - featureWindowStartedAt >= featureWindowMillis) {
            featureWindowStartedAt = nowMillis;
            chats = 0;
            binaryOperations = 0;
        }
    }
}
