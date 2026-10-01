package com.tonikelope.coronapoker.gdx;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Runs a switch mutation and its audible feedback in the only safe order.
 * Disabling must sound before the switch can mute its own cue; enabling must
 * sound afterwards so switches that re-enable audio can be heard immediately.
 */
final class GdxToggleSoundAction {

    private GdxToggleSoundAction() {
    }

    static void run(boolean enabledBefore, Runnable action,
            Consumer<Boolean> sound) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(sound, "sound");
        if (enabledBefore) sound.accept(false);
        action.run();
        if (!enabledBefore) sound.accept(true);
    }
}
