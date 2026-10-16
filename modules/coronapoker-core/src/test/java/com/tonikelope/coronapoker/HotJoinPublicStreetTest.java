/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.table.TableSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class HotJoinPublicStreetTest {

    @Test
    void internalStreetCannotRunAheadOfTheVisibleBoard() {
        assertEquals(TableSnapshot.Street.FLOP,
                Crupier.publicStreetForSnapshot(Crupier.TURN, false,
                        board(3)));
        assertEquals(TableSnapshot.Street.TURN,
                Crupier.publicStreetForSnapshot(Crupier.RIVER, false,
                        board(4)));
    }

    @Test
    void rabbitCardsCannotAdvanceTheBettingStreet() {
        assertEquals(TableSnapshot.Street.FLOP,
                Crupier.publicStreetForSnapshot(Crupier.FLOP, false,
                        board(5)));
    }

    @Test
    void showdownRemainsAuthoritative() {
        assertEquals(TableSnapshot.Street.SHOWDOWN,
                Crupier.publicStreetForSnapshot(Crupier.FLOP, true,
                        board(3)));
    }

    private static List<TableSnapshot.CardSnapshot> board(int faceUp) {
        return java.util.stream.IntStream.range(0, 5)
                .mapToObj(index -> new TableSnapshot.CardSnapshot(
                        index < faceUp ? "card" + index : "",
                        index < faceUp, false, true))
                .toList();
    }
}
