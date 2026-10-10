package com.tonikelope.coronapoker.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableSessionSummary;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HotJoinVisualEventCodecV1Test {

    private static final long RECEIVER_SEQUENCE = 900L;
    private static final TableSnapshot.CardSnapshot FACE_UP
            = new TableSnapshot.CardSnapshot("A_P", true, false, true);
    private static final TableSnapshot.CardSnapshot FACE_DOWN
            = new TableSnapshot.CardSnapshot("K_C", false, false, true);
    private static final TableVisualEvent.ChipTransfer CHIP
            = new TableVisualEvent.ChipTransfer("ana", 2d, 8d, 2d, 2d);

    @Test
    void everyPublicAnimationUsesTheOrdinaryVisualEventContract() {
        List<TableVisualEvent> events = List.of(
                new TableVisualEvent.AllInRunoutPause(1, 200),
                new TableVisualEvent.Shuffle(2, "default",
                        TableVisualEvent.Shuffle.Phase.START),
                new TableVisualEvent.PositionRotation(3,
                        List.of(new TableVisualEvent.PositionTransfer("ana",
                                "bea", TableSnapshot.Position.DEALER, false)),
                        250),
                new TableVisualEvent.CollectBets(4, List.of(CHIP), 2d, 4d),
                new TableVisualEvent.DealHoleCard(5, "ana", 0, FACE_DOWN),
                new TableVisualEvent.DealCommunityCard(6, 0),
                new TableVisualEvent.RunItTwiceBoard(7,
                        TableVisualEvent.RunItTwiceBoard.Side.B, "CARA B",
                        8d, List.of(3, 4)),
                new TableVisualEvent.FoldHoleCards(8, "ana"),
                new TableVisualEvent.RevealCommunityCards(9,
                        TableSnapshot.Street.FLOP, 0,
                        List.of(FACE_UP, FACE_UP, FACE_UP), 100),
                new TableVisualEvent.TurnTimer(10, "ana", 10_000, 9_000,
                        TableVisualEvent.TurnTimer.Phase.START),
                new TableVisualEvent.SharedProgress(11,
                        TableVisualEvent.SharedProgress.Mode.COUNTDOWN, 3),
                new TableVisualEvent.PlayerAction(12, "ana",
                        TableVisualEvent.PlayerAction.ActionKind.BET,
                        "APUESTA", 2d, 2d, 8d, 2d, 2d),
                new TableVisualEvent.Cinematic(13,
                        TableVisualEvent.Cinematic.Type.ALL_IN,
                        TableVisualEvent.Cinematic.Phase.START,
                        "ana", "allin.gif", 500),
                new TableVisualEvent.RevealHoleCards(14, "ana", FACE_UP,
                        FACE_UP, "PAREJA"),
                new TableVisualEvent.PartialHand(15, "ana", "PAREJA",
                        true, 75f),
                new TableVisualEvent.HandResult(16, "ana", "PAREJA", true,
                        TableSnapshot.Street.SHOWDOWN, List.of(1), false),
                new TableVisualEvent.RabbitCards(17,
                        List.of(new TableVisualEvent.RabbitCard(4, FACE_UP)),
                        true),
                new TableVisualEvent.RabbitResult(18, "ana", 1d, 7d, 1),
                new TableVisualEvent.RabbitNotice(19, "ana", 500),
                new TableVisualEvent.ShowdownHighlight(20, "ana", true,
                        List.of(0), List.of(0, 1, 2)),
                new TableVisualEvent.Payout(21, "ana", 4d, 0, 12d, 0d),
                new TableVisualEvent.PayoutBatch(22,
                        List.of(new TableVisualEvent.PayoutBatch.Transfer(
                                "ana", 4d, 0d, 12d)), 0d, 0d),
                new TableVisualEvent.AudioCue(23,
                        TableVisualEvent.AudioCue.Operation.PLAY,
                        "misc/check.wav", false, false, false, false),
                new TableVisualEvent.SpecialCardSound(24, "A_P"),
                new TableVisualEvent.Rebuy(25, List.of(CHIP), 500),
                new TableVisualEvent.RebuyDecision(26, "ana",
                        TableVisualEvent.RebuyDecision.Phase.REBOUGHT),
                new TableVisualEvent.InitialStackFill(27, List.of(CHIP),
                        500, "misc/cash.wav"),
                new TableVisualEvent.SeatRoster(28, List.of(player(
                        "ana", false, false, FACE_DOWN))),
                new TableVisualEvent.PauseStatus(29, true),
                new TableVisualEvent.TelemetryStatus(30, List.of(
                        new TableVisualEvent.PlayerTelemetry("ana", 10, 11,
                                2, 123L))),
                new TableVisualEvent.PlayerTimeout(31, "ana", true),
                new TableVisualEvent.PlayerDeparture(32, "ana", "SALE"),
                new TableVisualEvent.UnderTheGunStatus(33, "ana"),
                new TableVisualEvent.SwapHoleCards(34, "ana", false),
                new TableVisualEvent.TableInfo(35, 0.1d, 0.2d,
                        7, 5, 2, 1),
                new TableVisualEvent.ImmediateRebuyStatus(37, "ana", 10),
                new TableVisualEvent.LastHandStatus(39, true),
                new TableVisualEvent.HandLimitStatus(40, 100),
                new TableVisualEvent.GameConfigurationStatus(41,
                        configuration()),
                new TableVisualEvent.RunItTwiceLockStatus(42, true),
                new TableVisualEvent.CommunicationRulesStatus(43,
                        true, false),
                new TableVisualEvent.GameClock(44, 123L),
                new TableVisualEvent.CloseTable(45,
                        new TableSessionSummary("ana", 3, 60L, 123L,
                                TableSessionSummary.CloseReason.COMPLETED,
                                List.of(new TableSessionSummary.PlayerBalance(
                                        "ana", 12d, 10d, 0))),
                        TableSnapshot.Street.FINISHED),
                new TableVisualEvent.HandBoundary(46, 7L,
                        TableVisualEvent.HandBoundary.Phase.END,
                        new TableSnapshot(8L, "host",
                                TableSnapshot.Street.SHOWDOWN, 12d, "",
                                false, List.of(player("ana", false, false,
                                        FACE_UP)), List.of(FACE_UP))));

        for (TableVisualEvent source : events) {
            String encoded = HotJoinVisualEventCodecV1.encode(source)
                    .orElseThrow();
            TableVisualEvent decoded = HotJoinVisualEventCodecV1.decode(
                    encoded, RECEIVER_SEQUENCE);
            assertEquals(source.getClass(), decoded.getClass(),
                    source.getClass().getSimpleName());
            assertEquals(RECEIVER_SEQUENCE, decoded.sequence());
            assertEquals(encoded, HotJoinVisualEventCodecV1.encode(decoded)
                    .orElseThrow(), source.getClass().getSimpleName());
        }

        /*
         * This is intentionally a closed contract, not merely a collection of
         * round-trip examples.  If a new TableVisualEvent subtype is added,
         * the test must classify it as public/replayable above or explicitly
         * local-only below.  That prevents a future semantic visual state from
         * working for incumbents while silently disappearing for CALENTANDO.
         */
        Set<Class<?>> classified = new HashSet<>();
        events.stream().map(Object::getClass).forEach(classified::add);
        classified.addAll(Set.of(
                TableVisualEvent.HotJoinState.class,
                TableVisualEvent.PreparationStatus.class,
                TableVisualEvent.ActionControls.class,
                TableVisualEvent.PreActionControls.class,
                TableVisualEvent.IwtsthCandidates.class,
                TableVisualEvent.LateJoinRequest.class,
                TableVisualEvent.CallCost.class,
                TableVisualEvent.DeckChanged.class));
        assertEquals(Set.of(TableVisualEvent.class.getPermittedSubclasses()),
                classified,
                "every event must be public/replayable or deliberately local-only");
    }

    @Test
    void handBoundarySnapshotIsLocalizedForTheWarmingClient() {
        TableVisualEvent.HandBoundary source
                = new TableVisualEvent.HandBoundary(1L, 7L,
                        TableVisualEvent.HandBoundary.Phase.END,
                        new TableSnapshot(8L, "host",
                                TableSnapshot.Street.SHOWDOWN, 12d, "",
                                false, List.of(player("ana", false, false,
                                        FACE_UP)), List.of(FACE_UP)));

        TableVisualEvent.HandBoundary decoded = assertInstanceOf(
                TableVisualEvent.HandBoundary.class,
                HotJoinVisualEventCodecV1.decode(
                        HotJoinVisualEventCodecV1.encode(source)
                                .orElseThrow(),
                        RECEIVER_SEQUENCE, "warming-client"));

        assertEquals("warming-client",
                decoded.snapshot().localNickname());
        assertEquals(TableSnapshot.Street.SHOWDOWN,
                decoded.snapshot().street());
        assertEquals(12d, decoded.snapshot().pot());
    }

    @Test
    void concealedHoleCardCodeNeverCrossesThePresentationSideChannel() {
        TableVisualEvent.DealHoleCard decoded = assertInstanceOf(
                TableVisualEvent.DealHoleCard.class,
                HotJoinVisualEventCodecV1.decode(
                        HotJoinVisualEventCodecV1.encode(
                                new TableVisualEvent.DealHoleCard(1, "ana", 0,
                                        FACE_DOWN)).orElseThrow(),
                        RECEIVER_SEQUENCE));

        assertEquals("", decoded.card().code());
        assertFalse(decoded.card().faceUp());
        assertTrue(decoded.card().visible());

        TableVisualEvent.DealHoleCard localHostCard = assertInstanceOf(
                TableVisualEvent.DealHoleCard.class,
                HotJoinVisualEventCodecV1.decode(
                        HotJoinVisualEventCodecV1.encode(
                                new TableVisualEvent.DealHoleCard(2, "host", 1,
                                        FACE_UP)).orElseThrow(),
                        RECEIVER_SEQUENCE));
        assertEquals("", localHostCard.card().code());
        assertFalse(localHostCard.card().faceUp());

        TableVisualEvent.SeatRoster roster = assertInstanceOf(
                TableVisualEvent.SeatRoster.class,
                HotJoinVisualEventCodecV1.decode(
                        HotJoinVisualEventCodecV1.encode(
                                new TableVisualEvent.SeatRoster(3, List.of(
                                        player("host", false, false,
                                                FACE_UP),
                                        player("ana", false, false,
                                                FACE_DOWN))))
                                .orElseThrow(), RECEIVER_SEQUENCE));
        assertEquals(2, roster.players().size());
        assertEquals("A_P", roster.players().get(0).holeCards().get(0).code());
        assertTrue(roster.players().get(0).holeCards().get(0).faceUp());
        assertTrue(roster.players().get(1).holeCards().stream()
                .allMatch(card -> card.code().isBlank()
                        && !card.faceUp() && card.visible()));
        assertEquals(TableSnapshot.Decision.CHECK,
                roster.players().get(1).decision());
        assertEquals(TableSnapshot.ActionKind.CALL,
                roster.players().get(1).actionKind());
    }

    @Test
    void shuffleLifecycleDoesNotLeakTheHostsLocalDeckPreference() {
        TableVisualEvent.Shuffle decoded = assertInstanceOf(
                TableVisualEvent.Shuffle.class,
                HotJoinVisualEventCodecV1.decode(
                        HotJoinVisualEventCodecV1.encode(
                                new TableVisualEvent.Shuffle(1, "host-deck",
                                        TableVisualEvent.Shuffle.Phase.START))
                                .orElseThrow(), RECEIVER_SEQUENCE));

        assertEquals("", decoded.deck());
        assertEquals(TableVisualEvent.Shuffle.Phase.START, decoded.phase());
    }

    @Test
    void warmingObserverNeverInheritsHostLocalControls() {
        assertTrue(HotJoinVisualEventCodecV1.encode(
                new TableVisualEvent.PreActionControls(1, true, false))
                .isEmpty());
        assertTrue(HotJoinVisualEventCodecV1.encode(
                new TableVisualEvent.ActionControls(2,
                        ActionControlState.disabled()))
                .isEmpty());
        assertTrue(HotJoinVisualEventCodecV1.encode(new TableVisualEvent
                .IwtsthCandidates(3, List.of("ana"))).isEmpty());
        assertTrue(HotJoinVisualEventCodecV1.encode(new TableVisualEvent
                .CallCost(4, "+1.2", "host")).isEmpty(),
                "the call-cost overlay belongs to the local acting seat");
        assertTrue(HotJoinVisualEventCodecV1.encode(new TableVisualEvent
                .DeckChanged(5, "local-deck")).isEmpty(),
                "a warming client must retain its own deck preference");
    }

    @Test
    void malformedPayloadFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> HotJoinVisualEventCodecV1.decode("AAAA",
                        RECEIVER_SEQUENCE));
    }

    private static TableSnapshot.PlayerSnapshot player(String nickname,
            boolean spectator, boolean warming,
            TableSnapshot.CardSnapshot card) {
        return new TableSnapshot.PlayerSnapshot(nickname, 8d, 2d, 2d,
                !spectator, spectator, false, false, 10, 11, 1, 123L,
                false, false, TableSnapshot.Position.BIG_BLIND,
                spectator ? TableSnapshot.Decision.NONE
                        : TableSnapshot.Decision.CHECK,
                spectator ? TableSnapshot.ActionKind.NONE
                        : TableSnapshot.ActionKind.CALL,
                "CALL", "",
                List.of(card, card), 10, 0, warming,
                new TableSnapshot.PlayerPresentation(false, false, -1f,
                        false, "", List.of(), false, false, List.of(),
                        List.of(), TableSnapshot.RebuyPhase.WAITING, 10,
                        "CALL"));
    }

    private static GameConfigCodecV1.Configuration configuration() {
        return new GameConfigCodecV1.Configuration(10, 0.1d, 0.2d,
                5, 2, false, "session", true, 100, 0d, 0,
                true, true, 10, 100, 0, false, false, true, true,
                0, 30, true, 10, false, List.of());
    }
}
