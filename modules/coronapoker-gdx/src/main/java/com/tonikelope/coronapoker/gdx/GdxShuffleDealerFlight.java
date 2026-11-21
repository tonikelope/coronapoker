/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

/** Turns the squared mesh on the felt, then carries it into the centre of the dealer's seat. */
final class GdxShuffleDealerFlight implements Disposable {
    static final float SQUARE_SECONDS = .18f;
    static final float TURN_SECONDS = .18f;
    static final float LIFT_SECONDS = .12f;
    static final float APPROACH_SECONDS = TURN_SECONDS + LIFT_SECONDS + .44f;
    static final float FADE_SECONDS = .10f;

    private final GdxCardShell layer;
    private final Matrix4 turned = new Matrix4();
    private final Matrix4 airborne = new Matrix4();
    private final Vector3 center = new Vector3();
    private final Vector3 along = new Vector3();
    private final Rectangle crop = new Rectangle();

    GdxShuffleDealerFlight(int activeSamples) {
        layer = new GdxCardShell(activeSamples, false);
    }

    void draw(GdxRiffleShuffle riffle, Matrix4 table, Texture back, Texture[] faces, int mode, float aspect,
            float width, float height, float dealerX, float dealerY, float avatarSize, boolean local,
            float potRelief, float seconds) {
        float midHeight = GdxShuffleGeometry.stackHeight(aspect) * .5f;
        center.set(0f, midHeight, 0f).prj(table);
        float dx = dealerX * 2f / width - 1f - center.x;
        float dy = dealerY * 2f / height - 1f - center.y;
        // Express the bearing in the original table plane. Either short end can lead.
        float[] m = table.val;
        float ax = m[Matrix4.M00] - center.x * m[Matrix4.M30];
        float ay = m[Matrix4.M10] - center.y * m[Matrix4.M30];
        float bx = m[Matrix4.M02] - center.x * m[Matrix4.M32];
        float by = m[Matrix4.M12] - center.y * m[Matrix4.M32];
        float x = dx * by - dy * bx;
        float z = ax * dy - ay * dx;
        float bearing = MathUtils.atan2Deg(-z, x);
        if (bearing > 90f) bearing -= 180f;
        if (bearing < -90f) bearing += 180f;
        float turn = Interpolation.smooth.apply(Math.min(1f, seconds / TURN_SECONDS));
        turned.set(table).rotate(Vector3.Y, bearing);
        float finalScale = landingScale(width, height, aspect, midHeight, avatarSize, local);
        turned.set(table).rotate(Vector3.Y, bearing * turn);
        float takeoff = TURN_SECONDS + LIFT_SECONDS;
        float travel = Interpolation.smooth.apply(MathUtils.clamp(
                (seconds - takeoff) / (APPROACH_SECONDS - takeoff), 0f, 1f));
        float scale = MathUtils.lerp(1f, finalScale, travel);
        float rise = Interpolation.smooth.apply(MathUtils.clamp((seconds - TURN_SECONDS) / LIFT_SECONDS, 0f, 1f));
        float descent = Interpolation.smooth.apply(MathUtils.clamp((travel - .70f) / .30f, 0f, 1f));
        // A projected pile can extend beyond the viewport in a large pot. Keep the
        // takeoff readable on screen. The airborne deck is drawn above the chips.
        float clearance = MathUtils.clamp(potRelief + avatarSize * .10f, avatarSize * .34f, avatarSize * .58f);
        float lift = 2f * clearance / height * rise * (1f - descent);
        airborne.setToTranslation((1f - scale) * center.x + travel * dx,
                (1f - scale) * center.y + travel * dy + lift, 0f)
                .scale(scale, scale, 1f).mul(turned);

        float shadow = 1f - rise;
        if (shadow > 0f) riffle.drawOnTable(turned, back, faces, mode, shadow, false);
        if (seconds <= APPROACH_SECONDS) {
            riffle.drawOnTable(airborne, back, faces, mode, 0f, true);
            return;
        }
        int pixelsWide = Gdx.graphics.getBackBufferWidth(), pixelsHigh = Gdx.graphics.getBackBufferHeight();
        GdxShuffleGeometry.projectRestBounds(airborne, pixelsWide, pixelsHigh, aspect, crop, along);
        int left = Math.max(0, MathUtils.floor(crop.x) - 2);
        int bottom = Math.max(0, MathUtils.floor(crop.y) - 2);
        int right = Math.min(pixelsWide, MathUtils.ceil(crop.x + crop.width) + 2);
        int top = Math.min(pixelsHigh, MathUtils.ceil(crop.y + crop.height) + 2);
        if (left >= right || bottom >= top) return;
        // Fade the resolved deck once. Blending 52 cards individually would expose their inner faces.
        layer.beginPair(left, bottom, right - left, top - bottom);
        layer.renderLayer(() -> riffle.drawOnTable(airborne, back, faces, mode, 0f, true), width, height);
        float fade = MathUtils.clamp((seconds - APPROACH_SECONDS) / FADE_SECONDS, 0f, 1f);
        layer.endPair(1f - Interpolation.smooth.apply(fade));
    }

    private float landingScale(float width, float height, float aspect, float midHeight,
            float avatarSize, boolean local) {
        // Keep the landing footprint proportional to the HUD scale at any shuffle zoom.
        float radiusSquared = 0f;
        for (int corner = 0; corner < 8; corner++) {
            along.set((corner & 1) == 0 ? -.5f : .5f, (corner & 2) == 0 ? 0f : midHeight * 2f,
                    (corner & 4) == 0 ? -.5f * aspect : .5f * aspect).prj(turned);
            float x = (along.x - center.x) * width * .5f;
            float y = (along.y - center.y) * height * .5f;
            radiusSquared = Math.max(radiusSquared, x * x + y * y);
        }
        float radius = (float) Math.sqrt(radiusSquared);
        float targetRadius = avatarSize * (local ? .43f : .27f);
        return Math.min(local ? .8f : .6f, targetRadius / Math.max(1f, radius));
    }

    @Override public void dispose() {
        layer.dispose();
    }
}
