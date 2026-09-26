package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.game.CoreCardController;
import com.tonikelope.coronapoker.core.game.GameCardController;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

final class BadBeatCardReadinessTest {

    @Test
    void acceptsOnlyCompleteDistinctBoardAndPockets() {
        GameCardController[] board = cards(1, 2, 3, 4, 5);
        GameCardController[] loser = cards(6, 7);
        GameCardController[] winner = cards(8, 9);

        assertTrue(Crupier.hasCompleteBadBeatCards(board, loser, winner));

        GameCardController unset = new CoreCardController();
        assertFalse(Crupier.hasCompleteBadBeatCards(board,
                new GameCardController[]{loser[0], unset}, winner));

        assertFalse(Crupier.hasCompleteBadBeatCards(board,
                loser, new GameCardController[]{winner[0], board[0]}));
        assertFalse(Crupier.hasCompleteBadBeatCards(
                new GameCardController[]{board[0], board[1], board[2], board[3]}, loser, winner));
    }

    private static GameCardController[] cards(int... oneBasedIndices) {
        GameCardController[] cards = new GameCardController[oneBasedIndices.length];
        for (int i = 0; i < oneBasedIndices.length; i++) {
            cards[i] = new CoreCardController();
            cards[i].iniciarConValorNumerico(oneBasedIndices[i]);
        }
        return cards;
    }
}
