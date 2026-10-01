package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.MathUtils;

/** Frame-rate independent motion shared by every GDX settings switch. */
final class GdxToggleMotion {

    /** Roughly 95% of the travel is completed in 0.37 seconds. */
    static final float RESPONSE_RATE = 8f;
    private static final float SNAP_EPSILON = 0.001f;

    private GdxToggleMotion() {
    }

    static String stableKey(String context, float x, float y, float width,
            boolean compact) {
        return stableKey(context, "", x, y, width, compact);
    }

    static String stableKey(String context, String displayedLabel,
            float x, float y, float width, boolean compact) {
        String semanticLabel = displayedLabel != null
                && displayedLabel.endsWith(" *")
                        ? displayedLabel.substring(0,
                                displayedLabel.length() - 2)
                        : displayedLabel;
        return context + ':' + semanticLabel + ':' + Float.floatToIntBits(x) + ':'
                + Float.floatToIntBits(y) + ':' + Float.floatToIntBits(width)
                + ':' + compact;
    }

    static float next(float current, float target, float deltaSeconds) {
        float delta = MathUtils.clamp(deltaSeconds, 0f, 0.1f);
        float blend = 1f - (float) Math.exp(-RESPONSE_RATE * delta);
        float next = current + (target - current) * blend;
        return Math.abs(target - next) < SNAP_EPSILON ? target : next;
    }
}
