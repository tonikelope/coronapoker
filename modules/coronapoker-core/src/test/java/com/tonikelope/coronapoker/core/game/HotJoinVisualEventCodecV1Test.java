package com.tonikelope.coronapoker.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.util.List;
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
                        "ana", false, false, FACE_DOWN))));

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
    }

    @Test
    void warmingObserverNeverInheritsHostLocalControls() {
        assertTrue(HotJoinVisualEventCodecV1.encode(
                new TableVisualEvent.PreActionControls(1, true, false))
                .isEmpty());
        assertTrue(HotJoinVisualEventCodecV1.encode(
                new TableVisualEvent.CallCost(2, "1.0", "ana"))
                .isEmpty());
        assertTrue(HotJoinVisualEventCodecV1.encode(
                new TableVisualEvent.SwapHoleCards(3, "host", false))
                .isEmpty());
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
                false, false, TableSnapshot.Position.BIG_BLIND, "CALL", "",
                List.of(card, card), 10, 0, warming);
    }
}
