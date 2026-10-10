/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.game.CardCode;
import com.tonikelope.coronapoker.core.game.PlayerState;
import java.util.List;
import org.junit.jupiter.api.Test;

class TableSnapshotMapperTest {

    @Test
    void mapsEveryDurablePublicPlayerSemanticWithoutGuessingFromLabels() {
        PlayerState state = new PlayerState("ana");
        state.setBuyIn(25);
        state.setStack(8d);
        state.setPendingPayment(2d);
        state.setBet(1.5d);
        state.setPotContribution(4.5d);
        state.setDecision(PlayerState.Decision.ALL_IN);
        state.setActionKind(PlayerState.ActionKind.ALL_IN);
        state.setPosition(PlayerState.Position.DEALER_STRADDLE);
        state.setActive(true);
        state.setSpectator(false);
        state.setExited(false);
        state.setTimedOut(true);
        state.setWinner(true);
        state.setShowingCards(true);
        state.setUnderTheGun(true);
        state.setLastAction("ALL IN");
        state.setPublicActionLabel("VA ALL IN (+8)");
        state.setHandName("COLOR");
        state.setResolvedHandResult(true);
        state.setPublicHandName("COLOR");
        state.setWonPotIndexes(List.of(1, 3));
        state.setReturnedSidePot(true);
        state.setShowdownHighlight(true, List.of(0, 1),
                List.of(0, 2, 4));
        state.setRebuyPhase(PlayerState.RebuyPhase.REBOUGHT);
        state.setImmediateRebuyAmount(10);
        state.setTelemetry(12, 34, 2);
        state.firstCard().initialize(CardCode.parseShortCode("A_C"), true);
        state.firstCard().setVisible(true);
        state.secondCard().initialize(CardCode.parseShortCode("K_C"), true);
        state.secondCard().setVisible(true);

        TableSnapshot.PlayerSnapshot mapped = TableSnapshotMapper.player(
                state.snapshot(), 3);

        assertEquals("ana", mapped.nickname());
        assertEquals(25, mapped.buyIn());
        assertEquals(10d, mapped.stack(),
                "the public stack includes an awarded pending payout");
        assertEquals(1.5d, mapped.streetBet());
        assertEquals(4.5d, mapped.potContribution());
        assertEquals(TableSnapshot.Decision.ALL_IN, mapped.decision());
        assertEquals(TableSnapshot.ActionKind.ALL_IN, mapped.actionKind());
        assertEquals(TableSnapshot.Position.DEALER_STRADDLE,
                mapped.position());
        assertTrue(mapped.active());
        assertFalse(mapped.spectator());
        assertFalse(mapped.exited());
        assertTrue(mapped.timedOut());
        assertTrue(mapped.winner());
        assertTrue(mapped.underTheGun());
        assertEquals("ALL IN", mapped.lastAction());
        assertEquals("COLOR", mapped.handName());
        assertEquals(12, mapped.latency());
        assertEquals(34, mapped.previousLatency());
        assertEquals(2, mapped.reconnectionCount());
        assertTrue(mapped.telemetryAt() > 0L);
        assertEquals(List.of("A_C", "K_C"), mapped.holeCards().stream()
                .map(TableSnapshot.CardSnapshot::code).toList());
        assertTrue(mapped.holeCards().stream().allMatch(card
                -> card.faceUp() && card.visible() && !card.disabled()));
        assertEquals(3, mapped.rebuyCount());
        assertFalse(mapped.warming());

        TableSnapshot.PlayerPresentation presentation = mapped.presentation();
        assertTrue(presentation.showingCards());
        assertFalse(presentation.partialHand());
        assertEquals(-1f, presentation.partialWinPercentage());
        assertTrue(presentation.resultResolved());
        assertEquals("COLOR", presentation.publicHandName());
        assertEquals(List.of(1, 3), presentation.wonPotIndexes());
        assertTrue(presentation.returnedSidePot());
        assertTrue(presentation.showdownHighlightEnabled());
        assertEquals(List.of(0, 1), presentation.winningHoleCardSlots());
        assertEquals(List.of(0, 2, 4),
                presentation.winningCommunityCardSlots());
        assertEquals(TableSnapshot.RebuyPhase.REBOUGHT,
                presentation.rebuyPhase());
        assertEquals(10, presentation.immediateRebuyAmount());
        assertEquals("VA ALL IN (+8)", presentation.publicActionLabel());
    }

    @Test
    void mapsWarmingExplicitlyEvenWithNoStack() {
        PlayerState state = new PlayerState("late");
        state.setStack(0d);
        state.setSpectator(true);
        state.setWarming(true);

        TableSnapshot.PlayerSnapshot mapped = TableSnapshotMapper.player(
                state.snapshot());

        assertTrue(mapped.spectator());
        assertTrue(mapped.warming());
        assertEquals(0d, mapped.stack());
    }
}
