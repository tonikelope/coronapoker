/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.tonikelope.coronapoker.core.game.CardCode;
import com.tonikelope.coronapoker.table.TableSnapshot;
import java.util.List;

/** Card identity survives the core's logical sort and the physical crossing. */
final class GdxPocketOrder {
    record Pose(int slot, float x, float fan, float layers, float lift) {
        static Pose resting(int slot) {
            return new Pose(slot, slot == 0 ? .18f : -.18f, slot == 0 ? -3f : 3f, slot, 0f);
        }
    }
    private final String[] dealt = {"", ""};
    private boolean animateDeal, complete;
    private float elapsed = -1f;

    void reset() {
        dealt[0] = dealt[1] = "";
        animateDeal = complete = false;
        elapsed = -1f;
    }

    void deal(int slot, String code) { deal(slot, code, true); }

    void deal(int slot, String code, boolean animate) {
        if (slot == 0) reset();
        animateDeal = animate;
        if (slot >= 0 && slot < 2 && code != null && !code.isBlank()) dealt[slot] = code;
    }

    void remember(List<TableSnapshot.CardSnapshot> cards) {
        for (int i = 0; i < Math.min(2, cards.size()); i++) {
            if (dealt[i].isBlank() && !cards.get(i).code().isBlank()) dealt[i] = cards.get(i).code();
        }
    }

    void update(boolean ready, boolean animate, float delta) {
        if (complete || !known()) return;
        if (!ready) return;
        if (!crossing() || !animateDeal || !animate) { complete = true; return; }
        if (elapsed < 0f) { elapsed = 0f; return; }
        elapsed += MathUtils.clamp(delta, 0f, .05f);
        complete = elapsed >= duration();
    }

    boolean busy() { return animateDeal && known() && crossing() && !complete; }
    private boolean known() { return !dealt[0].isBlank() && !dealt[1].isBlank(); }
    private boolean crossing() {
        return CardCode.parseShortCode(dealt[0]).rank().aceHighValue()
                > CardCode.parseShortCode(dealt[1]).rank().aceHighValue();
    }
    private float duration() { return .74f; }

    int dealtSlot(String code, int fallbackSlot) {
        return dealt[0].equals(code) ? 0 : dealt[1].equals(code) ? 1 : fallbackSlot;
    }

    Pose pose(String code, int fallbackSlot) {
        if (!known()) return Pose.resting(fallbackSlot);
        int identity = dealtSlot(code, fallbackSlot);
        int target = crossing() ? 1 - identity : identity;
        if (complete) return Pose.resting(target);
        if (elapsed < 0f) return Pose.resting(identity);
        float p = MathUtils.clamp(elapsed / duration(), 0f, 1f);
        float sign = identity == 0 ? 1f : -1f;
        if (!crossing()) return Pose.resting(identity);
        // Separate before changing the vertical order. Cross with one sheet
        // lifted, change layers while disjoint, then close the compact fan.
        float spread = ease(p / .22f);
        float cross = ease((p - .34f) / .32f);
        float settle = ease((p - .66f) / .12f);
        float join = ease((p - .78f) / .22f);
        float x = sign * MathUtils.lerp(.18f, .508f, spread) * (1f - 2f * cross);
        x = MathUtils.lerp(x, -sign * .18f, join);
        float lift = identity == 1 ? .07f * ease((p - .22f) / .12f) * (1f - settle) : 0f;
        // Align the long sides when the cards exchange height, so the
        // necessary gap stays inside the corridor between the stacks.
        float fan = -3f * sign * (1f - 2f * cross) * (1f - spread * (1f - join));
        return new Pose(target, x, fan,
                MathUtils.lerp(identity, target, settle), lift);
    }

    private static float ease(float value) {
        return Interpolation.smoother.apply(MathUtils.clamp(value, 0f, 1f));
    }
}
