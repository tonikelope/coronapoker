/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;

/**
 * Single permission policy for settings changed while a table is running.
 * It mirrors the enabled/disabled rules of Swing's {@code GameSettingsPanel}
 * so drawing and pointer handling cannot disagree.
 */
final class GdxLiveSettingsPolicy {

    static final int MAXIMUM_HAND_LIMIT = Integer.MAX_VALUE;

    private GdxLiveSettingsPolicy() {
    }

    static boolean canEditGameRules(boolean host,
            GameConfigCodecV1.Configuration configuration) {
        return host && configuration != null;
    }

    static boolean canEditHotJoin(boolean host, boolean lobbyAvailable) {
        return host && lobbyAvailable;
    }

    static boolean canEditRunItTwice(boolean host,
            GameConfigCodecV1.Configuration configuration,
            boolean runItTwiceLocked) {
        return canEditGameRules(host, configuration) && !runItTwiceLocked;
    }

    static boolean canEditBotRebuy(boolean host,
            GameConfigCodecV1.Configuration configuration) {
        return canEditGameRules(host, configuration)
                && configuration.rebuy();
    }

    static int minimumHandLimit(int currentHand) {
        return (int) Math.min(MAXIMUM_HAND_LIMIT,
                Math.max(1L, (long) currentHand + 1L));
    }

    /**
     * Preserves the visible counter used by the waiting-room switch/stepper
     * pair. An unlimited live configuration has no stored count, so its first
     * safe value is the next hand.
     */
    static int handLimitValue(int currentHand, int configuredMaximum) {
        return Math.max(minimumHandLimit(currentHand), configuredMaximum);
    }

    static int adjustedHandLimit(int currentHand, int currentValue,
            int direction) {
        long requested = (long) handLimitValue(currentHand, currentValue)
                + Integer.signum(direction);
        return (int) Math.max(minimumHandLimit(currentHand),
                Math.min(MAXIMUM_HAND_LIMIT, requested));
    }
}
