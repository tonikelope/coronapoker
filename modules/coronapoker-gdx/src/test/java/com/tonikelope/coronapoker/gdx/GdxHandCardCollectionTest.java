/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.table.TableSnapshot;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GdxHandCardCollectionTest {
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
            sweep.advance(end / 4);
            assertTrue(sweep.boardPosition() > 0f && sweep.boardPosition() < 1f);
            sweep.advance(end / 2);
            assertEquals(2f, sweep.boardPosition(), .001f);
            sweep.advance(end);
            assertEquals(4f, sweep.boardPosition(), .001f);
            for (int index = 0; index < sweep.board.size(); index++) {
                assertEquals((4 - index) * GdxPlasticCard.thickness(140f),
                        sweep.cardElevation(index, 4f, 140f, 158f), .001f);
            }
            assertEquals(1f, sweep.opacity(), .001f);
            assertFalse(sweep.finished());
            sweep.advance(end + 90_000_000L);
            assertEquals(.5f, sweep.opacity(), .001f);
            sweep.advance(end + 181_000_000L);
            assertEquals(0f, sweep.opacity());
            assertTrue(sweep.finished());
        }
    }

    @Test
    void fadesWithoutWaitingForAnEmptyBoardToSweep() {
        var sweep = sequence(100);
        sweep.advance(0L);
        assertEquals(1f, sweep.opacity());
        sweep.advance(90_000_000L);
        assertEquals(.5f, sweep.opacity(), .001f);
        sweep.advance(181_000_000L);
        assertTrue(sweep.finished());
    }

    private static GdxHandCardCollection sequence(int speed, Integer... slots) {
        var normal = new TableSnapshot.CardSnapshot("A_C", true, false);
        var board = List.of(slots).stream().map(slot -> new GdxHandCardCollection.BoardCard(slot, normal)).toList();
        return new GdxHandCardCollection(board, speed, new CompletableFuture<>());
    }
}
