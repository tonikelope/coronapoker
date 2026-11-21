/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.table.TableSnapshot;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GdxHandCardCollectionTest {
    private static final long RESTORE_NANOS = Math.round(GdxHandCardCollection.BOARD_RESTORE_SECONDS * 1e9);
    @Test
    void collectsLeftToRightAtAnIncreasedSpeedAndKeepsTheFirstCardOnTop() {
        for (int speed : List.of(60, 100, 150)) {
            var sweep = sequence(speed, 0, 1, 2, 3, 4);
            long end = Math.round(.9f * GdxCommunityStreetDeal.flopSpreadSeconds(speed) * 1e9);
            sweep.advance(0L);
            assertEquals(0f, sweep.boardPosition());
            for (int index = 0; index < sweep.board.size(); index++) {
                assertEquals(0f, sweep.cardElevation(index, sweep.board.get(index).slot(), 140f, 158f));
            }
            sweep.advance(RESTORE_NANOS - 1_000L);
            assertEquals(0f, sweep.boardPosition());
            assertEquals(5, sweep.visibleBoardCardCount());
            assertEquals(1f, sweep.opacity());
            sweep.advance(RESTORE_NANOS + end / 4);
            assertTrue(sweep.boardPosition() > 0f && sweep.boardPosition() < 1f);
            sweep.advance(RESTORE_NANOS + end / 2);
            assertEquals(2f, sweep.boardPosition(), .001f);
            sweep.advance(RESTORE_NANOS + end);
            assertEquals(4f, sweep.boardPosition(), .001f);
            for (int index = 0; index < sweep.board.size(); index++) {
                assertEquals((4 - index) * GdxPlasticCard.thickness(140f),
                        sweep.cardElevation(index, 4f, 140f, 158f), .001f);
            }
            assertEquals(1f, sweep.opacity(), .001f);
            assertFalse(sweep.finished());
            sweep.advance(RESTORE_NANOS + end + 170_000_000L);
            assertEquals(.5f, sweep.opacity(), .001f);
            sweep.advance(RESTORE_NANOS + end + 341_000_000L);
            assertEquals(0f, sweep.opacity());
            assertTrue(sweep.finished());
        }
    }

    @Test
    void fadesWithoutWaitingForAnEmptyBoardToSweep() {
        var sweep = sequence(100);
        sweep.advance(0L);
        assertEquals(1f, sweep.opacity());
        sweep.advance(170_000_000L);
        assertEquals(.5f, sweep.opacity(), .001f);
        assertEquals(.5f, sweep.holeCardOpacity(), .001f);
        sweep.advance(341_000_000L);
        assertTrue(sweep.finished());
    }

    @Test
    void playersCardsFadeOnScheduleAndTheBoardFadesAfterItsSweep() {
        var sweep = sequence(100, 0, 1, 2, 3, 4);
        sweep.advance(0L);
        assertEquals(1f, sweep.holeCardOpacity());
        sweep.advance(170_000_000L);
        assertEquals(.5f, sweep.holeCardOpacity(), .001f);
        assertEquals(1f, sweep.opacity());
        sweep.advance(341_000_000L);
        assertEquals(0f, sweep.holeCardOpacity());
        assertEquals(1f, sweep.opacity());
        assertFalse(sweep.finished());
        sweep.advance(RESTORE_NANOS + 638_000_000L);
        assertEquals(.5f, sweep.opacity(), .001f);
    }

    @Test
    void collectingOnlyASecondRunoutLeavesPlayersCardsFullyVisible() {
        var card = new TableSnapshot.CardSnapshot("A_C", true, false);
        var sweep = new GdxHandCardCollection(
                List.of(new GdxHandCardCollection.BoardCard(4, card)),
                100, false, new CompletableFuture<>());
        sweep.advance(0L);
        sweep.advance(90_000_000L);
        assertEquals(.5f, sweep.opacity(), .001f);
        assertEquals(1f, sweep.holeCardOpacity());
        sweep.advance(181_000_000L);
        assertTrue(sweep.finished());
    }

    @Test
    void retiresTheLowerCardsBeforeTheFirstFadeFrameWithoutDroppingTheTop() {
        var sweep = sequence(100, 0, 1, 2, 3, 4);
        sweep.advance(0L);
        assertEquals(5, sweep.visibleBoardCardCount());
        sweep.advance(RESTORE_NANOS + 467_990_000L);
        assertEquals(5, sweep.visibleBoardCardCount());
        assertEquals(1f, sweep.opacity());

        sweep.advance(RESTORE_NANOS + 468_010_000L);
        assertEquals(4f, sweep.boardPosition(), .0001f);
        assertEquals(1f, sweep.opacity(), .0001f);
        assertEquals(1, sweep.visibleBoardCardCount(),
                "Lower cards must be gone before the composite starts fading");
        assertEquals(0, sweep.board.get(0).slot(), "The first flop card stays on top");
        assertEquals(4f * GdxPlasticCard.thickness(140f),
                sweep.cardElevation(0, 4f, 140f, 158f), .0001f,
                "Retiring the lower sheets must not move the surviving card");

        sweep.advance(RESTORE_NANOS + 638_000_000L);
        assertEquals(.5f, sweep.opacity(), .001f);
        assertEquals(1, sweep.visibleBoardCardCount());
        sweep.advance(RESTORE_NANOS + 810_000_000L);
        assertEquals(0, sweep.visibleBoardCardCount());
    }

    @Test
    void partialAndEmptyBoardsNeverInventATopFlopCard() {
        var partial = sequence(100, 2, 4);
        partial.advance(0L);
        assertEquals(2, partial.visibleBoardCardCount());
        partial.advance(RESTORE_NANOS + 234_010_000L);
        assertEquals(1, partial.visibleBoardCardCount());
        assertEquals(2, partial.board.get(0).slot());

        var riverOnly = sequence(100, 4);
        riverOnly.advance(0L);
        assertEquals(1, riverOnly.visibleBoardCardCount());
        riverOnly.advance(RESTORE_NANOS + 170_000_000L);
        assertEquals(1, riverOnly.visibleBoardCardCount());
        assertEquals(.5f, riverOnly.opacity(), .001f);

        var empty = sequence(100);
        empty.advance(0L);
        assertEquals(0, empty.visibleBoardCardCount());
        empty.advance(90_000_000L);
        assertEquals(0, empty.visibleBoardCardCount());
    }

    private static GdxHandCardCollection sequence(int speed, Integer... slots) {
        var normal = new TableSnapshot.CardSnapshot("A_C", true, false);
        var board = List.of(slots).stream().map(slot -> new GdxHandCardCollection.BoardCard(slot, normal)).toList();
        return new GdxHandCardCollection(board, speed, true, new CompletableFuture<>());
    }
}
