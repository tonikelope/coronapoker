/*
 * Copyright (C) 2016 Jellen Vermeir
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.vermeir.poker;

import java.util.HashSet;
import java.util.List;
import org.vermeir.poker.handranking.Card;
import org.vermeir.poker.handranking.util.HandRanker;
import org.vermeir.poker.handranking.util.HandRankingException;

/**
 * @author Jellen Vermeir
 */
public class PokerGame {

    public PokerGame() {
        // Todo: do something useful..
    }

    public static void main(String[] args) throws HandRankingException {
        Deck deck = new Deck();

        // starting hand
        Card ace_hearts = new Card(Card.SUIT_HEARTS, Card.RANK_ACE);
        //Card queen_hearts = new Card(Card.SUIT_HEARTS, Card.RANK_QUEEN);
        Card ace_spades = new Card(Card.SUIT_SPADES, Card.RANK_ACE);

        // flop
        Card three_hearts = new Card(Card.SUIT_HEARTS, Card.RANK_3);
        Card four_spades = new Card(Card.SUIT_SPADES, Card.RANK_4);
        Card jack_hearts = new Card(Card.SUIT_HEARTS, Card.RANK_JACK);

        HashSet<Card> flop = new HashSet<>();
        flop.add(three_hearts);
        flop.add(four_spades);
        flop.add(jack_hearts);

        deck.removeCard(ace_hearts);
        deck.removeCard(ace_spades);
        deck.removeCard(three_hearts);
        deck.removeCard(four_spades);
        deck.removeCard(jack_hearts);

        List<Card> remainingCards = deck.getCards();
        List<List<Double>> weightArray = HandRanker.getUniformWeightArray();

        GameState gameState = new GameState();

        gameState.setFlop(flop);

        Player player = new Player(ace_hearts, ace_spades, gameState);

        long start = System.currentTimeMillis();
        //player.calculateHandStrength(weightArray, 5, remainingCards);
        player.calculateHandPotential(weightArray, 5, true, remainingCards);
        long end = System.currentTimeMillis();
        System.out.println("Time: " + (end - start));

        System.out.println("HandStrength: " + player.getHandStrength());
        System.out.println("Positive Potential: " + player.getPositiveHandPotential());
        System.out.println("Negative Potential: " + player.getNegativeHandPotential());

        //System.out.println("Handranker new Starting Hands: " + HandFactory.rankableHandCounter);
        /**
         * *************************************************************************
         * *************************************************************************
         * *************************************************************************
         */
    }
}
