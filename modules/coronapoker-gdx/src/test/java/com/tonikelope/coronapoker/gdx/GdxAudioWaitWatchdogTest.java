/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GdxAudioWaitWatchdogTest {

    @Test
    void missingAudioDeviceCannotBlockTheTurnTimeoutDecision() {
        assertFalse(CoronaPokerGdxTable.audioWaitMustFinish(
                "misc/timeout.wav", 10f, 10.05f, false));
        assertTrue(CoronaPokerGdxTable.audioWaitMustFinish(
                "misc/timeout.wav", 10f, 10.13f, false));
    }

    @Test
    void lostTimeoutCompletionCallbackHasABoundedFallback() {
        assertFalse(CoronaPokerGdxTable.audioWaitMustFinish(
                "misc/timeout.wav", 10f, 13.99f, true));
        assertTrue(CoronaPokerGdxTable.audioWaitMustFinish(
                "misc/timeout.wav", 10f, 14f, true));
    }

    @Test
    void ordinaryPlayingCueKeepsItsLongerPresentationAllowance() {
        assertFalse(CoronaPokerGdxTable.audioWaitMustFinish(
                "misc/badbeat.wav", 10f, 21.99f, true));
        assertTrue(CoronaPokerGdxTable.audioWaitMustFinish(
                "misc/badbeat.wav", 10f, 22f, true));
    }

    @Test
    void gameOverAudioIsNotCutAtTheGenericTwelveSecondWatchdog() {
        assertFalse(CoronaPokerGdxTable.audioWaitMustFinish(
                "misc/game_over.wav", 10f, 23.99f, true));
        assertTrue(CoronaPokerGdxTable.audioWaitMustFinish(
                "misc/game_over.wav", 10f, 26f, true));
    }
}
