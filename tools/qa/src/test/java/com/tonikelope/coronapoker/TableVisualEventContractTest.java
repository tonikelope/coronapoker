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
        assertThrows(IllegalArgumentException.class,
                () -> new TableVisualEvent.RevealCommunityCards(2L, 1, List.of(CARD, CARD, CARD)));
        assertThrows(IllegalArgumentException.class,
                () -> new TableVisualEvent.RevealCommunityCards(3L, 0, List.of(CARD, CARD)));
    }

    @Test
    void turnAndRiverRemainSingleCardReveals() {
        new TableVisualEvent.RevealCommunityCards(1L, 3, List.of(CARD));
        new TableVisualEvent.RevealCommunityCards(2L, 4, List.of(CARD));
    }

    @Test
    void monetaryEventsRejectImpossibleValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new TableVisualEvent.PostChips(1L, "CoronaBot$1", -1d,
                        TableVisualEvent.PostChips.Destination.POT));
        assertThrows(IllegalArgumentException.class,
                () -> new TableVisualEvent.Payout(2L, "CoronaBot$1", Double.NaN, 0));
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
}
