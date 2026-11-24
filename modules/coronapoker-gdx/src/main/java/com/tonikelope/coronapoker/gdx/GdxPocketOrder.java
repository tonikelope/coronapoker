/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.tonikelope.coronapoker.core.game.CardCode;
import com.tonikelope.coronapoker.table.TableSnapshot;
import java.util.List;

/** Card identity survives the core's logical sort and the physical crossing. */
final class GdxPocketOrder {
    static final float DURATION_SECONDS = .56f;
    private static final float SLIDE_END = .64f;
    private static final float LAYER_CHANGE_END = .76f;
    // Allow a gap of 5.6% of a card width beyond the aligned mesh edges.
    private static final float SEPARATED_X = GdxPerspectiveTable.EDGE_MARGIN_SCALE * .5f + .028f;

    record Pose(int slot, float x, float fan, float layers) {
        static Pose resting(int slot) {
            return new Pose(slot, slot == 0 ? .18f : -.18f, slot == 0 ? -3f : 3f, slot);
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
        complete = elapsed >= DURATION_SECONDS;
    }

    boolean busy() { return animateDeal && known() && crossing() && !complete; }
    private boolean known() { return !dealt[0].isBlank() && !dealt[1].isBlank(); }
    private boolean crossing() {
        return CardCode.parseShortCode(dealt[0]).rank().aceHighValue()
                > CardCode.parseShortCode(dealt[1]).rank().aceHighValue();
    }

    int dealtSlot(String code, int fallbackSlot) {
        return dealt[0].equals(code) ? 0 : dealt[1].equals(code) ? 1 : fallbackSlot;
    }

    Pose pose(String code, int fallbackSlot) {
        if (!known()) return Pose.resting(fallbackSlot);
        int identity = dealtSlot(code, fallbackSlot);
        int target = crossing() ? 1 - identity : identity;
        if (complete) return Pose.resting(target);
        if (elapsed < 0f) return Pose.resting(identity);
        if (target == identity) return Pose.resting(identity);
        float p = MathUtils.clamp(elapsed / DURATION_SECONDS, 0f, 1f);
        Pose start = Pose.resting(identity);
        Pose end = Pose.resting(target);
        // Slide past each other on the dealt layers, and change layers only
        // after the edges clear, so the lower card slides underneath on return.
        float slide = ease(p / SLIDE_END);
        float settle = ease((p - SLIDE_END) / (LAYER_CHANGE_END - SLIDE_END));
        float join = ease((p - LAYER_CHANGE_END) / (1f - LAYER_CHANGE_END));
        float separatedX = Math.copySign(SEPARATED_X, end.x());
        float x = MathUtils.lerp(MathUtils.lerp(start.x(), separatedX, slide), end.x(), join);
        float fan = MathUtils.lerp(start.fan() * (1f - slide), end.fan(), join);
        return new Pose(target, x, fan, MathUtils.lerp(start.layers(), end.layers(), settle));
    }

    private static float ease(float value) {
        return Interpolation.smoother.apply(MathUtils.clamp(value, 0f, 1f));
    }
}
