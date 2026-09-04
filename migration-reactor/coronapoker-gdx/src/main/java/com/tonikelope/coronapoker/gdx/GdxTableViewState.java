package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import com.tonikelope.coronapoker.core.game.ActionControlState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Render-thread-owned projection. It contains no rules and never reads Swing. */
final class GdxTableViewState {

    private static final TableSnapshot.CardSnapshot HIDDEN_CARD =
            new TableSnapshot.CardSnapshot("", false, false);

    private TableSnapshot snapshot;
    private long lastSequence;
    private long turnTotalMillis;
    private long turnRemainingMillis;
    private long turnTimerUpdatedNanos;
    private ActionControlState actionControls = ActionControlState.disabled();

    GdxTableViewState(TableSnapshot initialState) {
        snapshot = Objects.requireNonNull(initialState, "initialState");
    }

    TableSnapshot snapshot() {
        return snapshot;
    }

    long lastSequence() {
        return lastSequence;
    }

    long turnTotalMillis() {
        return turnTotalMillis;
    }

    long turnRemainingMillis() {
        if (turnTotalMillis <= 0L || turnRemainingMillis <= 0L) {
            return 0L;
        }
        long elapsedMillis = Math.max(0L,
                (System.nanoTime() - turnTimerUpdatedNanos) / 1_000_000L);
        return Math.max(0L, turnRemainingMillis - elapsedMillis);
    }

    ActionControlState actionControls() {
        return actionControls;
    }

    void apply(TableVisualEvent event) {
        Objects.requireNonNull(event, "event");
        if (event.sequence() <= lastSequence) {
            throw new IllegalArgumentException("Non-monotonic GDX table event "
                    + event.sequence() + " after " + lastSequence);
        }
        lastSequence = event.sequence();

        if (event instanceof TableVisualEvent.Synchronize synchronize) {
            snapshot = synchronize.snapshot();
        } else if (event instanceof TableVisualEvent.SeatRoster roster) {
            snapshot = roster.snapshot();
        } else if (event instanceof TableVisualEvent.HandBoundary boundary) {
            applyHandBoundary(boundary);
        } else if (event instanceof TableVisualEvent.MovePosition move) {
            replacePlayer(move.nickname(), player -> copyPlayer(player,
                    player.stack(), player.streetBet(), player.potContribution(),
                    player.active(), player.winner(), move.position(),
                    player.lastAction(), player.handName(), player.holeCards()));
        } else if (event instanceof TableVisualEvent.PositionRotation rotation) {
            java.util.Set<TableSnapshot.Position> movingPositions = rotation.transfers()
                    .stream().map(TableVisualEvent.PositionTransfer::position)
                    .collect(java.util.stream.Collectors.toSet());
            for (TableSnapshot.PlayerSnapshot player : List.copyOf(snapshot.players())) {
                if (movingPositions.contains(player.position())) {
                    replacePlayer(player.nickname(), current -> copyPlayer(current,
                            current.stack(), current.streetBet(),
                            current.potContribution(), current.active(),
                            current.winner(), TableSnapshot.Position.NONE,
                            current.lastAction(), current.handName(),
                            current.holeCards()));
                }
            }
            for (TableVisualEvent.PositionTransfer transfer : rotation.transfers()) {
                replacePlayer(transfer.toNickname(), player -> copyPlayer(player,
                        player.stack(), player.streetBet(), player.potContribution(),
                        player.active(), player.winner(), transfer.position(),
                        player.lastAction(), player.handName(), player.holeCards()));
            }
        } else if (event instanceof TableVisualEvent.PostChips post) {
            replacePlayer(post.nickname(), player -> copyPlayer(player,
                    Math.max(0d, player.stack() - post.amount()),
                    post.destination() == TableVisualEvent.PostChips.Destination.STREET_BET
                            ? player.streetBet() + post.amount() : player.streetBet(),
                    post.destination() == TableVisualEvent.PostChips.Destination.POT
                            ? player.potContribution() + post.amount()
                            : player.potContribution(),
                    player.active(), player.winner(), player.position(),
                    player.lastAction(), player.handName(), player.holeCards()));
            if (post.destination() == TableVisualEvent.PostChips.Destination.POT) {
                snapshot = copySnapshot(snapshot, snapshot.pot() + post.amount(),
                        snapshot.currentTurnNickname(), snapshot.players(),
                        snapshot.communityCards());
            }
        } else if (event instanceof TableVisualEvent.CollectBets collect) {
            applyCollection(collect);
        } else if (event instanceof TableVisualEvent.DealHoleCard deal) {
            replacePlayer(deal.nickname(), player -> copyPlayer(player,
                    player.stack(), player.streetBet(), player.potContribution(),
                    player.active(), player.winner(), player.position(),
                    player.lastAction(), player.handName(),
                    replaceCard(player.holeCards(), deal.slot(), deal.card(),
                            deal.slot() + 1)));
        } else if (event instanceof TableVisualEvent.DealCommunityCard deal) {
            snapshot = copySnapshot(snapshot, snapshot.pot(),
                    snapshot.currentTurnNickname(), snapshot.players(),
                    replaceCard(snapshot.communityCards(), deal.slot(), HIDDEN_CARD,
                            deal.slot() + 1));
        } else if (event instanceof TableVisualEvent.SwapHoleCards swap) {
            replacePlayer(swap.nickname(), player -> {
                List<TableSnapshot.CardSnapshot> cards = padded(player.holeCards(), 2);
                return copyPlayer(player, player.stack(), player.streetBet(),
                        player.potContribution(), player.active(), player.winner(),
                        player.position(), player.lastAction(), player.handName(),
                        List.of(cards.get(1), cards.get(0)));
            });
        } else if (event instanceof TableVisualEvent.FoldHoleCards fold) {
            replacePlayer(fold.nickname(), player -> copyPlayer(player,
                    player.stack(), player.streetBet(), player.potContribution(),
                    false, player.winner(), player.position(), player.lastAction(),
                    player.handName(), player.holeCards().stream()
                            .map(card -> new TableSnapshot.CardSnapshot(
                            card.code(), card.faceUp(), true)).toList()));
        } else if (event instanceof TableVisualEvent.RevealCommunityCards reveal) {
            List<TableSnapshot.CardSnapshot> board = snapshot.communityCards();
            for (int offset = 0; offset < reveal.cards().size(); offset++) {
                board = replaceCard(board, reveal.firstSlot() + offset,
                        reveal.cards().get(offset), 5);
            }
            snapshot = copySnapshot(snapshot, snapshot.pot(),
                    snapshot.currentTurnNickname(), snapshot.players(), board);
        } else if (event instanceof TableVisualEvent.TurnTimer timer) {
            applyTurnTimer(timer);
        } else if (event instanceof TableVisualEvent.ActionControls controls) {
            actionControls = controls.state();
        } else if (event instanceof TableVisualEvent.PlayerAction action) {
            replacePlayer(action.nickname(), player -> copyPlayer(player,
                    Math.max(0d, player.stack() - action.potContribution()),
                    player.streetBet() + action.potContribution(),
                    player.potContribution(),
                    action.kind() != TableVisualEvent.PlayerAction.ActionKind.FOLD,
                    player.winner(), player.position(), action.label(),
                    player.handName(), player.holeCards()));
        } else if (event instanceof TableVisualEvent.RevealHoleCards reveal) {
            replacePlayer(reveal.nickname(), player -> copyPlayer(player,
                    player.stack(), player.streetBet(), player.potContribution(),
                    player.active(), player.winner(), player.position(),
                    player.lastAction(), player.handName(),
                    List.of(reveal.left(), reveal.right())));
        } else if (event instanceof TableVisualEvent.HandResult result) {
            replacePlayer(result.nickname(), player -> copyPlayer(player,
                    player.stack(), player.streetBet(), player.potContribution(),
                    player.active(), result.winner(), player.position(),
                    player.lastAction(), result.handName(), player.holeCards()));
        } else if (event instanceof TableVisualEvent.Payout payout) {
            replacePlayer(payout.nickname(), player -> copyPlayer(player,
                    player.stack() + payout.amount(), player.streetBet(),
                    player.potContribution(), player.active(), player.winner(),
                    player.position(), player.lastAction(), player.handName(),
                    player.holeCards()));
            snapshot = copySnapshot(snapshot,
                    Math.max(0d, snapshot.pot() - payout.amount()),
                    snapshot.currentTurnNickname(), snapshot.players(),
                    snapshot.communityCards());
        } else if (event instanceof TableVisualEvent.DeckChanged
                || event instanceof TableVisualEvent.Cinematic
                || event instanceof TableVisualEvent.ShowdownHighlight
                || event instanceof TableVisualEvent.CloseTable
                || event instanceof TableVisualEvent.Shuffle) {
            // Transient presentation-only events still consume their sequence.
        }
    }

    private void applyHandBoundary(TableVisualEvent.HandBoundary boundary) {
        if (boundary.phase() == TableVisualEvent.HandBoundary.Phase.END) {
            stopTurn();
            return;
        }
        List<TableSnapshot.PlayerSnapshot> players = snapshot.players().stream()
                .map(player -> copyPlayer(player, player.stack(), 0d, 0d,
                player.active(), false, player.position(), "", "", List.of()))
                .toList();
        snapshot = copySnapshot(snapshot, 0d, "", players, List.of());
        stopTurn();
    }

    private void applyCollection(TableVisualEvent.CollectBets collect) {
        List<TableSnapshot.PlayerSnapshot> players = new ArrayList<>(snapshot.players());
        for (TableVisualEvent.ChipTransfer transfer : collect.transfers()) {
            for (int index = 0; index < players.size(); index++) {
                TableSnapshot.PlayerSnapshot player = players.get(index);
                if (player.nickname().equals(transfer.nickname())) {
                    players.set(index, copyPlayer(player,
                            Math.max(0d, player.stack()
                                    - Math.max(0d, transfer.amount()
                                            - player.streetBet())), 0d,
                            player.potContribution() + transfer.amount(),
                            player.active(), player.winner(), player.position(),
                            player.lastAction(), player.handName(), player.holeCards()));
                    break;
                }
            }
        }
        snapshot = copySnapshot(snapshot, collect.potAfterLanding(),
                snapshot.currentTurnNickname(), players, snapshot.communityCards());
    }

    private void applyTurnTimer(TableVisualEvent.TurnTimer timer) {
        if (timer.phase() == TableVisualEvent.TurnTimer.Phase.STOP) {
            stopTurn();
            return;
        }
        turnTotalMillis = timer.totalMillis();
        turnRemainingMillis = timer.remainingMillis();
        turnTimerUpdatedNanos = System.nanoTime();
        snapshot = copySnapshot(snapshot, snapshot.pot(), timer.nickname(),
                snapshot.players(), snapshot.communityCards());
    }

    private void stopTurn() {
        turnTotalMillis = 0L;
        turnRemainingMillis = 0L;
        turnTimerUpdatedNanos = 0L;
        actionControls = ActionControlState.disabled();
        snapshot = copySnapshot(snapshot, snapshot.pot(), "",
                snapshot.players(), snapshot.communityCards());
    }

    private void replacePlayer(String nickname,
            java.util.function.UnaryOperator<TableSnapshot.PlayerSnapshot> transform) {
        List<TableSnapshot.PlayerSnapshot> players = new ArrayList<>(snapshot.players());
        for (int index = 0; index < players.size(); index++) {
            if (players.get(index).nickname().equals(nickname)) {
                players.set(index, transform.apply(players.get(index)));
                snapshot = copySnapshot(snapshot, snapshot.pot(),
                        snapshot.currentTurnNickname(), players,
                        snapshot.communityCards());
                return;
            }
        }
        throw new IllegalArgumentException("Unknown GDX table player: " + nickname);
    }

    private static List<TableSnapshot.CardSnapshot> replaceCard(
            List<TableSnapshot.CardSnapshot> source, int slot,
            TableSnapshot.CardSnapshot card, int size) {
        List<TableSnapshot.CardSnapshot> cards = padded(source, size);
        cards.set(slot, card);
        return List.copyOf(cards);
    }

    private static List<TableSnapshot.CardSnapshot> padded(
            List<TableSnapshot.CardSnapshot> source, int size) {
        List<TableSnapshot.CardSnapshot> cards = new ArrayList<>(source);
        while (cards.size() < size) {
            cards.add(HIDDEN_CARD);
        }
        return cards;
    }

    private static TableSnapshot copySnapshot(TableSnapshot source, double pot,
            String turn, List<TableSnapshot.PlayerSnapshot> players,
            List<TableSnapshot.CardSnapshot> board) {
        return new TableSnapshot(source.revision(), source.localNickname(),
                source.street(), pot, turn, source.paused(), players, board);
    }

    private static TableSnapshot.PlayerSnapshot copyPlayer(
            TableSnapshot.PlayerSnapshot source, double stack, double streetBet,
            double contribution, boolean active, boolean winner,
            TableSnapshot.Position position, String action, String hand,
            List<TableSnapshot.CardSnapshot> cards) {
        return new TableSnapshot.PlayerSnapshot(source.nickname(), stack,
                streetBet, contribution, active, source.spectator(),
                source.exited(), source.timedOut(), winner, position,
                action, hand, cards);
    }
}
