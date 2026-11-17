/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.tonikelope.coronapoker.table.TableSnapshot;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Sweeps the board from flop to river, then fades its top card. */
final class GdxHandCardCollection {
    static final int RIVER_SLOT = 4;
    static final float FADE_SECONDS = .18f;
    private static final float SWEEP_DURATION_SCALE = .45f;

    record BoardCard(int slot, TableSnapshot.CardSnapshot card) { }

    final List<BoardCard> board;
    final CompletableFuture<Void> barrier;
    private final GdxCardDealMotion.FrameClock clock = new GdxCardDealMotion.FrameClock();
    private final float sweepSeconds;

    GdxHandCardCollection(List<BoardCard> board, int speedPercent, CompletableFuture<Void> barrier) {
        this.board = List.copyOf(board);
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
        float progress = MathUtils.clamp((clock.elapsedSeconds() - sweepSeconds) / FADE_SECONDS, 0f, 1f);
        return 1f - Interpolation.smooth.apply(progress);
    }

    boolean finished() { return clock.elapsedSeconds() >= sweepSeconds + FADE_SECONDS; }
}
