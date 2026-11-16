/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.MathUtils;

/** Table-space geometry shared by the pot and the player's physical stack. */
final class GdxTableChipRack {
    private static final float CHIP_DIAMETER_MM = 39f;
    private static final float CHIP_THICKNESS_MM = 3.3f;
    private static final float CARD_WIDTH_MM = 63f;
    private static final float CENTRE_PITCH = 1.15f;
    static final int CHIPS_PER_PILE = 10;

    private GdxTableChipRack() {}

    static float diameter(float cardWidth) {
        return cardWidth * CHIP_DIAMETER_MM / CARD_WIDTH_MM;
    }

    static float thickness(float diameter) {
        return diameter * CHIP_THICKNESS_MM / CHIP_DIAMETER_MM;
    }

    static float gravity(float diameter) {
        return 9810f * diameter / CHIP_DIAMETER_MM;
    }

    static float halfFootprint(float diameter) {
        return (CENTRE_PITCH + 1f) * diameter * .5f;
    }

    static float localOffsetX(int slot, float diameter) {
        return (slot == 1 || slot == 3 ? -.5f : .5f) * CENTRE_PITCH * diameter;
    }

    static float localOffsetY(int slot, float diameter) {
        return (slot >= 2 ? .5f : -.5f) * CENTRE_PITCH * diameter;
    }

    static float initialLanding(int count, int column, int index, long seed) {
        float stagger = column * .024f;
        float jitter = GdxTableChipFlight.variation(
                GdxTableChipFlight.tossSeed(seed, index, column), 0);
        // Each chip has its own interval, so the lower layers always land first.
        return .35f + stagger + .56f * ((index + .18f + .64f * jitter) / count);
    }

    static float fallDuration(long seed, int color) {
        return .29f + .055f * GdxTableChipFlight.variation(
                GdxTableChipFlight.tossSeed(seed, -1, color), 0);
    }

    static int initialCount(int count, int column, float progress, long seed) {
        int low = 0, high = count;
        while (low < high) {
            int mid = low + (high - low) / 2;
            if (progress >= initialLanding(count, column, mid, seed)) low = mid + 1;
            else high = mid;
        }
        return low;
    }

    static final class Impact {
        private static final float DECAY = 19f;
        private static final float FREQUENCY = 38f;
        private static final float MAX_SWAY = .048f;
        private long sampledAt;
        private float x, y, vx, vy, scale;
        private boolean moving;

        void advance(long frameNanos) {
            if (!moving) return;
            float seconds = (frameNanos - sampledAt) / 1_000_000_000f;
            if (seconds <= 0f) return;
            sampledAt = frameNanos;
            if (seconds >= .4f) {
                settle();
                return;
            }
            float damping = (float) Math.exp(-DECAY * seconds);
            float cos = (float) Math.cos(FREQUENCY * seconds);
            float sin = (float) Math.sin(FREQUENCY * seconds);
            float bx = (vx + DECAY * x) / FREQUENCY;
            float by = (vy + DECAY * y) / FREQUENCY;
            float waveX = x * cos + bx * sin, waveY = y * cos + by * sin;
            vx = damping * (FREQUENCY * (bx * cos - x * sin) - DECAY * waveX);
            vy = damping * (FREQUENCY * (by * cos - y * sin) - DECAY * waveY);
            x = damping * waveX;
            y = damping * waveY;
            scale = Math.min(1f, MAX_SWAY / Math.max(MAX_SWAY, (float) Math.hypot(x, y)));
            if (Math.abs(x) + Math.abs(y) < .0001f && Math.abs(vx) + Math.abs(vy) < .005f) settle();
        }

        void kick(long frameNanos, long seed) {
            advance(frameNanos);
            sampledAt = frameNanos;
            moving = true;
            float angle = MathUtils.PI2 * GdxTableChipFlight.variation(seed, 0);
            float strength = 2.3f + 2.6f * GdxTableChipFlight.variation(seed, 16);
            vx += MathUtils.cos(angle) * strength;
            vy += MathUtils.sin(angle) * strength;
            float limit = 5.3f / Math.max(5.3f, (float) Math.hypot(vx, vy));
            vx *= limit;
            vy *= limit;
        }

        private void settle() {
            moving = false;
            x = y = vx = vy = scale = 0f;
        }

        private static float levelSway(int level) {
            return .62f + .38f * Math.min(1f, level / 4f);
        }

        float offsetX(int level, float diameter) {
            return x * scale * diameter * levelSway(level);
        }

        float offsetY(int level, float diameter) {
            return y * scale * diameter * levelSway(level);
        }
    }
}
