/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.tonikelope.coronapoker.table.TableSnapshot;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Fades the players' cards in place and collects the board from flop to river. */
final class GdxHandCardCollection {
    static final int RIVER_SLOT = 4;
    static final float HAND_FADE_SECONDS = .34f;
    private static final float RUNOUT_FADE_SECONDS = .18f;
    private static final float SWEEP_DURATION_SCALE = .45f;

    record BoardCard(int slot, TableSnapshot.CardSnapshot card) { }

    final List<BoardCard> board;
    final boolean wholeHand;
    final CompletableFuture<Void> barrier;
    private final GdxCardDealMotion.FrameClock clock = new GdxCardDealMotion.FrameClock();
    private final float sweepSeconds;

    GdxHandCardCollection(List<BoardCard> board, int speedPercent,
            boolean wholeHand, CompletableFuture<Void> barrier) {
        this.board = List.copyOf(board);
        this.wholeHand = wholeHand;
        this.barrier = barrier;
        // The outer flop card crosses two gaps during the spread. Scale that
        // cadence by the distance to the river, then tighten the collection.
        sweepSeconds = board.isEmpty() ? 0f : (RIVER_SLOT - board.get(0).slot())
                * GdxCommunityStreetDeal.flopSpreadSeconds(speedPercent) * .5f * SWEEP_DURATION_SCALE;
    }

    void advance(long frameNanos) { clock.advance(frameNanos); }

    float boardPosition() {
        if (sweepSeconds == 0f) return RIVER_SLOT;
        float progress = MathUtils.clamp(clock.elapsedSeconds() / sweepSeconds, 0f, 1f);
        return MathUtils.lerp(board.get(0).slot(), RIVER_SLOT, Interpolation.smooth.apply(progress));
    }

    int visibleBoardCardCount() {
        if (board.isEmpty() || finished()) return 0;
        // Retire the covered sheets before the first fade frame, even while
        // opacity still rounds to 1. Keep board intact for the top elevation.
        return clock.elapsedSeconds() < sweepSeconds ? board.size() : 1;
    }

    float cardElevation(int index, float position, float width, float gap) {
        float backEdge = width / gap;
        float layers = 0f;
        for (int above = index + 1; above < board.size(); above++) {
            // Raise one thickness in the gap before the incoming edge covers
            // the next card. The earliest card stays on top of the stack.
            float gapStart = board.get(above).slot() - 1f;
            layers += Interpolation.smooth.apply(MathUtils.clamp(
                    (position - gapStart) / (1f - backEdge), 0f, 1f));
        }
        return layers * GdxPlasticCard.thickness(width);
    }

    float opacity() {
        return fadeOpacity(clock.elapsedSeconds() - sweepSeconds, boardFadeSeconds());
    }

    float holeCardOpacity() {
        return wholeHand ? fadeOpacity(clock.elapsedSeconds(), HAND_FADE_SECONDS) : 1f;
    }

    private float boardFadeSeconds() {
        return wholeHand ? HAND_FADE_SECONDS : RUNOUT_FADE_SECONDS;
    }

    private static float fadeOpacity(float elapsed, float duration) {
        float progress = MathUtils.clamp(elapsed / duration, 0f, 1f);
        return 1f - Interpolation.smooth.apply(progress);
    }

    boolean finished() { return clock.elapsedSeconds() >= sweepSeconds + boardFadeSeconds(); }
}
