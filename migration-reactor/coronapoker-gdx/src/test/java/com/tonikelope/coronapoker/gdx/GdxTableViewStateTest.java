package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import com.tonikelope.coronapoker.core.game.ActionControlState;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GdxTableViewStateTest {

    @Test
    void gameTextResolvesShowdownKeysWithoutSwing() {
        assertEquals("DOBLE PAREJA",
                new GdxGameText("es").translate("hand.two_pair"));
        assertEquals("TWO PAIRS",
                new GdxGameText("en").translate("hand.two_pair"));
        assertEquals("NO VA",
                new GdxGameText("es").translate("action.label.fold2"));
    }

    @Test
    void liveHudHitMapRoutesEveryCanonicalPokerAction() {
        assertEquals(1, CoronaPokerGdxTable.hudTarget(700f, 70f, 1920f));
        assertEquals(2, CoronaPokerGdxTable.hudTarget(900f, 70f, 1920f));
        assertEquals(3, CoronaPokerGdxTable.hudTarget(1035f, 70f, 1920f));
        assertEquals(4, CoronaPokerGdxTable.hudTarget(1140f, 70f, 1920f));
        assertEquals(5, CoronaPokerGdxTable.hudTarget(1250f, 70f, 1920f));
        assertEquals(6, CoronaPokerGdxTable.hudTarget(1420f, 70f, 1920f));
        assertEquals(0, CoronaPokerGdxTable.hudTarget(300f, 70f, 1920f));
    }

    @Test
    void projectsCausalOpeningAndBetLandingWithoutReadingWidgets() {
        GdxTableViewState state = new GdxTableViewState(snapshot());

        state.apply(new TableVisualEvent.PositionRotation(1, List.of(
                new TableVisualEvent.PositionTransfer("ana", "borja",
                        TableSnapshot.Position.DEALER, false),
                new TableVisualEvent.PositionTransfer("borja", "ana",
                        TableSnapshot.Position.BIG_BLIND, false)), 440));
        state.apply(new TableVisualEvent.CollectBets(2,
                List.of(new TableVisualEvent.ChipTransfer("ana", 100d)),
                0d, 100d));
        state.apply(new TableVisualEvent.PlayerAction(3, "borja",
                TableVisualEvent.PlayerAction.ActionKind.CALL,
                "CALL", 100d, 100d));
        assertEquals(TableVisualEvent.PlayerAction.ActionKind.CALL,
                state.actionKind("borja"));
        state.apply(new TableVisualEvent.CollectBets(4,
                List.of(new TableVisualEvent.ChipTransfer("borja", 100d)),
                100d, 200d));

        TableSnapshot.PlayerSnapshot ana = player(state, "ana");
        TableSnapshot.PlayerSnapshot borja = player(state, "borja");
        assertEquals(TableSnapshot.Position.BIG_BLIND, ana.position());
        assertEquals(TableSnapshot.Position.DEALER, borja.position());
        assertEquals(900d, ana.stack());
        assertEquals(900d, borja.stack());
        assertEquals(0d, borja.streetBet());
        assertEquals(200d, state.snapshot().pot());
        assertEquals(4L, state.lastSequence());

        assertThrows(IllegalArgumentException.class,
                () -> state.apply(new TableVisualEvent.CloseTable(4)));
    }

    @Test
    void actionControlsFollowTurnLifecycle() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        ActionControlState controls = ActionControlState.forTurn(
                20d, 10d, 10d, 5d, 10d, 100d, 3, true, 0);
        state.apply(new TableVisualEvent.TurnTimer(1, "ana", 30_000,
                30_000, TableVisualEvent.TurnTimer.Phase.START));
        state.apply(new TableVisualEvent.ActionControls(2, controls));

        assertEquals(ActionControlState.CallAction.CALL,
                state.actionControls().callAction());
        assertEquals(10d, state.actionControls().callAmount());

        state.apply(new TableVisualEvent.ActionControls(3,
                ActionControlState.disabled().withShowCards(true)));
        assertTrue(state.actionControls().showCards());

        state.apply(new TableVisualEvent.TurnTimer(4, "", 30_000, 0,
                TableVisualEvent.TurnTimer.Phase.STOP));
        assertEquals(ActionControlState.disabled(), state.actionControls());
    }

    @Test
    void cardsAppearOnlyWhenTheirOwnDealEventLands() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        assertEquals(0, player(state, "ana").holeCards().size());
        assertEquals(0, state.snapshot().communityCards().size());

        state.apply(new TableVisualEvent.DealHoleCard(1, "ana", 0,
                new TableSnapshot.CardSnapshot("", false, false)));
        assertEquals(1, player(state, "ana").holeCards().size());

        state.apply(new TableVisualEvent.DealHoleCard(2, "ana", 1,
                new TableSnapshot.CardSnapshot("", false, false)));
        assertEquals(2, player(state, "ana").holeCards().size());

        state.apply(new TableVisualEvent.DealCommunityCard(3, 0));
        assertEquals(1, state.snapshot().communityCards().size());

        state.apply(new TableVisualEvent.ShowdownHighlight(4, "ana", true,
                List.of(0, 1), List.of(0)));
        assertTrue(state.hasShowdownHighlights());

        state.apply(new TableVisualEvent.HandBoundary(5, 2,
                TableVisualEvent.HandBoundary.Phase.PREPARE));
        assertEquals(0, player(state, "ana").holeCards().size());
        assertEquals(0, state.snapshot().communityCards().size());
        assertTrue(!state.hasShowdownHighlights());
    }

    @Test
    void acceptsEveryStateEventFamilyThroughPayoutAndClose() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        state.apply(new TableVisualEvent.PostChips(1, "ana", 10,
                TableVisualEvent.PostChips.Destination.STREET_BET));
        state.apply(new TableVisualEvent.PlayerAction(2, "ana",
                TableVisualEvent.PlayerAction.ActionKind.CALL, "IGUALA", 20, 10));
        state.apply(new TableVisualEvent.RevealHoleCards(3, "ana",
                card("A_C"), card("K_C")));
        state.apply(new TableVisualEvent.HandResult(4, "ana", "COLOR", true));
        state.apply(new TableVisualEvent.ShowdownHighlight(5, "ana", true,
                List.of(0, 1), List.of(0, 1, 2)));
        assertEquals(List.of(0, 1),
                state.showdownHighlight("ana").holeCardSlots());
        state.apply(new TableVisualEvent.Payout(6, "ana", 40, 0));
        state.apply(new TableVisualEvent.DeckChanged(7, "goliat"));
        state.apply(new TableVisualEvent.Cinematic(8,
                TableVisualEvent.Cinematic.Type.ALL_IN,
                TableVisualEvent.Cinematic.Phase.START));
        state.apply(new TableVisualEvent.CloseTable(9));

        assertEquals(9, state.lastSequence());
        assertEquals("COLOR", player(state, "ana").handName());
        assertTrue(player(state, "ana").winner());
    }

    private static TableSnapshot.CardSnapshot card(String code) {
        return new TableSnapshot.CardSnapshot(code, true, false);
    }

    private static TableSnapshot.PlayerSnapshot player(
            GdxTableViewState state, String nickname) {
        return state.snapshot().players().stream()
                .filter(player -> player.nickname().equals(nickname))
                .findFirst().orElseThrow();
    }

    private static TableSnapshot snapshot() {
        return new TableSnapshot(1L, "ana", TableSnapshot.Street.PREFLOP,
                0d, "", false, List.of(
                player("ana", TableSnapshot.Position.DEALER),
                player("borja", TableSnapshot.Position.BIG_BLIND)), List.of());
    }

    private static TableSnapshot.PlayerSnapshot player(String nickname,
            TableSnapshot.Position position) {
        return new TableSnapshot.PlayerSnapshot(nickname, 1_000d, 0d, 0d,
                true, false, false, false, false, position, "", "", List.of());
    }
}
