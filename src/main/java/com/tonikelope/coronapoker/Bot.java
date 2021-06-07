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
    private volatile Crupier crupier = null;
    private volatile boolean semi_bluff = false;
    private volatile int slow_play = -1;
    private volatile org.alberta.poker.Card card1 = null;
    private volatile org.alberta.poker.Card card2 = null;
    private volatile int conta_call = 0;

    public Bot(RemotePlayer player) {
        cpu_player = player;
    }

    public boolean isSlow_play() {
        return (slow_play == 1);
    }

    //LLAMAR DESDE EL CRUPIER UNA VEZ REPARTIDAS LAS CARTAS AL JUGADOR
    public void resetBot() {

        if (crupier == null) {
            crupier = GameFrame.getInstance().getCrupier();
        }

        card1 = new org.alberta.poker.Card(cpu_player.getPlayingCard1().getValorNumerico() - 2, getCardSuit(cpu_player.getPlayingCard1()));
        card2 = new org.alberta.poker.Card(cpu_player.getPlayingCard2().getValorNumerico() - 2, getCardSuit(cpu_player.getPlayingCard2()));

        semi_bluff = false;
        conta_call = 0;

        if (crupier.getFase() == Crupier.PREFLOP) {
            slow_play = -1;
        }
    }

    public float getBetSize() {

        if (crupier != null) {

            float min_raise = Helpers.float1DSecureCompare(0f, crupier.getUltimo_raise()) < 0 ? crupier.getUltimo_raise() : crupier.getCiega_grande();

            float b;

            switch (crupier.getFase()) {
                case Crupier.PREFLOP:
                    b = Helpers.floatClean1D((3 + crupier.getLimp_count()) * crupier.getCiega_grande());
                    break;
                default:
                    b = Helpers.floatClean1D(crupier.getBote_total() / 3);
                    break;
            }

            if (Helpers.float1DSecureCompare(crupier.getApuesta_actual(), 0f) == 0) {
                return (isSlow_play() && crupier.getFase() != Crupier.RIVER) ? crupier.getCiega_grande() : Math.max(crupier.getCiega_grande(), b);
            } else {
                return crupier.getApuesta_actual() + Math.max(min_raise, b);
            }

        } else {

            return 0f;
        }
    }

    public int calculateBotDecision(int opponents) {

        int fase = crupier.getFase();
        int activos = crupier.getJugadoresActivos();

        if (fase == Crupier.PREFLOP) {

            //Esto es claramente muy mejorable
            int valor1 = cpu_player.getPlayingCard1().getValorNumerico();
            int valor2 = cpu_player.getPlayingCard2().getValorNumerico();
            boolean pareja = (valor1 == valor2);
            boolean suited = cpu_player.getPlayingCard1().getPalo().equals(cpu_player.getPlayingCard2().getPalo());
            boolean straight = Math.abs(valor1 - valor2) == 1;

            if (crupier.getConta_bet() > 0 && Helpers.float1DSecureCompare(cpu_player.getStack(), crupier.getApuesta_actual() - cpu_player.getBet()) <= 0) {

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

                return crupier.getConta_bet() < Bot.MAX_CONTA_BET ? Player.BET : Player.CHECK;

            } else if ((Helpers.float1DSecureCompare(crupier.getApuesta_actual() - cpu_player.getBet(), cpu_player.getStack() / 2) <= 0) && (pareja || (suited && Math.max(valor1, valor2) >= 10) || (suited && Math.max(valor1, valor2) >= 13) || (straight && Math.min(valor1, valor2) >= 10) || Math.min(valor1, valor2) >= 11)) {

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

                return (crupier.getConta_bet() < Bot.MAX_CONTA_BET && vamos) ? Player.BET : Player.CHECK;

            } else if (crupier.getConta_bet() == 0) {

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

            if (Helpers.float1DSecureCompare(crupier.getApuesta_actual() - cpu_player.getBet(), cpu_player.getStack() / 5) <= 0) {

                //Vemos el X% de apuestas con el resto de cartas siempre que no haya que poner más del 20% de nuestro stack para ver la apuesta
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

        //System.out.println(cpu_player.getNickname() + " " + Bot.BOT_COMMUNITY_CARDS.size() + "  (" + String.valueOf(opponents) + ")  " + strength + "  " + effectiveStrength + "  " + poseffectiveStrength);
        if (poseffectiveStrength >= 0.85f) {

            if (!isSlow_play() && fase == Crupier.FLOP && Helpers.CSPRNG_GENERATOR.nextBoolean()) {

                //Jugamos lenta el 50% de las veces
                slow_play = 1;

            }

            conta_call++;

            return (crupier.getConta_bet() < Bot.MAX_CONTA_BET && (!isSlow_play() || (fase != Crupier.FLOP && crupier.getConta_bet() == 0))) ? Player.BET : Player.CHECK;

        } else if (poseffectiveStrength >= 0.60f && !(effectiveStrength < 0.75f && fase == Crupier.RIVER && Helpers.float1DSecureCompare(cpu_player.getStack(), crupier.getApuesta_actual() - cpu_player.getBet()) <= 0)) {

            if (crupier.getConta_bet() == 0) {

                conta_call++;

                return Player.BET;

            } else if (!(conta_call == 0 && crupier.getConta_bet() >= 2)) {

                conta_call++;

                return Player.CHECK;
            }
        }

        if (crupier.getConta_bet() == 0 && Helpers.CSPRNG_GENERATOR.nextBoolean()) {

            conta_call++;

            if (this.semi_bluff || (fase != Crupier.RIVER && ppot >= 1.5 * potOdds2())) {

                //System.out.println(cpu_player.getNickname() + " BET semi bluff");
                this.semi_bluff = true;

                return Player.BET;
            }

            //System.out.println(cpu_player.getNickname() + " CHECK semi bluff");
            return Player.CHECK;
        }

        if (fase == Crupier.RIVER && effectiveStrength >= 1.5 * potOdds()) {

            //System.out.println(cpu_player.getNickname() + " CHECK POT ODDS "+ 1.5*potOdds());
            conta_call++;

            return Player.CHECK;
        }

        if (fase != Crupier.RIVER && ppot >= potOdds()) {

            //System.out.println(cpu_player.getNickname() + " CHECK POT ODDS " + potOdds());
            conta_call++;

            return Player.CHECK;
        }

        float showdown_cost;

        if (fase == Crupier.RIVER) {

            return Player.FOLD;
        }

        if (fase == Crupier.FLOP) {
            showdown_cost = 4 * crupier.getApuesta_actual();
        } else {
            showdown_cost = crupier.getApuesta_actual();
        }

        if (effectiveStrength >= 1.5 * showdownOdds(showdown_cost)) {

            //System.out.println(cpu_player.getNickname() + " CHECK SHOWDOWN ODDS " + showdownOdds(showdown_cost));
            conta_call++;

            return Player.CHECK;
        }

        return Player.FOLD;

    }

    private float potOdds2() {

        return (4 * crupier.getCiega_grande()) / (crupier.getBote_total() + crupier.getApuestas() + 12 * crupier.getCiega_grande());

    }

    private float potOdds() {

        return (crupier.getApuesta_actual() - cpu_player.getBet()) / (crupier.getBote_total() + crupier.getApuestas() + (crupier.getApuesta_actual() - cpu_player.getBet()));

    }

    private float showdownOdds(float cost) {

        return ((crupier.getApuesta_actual() - cpu_player.getBet()) + cost) / (crupier.getBote_total() + crupier.getApuestas() + (crupier.getApuesta_actual() - cpu_player.getBet()) + 2 * cost);
    }

    public static int getCardSuit(Card carta) {

        return PALOS.indexOf(carta.getPalo());

    }

}
