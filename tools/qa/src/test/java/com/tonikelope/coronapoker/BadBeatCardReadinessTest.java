package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class BadBeatCardReadinessTest {

    @Test
    void acceptsOnlyCompleteDistinctBoardAndPockets() {
        Card[] board = cards(1, 2, 3, 4, 5);
        Card[] loser = cards(6, 7);
        Card[] winner = cards(8, 9);

        assertTrue(Crupier.hasCompleteBadBeatCards(board, loser, winner));

        Card unset = new Card();
        assertFalse(Crupier.hasCompleteBadBeatCards(board,
                new Card[]{loser[0], unset}, winner));

        assertFalse(Crupier.hasCompleteBadBeatCards(board,
                loser, new Card[]{winner[0], board[0]}));
        assertFalse(Crupier.hasCompleteBadBeatCards(
                new Card[]{board[0], board[1], board[2], board[3]}, loser, winner));
    }

    private static Card[] cards(int... oneBasedIndices) {
        Card[] cards = new Card[oneBasedIndices.length];
        for (int i = 0; i < oneBasedIndices.length; i++) {
            cards[i] = new Card();
            cards[i].iniciarConValorNumerico(oneBasedIndices[i]);
        }
        return cards;
    }
}
