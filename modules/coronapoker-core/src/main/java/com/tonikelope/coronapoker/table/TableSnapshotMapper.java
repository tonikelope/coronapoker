/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.table;

import com.tonikelope.coronapoker.core.game.CardState;
import com.tonikelope.coronapoker.core.game.HandState;
import com.tonikelope.coronapoker.core.game.MoneyMath;
import com.tonikelope.coronapoker.core.game.PlayerState;
import com.tonikelope.coronapoker.core.game.TableState;
import java.util.Objects;
import java.util.function.ToIntFunction;

/** Converts authoritative neutral state into the immutable renderer contract. */
public final class TableSnapshotMapper {

    private TableSnapshotMapper() {
    }

    public static TableSnapshot from(TableState.Snapshot state) {
        return from(state, ignored -> 0);
    }

    public static TableSnapshot from(TableState.Snapshot state,
            ToIntFunction<String> rebuyCount) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(rebuyCount, "rebuyCount");
        HandState.Snapshot hand = state.hand();
        return new TableSnapshot(state.revision(), state.localNickname(),
                street(hand.street(), state.finished()), hand.pot().total(),
                hand.turn().nickname(), state.paused(),
                state.players().stream().map(player -> player(player,
                        rebuyCount.applyAsInt(player.nickname()))).toList(),
                hand.communityCards().stream().map(TableSnapshotMapper::card).toList());
    }

    public static TableSnapshot.PlayerSnapshot player(PlayerState.Snapshot state) {
        return player(state, 0);
    }

    public static TableSnapshot.PlayerSnapshot player(PlayerState.Snapshot state,
            int rebuyCount) {
        Objects.requireNonNull(state, "state");
        // A payout is deliberately accumulated in pendingPayment until the
        // next hand commits it to the gameplay stack.  The ordinary renderer,
        // however, already receives stackAfter with that payment included.
        // TableSnapshot is a presentation contract, so publishing only the
        // raw gameplay stack here made a renderer attaching during settlement
        // show an older balance than every renderer already at the table.
        // Project the same visible amount on both sides of that hand boundary;
        // nuevaMano() moves the value from pendingPayment into stack, keeping
        // this sum stable rather than counting the payout twice.
        double presentedStack = MoneyMath.clean(state.stack()
                + state.pendingPayment());
        return new TableSnapshot.PlayerSnapshot(state.nickname(), presentedStack,
                state.bet(), state.potContribution(), state.active(),
                state.spectator(), state.exited(), state.timedOut(),
                state.latency(), state.previousLatency(),
                state.reconnectionCount(), state.telemetryAt(),
                state.winner(), state.underTheGun(),
                position(state.position()), decision(state.decision()),
                actionKind(state.actionKind()), state.lastAction(), state.handName(),
                state.holeCards().stream()
                        .map(TableSnapshotMapper::card).toList(),
                state.buyIn(), rebuyCount, state.warming(),
                new TableSnapshot.PlayerPresentation(state.showingCards(),
                        state.partialHand(), state.partialWinPercentage(),
                        state.resultResolved(), state.publicHandName(),
                        state.wonPotIndexes(),
                        state.returnedSidePot(),
                        state.showdownHighlightEnabled(),
                        state.winningHoleCardSlots(),
                        state.winningCommunityCardSlots(),
                        rebuyPhase(state.rebuyPhase()),
                        state.immediateRebuyAmount(),
                        state.publicActionLabel()));
    }

    public static TableSnapshot.CardSnapshot card(CardState.Snapshot state) {
        Objects.requireNonNull(state, "state");
        String code = state.initialized() && state.code() != null
                ? state.code().shortCode() : "";
        return new TableSnapshot.CardSnapshot(code,
                state.visible() && state.faceUp(), state.disabled(),
                state.initialized() && state.visible());
    }

    private static TableSnapshot.Street street(HandState.Street street,
            boolean finished) {
        if (finished) return TableSnapshot.Street.FINISHED;
        return TableSnapshot.Street.valueOf(street.name());
    }

    private static TableSnapshot.Position position(PlayerState.Position position) {
        return TableSnapshot.Position.valueOf(position.name());
    }

    private static TableSnapshot.Decision decision(PlayerState.Decision decision) {
        return TableSnapshot.Decision.valueOf(decision.name());
    }

    private static TableSnapshot.ActionKind actionKind(
            PlayerState.ActionKind actionKind) {
        return TableSnapshot.ActionKind.valueOf(actionKind.name());
    }

    private static TableSnapshot.RebuyPhase rebuyPhase(
            PlayerState.RebuyPhase rebuyPhase) {
        return TableSnapshot.RebuyPhase.valueOf(rebuyPhase.name());
    }
}
