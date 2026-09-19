package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TableVisualEventContractTest {

    private static final TableSnapshot.CardSnapshot CARD =
            new TableSnapshot.CardSnapshot("A_P", true, false);

    @Test
    void flopIsOneThreeCardSemanticReveal() {
        TableVisualEvent.RevealCommunityCards flop =
                new TableVisualEvent.RevealCommunityCards(1L, 0, List.of(CARD, CARD, CARD));

        assertEquals(3, flop.cards().size());
        TableVisualEvent.RevealCommunityCards resistedFlop
                = new TableVisualEvent.RevealCommunityCards(4L, 0,
                        List.of(CARD, CARD, CARD), 2_000L);
        assertEquals(2_000L, resistedFlop.leadInMillis());
        assertThrows(IllegalArgumentException.class,
                () -> new TableVisualEvent.RevealCommunityCards(2L, 1, List.of(CARD, CARD, CARD)));
        assertThrows(IllegalArgumentException.class,
                () -> new TableVisualEvent.RevealCommunityCards(3L, 0, List.of(CARD, CARD)));
        assertThrows(IllegalArgumentException.class,
                () -> new TableVisualEvent.RevealCommunityCards(5L, 0,
                        List.of(CARD, CARD, CARD), -1L));
    }

    @Test
    void turnAndRiverRemainSingleCardReveals() {
        new TableVisualEvent.RevealCommunityCards(1L, 3, List.of(CARD));
        new TableVisualEvent.RevealCommunityCards(2L, 4, List.of(CARD));
    }

    @Test
    void monetaryEventsRejectImpossibleValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new TableVisualEvent.ChipTransfer(
                        "CoronaBot$1", -1d, 10d, 0d, 0d));
        assertThrows(IllegalArgumentException.class,
                () -> new TableVisualEvent.Payout(2L, "CoronaBot$1",
                        Double.NaN, 0, 10d, 0d));
    }

    @Test
    void startedTimerRequiresAPlayerAndCoherentDuration() {
        assertThrows(IllegalArgumentException.class,
                () -> new TableVisualEvent.TurnTimer(1L, "", 10_000L, 10_000L,
                        TableVisualEvent.TurnTimer.Phase.START));
        assertThrows(IllegalArgumentException.class,
                () -> new TableVisualEvent.TurnTimer(2L, "CoronaBot$1", 10_000L, 10_001L,
                        TableVisualEvent.TurnTimer.Phase.UPDATE));
    }

    @Test
    void initialStackFillRequiresPlayersAndPositiveDuration() {
        TableVisualEvent.ChipTransfer transfer
                = new TableVisualEvent.ChipTransfer(
                        "server", 10d, 90d, 10d, 10d);
        TableVisualEvent.InitialStackFill fill
                = new TableVisualEvent.InitialStackFill(1L,
                        List.of(transfer), 1_000L,
                        "misc/balance_count.wav");
        assertEquals(1_000L, fill.durationMillis());
        assertEquals("misc/balance_count.wav", fill.soundResource());
        assertThrows(IllegalArgumentException.class,
                () -> new TableVisualEvent.InitialStackFill(2L,
                        List.of(), 1_000L, null));
        assertThrows(IllegalArgumentException.class,
                () -> new TableVisualEvent.InitialStackFill(3L,
                        List.of(transfer), 0L, null));
    }

    @Test
    void acceptedPokerDecisionsMapWithoutReadingSwingLabels() {
        assertEquals(TableVisualEvent.PlayerAction.ActionKind.FOLD,
                Crupier.actionKind(Player.FOLD, 0d, 100d));
        assertEquals(TableVisualEvent.PlayerAction.ActionKind.CHECK,
                Crupier.actionKind(Player.CHECK, 0d, 100d));
        assertEquals(TableVisualEvent.PlayerAction.ActionKind.CALL,
                Crupier.actionKind(Player.CHECK, 50d, 100d));
        assertEquals(TableVisualEvent.PlayerAction.ActionKind.BET,
                Crupier.actionKind(Player.BET, 100d, 0d));
        assertEquals(TableVisualEvent.PlayerAction.ActionKind.RAISE,
                Crupier.actionKind(Player.BET, 200d, 100d));
        assertEquals(TableVisualEvent.PlayerAction.ActionKind.ALL_IN,
                Crupier.actionKind(Player.ALLIN, 500d, 100d));
    }
}
