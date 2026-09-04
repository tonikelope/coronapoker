/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Frontend-independent automated-player state backed by Alberta Poker. */
final class AlbertaGameBotService implements GameBotService {

    private static final String SUITS = "TDCP";
    private final org.alberta.poker.Hand board = new org.alberta.poker.Hand();
    private final org.alberta.poker.HandEvaluator evaluator
            = new org.alberta.poker.HandEvaluator();
    private final org.alberta.poker.ai.HandPotential potential
            = new org.alberta.poker.ai.HandPotential();
    private final Map<String, GameOpponentStats> opponents
            = new ConcurrentHashMap<>();

    @Override
    public int thinkTimeMillis() {
        return 1500;
    }

    @Override
    public GameOpponentStats opponent(String nickname) {
        return opponents.computeIfAbsent(nickname, ignored -> new MutableStats());
    }

    @Override
    public void resetBoard() {
        board.makeEmpty();
    }

    @Override
    public void addBoardCard(int oneBasedCard) {
        board.addCard(evaluatorCard(oneBasedCard));
    }

    @Override
    public int boardSize() {
        return board.size();
    }

    @Override
    public int boardCardIndex(int index) {
        return index < 0 || index >= board.size()
                ? -1 : board.getCard(index + 1).getIndex();
    }

    @Override
    public org.alberta.poker.Card evaluatorCard(int oneBasedCard) {
        CardCode code = CardCode.fromOneBased(oneBasedCard);
        return new org.alberta.poker.Card(
                code.rank().aceHighValue() - 2,
                SUITS.indexOf(code.suit().wire()));
    }

    @Override
    public double effectiveStrength(org.alberta.poker.Card first,
            org.alberta.poker.Card second, org.alberta.poker.Hand currentBoard,
            int opponentCount) {
        double strength = evaluator.handRank(first, second, currentBoard,
                opponentCount);
        double ppot = potential.ppot_raw(first, second, currentBoard, false);
        double npot = potential.getLastNPot();
        return strength + (1 - strength) * ppot - strength * npot;
    }

    @Override
    public int compareHands(org.alberta.poker.Hand first,
            org.alberta.poker.Hand second) {
        return evaluator.compareHands(first, second);
    }

    private static final class MutableStats implements GameOpponentStats {
        private int handsPlayed;
        private int voluntarilyPutInPot;
        private int preflopRaises;
        private int postFlopBetsAndRaises;
        private int postFlopCalls;
        private int lastVpipHand = -1;
        private int lastPfrHand = -1;

        @Override
        public void recordHandPlayed() {
            handsPlayed++;
        }

        @Override
        public void recordVPIP(int handId) {
            if (lastVpipHand != handId) {
                voluntarilyPutInPot++;
                lastVpipHand = handId;
            }
        }

        @Override
        public void recordPFR(int handId) {
            if (lastPfrHand != handId) {
                preflopRaises++;
                lastPfrHand = handId;
            }
        }

        @Override
        public void recordPostFlopBetOrRaise() {
            postFlopBetsAndRaises++;
        }

        @Override
        public void recordPostFlopCall() {
            postFlopCalls++;
        }
    }
}
