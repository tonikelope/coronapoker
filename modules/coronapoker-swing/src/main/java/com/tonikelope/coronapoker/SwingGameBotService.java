/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.game.CardCode;
import com.tonikelope.coronapoker.core.game.GameBotService;
import com.tonikelope.coronapoker.core.game.GameOpponentStats;

/** Preserves the classic Bot statics while exposing them through the core API. */
final class SwingGameBotService implements GameBotService {

    @Override
    public int thinkTimeMillis() {
        return Bot.BOT_THINK_TIME;
    }

    @Override
    public GameOpponentStats opponent(String nickname) {
        return Bot.TRACKER_MEMORY.computeIfAbsent(
                nickname, ignored -> new Bot.OpponentTracker());
    }

    @Override
    public void resetBoard() {
        Bot.BOT_COMMUNITY_CARDS.makeEmpty();
    }

    @Override
    public void addBoardCard(int oneBasedCard) {
        Bot.BOT_COMMUNITY_CARDS.addCard(evaluatorCard(oneBasedCard));
    }

    @Override
    public int boardSize() {
        return Bot.BOT_COMMUNITY_CARDS.size();
    }

    @Override
    public int boardCardIndex(int index) {
        return index < 0 || index >= Bot.BOT_COMMUNITY_CARDS.size()
                ? -1 : Bot.BOT_COMMUNITY_CARDS.getCard(index + 1).getIndex();
    }

    @Override
    public org.alberta.poker.Card evaluatorCard(int oneBasedCard) {
        CardCode code = CardCode.fromOneBased(oneBasedCard);
        return new org.alberta.poker.Card(code.rank().aceHighValue() - 2,
                Bot.SUITS.indexOf(code.suit().wire()));
    }

    @Override
    public double effectiveStrength(org.alberta.poker.Card first,
            org.alberta.poker.Card second, org.alberta.poker.Hand board,
            int opponentCount) {
        double strength = Bot.HANDEVALUATOR.handRank(first, second, board,
                opponentCount);
        double ppot = Bot.HANDPOTENTIAL.ppot_raw(first, second, board, false);
        double npot = Bot.HANDPOTENTIAL.getLastNPot();
        return strength + (1 - strength) * ppot - strength * npot;
    }

    @Override
    public int compareHands(org.alberta.poker.Hand first,
            org.alberta.poker.Hand second) {
        return Bot.HANDEVALUATOR.compareHands(first, second);
    }
}
