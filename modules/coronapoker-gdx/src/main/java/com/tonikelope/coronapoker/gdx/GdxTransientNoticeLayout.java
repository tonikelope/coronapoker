package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;

/** Shared content-sized geometry for non-modal transient notifications. */
final class GdxTransientNoticeLayout {

    private GdxTransientNoticeLayout() {
    }

    static float contentWidth(float measuredTextWidth, float leadingWidth,
            float horizontalPadding, float minimumWidth, float maximumWidth,
            float availableWidth) {
        float available = finitePositiveOrOne(availableWidth);
        float maximum = Math.min(finitePositiveOrOne(maximumWidth), available);
        float minimum = Math.min(Math.max(1f,
                finiteNonNegative(minimumWidth)), maximum);
        float desired = finiteNonNegative(measuredTextWidth)
                + finiteNonNegative(leadingWidth)
                + finiteNonNegative(horizontalPadding);
        return MathUtils.clamp(desired, minimum, maximum);
    }

    static Rectangle topCentered(float worldWidth, float worldHeight,
            float desiredWidth, float height, float topInset,
            float horizontalMargin) {
        float safeWorldWidth = finitePositiveOrOne(worldWidth);
        float safeWorldHeight = finitePositiveOrOne(worldHeight);
        float margin = Math.min(finiteNonNegative(horizontalMargin),
                Math.max(0f, (safeWorldWidth - 1f) / 2f));
        float width = MathUtils.clamp(finitePositiveOrOne(desiredWidth), 1f,
                Math.max(1f, safeWorldWidth - margin * 2f));
        float safeHeight = MathUtils.clamp(finitePositiveOrOne(height), 1f,
                safeWorldHeight);
        float x = (safeWorldWidth - width) / 2f;
        float y = Math.max(0f, safeWorldHeight - finiteNonNegative(topInset)
                - safeHeight);
        return new Rectangle(x, y, width, safeHeight);
    }

    private static float finiteNonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0f, value) : 0f;
    }

    private static float finitePositiveOrOne(float value) {
        return Float.isFinite(value) && value > 0f ? value : 1f;
    }
}
