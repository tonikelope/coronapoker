package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.table.TableVisualEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GdxSoundFeedbackTest {

    @Test
    void pauseOnlySoundsOnTheAuthoritativePausedTransition() {
        assertTrue(GdxSoundFeedback.pauseStarted(false, true));
        assertFalse(GdxSoundFeedback.pauseStarted(true, false));
        assertFalse(GdxSoundFeedback.pauseStarted(true, true));
    }

    @Test
    void lastHandUsesTheSameOnAndOffCuesAsSwing() {
        assertEquals(GdxSoundFeedback.LAST_HAND_ON,
                GdxSoundFeedback.lastHandTransition(false, true));
        assertEquals(GdxSoundFeedback.LAST_HAND_OFF,
                GdxSoundFeedback.lastHandTransition(true, false));
        assertEquals("", GdxSoundFeedback.lastHandTransition(true, true));
    }

    @Test
    void yourTurnOnlySoundsForTheLocalStartMarker() {
        TableVisualEvent.TurnTimer local = new TableVisualEvent.TurnTimer(
                1L, "server", 30_000L, 30_000L,
                TableVisualEvent.TurnTimer.Phase.START);
        TableVisualEvent.TurnTimer remote = new TableVisualEvent.TurnTimer(
                2L, "remote", 30_000L, 30_000L,
                TableVisualEvent.TurnTimer.Phase.START);
        TableVisualEvent.TurnTimer update = new TableVisualEvent.TurnTimer(
                3L, "server", 30_000L, 20_000L,
                TableVisualEvent.TurnTimer.Phase.UPDATE);

        assertTrue(GdxSoundFeedback.localTurnStarted(local, "server"));
        assertFalse(GdxSoundFeedback.localTurnStarted(remote, "server"));
        assertFalse(GdxSoundFeedback.localTurnStarted(update, "server"));
    }
}
