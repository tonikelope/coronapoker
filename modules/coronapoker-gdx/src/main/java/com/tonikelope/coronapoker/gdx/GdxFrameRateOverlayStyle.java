package com.tonikelope.coronapoker.gdx;

/** Shared geometry contract for the global FPS/VSync overlay. */
final class GdxFrameRateOverlayStyle {

    static final float WIDTH = 206f;
    static final float HEIGHT = 38f;
    static final float RIGHT_MARGIN = 12f;
    static final float TOP_MARGIN = 10f;
    static final float FINAL_SUMMARY_RIGHT_MARGIN = 86f;

    static float x(float surfaceWidth, boolean reserveFinalControl) {
        float margin = reserveFinalControl
                ? FINAL_SUMMARY_RIGHT_MARGIN : RIGHT_MARGIN;
        return surfaceWidth - WIDTH - margin;
    }

    static float y(float surfaceHeight) {
        return surfaceHeight - HEIGHT - TOP_MARGIN;
    }

    private GdxFrameRateOverlayStyle() {
    }
}
