/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronapoker;

/**
 *
 * FULLY EXPERIMENTAL. B-A-S-E-D on the mythical Alberta's Loki Bot
 * https://poker.cs.ualberta.ca/publications/papp.msc.pdf
 *
 * @author tonikelope
 */
public class Bot {

    public static final String PALOS = "TDCP";
    public static final org.alberta.poker.Hand BOT_COMMUNITY_CARDS = new org.alberta.poker.Hand();
    public static final org.alberta.poker.HandEvaluator HANDEVALUATOR = new org.alberta.poker.HandEvaluator();
    public static final org.alberta.poker.ai.HandPotential HANDPOTENTIAL = new org.alberta.poker.ai.HandPotential();

    private RemotePlayer cpu_player = null;
    private boolean semi_bluff = false;
    private int check_raise = 0;
    private org.alberta.poker.Card card1 = null;
    private org.alberta.poker.Card card2 = null;

    public Bot(RemotePlayer player) {
        this.cpu_player = player;
        semi_bluff = false;
        check_raise = 0;
    }

    //LLAMAR DESDE EL CRUPIER UNA VEZ REPARTIDAS LAS CARTAS AL JUGADOR
    public void resetBot() {

        card1 = new org.alberta.poker.Card(cpu_player.getPlayingCard1().getValorNumerico() - 2, getCardSuit(cpu_player.getPlayingCard1()));
        card2 = new org.alberta.poker.Card(cpu_player.getPlayingCard2().getValorNumerico() - 2, getCardSuit(cpu_player.getPlayingCard2()));
        semi_bluff = false;
    }

    public int calculateBotDecision(int oponents) {

        if (Game.getInstance().getCrupier().getFase() == Crupier.PREFLOP) {

            //Esto es claramente mejorable
            boolean pareja = cpu_player.getPlayingCard1().getValorNumerico() == cpu_player.getPlayingCard2().getValorNumerico();

            if ((pareja && cpu_player.getPlayingCard1().getValorNumerico() >= 7)
                    || (cpu_player.getPlayingCard1().getPalo().equals(cpu_player.getPlayingCard2().getPalo()) && (cpu_player.getPlayingCard1().getValorNumerico() >= 10 || cpu_player.getPlayingCard2().getValorNumerico() >= 10))
                    || (cpu_player.getPlayingCard1().getValorNumerico() >= 12 && cpu_player.getPlayingCard2().getValorNumerico() >= 12)) {

                if (check_raise == 0 && Helpers.SPRNG_GENERATOR.nextBoolean()) {

                    check_raise = 1;

                    return Player.CHECK;

                } else if (check_raise == 1) {

                    check_raise = 2;

                    return Player.BET;
                }

                return Game.getInstance().getCrupier().getConta_bet() < 2 ? Player.BET : Player.CHECK;

            } else if (Game.getInstance().getCrupier().getConta_bet() == 0 && Helpers.SPRNG_GENERATOR.nextBoolean()) {

                //Limpeamos el 50% de las manos a ver si suena la flauta
                return Player.CHECK;
            }

            return Player.FOLD;
        }

        double strength = HANDEVALUATOR.handRank(card1, card2, Bot.BOT_COMMUNITY_CARDS, oponents);

        double ppot = HANDPOTENTIAL.ppot_raw(card1, card2, Bot.BOT_COMMUNITY_CARDS, true);

        double npot = HANDPOTENTIAL.getLastNPot();

        double effectiveStrength = strength + (1 - strength) * ppot - strength * npot;

        double poseffectiveStrength = strength + (1 - strength) * ppot;

        //System.out.println(cpu_player.getNickname()+" "+board.size()+"  ("+String.valueOf(oponents)+")  "+strength+"  "+effectiveStrength + "  "+poseffectiveStrength);
        if (poseffectiveStrength >= 0.80f) {

            return Player.BET;

        } else if (poseffectiveStrength >= 0.60f) {

            return Game.getInstance().getCrupier().getConta_bet() < 2 ? Player.BET : Player.CHECK;

        } else if (poseffectiveStrength >= 0.40f) {

            return (Game.getInstance().getCrupier().getConta_bet() < 2 && Helpers.SPRNG_GENERATOR.nextBoolean()) ? Player.BET : Player.CHECK;
        }

        if (poseffectiveStrength > 0.5f) {

            return (poseffectiveStrength > 0.85f && Game.getInstance().getCrupier().getConta_bet() < 2) ? Player.BET : ((Helpers.SPRNG_GENERATOR.nextBoolean() && Game.getInstance().getCrupier().getConta_bet() < 2) ? Player.BET : Player.CHECK);
        }

        if (Game.getInstance().getCrupier().getConta_bet() == 0) {

            if (this.semi_bluff || (Helpers.SPRNG_GENERATOR.nextBoolean() && Game.getInstance().getCrupier().getFase() != Crupier.RIVER && ppot >= potOdds2())) {

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

    public static int getCardSuit(Card carta) {

        return PALOS.indexOf(carta.getPalo());

    }

}
