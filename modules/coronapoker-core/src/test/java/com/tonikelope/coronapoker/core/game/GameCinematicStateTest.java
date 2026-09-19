package com.tonikelope.coronapoker.core.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GameCinematicStateTest {

    @Test
    void coordinatedStateGatesDealerUntilTheFrontendStopsIt() {
        GameCinematicState state = GameCinematicState.coordinated();

        assertFalse(state.isPlaying());
        state.start();
        assertTrue(state.isPlaying());
        state.stop();
        assertFalse(state.isPlaying());
    }
}
