package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.Crupier;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void funnyRaiseMatchesTheSwingRaiseAndReraiseContract() {
        assertTrue(GdxSoundFeedback.funnyRaise(
                TableVisualEvent.PlayerAction.ActionKind.RAISE, true));
        assertTrue(GdxSoundFeedback.funnyRaise(
                TableVisualEvent.PlayerAction.ActionKind.RERAISE, true));
        assertFalse(GdxSoundFeedback.funnyRaise(
                TableVisualEvent.PlayerAction.ActionKind.BET, true));
        assertFalse(GdxSoundFeedback.funnyRaise(
                TableVisualEvent.PlayerAction.ActionKind.RAISE, false));
        assertEquals("misc/raise.wav", GdxSoundFeedback.FUNNY_RAISE);
        assertEquals("misc/norebuy.wav", GdxSoundFeedback.FUNNY_NO_REBUY);
    }

    @Test
    void avatarZoomUsesAnAudibleInAndOutPair() {
        assertEquals(GdxSoundFeedback.ZOOM_IN,
                GdxSoundFeedback.avatarZoomTransition(false, true));
        assertEquals(GdxSoundFeedback.ZOOM_OUT,
                GdxSoundFeedback.avatarZoomTransition(true, false));
        assertEquals("", GdxSoundFeedback.avatarZoomTransition(false, false));
    }

    @Test
    void everyBundledFunnySoundDeclaredByTheDealerIsPackagedForGdx() {
        List<Map.Entry<String, String[]>> families = List.of(
                Crupier.ALLIN_SOUNDS_ES, Crupier.FOLD_SOUNDS_ES,
                Crupier.SHOWDOWN_SOUNDS_ES, Crupier.WINNER_SOUNDS_ES,
                Crupier.LOSER_SOUNDS_ES);
        for (Map.Entry<String, String[]> family : families) {
            for (String file : family.getValue()) {
                String resource = "/sounds/" + family.getKey() + file;
                assertNotNull(getClass().getResource(resource), resource);
            }
        }
        assertNotNull(getClass().getResource(
                "/sounds/" + GdxSoundFeedback.FUNNY_RAISE));
        assertNotNull(getClass().getResource(
                "/sounds/" + GdxSoundFeedback.FUNNY_NO_REBUY));
    }

    @Test
    void funnyResourceClassificationCoversEverySwingSpecialCue() {
        assertTrue(GdxSoundFeedback.funnyResource("joke/es/fold/foo.wav"));
        assertTrue(GdxSoundFeedback.funnyResource("misc/raise.wav"));
        assertTrue(GdxSoundFeedback.funnyResource("misc/norebuy.wav"));
        assertTrue(GdxSoundFeedback.funnyResource("misc/showyourcards.wav"));
        assertTrue(GdxSoundFeedback.funnyResource("misc/indivisible.wav"));
        assertTrue(GdxSoundFeedback.funnyResource("misc/lastcard.wav"));
        assertTrue(GdxSoundFeedback.funnyResource("misc/badbeat.wav"));
        assertTrue(GdxSoundFeedback.funnyResource("misc/youarelucky.wav"));
        assertFalse(GdxSoundFeedback.funnyResource("misc/check.wav"));
        assertFalse(GdxSoundFeedback.funnyResource(null));
    }
}
