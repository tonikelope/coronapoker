/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

/** Shared automated-player state and Alberta evaluation boundary. */
public interface GameBotService {

    int thinkTimeMillis();

    GameOpponentStats opponent(String nickname);

    void resetBoard();

    void addBoardCard(int oneBasedCard);

    int boardSize();

    int boardCardIndex(int index);

    org.alberta.poker.Card evaluatorCard(int oneBasedCard);

    default org.alberta.poker.Card evaluatorCard(GameCardController card) {
        return card == null ? null : evaluatorCard(card.getCartaComoEntero());
    }

    double effectiveStrength(org.alberta.poker.Card first,
            org.alberta.poker.Card second, org.alberta.poker.Hand board,
            int opponentCount);

    int compareHands(org.alberta.poker.Hand first,
            org.alberta.poker.Hand second);

    static GameBotService standalone() {
        return new AlbertaGameBotService();
    }
}
