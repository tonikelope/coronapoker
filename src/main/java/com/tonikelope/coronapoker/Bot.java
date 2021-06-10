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
    public static final int MAX_CONTA_BET = 2;
    public static final org.alberta.poker.Hand BOT_COMMUNITY_CARDS = new org.alberta.poker.Hand();
    public static final org.alberta.poker.HandEvaluator HANDEVALUATOR = new org.alberta.poker.HandEvaluator();
    public static final org.alberta.poker.ai.HandPotential HANDPOTENTIAL = new org.alberta.poker.ai.HandPotential();

    private volatile RemotePlayer cpu_player = null;
    private volatile boolean semi_bluff = false;
    private volatile org.alberta.poker.Card card1 = null;
    private volatile org.alberta.poker.Card card2 = null;
    private volatile int conta_call = 0;

    public Bot(RemotePlayer player) {
        cpu_player = player;
    }

    //LLAMAR DESDE EL CRUPIER UNA VEZ REPARTIDAS LAS CARTAS AL JUGADOR
    public void resetBot() {

        card1 = new org.alberta.poker.Card(cpu_player.getPlayingCard1().getValorNumerico() - 2, getCardSuit(cpu_player.getPlayingCard1()));
        card2 = new org.alberta.poker.Card(cpu_player.getPlayingCard2().getValorNumerico() - 2, getCardSuit(cpu_player.getPlayingCard2()));

        semi_bluff = false;
        conta_call = 0;
    }

    public float getBetSize() {

        float min_raise = Helpers.float1DSecureCompare(0f, GameFrame.getInstance().getCrupier().getUltimo_raise()) < 0 ? GameFrame.getInstance().getCrupier().getUltimo_raise() : GameFrame.getInstance().getCrupier().getCiega_grande();

        float b;

        switch (GameFrame.getInstance().getCrupier().getFase()) {
            case Crupier.PREFLOP:
                b = Helpers.floatClean1D((3 + GameFrame.getInstance().getCrupier().getLimpersCount()) * GameFrame.getInstance().getCrupier().getCiega_grande()); //Classic
                break;
            default:
                b = Helpers.floatClean1D(GameFrame.getInstance().getCrupier().getBote_total() / 3); //Gentle because we don't want a quick massacre
                break;
        }

        if (Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getApuesta_actual(), 0f) == 0 || (GameFrame.getInstance().getCrupier().getFase() == Crupier.PREFLOP && Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getApuesta_actual(), GameFrame.getInstance().getCrupier().getCiega_grande()) == 0)) {
            return Math.max(GameFrame.getInstance().getCrupier().getCiega_grande(), b);
        } else {
            return Math.max(GameFrame.getInstance().getCrupier().getApuesta_actual() + min_raise, b);
        }

    }

    public int calculateBotDecision(int opponents) {

        int fase = GameFrame.getInstance().getCrupier().getFase();
        int activos = GameFrame.getInstance().getCrupier().getJugadoresActivos();

        if (fase == Crupier.PREFLOP) {

            //Esto es claramente muy mejorable
            int valor1 = cpu_player.getPlayingCard1().getValorNumerico();
            int valor2 = cpu_player.getPlayingCard2().getValorNumerico();
            boolean pareja = (valor1 == valor2);
            boolean suited = cpu_player.getPlayingCard1().getPalo().equals(cpu_player.getPlayingCard2().getPalo());
            boolean straight = Math.abs(valor1 - valor2) == 1;

            if (GameFrame.getInstance().getCrupier().getConta_bet() > 0 && Helpers.float1DSecureCompare(cpu_player.getStack(), GameFrame.getInstance().getCrupier().getApuesta_actual() - cpu_player.getBet()) <= 0) {

                //Si la apuesta actual nos obliga a ir ALL-IN sólo lo hacemos con manos PREMIUM o el el 50% de las otras veces con manos buenas que no llegan a PREMIUM
                if ((pareja && valor1 >= 10) || (suited && Math.max(valor1, valor2) == 14) || (Helpers.CSPRNG_GENERATOR.nextBoolean() && (pareja && valor1 >= 7) || (suited && Math.max(valor1, valor2) >= 13) || Math.min(valor1, valor2) >= 12 || (suited && straight && Math.min(valor1, valor2) == 7))) {
                    conta_call++;

                    return Player.CHECK;

                } else {

                    return Player.FOLD;
                }

            } else if ((pareja && valor1 >= 7) || (suited && Math.max(valor1, valor2) >= 13) || Math.min(valor1, valor2) >= 12 || (suited && straight && Math.min(valor1, valor2) == 7)) {

                //Manos buenas (sin ser todas PREMIUM)
                conta_call++;

                return GameFrame.getInstance().getCrupier().getConta_bet() < Bot.MAX_CONTA_BET ? Player.BET : Player.CHECK;

            } else if ((Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getApuesta_actual() - cpu_player.getBet(), cpu_player.getStack() / 2) <= 0) && (pareja || (suited && Math.max(valor1, valor2) >= 10) || (suited && Math.max(valor1, valor2) >= 13) || (straight && Math.min(valor1, valor2) >= 10) || Math.min(valor1, valor2) >= 11)) {

                //El X% de las veces apostamos con una mano no tan fuerte (siempre que no haya que poner más del 50% de nuestro stack)
                boolean vamos;

                conta_call++;

                if (activos <= 4) {

                    //50%
                    vamos = Helpers.CSPRNG_GENERATOR.nextBoolean();

                } else if (activos <= 6) {

                    //40%
                    vamos = Helpers.CSPRNG_GENERATOR.nextInt(10) <= 3;

                } else if (activos <= 8) {

                    //30%
                    vamos = Helpers.CSPRNG_GENERATOR.nextInt(10) <= 2;

                } else {

                    //20%
                    vamos = Helpers.CSPRNG_GENERATOR.nextInt(10) <= 1;
                }

                return (GameFrame.getInstance().getCrupier().getConta_bet() < Bot.MAX_CONTA_BET && vamos) ? Player.BET : Player.CHECK;

            } else if (GameFrame.getInstance().getCrupier().getConta_bet() == 0) {

                //Limpeamos el X% de las manos a ver si suena la flauta
                boolean vamos;

                if (activos <= 4) {
                    //50%
                    vamos = Helpers.CSPRNG_GENERATOR.nextBoolean();

                } else if (activos <= 6) {

                    //40%
                    vamos = Helpers.CSPRNG_GENERATOR.nextInt(10) <= 3;

                } else if (activos <= 8) {

                    //30%
                    vamos = Helpers.CSPRNG_GENERATOR.nextInt(10) <= 2;

                } else {

                    //20%
                    vamos = Helpers.CSPRNG_GENERATOR.nextInt(10) <= 1;
                }

                if (vamos) {
                    conta_call++;
                    return Player.CHECK;
                }

            }

            if (Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getApuesta_actual() - cpu_player.getBet(), cpu_player.getStack() / 10) <= 0) {

                //Vemos el X% de apuestas con el resto de cartas siempre que no haya que poner más del 10% de nuestro stack para ver la apuesta
                boolean vamos;

                if (activos <= 4) {

                    //20%
                    vamos = Helpers.CSPRNG_GENERATOR.nextInt(20) <= 3;

                } else if (activos <= 6) {

                    //15%
                    vamos = Helpers.CSPRNG_GENERATOR.nextInt(20) <= 2;

                } else if (activos <= 8) {

                    //10%
                    vamos = Helpers.CSPRNG_GENERATOR.nextInt(20) <= 1;

                } else {

                    //5%
                    vamos = Helpers.CSPRNG_GENERATOR.nextInt(20) == 0;
                }

                if (vamos) {
                    conta_call++;

                    return Player.CHECK;
                }
            }

            return Player.FOLD;
        }

        double strength = HANDEVALUATOR.handRank(card1, card2, Bot.BOT_COMMUNITY_CARDS, opponents);

        double ppot = HANDPOTENTIAL.ppot_raw(card1, card2, Bot.BOT_COMMUNITY_CARDS, true);

        double npot = HANDPOTENTIAL.getLastNPot();

        double effectiveStrength = strength + (1 - strength) * ppot - strength * npot;

        double poseffectiveStrength = strength + (1 - strength) * ppot;

        if (poseffectiveStrength >= 0.85f) {

            conta_call++;

            return (GameFrame.getInstance().getCrupier().getConta_bet() < Bot.MAX_CONTA_BET) ? Player.BET : Player.CHECK;

        } else if (poseffectiveStrength >= 0.60f && !(effectiveStrength < 0.75f && fase == Crupier.RIVER && Helpers.float1DSecureCompare(cpu_player.getStack(), GameFrame.getInstance().getCrupier().getApuesta_actual() - cpu_player.getBet()) <= 0)) {

            if (GameFrame.getInstance().getCrupier().getConta_bet() == 0) {

                conta_call++;

                return Player.BET;

            } else if (!(conta_call == 0 && GameFrame.getInstance().getCrupier().getConta_bet() >= 2)) {

                conta_call++;

                return Player.CHECK;
            }
        }

        if (GameFrame.getInstance().getCrupier().getConta_bet() == 0 && Helpers.CSPRNG_GENERATOR.nextBoolean()) {

            conta_call++;

            if (this.semi_bluff || (fase != Crupier.RIVER && ppot >= 2 * potOdds2())) {

                this.semi_bluff = true;

                return Player.BET;
            }

            return Player.CHECK;
        }

        if (fase == Crupier.RIVER && effectiveStrength >= 2 * potOdds()) {

            conta_call++;

            return Player.CHECK;
        }

        if (fase != Crupier.RIVER && ppot >= potOdds()) {

            conta_call++;

            return Player.CHECK;
        }

        float showdown_cost;

        if (fase == Crupier.RIVER) {

            return Player.FOLD;
        }

        if (fase == Crupier.FLOP) {
            showdown_cost = 4 * GameFrame.getInstance().getCrupier().getApuesta_actual();
        } else {
            showdown_cost = GameFrame.getInstance().getCrupier().getApuesta_actual();
        }

        if (effectiveStrength >= 2 * showdownOdds(showdown_cost) && !this.semi_bluff) {

            conta_call++;

            return Player.CHECK;
        }

        return Player.FOLD;

    }

    private float potOdds2() {

        return (4 * GameFrame.getInstance().getCrupier().getCiega_grande()) / (GameFrame.getInstance().getCrupier().getBote_total() + 12 * GameFrame.getInstance().getCrupier().getCiega_grande());

    }

    private float potOdds() {

        return (GameFrame.getInstance().getCrupier().getApuesta_actual() - cpu_player.getBet()) / (GameFrame.getInstance().getCrupier().getBote_total() + (GameFrame.getInstance().getCrupier().getApuesta_actual() - cpu_player.getBet()));

    }

    private float showdownOdds(float cost) {

        return ((GameFrame.getInstance().getCrupier().getApuesta_actual() - cpu_player.getBet()) + cost) / (GameFrame.getInstance().getCrupier().getBote_total() + (GameFrame.getInstance().getCrupier().getApuesta_actual() - cpu_player.getBet()) + 2 * cost);
    }

    public static int getCardSuit(Card carta) {

        return PALOS.indexOf(carta.getPalo());

    }

}
