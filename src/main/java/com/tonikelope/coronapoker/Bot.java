/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronapoker;

/**
 *
 * FULLY EXPERIMENTAL. Based on the mythical Alberta's Loki Bot
 * https://poker.cs.ualberta.ca/publications/papp.msc.pdf
 *
 * @author tonikelope
 */
public class Bot {

    public static final String PALOS = "TDCP";

    private RemotePlayer cpu_player = null;
    private boolean semi_bluff = false;
    private org.alberta.poker.Hand board = null;

    public Bot(RemotePlayer player) {

        this.board = new org.alberta.poker.Hand();
        this.cpu_player = player;
        semi_bluff = false;
    }

    public void resetBot() {

        this.board.makeEmpty();
        semi_bluff = false;
    }

    public int calculateBotDecision(int oponents) {

        org.alberta.poker.Card card1 = new org.alberta.poker.Card(cpu_player.getPlayingCard1().getValorNumerico() - 2, getCardSuit(cpu_player.getPlayingCard1()));
        org.alberta.poker.Card card2 = new org.alberta.poker.Card(cpu_player.getPlayingCard2().getValorNumerico() - 2, getCardSuit(cpu_player.getPlayingCard2()));

        org.alberta.poker.Card flop1;
        org.alberta.poker.Card flop2;
        org.alberta.poker.Card flop3;
        org.alberta.poker.Card turn;
        org.alberta.poker.Card river;

        switch (Game.getInstance().getCrupier().getFase()) {

            case Crupier.PREFLOP:

                if ((cpu_player.getPlayingCard1().getValorNumerico() == cpu_player.getPlayingCard2().getValorNumerico() && cpu_player.getPlayingCard1().getValorNumerico() >= 7)
                        || (cpu_player.getPlayingCard1().getPalo().equals(cpu_player.getPlayingCard2().getPalo()) && (cpu_player.getPlayingCard1().getValorNumerico() >= 10 || cpu_player.getPlayingCard2().getValorNumerico() >= 10))
                        || (cpu_player.getPlayingCard1().getValorNumerico() >= 12 && cpu_player.getPlayingCard2().getValorNumerico() >= 12)) {

                    return Game.getInstance().getCrupier().getConta_bet() < 2 ? Player.BET : Player.CHECK;

                } else {

                    return (Helpers.SPRNG_GENERATOR.nextBoolean() && Helpers.float1DSecureCompare(Game.getInstance().getCrupier().getApuesta_actual(), 4 * Game.getInstance().getCrupier().getCiega_grande()) <= 0) ? Player.CHECK : Player.FOLD;
                }

            case Crupier.FLOP:

                if (board.size() == 0) {

                    flop1 = new org.alberta.poker.Card(Game.getInstance().getFlop1().getValorNumerico() - 2, getCardSuit(Game.getInstance().getFlop1()));
                    flop2 = new org.alberta.poker.Card(Game.getInstance().getFlop2().getValorNumerico() - 2, getCardSuit(Game.getInstance().getFlop2()));
                    flop3 = new org.alberta.poker.Card(Game.getInstance().getFlop3().getValorNumerico() - 2, getCardSuit(Game.getInstance().getFlop3()));

                    board.addCard(flop1);
                    board.addCard(flop2);
                    board.addCard(flop3);
                }

                break;

            case Crupier.TURN:

                if (board.size() == 0) {

                    flop1 = new org.alberta.poker.Card(Game.getInstance().getFlop1().getValorNumerico() - 2, getCardSuit(Game.getInstance().getFlop1()));
                    flop2 = new org.alberta.poker.Card(Game.getInstance().getFlop2().getValorNumerico() - 2, getCardSuit(Game.getInstance().getFlop2()));
                    flop3 = new org.alberta.poker.Card(Game.getInstance().getFlop3().getValorNumerico() - 2, getCardSuit(Game.getInstance().getFlop3()));

                    board.addCard(flop1);
                    board.addCard(flop2);
                    board.addCard(flop3);

                    turn = new org.alberta.poker.Card(Game.getInstance().getTurn().getValorNumerico() - 2, getCardSuit(Game.getInstance().getTurn()));

                    board.addCard(turn);
                }

                break;

            case Crupier.RIVER:

                if (board.size() == 0) {

                    flop1 = new org.alberta.poker.Card(Game.getInstance().getFlop1().getValorNumerico() - 2, getCardSuit(Game.getInstance().getFlop1()));
                    flop2 = new org.alberta.poker.Card(Game.getInstance().getFlop2().getValorNumerico() - 2, getCardSuit(Game.getInstance().getFlop2()));
                    flop3 = new org.alberta.poker.Card(Game.getInstance().getFlop3().getValorNumerico() - 2, getCardSuit(Game.getInstance().getFlop3()));

                    board.addCard(flop1);
                    board.addCard(flop2);
                    board.addCard(flop3);

                    turn = new org.alberta.poker.Card(Game.getInstance().getTurn().getValorNumerico() - 2, getCardSuit(Game.getInstance().getTurn()));

                    board.addCard(turn);

                    river = new org.alberta.poker.Card(Game.getInstance().getRiver().getValorNumerico() - 2, getCardSuit(Game.getInstance().getRiver()));

                    board.addCard(river);
                }

                break;
        }

        org.alberta.poker.HandEvaluator handevaluator = new org.alberta.poker.HandEvaluator();

        double strength = handevaluator.handRank(card1, card2, board, oponents);

        org.alberta.poker.ai.HandPotential potential = new org.alberta.poker.ai.HandPotential();

        double ppot = potential.ppot_raw(card1, card2, board, true);

        double npot = potential.getLastNPot();

        double effectiveStrength = strength + (1 - strength) * ppot - strength * npot;

        double poseffectiveStrength = strength + (1 - strength) * ppot;

        //System.out.println(cpu_player.getNickname()+" "+board.size()+"  ("+String.valueOf(oponents)+")  "+strength+"  "+effectiveStrength + "  "+poseffectiveStrength);
        if (poseffectiveStrength > 0.5f && Game.getInstance().getCrupier().getConta_bet() < 2) {

            return poseffectiveStrength > 0.7f ? Player.BET : (Helpers.SPRNG_GENERATOR.nextBoolean() ? Player.BET : Player.CHECK);

        } else if (effectiveStrength > 0.15f && Helpers.float1DSecureCompare(Game.getInstance().getCrupier().getApuesta_actual(), 3 * Game.getInstance().getCrupier().getCiega_grande()) <= 0) {

            return poseffectiveStrength > 0.35f ? Player.CHECK : (Helpers.SPRNG_GENERATOR.nextBoolean() ? Player.CHECK : Player.FOLD);
        }

        if (Game.getInstance().getCrupier().getConta_bet() == 0) {

            if (this.semi_bluff || (Helpers.SPRNG_GENERATOR.nextInt(5) == 0 && Game.getInstance().getCrupier().getFase() != Crupier.RIVER && ppot >= potOdds2())) {

                this.semi_bluff = true;

                return Player.BET;
            }

            return Player.CHECK;
        }

        if (Game.getInstance().getCrupier().getFase() == Crupier.RIVER && effectiveStrength >= potOdds()) {

            return Player.CHECK;
        }

        if (Game.getInstance().getCrupier().getFase() != Crupier.RIVER && ppot >= potOdds()) {

            return Player.CHECK;
        }

        float showdown_cost;

        if (Game.getInstance().getCrupier().getFase() == Crupier.RIVER) {
            return Player.FOLD;
        }

        if (Game.getInstance().getCrupier().getFase() == Crupier.FLOP) {
            showdown_cost = 4 * Game.getInstance().getCrupier().getApuesta_actual();
        } else {
            showdown_cost = Game.getInstance().getCrupier().getApuesta_actual();
        }

        if (effectiveStrength >= showdownOdds(showdown_cost)) {

            return Player.CHECK;
        }

        return Player.FOLD;

    }

    private float potOdds2() {

        return (2 * Game.getInstance().getCrupier().getApuesta_actual()) / (Game.getInstance().getCrupier().getBote_total() + 6 * Game.getInstance().getCrupier().getApuesta_actual());

    }

    private float potOdds() {

        return Game.getInstance().getCrupier().getConta_bet() / (Game.getInstance().getCrupier().getBote_total() + Game.getInstance().getCrupier().getConta_bet());

    }

    private float showdownOdds(float cost) {

        return (Game.getInstance().getCrupier().getConta_bet() + cost) / (Game.getInstance().getCrupier().getBote_total() + Game.getInstance().getCrupier().getConta_bet() + 2 * cost);
    }

    private int getCardSuit(Card carta) {

        return PALOS.indexOf(carta.getPalo());

    }

}
