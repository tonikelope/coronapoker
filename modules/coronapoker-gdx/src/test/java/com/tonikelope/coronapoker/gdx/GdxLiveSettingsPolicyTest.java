/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GdxLiveSettingsPolicyTest {

    @Test
    void onlyHostWithAuthoritativeConfigurationCanEditGameRules() {
        GameConfigCodecV1.Configuration configuration = configuration(true);

        assertTrue(GdxLiveSettingsPolicy.canEditGameRules(true,
                configuration));
        assertFalse(GdxLiveSettingsPolicy.canEditGameRules(false,
                configuration));
        assertFalse(GdxLiveSettingsPolicy.canEditGameRules(true, null));
    }

    @Test
    void runItTwiceCannotChangeWhileCanonicalRunoutIsLocked() {
        GameConfigCodecV1.Configuration configuration = configuration(true);

        assertTrue(GdxLiveSettingsPolicy.canEditRunItTwice(true,
                configuration, false));
        assertFalse(GdxLiveSettingsPolicy.canEditRunItTwice(true,
                configuration, true));
        assertFalse(GdxLiveSettingsPolicy.canEditRunItTwice(false,
                configuration, false));
    }

    @Test
    void botRebuyMatchesSwingAndRequiresTableRebuys() {
        assertTrue(GdxLiveSettingsPolicy.canEditBotRebuy(true,
                configuration(true)));
        assertFalse(GdxLiveSettingsPolicy.canEditBotRebuy(true,
                configuration(false)));
        assertFalse(GdxLiveSettingsPolicy.canEditBotRebuy(false,
                configuration(true)));
    }

    private static GameConfigCodecV1.Configuration configuration(
            boolean rebuy) {
        return new GameConfigCodecV1.Configuration(10, 0.10d, 0.20d,
                0, 1, false, "session", rebuy, -1, 0d, 0,
                true, true, 10, 100, 0, false, false, true,
                true, 0, 40, true, 10, false, List.of());
    }
}
