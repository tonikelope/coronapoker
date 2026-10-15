/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.table;

import com.tonikelope.coronapoker.core.game.CardState;
import com.tonikelope.coronapoker.core.game.HandState;
import com.tonikelope.coronapoker.core.game.MoneyMath;
import com.tonikelope.coronapoker.core.game.PlayerState;
import com.tonikelope.coronapoker.core.game.TableState;
import java.util.Map;
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
                state.winner(), pokerRoleEligible(state) && state.underTheGun(),
                position(state), decision(state.decision()),
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

    /**
     * Rewinds only the presentation part of a settled hand until its ordered
     * payout event is accepted. Gameplay deliberately keeps winnings in
     * {@code pendingPayment}; the ordinary mapping above exposes the resulting
     * final stack so a renderer attaching after the payout is current. During
     * the showdown dwell, however, the pot has not visibly moved yet and both
     * sides of that transfer must describe the same instant.
     */
    public static TableSnapshot beforePendingPayout(TableSnapshot settled,
            double visiblePot, Map<String, Double> stacksBeforePayout,
            Map<String, Double> contributionsBeforePayout) {
        Objects.requireNonNull(settled, "settled");
        Objects.requireNonNull(stacksBeforePayout, "stacksBeforePayout");
        Objects.requireNonNull(contributionsBeforePayout,
                "contributionsBeforePayout");
        double pot = MoneyMath.clean(visiblePot);
        if (MoneyMath.compare(pot, 0d) < 0) {
            throw new IllegalArgumentException("Visible pot cannot be negative");
        }
        java.util.List<TableSnapshot.PlayerSnapshot> players
                = settled.players().stream().map(player -> {
                    Double stack = stacksBeforePayout.get(player.nickname());
                    Double contribution = contributionsBeforePayout.get(
                            player.nickname());
                    if (stack == null && contribution == null) return player;
                    return withSettlementAmounts(player,
                            stack == null ? player.stack()
                                    : MoneyMath.clean(stack),
                            contribution == null ? player.potContribution()
                                    : MoneyMath.clean(contribution));
                }).toList();
        return new TableSnapshot(settled.revision(), settled.localNickname(),
                settled.street(), pot, settled.currentTurnNickname(),
                settled.paused(), players, settled.communityCards(),
                settled.presentation());
    }

    private static TableSnapshot.PlayerSnapshot withSettlementAmounts(
            TableSnapshot.PlayerSnapshot source, double stack,
            double contribution) {
        return new TableSnapshot.PlayerSnapshot(source.nickname(), stack,
                source.streetBet(), contribution, source.active(),
                source.spectator(), source.exited(), source.timedOut(),
                source.latency(), source.previousLatency(),
                source.reconnectionCount(), source.telemetryAt(),
                source.winner(), source.underTheGun(), source.position(),
                source.decision(), source.actionKind(), source.lastAction(),
                source.handName(), source.holeCards(), source.buyIn(),
                source.rebuyCount(), source.warming(), source.presentation());
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

    private static TableSnapshot.Position position(PlayerState.Snapshot state) {
        // Frontends must never infer an active poker role from stale state at a
        // lifecycle boundary. Spectators, warming peers and exited seats are
        // passive observers even if an older snapshot once named them dealer,
        // blind, UTG or straddler.
        if (!pokerRoleEligible(state)) return TableSnapshot.Position.NONE;
        return TableSnapshot.Position.valueOf(state.position().name());
    }

    private static boolean pokerRoleEligible(PlayerState.Snapshot state) {
        return state.active() && !state.spectator() && !state.warming()
                && !state.exited();
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
