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
    private org.alberta.poker.Card card1 = null;
    private org.alberta.poker.Card card2 = null;
    private int conta_call = 0;

    public Bot(RemotePlayer player) {
        this.cpu_player = player;
        semi_bluff = false;
        conta_call = 0;
    }

    //LLAMAR DESDE EL CRUPIER UNA VEZ REPARTIDAS LAS CARTAS AL JUGADOR
    public void resetBot() {

        card1 = new org.alberta.poker.Card(cpu_player.getPlayingCard1().getValorNumerico() - 2, getCardSuit(cpu_player.getPlayingCard1()));
        card2 = new org.alberta.poker.Card(cpu_player.getPlayingCard2().getValorNumerico() - 2, getCardSuit(cpu_player.getPlayingCard2()));
        semi_bluff = false;
        conta_call = 0;
    }

    public int calculateBotDecision(int opponents) {

        if (Game.getInstance().getCrupier().getFase() == Crupier.PREFLOP) {

            //Esto es claramente muy mejorable
            boolean pareja = cpu_player.getPlayingCard1().getValorNumerico() == cpu_player.getPlayingCard2().getValorNumerico();

            if ((pareja && cpu_player.getPlayingCard1().getValorNumerico() >= 7)
                    || (cpu_player.getPlayingCard1().getPalo().equals(cpu_player.getPlayingCard2().getPalo()) && (cpu_player.getPlayingCard1().getValorNumerico() >= 10 || cpu_player.getPlayingCard2().getValorNumerico() >= 10))
                    || (cpu_player.getPlayingCard1().getValorNumerico() >= 12 && cpu_player.getPlayingCard2().getValorNumerico() >= 12)) {

                conta_call++;

                return Game.getInstance().getCrupier().getConta_bet() < 2 ? Player.BET : Player.CHECK;

            } else if (Game.getInstance().getCrupier().getConta_bet() == 0 && Helpers.SPRNG_GENERATOR.nextBoolean()) {

                //Limpeamos el 50% de las manos a ver si suena la flauta
                conta_call++;

                return Player.CHECK;

            } else if (Helpers.float1DSecureCompare(Game.getInstance().getCrupier().getApuesta_actual() - cpu_player.getBet(), cpu_player.getStack() / 4) <= 0 && Helpers.SPRNG_GENERATOR.nextInt(5) == 0) {

                //Vemos el 20% de apuestas con cartas mediocres hasta el 25% de nuestro stack
                conta_call++;

                return Player.CHECK;
            }

            return Player.FOLD;
        }

        double strength = HANDEVALUATOR.handRank(card1, card2, Bot.BOT_COMMUNITY_CARDS, opponents);

        double ppot = HANDPOTENTIAL.ppot_raw(card1, card2, Bot.BOT_COMMUNITY_CARDS, true);

        double npot = HANDPOTENTIAL.getLastNPot();

        double effectiveStrength = strength + (1 - strength) * ppot - strength * npot;

        double poseffectiveStrength = strength + (1 - strength) * ppot;

        //System.out.println(cpu_player.getNickname() + " " + Bot.BOT_COMMUNITY_CARDS.size() + "  (" + String.valueOf(opponents) + ")  " + strength + "  " + effectiveStrength + "  " + poseffectiveStrength);
        if (poseffectiveStrength >= 0.85f) {

            conta_call++;

            return Game.getInstance().getCrupier().getConta_bet() < 2 ? Player.BET : Player.CHECK;

        } else if (poseffectiveStrength >= 0.60f && !(effectiveStrength < 0.70f && Game.getInstance().getCrupier().getFase() == Crupier.RIVER && Helpers.float1DSecureCompare(Game.getInstance().getCrupier().getApuesta_actual() - cpu_player.getBet(), cpu_player.getStack()) >= 0)) {

            if (Game.getInstance().getCrupier().getConta_bet() == 0) {

                conta_call++;

                return Player.BET;

            } else if (!(conta_call == 0 && Game.getInstance().getCrupier().getConta_bet() >= 2)) {

                conta_call++;

                return Player.CHECK;
            }

        }

        if (Game.getInstance().getCrupier().getConta_bet() == 0 && Helpers.SPRNG_GENERATOR.nextBoolean()) {

            conta_call++;

            if (this.semi_bluff || (Game.getInstance().getCrupier().getFase() != Crupier.RIVER && ppot >= potOdds2())) {

                //System.out.println(cpu_player.getNickname() + " BET semi bluff");
                this.semi_bluff = true;

                return Player.BET;
            }

            //System.out.println(cpu_player.getNickname() + " CHECK semi bluff");
            return Player.CHECK;
        }

        if (Game.getInstance().getCrupier().getFase() == Crupier.RIVER && effectiveStrength >= 1.5 * potOdds()) {

            //System.out.println(cpu_player.getNickname() + " CHECK POT ODDS "+ 1.5*potOdds());
            conta_call++;

            return Player.CHECK;
        }

        if (Game.getInstance().getCrupier().getFase() != Crupier.RIVER && ppot >= potOdds()) {

            //System.out.println(cpu_player.getNickname() + " CHECK POT ODDS " + potOdds());
            conta_call++;

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

            //System.out.println(cpu_player.getNickname() + " CHECK SHOWDOWN ODDS " + showdownOdds(showdown_cost));
            conta_call++;

            return Player.CHECK;
        }

        return Player.FOLD;

    }

    private float potOdds2() {

        return (4 * Game.getInstance().getCrupier().getCiega_grande()) / (Game.getInstance().getCrupier().getBote_total() + Game.getInstance().getCrupier().getApuestas() + 12 * Game.getInstance().getCrupier().getCiega_grande());

    }

    private float potOdds() {

        return (Game.getInstance().getCrupier().getApuesta_actual() - cpu_player.getBet()) / (Game.getInstance().getCrupier().getBote_total() + Game.getInstance().getCrupier().getApuestas() + (Game.getInstance().getCrupier().getApuesta_actual() - cpu_player.getBet()));

    }

    private float showdownOdds(float cost) {

        return ((Game.getInstance().getCrupier().getApuesta_actual() - cpu_player.getBet()) + cost) / (Game.getInstance().getCrupier().getBote_total() + Game.getInstance().getCrupier().getApuestas() + (Game.getInstance().getCrupier().getApuesta_actual() - cpu_player.getBet()) + 2 * cost);
    }

    public static int getCardSuit(Card carta) {

        return PALOS.indexOf(carta.getPalo());

    }

}
