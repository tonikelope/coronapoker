/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;

/**
 * Single permission policy for settings changed while a table is running.
 * It mirrors the enabled/disabled rules of Swing's {@code GameSettingsPanel}
 * so drawing and pointer handling cannot disagree.
 */
final class GdxLiveSettingsPolicy {

    private GdxLiveSettingsPolicy() {
    }

    static boolean canEditGameRules(boolean host,
            GameConfigCodecV1.Configuration configuration) {
        return host && configuration != null;
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
}
