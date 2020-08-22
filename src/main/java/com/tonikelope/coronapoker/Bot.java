/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronapoker;

import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.vermeir.poker.Deck;
import org.vermeir.poker.GameState;
import org.vermeir.poker.handranking.util.HandRanker;
import org.vermeir.poker.handranking.util.HandRankingException;

/**
 *
 * FULLY EXPERIMENTAL. Based on the mythical Alberta's Loki Bot
 * https://poker.cs.ualberta.ca/publications/papp.msc.pdf with the help of
 * Jellen Vermeir's implementation
 * https://github.com/VermeirJellen/Poker_HandEvaluation MUCH to improve but
 * playable.
 *
 * @author tonikelope
 */
public class Bot {

    public static final String PALOS = "TDCP";

    public static int calculateBotDecision(Player cpu_player, int resisten) {

        try {

            Deck deck = new Deck();

            org.vermeir.poker.handranking.Card card1 = new org.vermeir.poker.handranking.Card(getCardSuit(cpu_player.getPlayingCard1()), cpu_player.getPlayingCard1().getValorNumerico());
            org.vermeir.poker.handranking.Card card2 = new org.vermeir.poker.handranking.Card(getCardSuit(cpu_player.getPlayingCard2()), cpu_player.getPlayingCard2().getValorNumerico());

            deck.removeCard(card1);
            deck.removeCard(card2);

            GameState gameState = new GameState();

            HashSet<org.vermeir.poker.handranking.Card> flop = new HashSet<>();

            org.vermeir.poker.handranking.Card flop1;
            org.vermeir.poker.handranking.Card flop2;
            org.vermeir.poker.handranking.Card flop3;
            org.vermeir.poker.handranking.Card turn;
            org.vermeir.poker.handranking.Card river;

            switch (Game.getInstance().getCrupier().getFase()) {

                case Crupier.PREFLOP:

                    if ((cpu_player.getPlayingCard1().getValorNumerico() == cpu_player.getPlayingCard2().getValorNumerico() && cpu_player.getPlayingCard1().getValorNumerico() >= 7)
                            || (cpu_player.getPlayingCard1().getPalo().equals(cpu_player.getPlayingCard2().getPalo()) && (cpu_player.getPlayingCard1().getValorNumerico() >= 10 || cpu_player.getPlayingCard2().getValorNumerico() >= 10))
                            || (cpu_player.getPlayingCard1().getValorNumerico() >= 12 && cpu_player.getPlayingCard2().getValorNumerico() >= 12)) {

                        return Helpers.SPRNG_GENERATOR.nextBoolean() && Game.getInstance().getCrupier().getConta_bet() < 2 ? Player.BET : Player.CHECK;

                    } else if (Helpers.SPRNG_GENERATOR.nextBoolean()) {

                        return Player.CHECK;

                    } else {

                        return Helpers.SPRNG_GENERATOR.nextBoolean() && Helpers.float1DSecureCompare(Game.getInstance().getCrupier().getApuesta_actual(), 3 * Game.getInstance().getCrupier().getCiega_grande()) <= 0 ? Player.CHECK : Player.FOLD;
                    }

                case Crupier.FLOP:

                    flop1 = new org.vermeir.poker.handranking.Card(getCardSuit(Game.getInstance().getFlop1()), Game.getInstance().getFlop1().getValorNumerico());
                    flop2 = new org.vermeir.poker.handranking.Card(getCardSuit(Game.getInstance().getFlop2()), Game.getInstance().getFlop2().getValorNumerico());
                    flop3 = new org.vermeir.poker.handranking.Card(getCardSuit(Game.getInstance().getFlop3()), Game.getInstance().getFlop3().getValorNumerico());

                    flop.add(flop1);
                    flop.add(flop2);
                    flop.add(flop3);

                    deck.removeCard(flop1);
                    deck.removeCard(flop2);
                    deck.removeCard(flop3);

                    gameState.setFlop(flop);

                    break;

                case Crupier.TURN:

                    flop1 = new org.vermeir.poker.handranking.Card(getCardSuit(Game.getInstance().getFlop1()), Game.getInstance().getFlop1().getValorNumerico());
                    flop2 = new org.vermeir.poker.handranking.Card(getCardSuit(Game.getInstance().getFlop2()), Game.getInstance().getFlop2().getValorNumerico());
                    flop3 = new org.vermeir.poker.handranking.Card(getCardSuit(Game.getInstance().getFlop3()), Game.getInstance().getFlop3().getValorNumerico());

                    flop.add(flop1);
                    flop.add(flop2);
                    flop.add(flop3);

                    turn = new org.vermeir.poker.handranking.Card(getCardSuit(Game.getInstance().getTurn()), Game.getInstance().getTurn().getValorNumerico());

                    deck.removeCard(flop1);
                    deck.removeCard(flop2);
                    deck.removeCard(flop3);
                    deck.removeCard(turn);

                    gameState.setFlop(flop);
                    gameState.setTurn(turn);

                    break;

                case Crupier.RIVER:

                    flop1 = new org.vermeir.poker.handranking.Card(getCardSuit(Game.getInstance().getFlop1()), Game.getInstance().getFlop1().getValorNumerico());
                    flop2 = new org.vermeir.poker.handranking.Card(getCardSuit(Game.getInstance().getFlop2()), Game.getInstance().getFlop2().getValorNumerico());
                    flop3 = new org.vermeir.poker.handranking.Card(getCardSuit(Game.getInstance().getFlop3()), Game.getInstance().getFlop3().getValorNumerico());

                    flop.add(flop1);
                    flop.add(flop2);
                    flop.add(flop3);

                    turn = new org.vermeir.poker.handranking.Card(getCardSuit(Game.getInstance().getTurn()), Game.getInstance().getTurn().getValorNumerico());

                    river = new org.vermeir.poker.handranking.Card(getCardSuit(Game.getInstance().getRiver()), Game.getInstance().getRiver().getValorNumerico());

                    deck.removeCard(flop1);
                    deck.removeCard(flop2);
                    deck.removeCard(flop3);
                    deck.removeCard(turn);
                    deck.removeCard(river);

                    gameState.setFlop(flop);
                    gameState.setTurn(turn);
                    gameState.setRiver(river);

                    break;
            }

            List<org.vermeir.poker.handranking.Card> remainingCards = deck.getCards();

            List<List<Double>> weightArray = HandRanker.getUniformWeightArray();

            org.vermeir.poker.Player player = new org.vermeir.poker.Player(card1, card2, gameState);

            player.calculateHandPotential(weightArray, resisten, true, remainingCards);

            double effectiveStrength = player.getHandStrength() + (1 - player.getHandStrength()) * player.getPositiveHandPotential() - player.getHandStrength() * player.getNegativeHandPotential();

            double poseffectiveStrength = player.getHandStrength() + (1 - player.getHandStrength()) * player.getPositiveHandPotential();

            if (poseffectiveStrength > 0.5f && Game.getInstance().getCrupier().getConta_bet() < 2) {

                return poseffectiveStrength > 0.7f ? Player.BET : (Helpers.SPRNG_GENERATOR.nextBoolean() ? Player.BET : Player.CHECK);

            } else if (effectiveStrength > 0.15f) {

                return poseffectiveStrength > 0.35f ? Player.CHECK : (Helpers.SPRNG_GENERATOR.nextBoolean() ? Player.CHECK : Player.FOLD);

            } else {

                if (Game.getInstance().getCrupier().getFase() == Crupier.RIVER && Helpers.SPRNG_GENERATOR.nextBoolean() && Game.getInstance().getCrupier().getConta_bet() < 2) {

                    //Si llegamos al River con una mano mala intentamos un farol el 50%
                    return Player.BET;
                }

                return Player.FOLD;
            }

        } catch (HandRankingException ex) {
            Logger.getLogger(Bot.class.getName()).log(Level.SEVERE, null, ex);
        }

        return Player.NODEC;
    }

    private static int getCardSuit(Card carta) {

        return PALOS.indexOf(carta.getPalo());

    }

}
