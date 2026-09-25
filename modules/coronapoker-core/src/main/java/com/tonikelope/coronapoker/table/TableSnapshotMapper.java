/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.table;

import com.tonikelope.coronapoker.core.game.CardState;
import com.tonikelope.coronapoker.core.game.HandState;
import com.tonikelope.coronapoker.core.game.PlayerState;
import com.tonikelope.coronapoker.core.game.TableState;
import java.util.Objects;

/** Converts authoritative neutral state into the immutable renderer contract. */
public final class TableSnapshotMapper {

    private TableSnapshotMapper() {
    }

    public static TableSnapshot from(TableState.Snapshot state) {
        Objects.requireNonNull(state, "state");
        HandState.Snapshot hand = state.hand();
        return new TableSnapshot(state.revision(), state.localNickname(),
                street(hand.street(), state.finished()), hand.pot().total(),
                hand.turn().nickname(), state.paused(),
                state.players().stream().map(TableSnapshotMapper::player).toList(),
                hand.communityCards().stream().map(TableSnapshotMapper::card).toList());
    }

    public static TableSnapshot.PlayerSnapshot player(PlayerState.Snapshot state) {
        Objects.requireNonNull(state, "state");
        return new TableSnapshot.PlayerSnapshot(state.nickname(), state.stack(),
                state.bet(), state.potContribution(), state.active(),
                state.spectator(), state.exited(), state.timedOut(),
                state.latency(), state.previousLatency(),
                state.reconnectionCount(), state.telemetryAt(),
                state.winner(), position(state.position()), state.lastAction(),
                state.handName(), state.holeCards().stream()
                        .map(TableSnapshotMapper::card).toList());
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
}
