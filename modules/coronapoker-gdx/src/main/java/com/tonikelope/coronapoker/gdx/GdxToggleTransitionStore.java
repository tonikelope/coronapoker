/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Time-based switch transitions which keep progressing while a row is not
 * being rendered.
 *
 * <p>A scrollable settings page cannot advance switch animations only from
 * draw calls. Doing so makes an off-screen child start moving when it becomes
 * visible instead of when its parent changed. This store records the common
 * transition instant and reconstructs the correct visual position from
 * elapsed time.</p>
 */
final class GdxToggleTransitionStore {

    private static final float NANOS_PER_SECOND = 1_000_000_000f;

    private record Transition(float from, float target, long startedNanos) {
    }

    private final Map<String, Transition> transitions = new HashMap<>();

    float sample(String key, float target, long nowNanos) {
        Objects.requireNonNull(key, "key");
        float boundedTarget = GdxToggleMotion.bounded(target);
        Transition transition = transitions.get(key);
        if (transition == null) {
            transitions.put(key, new Transition(boundedTarget, boundedTarget,
                    nowNanos));
            return boundedTarget;
        }
        if (Float.compare(transition.target(), boundedTarget) != 0) {
            float current = valueAt(transition, nowNanos);
            transition = new Transition(current, boundedTarget, nowNanos);
            transitions.put(key, transition);
        }
        float value = valueAt(transition, nowNanos);
        if (Float.compare(value, transition.target()) == 0
                && Float.compare(transition.from(), transition.target()) != 0) {
            transitions.put(key, new Transition(value, value, nowNanos));
        }
        return value;
    }

    void begin(String key, float oldTarget, float newTarget, long nowNanos) {
        Objects.requireNonNull(key, "key");
        float boundedOld = GdxToggleMotion.bounded(oldTarget);
        float boundedNew = GdxToggleMotion.bounded(newTarget);
        Transition transition = transitions.get(key);
        float current = transition == null
                ? boundedOld : valueAt(transition, nowNanos);
        if (Float.compare(current, boundedNew) == 0) {
            transitions.put(key, new Transition(boundedNew, boundedNew,
                    nowNanos));
            return;
        }
        transitions.put(key, new Transition(current, boundedNew, nowNanos));
    }

    void clear() {
        transitions.clear();
    }

    private static float valueAt(Transition transition, long nowNanos) {
        float elapsedSeconds = Math.max(0L,
                nowNanos - transition.startedNanos()) / NANOS_PER_SECOND;
        return GdxToggleMotion.atElapsed(transition.from(),
                transition.target(), elapsedSeconds);
    }
}
