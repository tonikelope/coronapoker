/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.game.GamePresentationSettings;
import org.junit.jupiter.api.Test;

final class GamePresentationAudioPolicyTest {

    @Test
    void concreteJokePreferenceKeyIsStableAndReversible() {
        String resource = "joke/es/allin/esto_es_el_maximo.wav";
        String key = GamePresentationSettings.jokeSoundPreferenceKey(resource);

        assertTrue(key.startsWith(
                GamePresentationSettings.JOKE_SOUND_PREFERENCE_PREFIX));
        assertEquals(resource,
                GamePresentationSettings.jokeSoundResource(key));
        assertFalse(key.contains("/"));
    }

    @Test
    void cinematicOnlySuppressesJokeAudioWhenCompanionActuallyPlays() {
        assertTrue(Crupier.shouldPlayAllInCompanionAudio(true, true, true));
        assertFalse(Crupier.shouldPlayAllInCompanionAudio(true, true, false));
        assertFalse(Crupier.shouldPlayAllInCompanionAudio(true, false, true));
        assertFalse(Crupier.shouldPlayAllInCompanionAudio(false, true, true));
    }
}
