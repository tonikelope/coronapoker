package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.table.TableVisualEvent;

/** Pure decisions for renderer-owned UI feedback sounds. */
final class GdxSoundFeedback {

    static final String PAUSE = "misc/pause.wav";
    static final String LAST_HAND_ON = "misc/last_hand_on.wav";
    static final String LAST_HAND_OFF = "misc/last_hand_off.wav";
    static final String YOUR_TURN = "misc/yourturn.wav";
    static final String VOLUME_CHANGE = "misc/volume_change.wav";

    private GdxSoundFeedback() {
    }

    static boolean pauseStarted(boolean before, boolean after) {
        return !before && after;
    }

    static String lastHandTransition(boolean before, boolean after) {
        if (before == after) return "";
        return after ? LAST_HAND_ON : LAST_HAND_OFF;
    }

    static boolean localTurnStarted(TableVisualEvent.TurnTimer timer,
            String localNickname) {
        return timer.phase() == TableVisualEvent.TurnTimer.Phase.START
                && !localNickname.isBlank()
                && localNickname.equals(timer.nickname());
    }
}
